package com.nexplay.dronepreflight.ui

data class ChecklistItem(
    val id: String,
    val title: String,
    val hint: String,
    val auto: Boolean = false,
)

data class ChecklistSection(val title: String, val items: List<ChecklistItem>)

/** IDs zaznaczane automatycznie na podstawie oceny pogody z Dashboarda. */
val AutoWeatherIds: Set<String> = setOf("wind", "temp", "kp", "precip", "storm")

val PreflightChecklist: List<ChecklistSection> = listOf(
    ChecklistSection(
        title = "Planowanie lotu",
        items = listOf(
            ChecklistItem(
                "route",
                "Trasa lotu naniesiona na mapę",
                "Sprawdź strefy geograficzne i obiekty o charakterze strategicznym (drone.pansa.pl).",
            ),
            ChecklistItem(
                "takeoff",
                "Miejsce startu i lądowania",
                "Utwardzona, niepiaszczysta powierzchnia – unikaj kurzu, który zanieczyszcza silniki.",
            ),
            ChecklistItem(
                "range",
                "Maksymalny zasięg BSP",
                "Uwzględnij warunki atmosferyczne. Musi wystarczyć energii również na powrót.",
            ),
            ChecklistItem(
                "altitude",
                "Zmiany wysokości terenu",
                "Maksymalna wysokość lotu: 120 m nad najbliższym punktem na ziemi.",
            ),
            ChecklistItem(
                "obstacles",
                "Przeszkody ograniczające widoczność",
                "VLOS: musisz utrzymać BSP w polu widzenia gołym okiem przez cały lot.",
            ),
            ChecklistItem(
                "bystanders",
                "Analiza obecności osób postronnych",
                "Jeśli się pojawią – jak najszybciej omiń, zachowując bezpieczną odległość.",
            ),
            ChecklistItem(
                "em",
                "Źródła zakłóceń elektromagnetycznych",
                "Maszty BTS, linie energetyczne, Wi-Fi, duże konstrukcje stalowe.",
            ),
            ChecklistItem(
                "observer",
                "Zasady komunikacji z obserwatorem",
                "Jeżeli obserwator uczestniczy w misji – ustalcie sygnały i procedury.",
            ),
        ),
    ),
    ChecklistSection(
        title = "Pogoda i środowisko",
        items = listOf(
            ChecklistItem(
                "wind",
                "Wiatr w limitach BSP",
                "Prędkość i kierunek. Dron lata względem powietrza – uwzględnij dryf.",
                auto = true,
            ),
            ChecklistItem(
                "temp",
                "Temperatura w zakresie roboczym",
                "Sprawdź instrukcję obsługi BSP i własny komfort pilotowania.",
                auto = true,
            ),
            ChecklistItem(
                "kp",
                "KP index sprawdzony",
                "Wysoka wartość = zakłócenia GNSS i łączności.",
                auto = true,
            ),
            ChecklistItem(
                "precip",
                "Brak opadów i mgły",
                "Widzialność + odporność BSP na wilgoć.",
                auto = true,
            ),
            ChecklistItem(
                "storm",
                "Brak burz w prognozie",
                "Wyładowania mogą uszkodzić BSP. W razie prognozy – przełóż lot.",
                auto = true,
            ),
            ChecklistItem(
                "gnss",
                "Znany tryb bez GNSS (np. ATTI)",
                "Przećwicz sterowanie w trybie bez satelitów.",
            ),
        ),
    ),
    ChecklistSection(
        title = "Formalności i sprzęt",
        items = listOf(
            ChecklistItem(
                "checkin",
                "Check-in w checkin.pansa.pl",
                "Obowiązkowe zgłoszenie operacji lotniczej z użyciem BSP.",
            ),
            ChecklistItem(
                "battery",
                "Akumulatory naładowane (BSP + aparatura)",
                "Sprawdź napięcie ogniw, brak spuchnięcia.",
            ),
            ChecklistItem(
                "props",
                "Śmigła bez uszkodzeń",
                "Wymień pęknięte / wyszczerbione. Sprawdź dokręcenie.",
            ),
            ChecklistItem(
                "firmware",
                "Aktualne firmware BSP i aparatury",
                "Zapewnia zgodność z ostatnimi poprawkami bezpieczeństwa.",
            ),
            ChecklistItem(
                "compass",
                "Kalibracja kompasu",
                "Jeżeli zmieniłeś lokalizację o setki km lub w pobliżu metalu.",
            ),
        ),
    ),
)

val AllChecklistIds: Set<String> = PreflightChecklist.flatMap { it.items }.map { it.id }.toSet()
