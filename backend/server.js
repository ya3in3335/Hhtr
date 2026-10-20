import express from "express";
import { readFile, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const __dirname = dirname(fileURLToPath(import.meta.url));
const CONFIG_PATH = join(__dirname, "data", "config.json");

const PORT = process.env.PORT || 8787;
const OPENROUTER_API_KEY = process.env.OPENROUTER_API_KEY || "";
const ADMIN_TOKEN = process.env.ADMIN_TOKEN || "change-me";
const OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions";

const app = express();
app.use(express.json({ limit: "1mb" }));

// Permissive CORS so the chatbot PWA (possibly served from another origin) can call the API.
app.use((req, res, next) => {
  res.header("Access-Control-Allow-Origin", process.env.CORS_ORIGIN || "*");
  res.header("Access-Control-Allow-Headers", "Content-Type, X-Admin-Token");
  res.header("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
  if (req.method === "OPTIONS") return res.sendStatus(204);
  next();
});

async function readConfig() {
  try {
    return JSON.parse(await readFile(CONFIG_PATH, "utf8"));
  } catch {
    return { systemPrompt: "", model: "poolside/laguna-s-2.1:free", reasoningEnabled: true, updatedAt: null };
  }
}

async function writeConfig(cfg) {
  await writeFile(CONFIG_PATH, JSON.stringify(cfg, null, 2), "utf8");
}

function requireAdmin(req, res, next) {
  const token = req.get("X-Admin-Token");
  if (!token || token !== ADMIN_TOKEN) {
    return res.status(401).json({ error: "Unauthorized. Provide a valid X-Admin-Token." });
  }
  next();
}

// --- Admin API: read/write the internal prompt. No prompt is defined here by default. ---
app.get("/api/config", requireAdmin, async (_req, res) => {
  const cfg = await readConfig();
  res.json(cfg);
});

app.post("/api/config", requireAdmin, async (req, res) => {
  const cfg = await readConfig();
  const { systemPrompt, model, reasoningEnabled } = req.body ?? {};
  if (typeof systemPrompt === "string") cfg.systemPrompt = systemPrompt;
  if (typeof model === "string" && model.trim()) cfg.model = model.trim();
  if (typeof reasoningEnabled === "boolean") cfg.reasoningEnabled = reasoningEnabled;
  cfg.updatedAt = new Date().toISOString();
  await writeConfig(cfg);
  res.json({ ok: true, config: cfg });
});

// Public: lets the chatbot know which model is active and whether a prompt has been set
// (never exposes the prompt text itself).
app.get("/api/meta", async (_req, res) => {
  const cfg = await readConfig();
  res.json({
    model: cfg.model,
    reasoningEnabled: cfg.reasoningEnabled,
    promptConfigured: Boolean(cfg.systemPrompt && cfg.systemPrompt.trim()),
    keyConfigured: Boolean(OPENROUTER_API_KEY),
  });
});

// --- Chat proxy: injects ONLY the admin-supplied prompt (if any) and calls OpenRouter. ---
app.post("/api/chat", async (req, res) => {
  if (!OPENROUTER_API_KEY) {
    return res.status(500).json({ error: "Server missing OPENROUTER_API_KEY." });
  }
  const cfg = await readConfig();
  const clientMessages = Array.isArray(req.body?.messages) ? req.body.messages : [];

  // The ONLY system content is whatever the admin typed. If the field is empty,
  // no system message is sent at all.
  const messages = [];
  if (cfg.systemPrompt && cfg.systemPrompt.trim()) {
    messages.push({ role: "system", content: cfg.systemPrompt });
  }
  for (const m of clientMessages) {
    if (!m || typeof m.role !== "string") continue;
    const msg = { role: m.role, content: m.content ?? "" };
    // Preserve reasoning_details unmodified when the client passes assistant turns back.
    if (m.reasoning_details) msg.reasoning_details = m.reasoning_details;
    messages.push(msg);
  }

  const payload = {
    model: cfg.model || "poolside/laguna-s-2.1:free",
    messages,
  };
  if (cfg.reasoningEnabled) payload.reasoning = { enabled: true };

  try {
    const upstream = await fetch(OPENROUTER_URL, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${OPENROUTER_API_KEY}`,
        "Content-Type": "application/json",
        "HTTP-Referer": process.env.PUBLIC_URL || "http://localhost",
        "X-Title": "Hhtr Chatbot",
      },
      body: JSON.stringify(payload),
    });
    const data = await upstream.json();
    if (!upstream.ok) {
      return res.status(upstream.status).json({ error: data?.error || "OpenRouter error", data });
    }
    const message = data?.choices?.[0]?.message ?? { role: "assistant", content: "" };
    res.json({ message, usage: data?.usage ?? null });
  } catch (err) {
    res.status(502).json({ error: "Failed to reach OpenRouter", detail: String(err) });
  }
});

app.get("/api/health", (_req, res) => res.json({ ok: true }));

// Serve the admin single-page app.
app.use("/admin", express.static(join(__dirname, "public", "admin")));
app.get("/", (_req, res) => res.redirect("/admin"));

app.listen(PORT, () => {
  console.log(`Hhtr backend listening on http://localhost:${PORT}`);
  console.log(`Admin app: http://localhost:${PORT}/admin`);
});
