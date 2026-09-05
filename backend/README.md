# NexHub — backend

Wspólny profil dla NexDrone (Android) i NexPlay (Windows).
Trzyma rangę RL, statystyki lotów, imię pilota, preferencje.

## Deploy — jednorazowo, ~10 min

### 1. Zainstaluj Node.js jeśli nie masz
https://nodejs.org — pobierz LTS, zainstaluj.

### 2. Zainstaluj Wrangler CLI (narzędzie Cloudflare)
```bash
npm install -g wrangler
```

### 3. Zarejestruj się w Cloudflare (za darmo)
https://dash.cloudflare.com/sign-up

### 4. Zaloguj się w terminalu
```bash
cd D:\Users\micha\source\repos\DronePreflight\backend
wrangler login
```
Otworzy przeglądarkę — kliknij Allow.

### 5. Utwórz KV storage
```bash
wrangler kv namespace create profiles
```
Wyświetli coś jak:
```
🌀 Creating namespace with title "nexhub-profiles"
Success! Add the following to your configuration file:
[[kv_namespaces]]
binding = "PROFILES"
id = "abc123def456..."
```

Skopiuj wartość `id` i wklej do `wrangler.toml` w polu `id = "..."`.

### 6. Deploy!
```bash
wrangler deploy
```

Zwróci URL jak `https://nexhub.twoj-username.workers.dev`.

## Test

```bash
# Utwórz nowy profil
curl -X POST https://nexhub.YOUR.workers.dev/profile
# → {"token": "..."}

# Ustaw statystyki
TOKEN=twoj-token
curl -X PUT https://nexhub.YOUR.workers.dev/profile/rl \
  -H "x-token: $TOKEN" \
  -H "content-type: application/json" \
  -d '{"rank": "Diamond III", "mmr": 1234, "winrate": 62}'

# Odczytaj profil
curl https://nexhub.YOUR.workers.dev/profile -H "x-token: $TOKEN"
```

## Limity darmowe

- 100 000 requestów / dzień
- KV: 100 000 odczytów + 1000 zapisów / dzień
- 1 GB storage total

Dla jednego usera + dwóch apek = **10× więcej niż potrzebujesz**.

## Endpointy

- `POST /profile` — utwórz nowy profil, zwróć token
- `GET /profile` — pobierz profil (wymaga x-token)
- `PUT /profile/rl` — aktualizuj sekcję Rocket League (z NexPlay)
- `PUT /profile/drone` — aktualizuj sekcję drona (z NexDrone)
- `PUT /profile/meta` — imię, preferencje
- `DELETE /profile` — wyzeruj wszystko
