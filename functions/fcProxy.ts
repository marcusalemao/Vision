import { createClientFromRequest } from 'npm:@base44/sdk@0.8.31';

const ENTITIES: Record<string, string> = {
  FaceContextPerson:     'FaceContextPerson',
  FaceContextEncounter:  'FaceContextEncounter',
  FaceContextHUDLayout:  'FaceContextHUDLayout',
  persons:    'FaceContextPerson',
  encounters: 'FaceContextEncounter',
  layouts:    'FaceContextHUDLayout',
};

const GEMINI_API_KEY = Deno.env.get("GEMINI_API_KEY") || "";
const GEMINI_MODEL   = "gemini-2.5-flash";

async function callGemini(prompt: string, maxTokens = 150): Promise<string> {
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
  return d?.candidates?.[0]?.content?.parts?.[0]?.text?.trim() || "";
}

// Detecta idioma da transcrição e retorna código (pt, en, es, fr, etc)
async function detectLang(text: string): Promise<string> {
  if (!text || text.length < 10) return "pt";
  const r = await callGemini(
    `Qual o idioma deste texto? Responda APENAS com o código ISO 639-1 (ex: pt, en, es, fr, de, ja, zh).\nTexto: "${text.substring(0,200)}"`,
    5
  );
  return r.replace(/[^a-z]/g,"").substring(0,2) || "pt";
}

Deno.serve(async (req) => {
  const CORS = {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type",
  };

  if (req.method === "OPTIONS") {
    return new Response(null, { status: 204, headers: CORS });
  }

  try {
    const base44 = createClientFromRequest(req);
    const url    = new URL(req.url);
    const id     = url.searchParams.get("id");
    const entityParam = url.searchParams.get("entity") || "FaceContextPerson";
    const entityName  = ENTITIES[entityParam] || "FaceContextPerson";
    const db = base44.asServiceRole.entities[entityName];

    // ── GET ──
    if (req.method === "GET") {
      if (id) {
        const record = await db.get(id);
        return Response.json(record, { headers: CORS });
      }
      const all: unknown[] = [];
      let skip = 0;
      while (true) {
        const batch = await db.filter({}, { limit: 100, skip });
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

      // ── ai_summary — gera resumo do encontro via Gemini ──
      if (body.action === "ai_summary") {
        const transcript: string  = body.transcript  || "";
        const personName: string  = body.person_name || "desconhecido";
        const userLang: string    = body.lang        || "";   // idioma preferido do usuário

        if (!transcript.trim()) {
          return Response.json({ summary: "Encontro registrado", lang: "pt" }, { headers: CORS });
        }

        // Detecta idioma da conversa
        const lang = userLang || await detectLang(transcript);

        const langNames: Record<string,string> = {
          pt:"português", en:"English", es:"español",
          fr:"français", de:"Deutsch", ja:"日本語", zh:"中文", it:"italiano"
        };
        const langLabel = langNames[lang] || lang;

        const prompt =
          `Você é um assistente de memória social discreto.\n` +
          `Responda SEMPRE em ${langLabel}.\n` +
          `Resuma em UMA linha curtíssima (máx 80 caracteres) o que foi conversado ou o contexto do encontro.\n` +
          `Seja direto, sem verbosidade. Use linguagem natural.\n\n` +
          `Pessoa: ${personName}\n` +
          `Conversa:\n${transcript.substring(0, 1000)}\n\n` +
          `Resumo (1 linha, em ${langLabel}):`;

        try {
          const summary = (await callGemini(prompt, 80))
            .replace(/^["'`]|["'`]$/g,"")
            .substring(0, 120);

          return Response.json({
            summary: summary || transcript.substring(0,80),
            lang,
            model: GEMINI_MODEL
          }, { headers: CORS });

        } catch (e) {
          return Response.json({
            summary: transcript.substring(0,80),
            lang: "pt",
            model: "fallback"
          }, { headers: CORS });
        }
      }

      // ── ai_detect_name — extrai nome de uma frase via Gemini ──
      if (body.action === "ai_detect_name") {
        const text: string = body.text || "";
        if (!text.trim()) return Response.json({ name: null }, { headers: CORS });

        const prompt =
          `Extraia APENAS o nome próprio de uma pessoa desta frase de apresentação.\n` +
          `Se não houver nome próprio claro, responda: null\n` +
          `Responda APENAS com o nome, sem mais nada.\n\n` +
          `Frase: "${text}"`;

        const name = (await callGemini(prompt, 20)).replace(/[^a-zA-ZÀ-ú\s]/g,"").trim();
        return Response.json({ name: name && name !== "null" ? name : null }, { headers: CORS });
      }

      // ── CRUD normal ──
      const created = await db.create(body);
      return Response.json(created, { status: 201, headers: CORS });
    }

    // ── PUT ──
    if (req.method === "PUT") {
      if (!id) return Response.json({ error: "id required" }, { status: 400, headers: CORS });
      const body    = await req.json().catch(() => ({}));
      const updated = await db.update(id, body);
      return Response.json(updated, { headers: CORS });
    }

    // ── DELETE ──
    if (req.method === "DELETE") {
      if (!id) return Response.json({ error: "id required" }, { status: 400, headers: CORS });
      await db.delete(id);
      return Response.json({ ok: true }, { headers: CORS });
    }

    return Response.json({ error: "method_not_supported" }, { status: 405, headers: CORS });

  } catch (error) {
    return Response.json({ error: error.message }, { status: 500, headers: CORS });
  }
});
