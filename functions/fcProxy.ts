import { createClientFromRequest } from 'npm:@base44/sdk@0.8.31';

const ENTITIES: Record<string, string> = {
  FaceContextPerson:     'FaceContextPerson',
  FaceContextEncounter:  'FaceContextEncounter',
  FaceContextHUDLayout:  'FaceContextHUDLayout',
  AIUsageLog:            'AIUsageLog',
  persons:    'FaceContextPerson',
  encounters: 'FaceContextEncounter',
  layouts:    'FaceContextHUDLayout',
};

const GEMINI_API_KEY = Deno.env.get("GEMINI_API_KEY") || "";
const GEMINI_MODEL   = "gemini-2.5-flash";

// ── Preços Gemini 2.5 Flash (por 1M tokens, em USD) ──
const PRICE_INPUT_PER_M  = 0.30;
const PRICE_OUTPUT_PER_M = 2.50;
const USD_TO_BRL         = 5.70;   // atualizar periodicamente
const BUDGET_USD         = 18.00;  // ~R$100 convertido
const ALERT_THRESHOLD    = 0.80;   // alerta em 80% do budget

// ── Estimativa de tokens (sem chamar API de contagem) ──
function estimateTokens(text: string): number {
  // ~1 token por 4 chars em português/inglês
  return Math.ceil(text.length / 4);
}

function calcCost(inputTokens: number, outputTokens: number) {
  const usd = (inputTokens / 1_000_000) * PRICE_INPUT_PER_M
            + (outputTokens / 1_000_000) * PRICE_OUTPUT_PER_M;
  return { usd, brl: usd * USD_TO_BRL };
}

// ── Caching simples: evita resumo duplicado da mesma transcrição ──
const recentCache = new Map<string, string>();

function cacheKey(text: string): string {
  // hash leve: primeiros 60 chars + tamanho
  return text.substring(0, 60).trim() + "|" + text.length;
}

async function callGemini(prompt: string, maxTokens = 150): Promise<{ text: string; inputTokens: number; outputTokens: number }> {
  const res = await fetch(
    `https://generativelanguage.googleapis.com/v1beta/models/${GEMINI_MODEL}:generateContent?key=${GEMINI_API_KEY}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        contents: [{ parts: [{ text: prompt }] }],
        generationConfig: { maxOutputTokens: maxTokens, temperature: 0.4 }
      })
    }
  );
  const d = await res.json();
  if (d.error) throw new Error(d.error.message || "Gemini error");

  const text = d?.candidates?.[0]?.content?.parts?.[0]?.text?.trim() || "";
  // Usa contagem real se disponível, senão estima
  const inputTokens  = d?.usageMetadata?.promptTokenCount     || estimateTokens(prompt);
  const outputTokens = d?.usageMetadata?.candidatesTokenCount || estimateTokens(text);

  return { text, inputTokens, outputTokens };
}

async function detectLang(text: string): Promise<string> {
  if (!text || text.length < 10) return "pt";
  // Heurística rápida antes de chamar a API (economiza tokens)
  const ptWords = /\b(que|você|para|com|uma|não|mais|por|isso|como|mas)\b/i;
  const enWords = /\b(the|and|for|you|that|with|this|from|have|are)\b/i;
  if (ptWords.test(text)) return "pt";
  if (enWords.test(text)) return "en";
  // Só chama Gemini se realmente não dá pra detectar localmente
  const r = await callGemini(
    `Language code only (ISO 639-1, 2 chars): "${text.substring(0,100)}"`, 3
  );
  return r.text.replace(/[^a-z]/g,"").substring(0,2) || "pt";
}

// ── Verifica budget mensal e dispara alerta ──
async function checkBudgetAlert(db: any, newCostUsd: number) {
  const monthKey = new Date().toISOString().substring(0, 7);
  const logs = await db["AIUsageLog"].filter({ month_key: monthKey }, undefined, 500);
  const totalUsd = (logs || []).reduce((sum: number, l: any) => sum + (l.cost_usd || 0), 0) + newCostUsd;
  const pct = (totalUsd / BUDGET_USD) * 100;

  return {
    month_total_usd: totalUsd,
    month_total_brl: totalUsd * USD_TO_BRL,
    budget_pct: Math.round(pct),
    alert: pct >= ALERT_THRESHOLD * 100,
    exhausted: totalUsd >= BUDGET_USD
  };
}

Deno.serve(async (req) => {
  const CORS = {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type",
  };
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });

  try {
    const base44 = createClientFromRequest(req);
    const url    = new URL(req.url);
    const id     = url.searchParams.get("id");
    const entityParam = url.searchParams.get("entity") || "FaceContextPerson";
    const entityName  = ENTITIES[entityParam] || "FaceContextPerson";

    // Proxy para entidades genéricas
    const db: Record<string, any> = {};
    for (const key of Object.keys(ENTITIES)) {
      db[key] = base44.asServiceRole.entities[ENTITIES[key]];
    }
    const entityDb = db[entityParam] || db["FaceContextPerson"];

    // ── GET ──
    if (req.method === "GET") {
      // Rota especial: /usage — retorna resumo de gastos
      if (url.searchParams.get("action") === "usage") {
        const monthKey = url.searchParams.get("month") || new Date().toISOString().substring(0, 7);
        const logs = await db["AIUsageLog"].filter({ month_key: monthKey }, undefined, 500);
        const totalUsd = (logs || []).reduce((s: number, l: any) => s + (l.cost_usd || 0), 0);
        const totalCalls = (logs || []).length;
        const byAction: Record<string, number> = {};
        for (const l of (logs || [])) {
          byAction[l.action] = (byAction[l.action] || 0) + 1;
        }
        return Response.json({
          month: monthKey,
          total_calls: totalCalls,
          total_usd:   +totalUsd.toFixed(4),
          total_brl:   +(totalUsd * USD_TO_BRL).toFixed(2),
          budget_usd:  BUDGET_USD,
          budget_pct:  Math.round((totalUsd / BUDGET_USD) * 100),
          budget_remaining_brl: +((BUDGET_USD - totalUsd) * USD_TO_BRL).toFixed(2),
          by_action:   byAction,
          model:       GEMINI_MODEL
        }, { headers: CORS });
      }

      if (id) return Response.json(await entityDb.get(id), { headers: CORS });
      const all: unknown[] = [];
      let skip = 0;
      while (true) {
        const batch = await entityDb.filter({}, undefined, 100, skip);
        if (!batch || batch.length === 0) break;
        all.push(...batch);
        if (batch.length < 100) break;
        skip += 100;
      }
      return Response.json(all, { headers: CORS });
    }

    // ── POST ──
    if (req.method === "POST") {
      const body = await req.json().catch(() => ({}));

      // ── ai_summary ──
      if (body.action === "ai_summary") {
        const transcript: string = body.transcript  || "";
        const personName: string = body.person_name || "desconhecido";
        const userLang: string   = body.lang        || "";

        if (!transcript.trim()) {
          return Response.json({ summary: "Encontro registrado", lang: "pt", cost_usd: 0 }, { headers: CORS });
        }

        // Cache: evita chamar Gemini para a mesma conversa
        const ck = cacheKey(transcript);
        if (recentCache.has(ck)) {
          return Response.json({ summary: recentCache.get(ck), lang: userLang||"pt", cost_usd: 0, cached: true }, { headers: CORS });
        }

        // Trunca input pra economizar tokens (máx 600 chars ~ 150 tokens)
        const truncated = transcript.length > 600 ? transcript.substring(0, 600) + "…" : transcript;

        const lang = userLang || await detectLang(truncated);
        const langNames: Record<string,string> = {
          pt:"português", en:"English", es:"español", fr:"français",
          de:"Deutsch", ja:"日本語", zh:"中文", it:"italiano"
        };
        const langLabel = langNames[lang] || lang;

        // Prompt enxuto (menos tokens)
        const prompt =
          `Assistente de memória. Responda em ${langLabel}. ` +
          `1 linha, máx 80 chars, sem verbosidade.\n` +
          `Pessoa: ${personName}\n` +
          `"${truncated}"\nResumo:`;

        try {
          const { text, inputTokens, outputTokens } = await callGemini(prompt, 80);
          const summary = text.replace(/^["'`]|["'`]$/g,"").substring(0, 120);
          const { usd, brl } = calcCost(inputTokens, outputTokens);
          const monthKey = new Date().toISOString().substring(0, 7);

          // Salva log assincronamente
          db["AIUsageLog"].create({
            action: "ai_summary", model: GEMINI_MODEL,
            input_tokens: inputTokens, output_tokens: outputTokens,
            cost_usd: usd, cost_brl: brl,
            person_name: personName, success: true, month_key: monthKey
          }).catch(() => {});

          // Cache por 5 min
          recentCache.set(ck, summary);
          setTimeout(() => recentCache.delete(ck), 5 * 60 * 1000);

          // Verifica budget (assíncrono, não bloqueia resposta)
          const budgetInfo = await checkBudgetAlert(db, usd);

          return Response.json({
            summary: summary || truncated.substring(0,80),
            lang, model: GEMINI_MODEL,
            cost_usd: +usd.toFixed(6),
            ...(budgetInfo.alert ? {
              budget_alert: true,
              budget_pct: budgetInfo.budget_pct,
              budget_remaining_brl: budgetInfo.budget_remaining_brl
            } : {})
          }, { headers: CORS });
        } catch (e: any) {
          return Response.json({ summary: transcript.substring(0,80), lang: "pt", model: "fallback", error: e.message }, { headers: CORS });
        }
      }

      // ── ai_detect_name ──
      if (body.action === "ai_detect_name") {
        const text: string = body.text || "";
        if (!text.trim()) return Response.json({ name: null }, { headers: CORS });

        // Tenta regex primeiro (zero custo)
        const regexPatterns = [
          /(?:me chamo|meu nome é|sou (?:a |o |))([\w\s]{2,25})/i,
          /(?:pode me chamar de|me chamam de)\s+([\w\s]{2,20})/i,
          /(?:i(?:'m| am)|my name(?:'s| is))\s+([\w\s]{2,25})/i,
          /(?:soy|me llamo)\s+([\w\s]{2,25})/i,
        ];
        for (const rx of regexPatterns) {
          const m = text.match(rx);
          if (m?.[1]) return Response.json({ name: m[1].trim(), source: "regex" }, { headers: CORS });
        }

        // Só chama Gemini se regex falhar
        const prompt = `Nome próprio de pessoa nesta frase (só o nome, ou "null"):\n"${text.substring(0,200)}"`;
        try {
          const { text: result, inputTokens, outputTokens } = await callGemini(prompt, 15);
          const name = result.replace(/[^a-zA-ZÀ-ú\s]/g,"").trim();
          const { usd, brl } = calcCost(inputTokens, outputTokens);
          const monthKey = new Date().toISOString().substring(0, 7);
          db["AIUsageLog"].create({
            action: "ai_detect_name", model: GEMINI_MODEL,
            input_tokens: inputTokens, output_tokens: outputTokens,
            cost_usd: usd, cost_brl: brl, success: true, month_key: monthKey
          }).catch(() => {});
          return Response.json({ name: name && name !== "null" ? name : null, source: "gemini" }, { headers: CORS });
        } catch (e: any) {
          return Response.json({ name: null, error: e.message }, { headers: CORS });
        }
      }

      // ── list ── (retorna registros da entidade sem criar lixo)
      if (body.action === "list") {
        const all: any[] = [];
        let skip = 0;
        const limit = Math.min(Number(body.limit) || 500, 500);
        while (all.length < limit) {
          const batch = await entityDb.filter({}, undefined, 100, skip);
          if (!batch || batch.length === 0) break;
          all.push(...batch);
          if (batch.length < 100) break;
          skip += 100;
        }
        return Response.json(all.slice(0, limit), { headers: CORS });
      }

      // ── save_embedding ── (cadastro de rosto: descriptor 128 floats do face-api no cliente)
      if (body.action === "save_embedding") {
        const personId = body.person_id;
        const emb = body.embedding;
        if (!personId) return Response.json({ ok: false, error: "person_id required" }, { status: 400, headers: CORS });
        if (!Array.isArray(emb) || emb.length !== 128 || !emb.every((v: any) => typeof v === "number" && isFinite(v))) {
          return Response.json({ ok: false, error: "embedding must be an array of 128 finite floats" }, { status: 400, headers: CORS });
        }
        try {
          const updated = await db["FaceContextPerson"].update(personId, { face_embedding: JSON.stringify(emb) });
          return Response.json({ ok: true, person_id: personId, face_embedding_saved: true }, { headers: CORS });
        } catch (e: any) {
          return Response.json({ ok: false, error: e.message }, { status: 500, headers: CORS });
        }
      }

      // Acao desconhecida: NAO criar registro lixo (bug historico: action 'list' criava registros nulos)
      if (body.action) return Response.json({ error: "unknown_action: " + body.action }, { status: 400, headers: CORS });

      // ── CRUD normal (apenas sem action) ──
      const created = await entityDb.create(body);
      return Response.json(created, { status: 201, headers: CORS });
    }

    // ── PUT ──
    if (req.method === "PUT") {
      if (!id) return Response.json({ error: "id required" }, { status: 400, headers: CORS });
      const updated = await entityDb.update(id, await req.json().catch(() => ({})));
      return Response.json(updated, { headers: CORS });
    }

    // ── DELETE ──
    if (req.method === "DELETE") {
      if (!id) return Response.json({ error: "id required" }, { status: 400, headers: CORS });
      await entityDb.delete(id);
      return Response.json({ ok: true }, { headers: CORS });
    }

    return Response.json({ error: "method_not_supported" }, { status: 405, headers: CORS });
  } catch (error: any) {
    return Response.json({ error: error.message }, { status: 500, headers: CORS });
  }
});
