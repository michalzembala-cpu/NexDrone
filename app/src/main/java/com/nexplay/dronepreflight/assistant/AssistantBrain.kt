package com.nexplay.dronepreflight.assistant

import android.util.Log
import com.nexplay.dronepreflight.data.SettingsStore
import com.nexplay.dronepreflight.ui.PreflightViewModel
import kotlinx.coroutines.flow.first

/**
 * Prowadzi jedną wypowiedź do odpowiedzi. Najpierw dopasowanie offline — natychmiastowe, darmowe
 * i działające bez zasięgu — potem model, jeśli user go włączył. Cokolwiek zapisuje dane,
 * zatrzymuje się tu na głosowe „tak” albo „nie”, niezależnie od tego, która połowa to wymyśliła.
 */
class AssistantBrain(
    private val vm: PreflightViewModel,
    private val settings: SettingsStore,
) {
    private val tools = AssistantTools(vm)
    private var pending: Pair<AssistantTool, Map<String, String>>? = null

    val awaitingConfirmation: Boolean get() = pending != null

    fun cancelPending() {
        pending = null
    }

    private fun systemPrompt(): String {
        val s = vm.state.value
        val where = s.snapshot?.locationName ?: "nieznana lokalizacja"
        val verdict = s.assessment?.overall?.name ?: "brak oceny"
        // Czytane na głos, więc długość jest tu wszystkim. Model dostaje też zakaz zgadywania
        // liczb: każda realna wartość jest dostępna przez narzędzie, a wymyślona pogoda przy
        // decyzji o starcie drona jest gorsza niż brak odpowiedzi.
        return "Jesteś głosowym asystentem w aplikacji NexDrone, która ocenia warunki do lotu dronem. " +
            "Aktualna lokalizacja: $where. Bieżąca ocena lotu: $verdict. " +
            "Odpowiadaj po polsku, maksymalnie dwoma krótkimi zdaniami — Twoja odpowiedź jest czytana na głos. " +
            "Wszystkie liczby i oceny bierz wyłącznie z narzędzi. Nigdy nie zgaduj pogody, wiatru ani Kp. " +
            "Jeśli narzędzia tego nie pokazują, powiedz wprost, że tego nie wiesz. " +
            "Nie zachęcaj do lotu wbrew ocenie NO_GO."
    }

    suspend fun ask(utterance: String): String {
        if (utterance.isBlank()) return "Nie usłyszałem."

        // ---- najpierw rozstrzygnij zawieszony zapis ----
        pending?.let { (tool, args) ->
            when {
                IntentMatcher.isYes(utterance) -> {
                    pending = null
                    return safely { tool.run(args) }
                }
                IntentMatcher.isNo(utterance) -> {
                    pending = null
                    return "Anulowane."
                }
                // Cokolwiek innego traktujemy jak nową komendę, a nie jak uparte dopytywanie:
                // user wyraźnie poszedł dalej, a niepotwierdzony zapis po prostu się nie dzieje.
                else -> pending = null
            }
        }

        // ---- intencje offline ----
        IntentMatcher.match(utterance, tools)?.let { hit ->
            if (hit.tool.kind == ToolKind.WRITE) {
                pending = hit.tool to hit.args
                return hit.tool.preview?.invoke(hit.args) ?: "Potwierdzasz?"
            }
            return safely { hit.tool.run(hit.args) }
        }

        // ---- model, tylko gdy user go włączył ----
        val useLlm = settings.assistantUseLlm.first()
        val key = settings.assistantApiKey.first()
        if (useLlm && key.isNotBlank()) {
            return try {
                when (val out = ClaudeClient(key.trim()).ask(utterance, tools, systemPrompt())) {
                    is LlmOutcome.Answer -> out.text
                    is LlmOutcome.NeedsConfirmation -> {
                        pending = out.tool to out.args
                        out.tool.preview?.invoke(out.args) ?: "Potwierdzasz?"
                    }
                }
            } catch (e: Exception) {
                // Brak zasięgu w polu to norma, nie awaria — połowa offline dalej działa.
                Log.w("AssistantBrain", "LLM failed", e)
                "Nie mogę teraz dosięgnąć modelu. Komendy offline działają dalej."
            }
        }

        return "Nie rozumiem. Spróbuj: czy mogę lecieć, jaki jest wiatr, ile zostało na checkliście, odśwież."
    }

    private inline fun safely(run: () -> String): String = try {
        run()
    } catch (e: Exception) {
        Log.w("AssistantBrain", "tool failed", e)
        "Coś poszło nie tak przy tej komendzie."
    }
}
