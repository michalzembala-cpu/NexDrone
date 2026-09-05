/**
 * NexHub — wspólny backend dla NexDrone (Android) i NexPlay (Windows).
 * Trzyma profil gracza/pilota: RL rank, statystyki, preferencje, log lotów.
 * Deploy: Cloudflare Worker + KV storage (bindowany jako env.PROFILES).
 *
 * Auth: prosty token per profil (generowany przy CREATE).
 * Klient trzyma token w bezpiecznym miejscu i wysyła w headerze x-token.
 */

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    const path = url.pathname;
    const method = request.method;

    // CORS — apka Android + NexPlay pytają z różnych źródeł
    if (method === "OPTIONS") return cors(new Response(null, { status: 204 }));

    try {
      // POST /profile — utworz nowy profil, zwroc token
      if (method === "POST" && path === "/profile") {
        const token = crypto.randomUUID();
        const initial = { createdAt: Date.now(), rl: {}, drone: {}, meta: {} };
        await env.PROFILES.put(token, JSON.stringify(initial));
        return json({ token }, 201);
      }

      // Wszystkie kolejne wymagają x-token
      const token = request.headers.get("x-token");
      if (!token) return json({ error: "Brak x-token" }, 401);

      const existing = await env.PROFILES.get(token);
      if (!existing) return json({ error: "Nieznany token" }, 404);

      const profile = JSON.parse(existing);

      // GET /profile — pełny profil
      if (method === "GET" && path === "/profile") {
        return json(profile);
      }

      // PUT /profile/rl — aktualizuj sekcje Rocket League (z NexPlay)
      if (method === "PUT" && path === "/profile/rl") {
        const body = await request.json();
        profile.rl = { ...profile.rl, ...body, updatedAt: Date.now() };
        await env.PROFILES.put(token, JSON.stringify(profile));
        return json({ ok: true });
      }

      // PUT /profile/drone — aktualizuj sekcje drone (z NexDrone)
      if (method === "PUT" && path === "/profile/drone") {
        const body = await request.json();
        profile.drone = { ...profile.drone, ...body, updatedAt: Date.now() };
        await env.PROFILES.put(token, JSON.stringify(profile));
        return json({ ok: true });
      }

      // PUT /profile/meta — imie, preferencje itp.
      if (method === "PUT" && path === "/profile/meta") {
        const body = await request.json();
        profile.meta = { ...profile.meta, ...body };
        await env.PROFILES.put(token, JSON.stringify(profile));
        return json({ ok: true });
      }

      // DELETE /profile — wywal wszystko
      if (method === "DELETE" && path === "/profile") {
        await env.PROFILES.delete(token);
        return json({ ok: true });
      }

      return json({ error: "Nieznany endpoint" }, 404);
    } catch (e) {
      return json({ error: String(e) }, 500);
    }
  },
};

function json(body, status = 200) {
  return cors(new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  }));
}

function cors(response) {
  const headers = new Headers(response.headers);
  headers.set("Access-Control-Allow-Origin", "*");
  headers.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
  headers.set("Access-Control-Allow-Headers", "content-type, x-token");
  return new Response(response.body, { status: response.status, headers });
}
