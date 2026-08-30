# Drone Preflight

Aplikacja Android (Kotlin + Jetpack Compose) do przygotowania lotu BSP:
lista kontrolna z materiału PANSA + automatyczne pobieranie warunków pogodowych
i oceny **GO / OSTROŻNIE / NO-GO** względem limitów Twojego drona.

## Funkcje

- **Dashboard** – aktualny wiatr (m/s + kierunek + porywy), temperatura, opady,
  widzialność, zjawiska atmosferyczne (mgła/burza) i **KP index**.
  Każdy parametr dostaje własną ocenę, plus ocena łączna u góry.
- **Checklista** – 19 pozycji w 3 sekcjach (planowanie lotu, pogoda, formalności/sprzęt),
  postęp zapisywany między uruchomieniami.
- **Ustawienia** – limity Twojego BSP z instrukcji obsługi
  (maks. wiatr, min./maks. temperatura). Ocena adaptuje się na żywo.

## Źródła danych

**Pogoda pobierana równolegle z pięciu niezależnych źródeł** (bez kluczy API):

1. **Open-Meteo** (api.open-meteo.com) – model ICON/GFS
2. **MET Norway** (api.met.no) – Norweski Instytut Meteorologiczny
3. **wttr.in** (wttr.in) – proxy do WorldWeatherOnline
4. **Bright Sky / DWD** (api.brightsky.dev) – niemiecki Deutscher Wetterdienst
5. **7Timer!** (www.7timer.info) – model GFS globalny

Dla każdego parametru (wiatr, temperatura, opady, chmury) obliczana jest **mediana**
i zakres min–max. Ocena łączna uwzględnia:
- rozbieżność między źródłami (jeśli σ wiatru > 3 m/s → OSTROŻNIE),
- głosowanie na burzę/mgłę (≥2 źródła → NO-GO/OSTROŻNIE),
- liczbę źródeł, które odpowiedziały (≤2 → automatyczne OSTROŻNIE).

Każde źródło ma timeout 10 s. Błąd jednego nie wpływa na resztę.

**Space weather:**
- KP index: **NOAA SWPC** (podstawowe) → fallback **GFZ Potsdam**

**Lokalizacja:** FusedLocationProvider (Google Play Services); fallback Warszawa.

## Uprawnienia

- `INTERNET` – pobranie pogody / KP.
- `ACCESS_COARSE_LOCATION` / `ACCESS_FINE_LOCATION` – auto-lokalizacja
  (opcjonalne; bez tego działa fallback).

## Jak zbudować APK

1. Otwórz folder `DronePreflight` w **Android Studio Hedgehog** (2023.1) lub nowszym.
2. Android Studio pobierze Gradle 8.9 z distributionUrl (`gradle/wrapper/gradle-wrapper.properties`),
   zsynchronizuje projekt i pobierze zależności.
3. `Build → Build Bundle(s) / APK(s) → Build APK(s)`.
4. APK znajdziesz w `app/build/outputs/apk/debug/app-debug.apk`.

Alternatywnie (CLI, jeśli masz zainstalowany Gradle 8.9+):

```bash
gradle wrapper
./gradlew assembleDebug
```

## Stack

- Kotlin 2.0.20 + Compose Multiplatform BOM 2024.09
- Material 3, Navigation Compose
- Ktor 2.3.12 (client + JSON)
- kotlinx.serialization 1.7.3
- DataStore Preferences
- Play Services Location

## Ograniczenia świadome

- Aplikacja **nie zastępuje** check-inu w PANSA (`checkin.pansa.pl`) ani sprawdzenia
  stref geograficznych na `drone.pansa.pl`. Checklista o tym przypomina.
- Ocena bazuje wyłącznie na danych publicznych z API. **Ostateczną decyzję o starcie
  podejmuje pilot na miejscu.**
- Nie ma spoofingu/jammingu w API pogodowym – KP index jest dobrym przybliżeniem
  ryzyka zakłóceń GNSS, ale nie jedynym.
