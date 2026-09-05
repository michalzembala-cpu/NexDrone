package com.nexplay.dronepreflight.assistant

data class IntentHit(val tool: AssistantTool, val args: Map<String, String>)

/**
 * Offline połowa mózgu: dopasowanie fraz do tego samego katalogu narzędzi, z którego korzysta
 * model. Dzięki temu asystent działa od razu po instalacji — bez klucza, bez sieci, bez kosztu
 * — a w polu, gdzie zasięgu często po prostu nie ma, to jedyna połowa, która w ogóle zadziała.
 *
 * Wszystko porównujemy na tekście bez ogonków i bez interpunkcji: rozpoznawanie mowy bywa
 * niekonsekwentne w polskich znakach, a piszący z ręki i tak rzadko je stawia.
 */
object IntentMatcher {

    private class Rule(
        val tool: String,
        /** Każda grupa musi trafić co najmniej jednym słowem. */
        val groups: List<List<String>>,
        val args: ((normalized: String, original: String) -> Map<String, String>)? = null,
    )

    fun normalize(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s.lowercase()) {
            sb.append(
                when (ch) {
                    'ą' -> 'a'; 'ć' -> 'c'; 'ę' -> 'e'; 'ł' -> 'l'; 'ń' -> 'n'
                    'ó' -> 'o'; 'ś' -> 's'; 'ż', 'ź' -> 'z'
                    else -> ch
                }
            )
        }
        return sb.toString()
            .map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("")
            .split(' ')
            .filter { it.isNotEmpty() }
            .joinToString(" ")
    }

    private val yes = setOf("tak", "yes", "ok", "okej", "jasne", "dawaj", "potwierdzam", "zapisz")
    private val no = setOf("nie", "no", "anuluj", "cancel", "stop", "zostaw")

    fun isYes(s: String) = normalize(s) in yes
    fun isNo(s: String) = normalize(s) in no

    /** Ostatni fragment po słowie-wyzwalaczu, z oryginału — notatka trafia do dziennika dosłownie. */
    private fun tail(original: String, vararg triggers: String): String {
        val lower = original.lowercase()
        var best = -1
        for (t in triggers) {
            val i = lower.lastIndexOf(t)
            if (i >= 0 && i + t.length > best) best = i + t.length
        }
        if (best < 0) return ""
        return original.substring(best).trim(' ', ',', ':', '.', '-')
    }

    // Kolejność ma znaczenie: zapisy i akcje są bardziej szczegółowe niż odczyty, więc idą
    // pierwsze — inaczej „wyczyść checklistę” trafiłoby w odczyt checklisty.
    private val rules = listOf(
        Rule(
            tool = "reset_checklist",
            groups = listOf(
                listOf("wyczysc", "zresetuj", "od nowa", "reset"),
                listOf("checkliste", "checklista", "checklisty", "liste"),
            ),
        ),
        Rule(
            tool = "check_item",
            groups = listOf(listOf("odhacz", "zaznacz", "zrobione", "gotowe")),
            args = { _, orig -> mapOf("text" to tail(orig, "odhacz", "zaznacz", "zrobione", "gotowe")) },
        ),
        Rule(
            tool = "log_flight",
            groups = listOf(
                listOf("zapisz", "dopisz", "zaloguj"),
                listOf("lot", "lotu", "przelot"),
            ),
            args = { n, orig ->
                val minutes = Regex("(\\d{1,3})\\s*(minut|min)").find(n)?.groupValues?.get(1).orEmpty()
                mapOf(
                    "note" to tail(orig, "lot", "lotu", "przelot"),
                    "minutes" to minutes,
                )
            },
        ),

        Rule(
            tool = "refresh",
            groups = listOf(listOf("odswiez", "pobierz", "aktualizuj", "sprawdz pogode", "refresh")),
        ),
        Rule(
            tool = "set_hour",
            groups = listOf(
                listOf("godzina", "godzine", "o godzinie", "ustaw godzine"),
            ),
            args = { n, _ ->
                mapOf("hour" to (Regex("\\b([01]?\\d|2[0-3])\\b").find(n)?.groupValues?.get(1) ?: "12"))
            },
        ),

        Rule(
            tool = "get_best_window",
            groups = listOf(listOf("okno", "najlepszy moment", "kiedy lecec", "kiedy moge lecec")),
        ),
        Rule(
            tool = "get_kp",
            groups = listOf(listOf("kp", "geomagnet", "kompas", "burza magnetyczna")),
        ),
        Rule(
            tool = "get_wind",
            groups = listOf(listOf("wiatr", "wiatru", "porywy", "podmuchy")),
        ),
        Rule(
            tool = "get_checklist",
            groups = listOf(listOf("checklista", "checkliste", "checklisty", "lista kontrolna")),
        ),
        Rule(
            tool = "get_last_flight",
            groups = listOf(listOf("ostatni lot", "poprzedni lot", "dziennik", "historia lotow")),
        ),
        Rule(
            tool = "get_flyability",
            groups = listOf(
                listOf(
                    "moge lecec", "can i fly", "da sie lecec", "czy lecec", "warunki na lot",
                    "moge latac", "czy polece", "ocena",
                ),
            ),
        ),
        Rule(
            tool = "get_conditions",
            groups = listOf(listOf("pogoda", "pogode", "warunki", "temperatura", "jak jest")),
        ),
    )

    fun match(utterance: String, tools: AssistantTools): IntentHit? {
        val n = normalize(utterance)
        if (n.isEmpty()) return null

        for (rule in rules) {
            val all = rule.groups.all { group -> group.any { n.contains(normalize(it)) } }
            if (!all) continue
            val tool = tools.find(rule.tool) ?: continue
            val args = rule.args?.invoke(n, utterance) ?: emptyMap()
            return IntentHit(tool, tools.sanitize(tool, args))
        }
        return null
    }
}
