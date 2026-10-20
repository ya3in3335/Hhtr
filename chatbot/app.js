"use strict";

/* ---------- State ---------- */
const LS = {
  apiBase: "ultron_api_base",
  perms: "ultron_perms",
  ghToken: "ultron_gh_token",
};
const state = {
  apiBase: localStorage.getItem(LS.apiBase) || `${location.origin.includes("localhost") ? "http://localhost:8787" : location.origin}`,
  perms: JSON.parse(localStorage.getItem(LS.perms) || '{"mic":false,"loc":false}'),
  ghToken: localStorage.getItem(LS.ghToken) || "",
  messages: [], // {role, content, reasoning_details?}
  busy: false,
};

const $ = (id) => document.getElementById(id);
const chat = $("chat");

/* ---------- Tiny markdown (safe: escape then limited formatting) ---------- */
function escapeHtml(s) {
  return s.replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}
function mdLite(s) {
  let h = escapeHtml(s);
  h = h.replace(/```([\s\S]*?)```/g, (_, c) => `<pre><code>${c.replace(/^\n/, "")}</code></pre>`);
  h = h.replace(/`([^`\n]+)`/g, "<code>$1</code>");
  h = h.replace(/\*\*([^*]+)\*\*/g, "<b>$1</b>");
  h = h.replace(/(https?:\/\/[^\s<]+)/g, '<a href="$1" target="_blank" rel="noopener">$1</a>');
  return h;
}

/* ---------- Rendering ---------- */
function reasoningText(details) {
  if (!details) return "";
  if (typeof details === "string") return details;
  if (Array.isArray(details)) {
    return details.map((d) => d?.text || d?.content || d?.summary || "").filter(Boolean).join("\n\n");
  }
  return "";
}

function addMessageEl(role, content, reasoning_details) {
  $("empty")?.remove();
  const wrap = document.createElement("div");
  wrap.className = `msg ${role}`;

  const avatar = document.createElement("div");
  avatar.className = "avatar";
  avatar.innerHTML = role === "user"
    ? `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#ff9aa0" stroke-width="2"><circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0116 0"/></svg>`
    : `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2"><rect x="4" y="7" width="16" height="12" rx="4"/><circle cx="9" cy="13" r="1.6" fill="#fff"/><circle cx="15" cy="13" r="1.6" fill="#fff"/></svg>`;

  const col = document.createElement("div");
  const bubble = document.createElement("div");
  bubble.className = "bubble";
  bubble.innerHTML = mdLite(content || "");
  col.appendChild(bubble);

  const rtext = reasoningText(reasoning_details);
  if (rtext) {
    const det = document.createElement("details");
    det.className = "reasoning";
    det.innerHTML = `<summary>🧠 عرض التفكير</summary><div class="r-body">${escapeHtml(rtext)}</div>`;
    col.appendChild(det);
  }

  wrap.appendChild(avatar);
  wrap.appendChild(col);
  chat.appendChild(wrap);
  chat.scrollTop = chat.scrollHeight;
  return bubble;
}

function addTyping() {
  const wrap = document.createElement("div");
  wrap.className = "msg assistant";
  wrap.id = "typing";
  wrap.innerHTML = `<div class="avatar"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2"><rect x="4" y="7" width="16" height="12" rx="4"/><circle cx="9" cy="13" r="1.6" fill="#fff"/><circle cx="15" cy="13" r="1.6" fill="#fff"/></svg></div>
    <div class="bubble"><div class="typing"><span></span><span></span><span></span></div></div>`;
  chat.appendChild(wrap);
  chat.scrollTop = chat.scrollHeight;
}
function removeTyping() { $("typing")?.remove(); }

/* ---------- Device capability layer (only after the user confirms permissions) ---------- */
// Local, consent-gated helpers. The assistant replies normally; these add a device answer
// ONLY when the matching permission is on and the user explicitly asks.
function wantsLocation(text) {
  return /أين أنا|موقعي|الموقع الحالي|where am i|my location|اين انا/i.test(text);
}
function getLocation() {
  return new Promise((resolve) => {
    if (!state.perms.loc || !navigator.geolocation) return resolve(null);
    navigator.geolocation.getCurrentPosition(
      (p) => resolve({ lat: p.coords.latitude, lon: p.coords.longitude, acc: p.coords.accuracy }),
      () => resolve(null),
      { enableHighAccuracy: true, timeout: 8000 }
    );
  });
}

/* ---------- Chat send ---------- */
async function send(text) {
  text = (text || $("input").value).trim();
  if (!text || state.busy) return;
  $("input").value = "";
  autosize();
  setSend();

  addMessageEl("user", text);
  state.messages.push({ role: "user", content: text });

  // Consent-gated device enrichment (location), appended as context for the user.
  let deviceNote = "";
  if (wantsLocation(text)) {
    const loc = await getLocation();
    if (loc) deviceNote = `\n\n📍 موقعك الحالي: ${loc.lat.toFixed(5)}, ${loc.lon.toFixed(5)} (±${Math.round(loc.acc)}م)`;
    else if (!state.perms.loc) deviceNote = "\n\n(إذن الموقع غير مفعّل — فعّله من الدرع بالأعلى لأعرف موقعك.)";
  }

  state.busy = true;
  setState("يفكّر…");
  addTyping();

  try {
    const r = await fetch(`${state.apiBase.replace(/\/$/, "")}/api/chat`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ messages: state.messages }),
    });
    removeTyping();
    const data = await r.json();
    if (!r.ok) throw new Error(data?.error?.message || data?.error || JSON.stringify(data));

    const msg = data.message || { role: "assistant", content: "" };
    const content = (msg.content || "") + deviceNote;
    addMessageEl("assistant", content, msg.reasoning_details);
    // Preserve reasoning_details unmodified for the next turn.
    state.messages.push({ role: "assistant", content: msg.content || "", reasoning_details: msg.reasoning_details });
    speak(msg.content || "");
  } catch (e) {
    removeTyping();
    addMessageEl("assistant", "تعذّر الاتصال بالخادم: " + e.message + "\n\nتأكد من تشغيل الـ backend وإعداد المفتاح، ومن عنوان الخادم في الإعدادات.");
  } finally {
    state.busy = false;
    setState("جاهز");
  }
}

/* ---------- Voice: recognition + TTS ---------- */
let recog = null, listening = false;
function initRecog() {
  const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
  if (!SR) return null;
  const r = new SR();
  r.lang = "ar-SA";
  r.interimResults = true;
  r.continuous = false;
  r.onresult = (e) => {
    const t = Array.from(e.results).map((x) => x[0].transcript).join("");
    $("input").value = t;
    autosize(); setSend();
  };
  r.onend = () => {
    listening = false;
    $("btnMic").classList.remove("listening");
    const t = $("input").value.trim();
    if (t) send(t);
  };
  r.onerror = () => { listening = false; $("btnMic").classList.remove("listening"); };
  return r;
}
function toggleMic() {
  if (!state.perms.mic) { openSheet("permSheet"); setBanner("فعّل إذن المايك أولًا."); return; }
  if (!recog) recog = initRecog();
  if (!recog) { setBanner("التعرف على الصوت غير مدعوم في هذا المتصفح."); return; }
  if (listening) { recog.stop(); return; }
  try { recog.start(); listening = true; $("btnMic").classList.add("listening"); setState("يستمع…"); }
  catch { /* already started */ }
}
function speak(text) {
  if (!state.perms.mic || !window.speechSynthesis || !text) return;
  const u = new SpeechSynthesisUtterance(text.replace(/[*`#>]/g, ""));
  u.lang = "ar-SA";
  u.onend = () => setState("جاهز");
  window.speechSynthesis.cancel();
  window.speechSynthesis.speak(u);
}

/* ---------- Permissions ---------- */
async function requestMic() {
  try { const s = await navigator.mediaDevices.getUserMedia({ audio: true }); s.getTracks().forEach((t) => t.stop()); return true; }
  catch { return false; }
}
function requestLoc() {
  return new Promise((res) => {
    if (!navigator.geolocation) return res(false);
    navigator.geolocation.getCurrentPosition(() => res(true), () => res(false), { timeout: 8000 });
  });
}
async function savePerms() {
  const wantMic = $("permMic").checked;
  const wantLoc = $("permLoc").checked;
  let mic = false, loc = false;
  if (wantMic) { mic = await requestMic(); if (!mic) setBanner("رفض المتصفح إذن المايك."); }
  if (wantLoc) { loc = await requestLoc(); if (!loc) setBanner("رفض المتصفح إذن الموقع."); }
  state.perms = { mic, loc };
  localStorage.setItem(LS.perms, JSON.stringify(state.perms));
  reflectPerms();
  closeSheet("permSheet");
}
function reflectPerms() {
  $("permMic").checked = state.perms.mic;
  $("permLoc").checked = state.perms.loc;
  const pm = $("pillMic"), pl = $("pillLoc");
  if (pm) { pm.textContent = `🎤 المايك: ${state.perms.mic ? "مفعّل" : "مغلق"}`; pm.classList.toggle("on", state.perms.mic); }
  if (pl) { pl.textContent = `📍 الموقع: ${state.perms.loc ? "مفعّل" : "مغلق"}`; pl.classList.toggle("on", state.perms.loc); }
  $("btnPerms").classList.toggle("active", state.perms.mic || state.perms.loc);
}

/* ---------- GitHub quick link ---------- */
async function connectGitHub() {
  const token = $("ghToken").value.trim();
  if (!token) { $("ghStatus").textContent = "ألصق التوكن أولًا."; return; }
  $("ghStatus").textContent = "جارٍ التحقق…";
  try {
    const r = await fetch("https://api.github.com/user", {
      headers: { Authorization: `Bearer ${token}`, Accept: "application/vnd.github+json" },
    });
    if (!r.ok) throw new Error("توكن غير صالح (" + r.status + ")");
    const u = await r.json();
    state.ghToken = token;
    localStorage.setItem(LS.ghToken, token);
    $("ghStatus").textContent = `تم الربط ✓ مرحبًا ${u.login}`;
  } catch (e) { $("ghStatus").textContent = "فشل: " + e.message; }
}

/* ---------- UI helpers ---------- */
function setState(s) { $("state").textContent = s; }
function setBanner(s) { $("banner").textContent = s || ""; if (s) setTimeout(() => { if ($("banner").textContent === s) $("banner").textContent = ""; }, 4000); }
function setSend() { $("btnSend").disabled = !$("input").value.trim() || state.busy; }
function autosize() { const t = $("input"); t.style.height = "auto"; t.style.height = Math.min(t.scrollHeight, 120) + "px"; }
function openSheet(id) { $(id).classList.add("open"); }
function closeSheet(id) { $(id).classList.remove("open"); }

function newChat() {
  state.messages = [];
  chat.innerHTML = `<div class="empty" id="empty">
    <svg class="logo" viewBox="0 0 48 48"><rect x="6" y="12" width="36" height="26" rx="8" fill="#e11d2a"/><circle cx="18" cy="25" r="3.6" fill="#17090a"/><circle cx="30" cy="25" r="3.6" fill="#17090a"/><path d="M24 5v6" stroke="#ff4d5a" stroke-width="3" stroke-linecap="round"/><circle cx="24" cy="4.5" r="2.8" fill="#ff4d5a"/></svg>
    <h3>محادثة جديدة</h3><p>اكتب رسالتك أو اضغط المايك للتحدث.</p>
    <div class="pill-row" style="justify-content:center"><span class="pill" id="pillMic"></span><span class="pill" id="pillLoc"></span></div></div>`;
  reflectPerms();
}

/* ---------- Wire up ---------- */
$("btnSend").onclick = () => send();
$("btnMic").onclick = toggleMic;
$("btnNew").onclick = newChat;
$("btnPerms").onclick = () => { reflectPerms(); openSheet("permSheet"); };
$("btnSettings").onclick = () => { $("apiBase").value = state.apiBase; $("ghToken").value = state.ghToken; openSheet("setSheet"); };
$("permCancel").onclick = () => closeSheet("permSheet");
$("permSave").onclick = savePerms;
$("ghConnect").onclick = connectGitHub;
$("setSave").onclick = () => {
  state.apiBase = $("apiBase").value.trim() || state.apiBase;
  localStorage.setItem(LS.apiBase, state.apiBase);
  if ($("ghToken").value.trim()) { state.ghToken = $("ghToken").value.trim(); localStorage.setItem(LS.ghToken, state.ghToken); }
  closeSheet("setSheet"); setBanner("تم الحفظ.");
};
document.querySelectorAll(".sheet-backdrop").forEach((el) => {
  el.addEventListener("click", (e) => { if (e.target === el) el.classList.remove("open"); });
});
$("input").addEventListener("input", () => { autosize(); setSend(); });
$("input").addEventListener("keydown", (e) => {
  if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); send(); }
});

/* ---------- Splash screen ---------- */
(function splash() {
  const el = document.getElementById("splash");
  if (!el) return;
  const reduce = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  const dismiss = () => el.classList.add("hide");
  el.addEventListener("click", dismiss);        // tap to skip
  setTimeout(dismiss, reduce ? 700 : 2600);     // auto-dismiss after the animation
})();

/* ---------- Service worker + quick voice shortcut ---------- */
if ("serviceWorker" in navigator) {
  navigator.serviceWorker.register("sw.js").catch(() => {});
}
reflectPerms();
setSend();
// Launched via the home-screen "voice" shortcut → start listening immediately.
if (new URLSearchParams(location.search).get("voice") === "1") {
  window.addEventListener("load", () => setTimeout(() => { if (state.perms.mic) toggleMic(); else openSheet("permSheet"); }, 400));
}
