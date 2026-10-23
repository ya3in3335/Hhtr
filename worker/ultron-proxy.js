// Ultron proxy — Cloudflare Worker.
// - Hides the model-provider key (API_KEY secret; OPENROUTER_API_KEY also honored).
// - Talks to any OpenAI-compatible provider (default: XKIRO, api.xkiro.com/v1).
// - Stores the admin's system prompt in Cloudflare KV (PROMPT_KV), editable live
//   from the built-in admin page at /admin. No internal prompt ships by default.
// - Mirrors /api/chat so the apps work by pointing at this Worker's URL.

const DEFAULT_MODEL = "qwen/qwen3.8-omni-flash:free";
const DEFAULT_API_BASE = "https://api.xkiro.com/v1";
const apiKeyOf = (env) => env.API_KEY || env.OPENROUTER_API_KEY || "";
const apiBaseOf = (env) => (env.API_BASE || DEFAULT_API_BASE).replace(/\/+$/, "");

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

// Admin password: a KV-stored value (changeable from the panel) overriding the
// initial ADMIN_TOKEN secret.
async function getAdminPw(env) {
  if (env.PROMPT_KV) {
    const v = await env.PROMPT_KV.get("admin_password");
    if (v) return v;
  }
  return env.ADMIN_TOKEN || "";
}

// ===== Edge TTS (Microsoft online neural voices) =====
const EDGE_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4";
const EDGE_WSS = "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1";
const EDGE_UA =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0";

function ttsVoice(lang) {
  switch (lang) {
    case "ar": return { voice: "ar-SA-HamedNeural", xml: "ar-SA" };
    case "fr": return { voice: "fr-FR-HenriNeural", xml: "fr-FR" };
    default: return { voice: "en-US-ChristopherNeural", xml: "en-US" };
  }
}
function uuidHex() { return crypto.randomUUID().replace(/-/g, ""); }
function xmlEsc(s) {
  return String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&apos;");
}
async function secMsGec() {
  const ticks = (BigInt(Math.floor(Date.now() / 1000) + 11644473600)) * 10000000n;
  const rounded = ticks - (ticks % 3000000000n);
  const buf = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(rounded.toString() + EDGE_TOKEN));
  return [...new Uint8Array(buf)].map((b) => b.toString(16).padStart(2, "0")).join("").toUpperCase();
}
async function synthEdge(text, lang, pitch, rate) {
  const { voice, xml } = ttsVoice(lang);
  const sec = await secMsGec();
  const connId = uuidHex();
  const url =
    `${EDGE_WSS}?TrustedClientToken=${EDGE_TOKEN}&Sec-MS-GEC=${sec}&Sec-MS-GEC-Version=1-130.0.2849.68&ConnectionId=${connId}`
      .replace("wss://", "https://");
  const resp = await fetch(url, {
    headers: {
      Upgrade: "websocket",
      "User-Agent": EDGE_UA,
      Origin: "https://azure.microsoft.com",
      "Accept-Encoding": "gzip, deflate, br",
      "Accept-Language": "en-US,en;q=0.9",
      Pragma: "no-cache",
      "Cache-Control": "no-cache",
    },
  });
  const ws = resp.webSocket;
  if (!ws) throw new Error("No WebSocket from Edge (status " + resp.status + ")");
  ws.accept();

  const config = `Content-Type:application/json; charset=utf-8\r\nPath:speech.config\r\n\r\n{"context":{"synthesis":{"audio":{"metadataoptions":{"sentenceBoundaryEnabled":"false","wordBoundaryEnabled":"false"},"outputFormat":"audio-24khz-48kbitrate-mono-mp3"}}}}`;
  const ssml =
    `<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='${xml}'>` +
    `<voice name='${voice}'><prosody pitch='${pitch}' rate='${rate}'>${xmlEsc(text)}</prosody></voice></speak>`;
  const ssmlMsg =
    `X-RequestId:${uuidHex()}\r\nContent-Type:application/ssml+xml\r\nX-Timestamp:${new Date().toString()}\r\nPath:ssml\r\n\r\n${ssml}`;

  const chunks = [];
  const done = new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error("tts timeout")), 25000);
    ws.addEventListener("message", (ev) => {
      const d = ev.data;
      if (typeof d === "string") {
        if (d.includes("Path:turn.end")) { clearTimeout(timer); resolve(); }
      } else {
        const bytes = new Uint8Array(d);
        if (bytes.length > 2) {
          const headerLen = (bytes[0] << 8) | bytes[1];
          const audio = bytes.subarray(2 + headerLen);
          if (audio.length) chunks.push(audio);
        }
      }
    });
    ws.addEventListener("close", () => { clearTimeout(timer); resolve(); });
    ws.addEventListener("error", () => { clearTimeout(timer); reject(new Error("ws error")); });
  });

  ws.send(config);
  ws.send(ssmlMsg);
  await done;
  try { ws.close(); } catch (e) {}

  const total = chunks.reduce((n, c) => n + c.length, 0);
  const out = new Uint8Array(total);
  let off = 0;
  for (const c of chunks) { out.set(c, off); off += c.length; }
  return out;
}

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });
    const url = new URL(request.url);

    // ---- Edge TTS: POST { text, lang?, pitch?, rate? } -> audio/mpeg ----
    if (url.pathname === "/api/tts" && request.method === "POST") {
      let body; try { body = await request.json(); } catch { return json({ error: "bad json" }, 400); }
      const text = (body?.text ?? "").toString().slice(0, 2000);
      if (!text.trim()) return json({ error: "empty text" }, 400);
      const lang = (body?.lang ?? "ar").toString();
      const pitch = (body?.pitch ?? "-40%").toString();
      const rate = (body?.rate ?? "-8%").toString();
      try {
        const audio = await synthEdge(text, lang, pitch, rate);
        if (!audio.length) return json({ error: "no audio produced" }, 502);
        return new Response(audio, { status: 200, headers: { "Content-Type": "audio/mpeg", "Cache-Control": "no-store", ...CORS } });
      } catch (e) {
        return json({ error: "tts failed", detail: String(e) }, 502);
      }
    }

    // ---- Admin page (static UI served by the Worker) ----
    if (url.pathname === "/admin" || url.pathname === "/admin/") {
      return new Response(ADMIN_HTML, { status: 200, headers: { "Content-Type": "text/html; charset=utf-8", ...CORS } });
    }

    // ---- Admin API: read/write the prompt (auth via X-Admin-Token) ----
    if (url.pathname === "/admin/prompt") {
      const token = request.headers.get("X-Admin-Token");
      const adminPw = await getAdminPw(env);
      if (!adminPw || token !== adminPw) return json({ error: "Unauthorized" }, 401);
      if (request.method === "GET") {
        return json({ prompt: await getPrompt(env), model: env.MODEL || DEFAULT_MODEL });
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

    // ---- Admin API: change the admin password (stored in KV) ----
    if (url.pathname === "/admin/password") {
      const token = request.headers.get("X-Admin-Token");
      const adminPw = await getAdminPw(env);
      if (!adminPw || token !== adminPw) return json({ error: "Unauthorized" }, 401);
      if (request.method !== "POST") return json({ error: "method" }, 405);
      if (!env.PROMPT_KV) return json({ error: "KV not bound (PROMPT_KV)" }, 500);
      let body; try { body = await request.json(); } catch { return json({ error: "bad json" }, 400); }
      const np = typeof body?.password === "string" ? body.password.trim() : "";
      if (np.length < 4) return json({ error: "Password must be at least 4 characters" }, 400);
      await env.PROMPT_KV.put("admin_password", np);
      return json({ ok: true });
    }

    // ---- Public meta ----
    if (url.pathname === "/api/meta") {
      const p = await getPrompt(env);
      return json({
        model: env.MODEL || DEFAULT_MODEL,
        provider: apiBaseOf(env),
        reasoningEnabled: env.REASONING === "true",
        promptConfigured: Boolean(p && p.trim()),
        keyConfigured: Boolean(apiKeyOf(env)),
        kvBound: Boolean(env.PROMPT_KV),
      });
    }

    // ---- Chat ----
    if (url.pathname !== "/api/chat" || request.method !== "POST") {
      return new Response("Ultron proxy is running.", { status: 200, headers: CORS });
    }
    const KEY = apiKeyOf(env);
    if (!KEY) return json({ error: "Server missing API_KEY secret." }, 500);

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

    const wantStream = body?.stream === true;
    const payload = { model: env.MODEL || DEFAULT_MODEL, messages };
    // Reasoning is opt-in (set REASONING=true); most OpenAI-compatible models
    // reject the extra param, so it stays off by default.
    if (env.REASONING === "true") payload.reasoning = { enabled: true };
    if (wantStream) payload.stream = true;

    let upstream;
    try {
      upstream = await fetch(`${apiBaseOf(env)}/chat/completions`, {
        method: "POST",
        headers: { Authorization: `Bearer ${KEY}`, "Content-Type": "application/json", "X-Title": "Ultron" },
        body: JSON.stringify(payload),
      });
    } catch (e) {
      return json({ error: "Failed to reach model provider", detail: String(e) }, 502);
    }

    // Live stream: pass the Server-Sent Events straight through.
    if (wantStream && upstream.ok && upstream.body) {
      return new Response(upstream.body, {
        status: 200,
        headers: {
          "Content-Type": "text/event-stream; charset=utf-8",
          "Cache-Control": "no-cache",
          "Connection": "keep-alive",
          ...CORS,
        },
      });
    }

    // Non-streaming (or an upstream error).
    const data = await upstream.json().catch(() => ({}));
    if (!upstream.ok) return json({ error: data?.error || "Provider error", data }, upstream.status);
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
</div>
<div class="card">
<label for="newpw">تغيير كلمة مرور الأدمن</label>
<p class="hint">تُخزَّن الكلمة الجديدة في Cloudflare KV وتُستخدم في الدخول القادم.</p>
<div class="row"><input id="newpw" type="password" placeholder="كلمة مرور جديدة (4 أحرف فأكثر)" autocomplete="off"/><button id="changepw" class="ghost" style="flex:0 0 auto">تغيير</button></div>
<div class="status" id="pwstatus"></div>
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
async function changePw(){const t=$("token").value.trim();const np=$("newpw").value.trim();const s=$("pwstatus");
if(!t){s.textContent="أدخل كلمة المرور الحالية بالأعلى أولًا.";s.className="status err";return;}
if(np.length<4){s.textContent="كلمة المرور الجديدة قصيرة.";s.className="status err";return;}
$("changepw").disabled=true;
try{const r=await fetch("/admin/password",{method:"POST",headers:{"Content-Type":"application/json","X-Admin-Token":t},body:JSON.stringify({password:np})});
if(!r.ok)throw new Error((await r.json()).error||r.status);
$("token").value=np;$("newpw").value="";s.textContent="تم تغيير كلمة المرور ✓";s.className="status ok";}
catch(e){s.textContent="فشل: "+e.message;s.className="status err";}finally{$("changepw").disabled=false;}}
$("load").onclick=load;$("save").onclick=save;$("changepw").onclick=changePw;meta();
</script></body></html>`;
