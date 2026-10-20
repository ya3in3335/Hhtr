// Ultron proxy — Cloudflare Worker.
// Hides the OpenRouter key (stored as the OPENROUTER_API_KEY secret) and injects
// ONLY the admin-supplied SYSTEM_PROMPT (empty by default). Mirrors the backend's
// /api/chat so the apps work by only pointing at this Worker's URL.

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "Content-Type",
  "Access-Control-Allow-Methods": "POST, GET, OPTIONS",
};

function json(obj, status = 200) {
  return new Response(JSON.stringify(obj), {
    status,
    headers: { "Content-Type": "application/json", ...CORS },
  });
}

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });

    const url = new URL(request.url);

    if (url.pathname === "/api/meta") {
      return json({
        model: env.MODEL || "poolside/laguna-s-2.1:free",
        reasoningEnabled: env.REASONING !== "false",
        promptConfigured: Boolean(env.SYSTEM_PROMPT && env.SYSTEM_PROMPT.trim()),
        keyConfigured: Boolean(env.OPENROUTER_API_KEY),
      });
    }

    if (url.pathname !== "/api/chat" || request.method !== "POST") {
      return new Response("Ultron proxy is running.", { status: 200, headers: CORS });
    }

    if (!env.OPENROUTER_API_KEY) return json({ error: "Server missing OPENROUTER_API_KEY secret." }, 500);

    let body;
    try { body = await request.json(); } catch { return json({ error: "Invalid JSON body." }, 400); }

    const clientMessages = Array.isArray(body?.messages) ? body.messages : [];

    // The ONLY system content is the admin's SYSTEM_PROMPT. If empty, none is sent.
    const messages = [];
    if (env.SYSTEM_PROMPT && env.SYSTEM_PROMPT.trim()) {
      messages.push({ role: "system", content: env.SYSTEM_PROMPT });
    }
    for (const m of clientMessages) {
      if (!m || typeof m.role !== "string") continue;
      const msg = { role: m.role, content: m.content ?? "" };
      if (m.reasoning_details) msg.reasoning_details = m.reasoning_details; // pass back unmodified
      messages.push(msg);
    }

    const payload = { model: env.MODEL || "poolside/laguna-s-2.1:free", messages };
    if (env.REASONING !== "false") payload.reasoning = { enabled: true };

    let data;
    try {
      const upstream = await fetch("https://openrouter.ai/api/v1/chat/completions", {
        method: "POST",
        headers: {
          Authorization: `Bearer ${env.OPENROUTER_API_KEY}`,
          "Content-Type": "application/json",
          "X-Title": "Ultron",
        },
        body: JSON.stringify(payload),
      });
      data = await upstream.json();
      if (!upstream.ok) return json({ error: data?.error || "OpenRouter error", data }, upstream.status);
    } catch (e) {
      return json({ error: "Failed to reach OpenRouter", detail: String(e) }, 502);
    }

    const message = data?.choices?.[0]?.message ?? { role: "assistant", content: "" };
    return json({ message, usage: data?.usage ?? null });
  },
};
