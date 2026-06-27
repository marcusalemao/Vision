import { createClientFromRequest } from 'npm:@base44/sdk@0.8.31';

const ENTITIES: Record<string, string> = {
  FaceContextPerson:     'FaceContextPerson',
  FaceContextEncounter:  'FaceContextEncounter',
  FaceContextHUDLayout:  'FaceContextHUDLayout',
  persons:    'FaceContextPerson',
  encounters: 'FaceContextEncounter',
  layouts:    'FaceContextHUDLayout',
};

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
    const url = new URL(req.url);
    const id = url.searchParams.get("id");
    const entityParam = url.searchParams.get("entity") || "FaceContextPerson";
    const entityName = ENTITIES[entityParam] || "FaceContextPerson";
    const db = base44.asServiceRole.entities[entityName];

    // GET
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

    // POST
    if (req.method === "POST") {
      const body = await req.json().catch(() => ({}));

      // ── Ação especial: ai_summary ──
      if (body.action === "ai_summary") {
        const transcript: string = body.transcript || "";
        const personName: string = body.person_name || "desconhecido";
        if (!transcript.trim()) {
          return Response.json({ summary: "Encontro registrado" }, { headers: CORS });
        }
        try {
          // Usa o modelo do agente via base44 SDK
          const ai = base44.ai;
          const prompt = `Você é um assistente de memória social. Resuma em UMA linha curtíssima (máx 80 caracteres) o que foi conversado. Seja direto, sem verbosidade.\n\nPessoa: ${personName}\nConversa transcrita:\n${transcript.substring(0, 800)}\n\nResumo (1 linha):`;
          const result = await ai.complete({ prompt, max_tokens: 80 });
          const summary = (result?.text || result?.content || "").trim().replace(/^["']|["']$/g,"").substring(0, 120);
          return Response.json({ summary: summary || transcript.substring(0, 80) }, { headers: CORS });
        } catch (aiErr) {
          // Fallback: primeiras palavras da transcrição
          const fallback = transcript.substring(0, 80).replace(/\s+/g, " ").trim();
          return Response.json({ summary: fallback }, { headers: CORS });
        }
      }

      const created = await db.create(body);
      return Response.json(created, { status: 201, headers: CORS });
    }

    // PUT
    if (req.method === "PUT") {
      if (!id) return Response.json({ error: "id required" }, { status: 400, headers: CORS });
      const body = await req.json().catch(() => ({}));
      const updated = await db.update(id, body);
      return Response.json(updated, { headers: CORS });
    }

    // DELETE
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
