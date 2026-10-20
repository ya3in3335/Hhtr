// Ultron proxy — Cloudflare Worker.
// - Hides the OpenRouter key (OPENROUTER_API_KEY secret).
// - Stores the admin's system prompt in Cloudflare KV (PROMPT_KV), editable live
//   from the built-in admin page at /admin. No internal prompt ships by default.
// - Mirrors /api/chat so the apps work by pointing at this Worker's URL.

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "Content-Type, X-Admin-Token",
  "Access-Control-Allow-Methods": "POST, GET, OPTIONS",
};

const json = (obj, status = 200) =>
  new Response(JSON.stringify(obj), { status, headers: { "Content-Type": "application/json", ...CORS } });

async function getPrompt(env) {
  if (env.PROMPT_KV) {
    const v = await env.PROMPT_KV.get("system_prompt");
    if (v !== null && v !== undefined) return v;
  }
  return env.SYSTEM_PROMPT || "";
}

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });
    const url = new URL(request.url);

    // ---- Admin page (static UI served by the Worker) ----
    if (url.pathname === "/admin" || url.pathname === "/admin/") {
      return new Response(ADMIN_HTML, { status: 200, headers: { "Content-Type": "text/html; charset=utf-8", ...CORS } });
    }

    // ---- Admin API: read/write the prompt (auth via X-Admin-Token) ----
    if (url.pathname === "/admin/prompt") {
      const token = request.headers.get("X-Admin-Token");
      if (!env.ADMIN_TOKEN || token !== env.ADMIN_TOKEN) return json({ error: "Unauthorized" }, 401);
      if (request.method === "GET") {
        return json({ prompt: await getPrompt(env), model: env.MODEL || "poolside/laguna-s-2.1:free" });
      }
      if (request.method === "POST") {
        if (!env.PROMPT_KV) return json({ error: "KV not bound (PROMPT_KV)" }, 500);
        let body; try { body = await request.json(); } catch { return json({ error: "bad json" }, 400); }
        const prompt = typeof body?.prompt === "string" ? body.prompt : "";
        await env.PROMPT_KV.put("system_prompt", prompt);
        return json({ ok: true, savedAt: new Date().toISOString() });
      }
      return json({ error: "method" }, 405);
    }

    // ---- Public meta ----
    if (url.pathname === "/api/meta") {
      const p = await getPrompt(env);
      return json({
        model: env.MODEL || "poolside/laguna-s-2.1:free",
        reasoningEnabled: env.REASONING !== "false",
        promptConfigured: Boolean(p && p.trim()),
        keyConfigured: Boolean(env.OPENROUTER_API_KEY),
        kvBound: Boolean(env.PROMPT_KV),
      });
    }

    // ---- Chat ----
    if (url.pathname !== "/api/chat" || request.method !== "POST") {
      return new Response("Ultron proxy is running.", { status: 200, headers: CORS });
    }
    if (!env.OPENROUTER_API_KEY) return json({ error: "Server missing OPENROUTER_API_KEY secret." }, 500);

    let body; try { body = await request.json(); } catch { return json({ error: "Invalid JSON body." }, 400); }
    const clientMessages = Array.isArray(body?.messages) ? body.messages : [];

    const systemPrompt = await getPrompt(env);
    const messages = [];
    if (systemPrompt && systemPrompt.trim()) messages.push({ role: "system", content: systemPrompt });
    for (const m of clientMessages) {
      if (!m || typeof m.role !== "string") continue;
      const msg = { role: m.role, content: m.content ?? "" };
      if (m.reasoning_details) msg.reasoning_details = m.reasoning_details;
      messages.push(msg);
    }

    const payload = { model: env.MODEL || "poolside/laguna-s-2.1:free", messages };
    if (env.REASONING !== "false") payload.reasoning = { enabled: true };

    let data;
    try {
      const upstream = await fetch("https://openrouter.ai/api/v1/chat/completions", {
        method: "POST",
        headers: { Authorization: `Bearer ${env.OPENROUTER_API_KEY}`, "Content-Type": "application/json", "X-Title": "Ultron" },
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

// ===== Admin page HTML (red theme, Arabic, SVG icon) =====
const ADMIN_HTML = `<!doctype html><html lang="ar" dir="rtl"><head><meta charset="utf-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1"/><title>Ultron — لوحة الأدمن</title>
<style>
:root{--bg:#1a0405;--panel:#2a0709;--line:#5a1418;--red:#e11d2a;--red2:#ff4d5a;--text:#ffe9ea;--muted:#d08a8f}
*{box-sizing:border-box}body{margin:0;font-family:system-ui,-apple-system,Segoe UI,Tahoma,sans-serif;
background:radial-gradient(1200px 600px at 80% -10%,#4a0d11,transparent),var(--bg);color:var(--text);min-height:100vh;padding:22px}
.wrap{max-width:720px;margin:0 auto}header{display:flex;align-items:center;gap:12px;margin-bottom:20px}
.logo{width:42px;height:42px}h1{font-size:21px;margin:0}.sub{color:var(--muted);font-size:13px;margin-top:2px}
.card{background:var(--panel);border:1px solid var(--line);border-radius:16px;padding:18px;margin-bottom:16px}
label{display:block;font-weight:600;margin-bottom:8px}.hint{color:var(--muted);font-size:12px;margin:0 0 10px;line-height:1.6}
input,textarea{width:100%;background:#200506;border:1px solid var(--line);color:var(--text);border-radius:12px;padding:12px 14px;font-size:15px;font-family:inherit}
textarea{min-height:220px;resize:vertical;line-height:1.7}
.row{display:flex;gap:10px;align-items:center;flex-wrap:wrap}
button{cursor:pointer;border:none;border-radius:12px;padding:12px 18px;font-size:15px;font-weight:700;color:#fff;background:var(--red)}
button.ghost{background:transparent;border:1px solid var(--line);color:var(--text)}
.status{font-size:13px;margin-top:12px;min-height:18px}.ok{color:#7CFFA0}.err{color:var(--red2)}
.pill{display:inline-flex;gap:6px;align-items:center;font-size:12px;color:var(--muted);border:1px solid var(--line);border-radius:999px;padding:4px 10px}
svg{display:block}
</style></head><body><div class="wrap">
<header>
<svg class="logo" viewBox="0 0 48 48"><rect x="4" y="9" width="40" height="30" rx="8" fill="#e11d2a"/><circle cx="17" cy="24" r="4" fill="#1a0405"/><circle cx="31" cy="24" r="4" fill="#1a0405"/><path d="M24 4v5" stroke="#ff4d5a" stroke-width="3" stroke-linecap="round"/><circle cx="24" cy="4" r="3" fill="#ff4d5a"/></svg>
<div><h1>Ultron — لوحة تحكم الأدمن</h1><div class="sub">البرومبت يُحفظ تلقائيًا في Cloudflare KV ويُطبّق فورًا. لا يوجد برومبت مسبق — الخانة فارغة.</div></div>
</header>
<div class="card">
<label for="token">رمز دخول الأدمن</label>
<p class="hint">الرمز الموجود في سرّ الـWorker <code>ADMIN_TOKEN</code>. أدخله لتحميل/حفظ البرومبت.</p>
<div class="row"><input id="token" type="password" placeholder="أدخل رمز الأدمن" autocomplete="off"/><button class="ghost" id="load" style="flex:0 0 auto">تحميل</button></div>
</div>
<div class="card">
<label for="prompt">البرومبت الداخلي للنموذج</label>
<p class="hint">ما تكتبه هنا هو فقط ما يُرسل كـ<code>system</code>. اتركه فارغًا لعدم إرسال أي برومبت.</p>
<textarea id="prompt" placeholder="اكتب البرومبت الداخلي هنا…"></textarea>
<div class="row" style="margin-top:14px"><button id="save" style="flex:0 0 auto">حفظ في Cloudflare</button><span class="pill" id="meta">—</span></div>
<div class="status" id="status"></div>
</div></div>
<script>
const $=(i)=>document.getElementById(i);
const st=(m,c="")=>{const s=$("status");s.textContent=m;s.className="status "+c};
async function load(){const t=$("token").value.trim();if(!t)return st("أدخل الرمز أولًا.","err");
try{const r=await fetch("/admin/prompt",{headers:{"X-Admin-Token":t}});if(!r.ok)throw new Error((await r.json()).error||r.status);
const d=await r.json();$("prompt").value=d.prompt||"";st("تم التحميل ✓","ok")}catch(e){st("فشل: "+e.message,"err")}}
async function save(){const t=$("token").value.trim();if(!t)return st("أدخل الرمز أولًا.","err");$("save").disabled=true;
try{const r=await fetch("/admin/prompt",{method:"POST",headers:{"Content-Type":"application/json","X-Admin-Token":t},body:JSON.stringify({prompt:$("prompt").value})});
if(!r.ok)throw new Error((await r.json()).error||r.status);st("تم الحفظ في Cloudflare ✓","ok")}catch(e){st("فشل الحفظ: "+e.message,"err")}finally{$("save").disabled=false}}
async function meta(){try{const r=await fetch("/api/meta");const m=await r.json();
$("meta").textContent=(m.keyConfigured?"المفتاح ✓":"المفتاح ✗")+" · "+(m.promptConfigured?"برومبت مضبوط":"لا برومبت")+" · "+(m.kvBound?"KV ✓":"KV ✗")}catch{$("meta").textContent="—"}}
$("load").onclick=load;$("save").onclick=save;meta();
</script></body></html>`;
