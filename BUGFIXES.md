[🇬🇧 English](#english) | [🇧🇷 Português](#portuguese)

<a id="english"></a>
# FaceContext — Bug Fix History

> Log file of bug fixes applied to the project.  
> Repository: [marcusalemao/facecontext](https://github.com/marcusalemao/facecontext)

---

## 2026-06-30 — Facial Recognition Fixes

**Commit:** `dbea2b04fb`  
**Affected file:** `emulator/index.html`  
**Commit message:** `fix: reconhecimento facial — THRESH, inputSize, buildEmbeddings, catch log`

---

### Bug 1 — Overly restrictive recognition threshold

| Field | Before | After |
|-------|--------|-------|
| `THRESH` | `0.44` | `0.55` |

**Cause:** The system used Euclidean distance < 0.44 to consider a face recognized. This value is suitable for photos taken under controlled conditions, but profile photos (WhatsApp, LinkedIn) have different angles, lighting, and expressions compared to live camera feeds — generating larger distances even for the same person.

**Effect before fix:** The system detected faces in the frame but never confirmed a match, resulting in "none recognized" even with registered profiles.

**Fix:** Increased to `0.55`, which is the recommended threshold by the official `face-api.js` repository for comparing profile photos with live camera feeds.

---

### Bug 2 — inputSize too low in real-time detection

| Field | Before | After |
|-------|--------|-------|
| `inputSize` (live camera) | `224` | `416` |
| `scoreThreshold` (camera) | `0.38` | `0.35` |
| `inputSize` (profile photo) | `224` | `416` |
| `scoreThreshold` (profile photo) | `0.35` | `0.30` |

**Cause:** The `TinyFaceDetector` from `face-api.js` accepts `inputSize` of 128, 224, 320, 416, or 608. The 224px value is the fastest but consistently fails on:
- Partially framed faces
- Smaller faces (person more than ~1m from camera)
- Frames with movement

**Effect before fix:** Intermittent detection, especially under real-world usage conditions. The badge displayed "⬡ 0 faces" even with valid photos in the database.

**Fix:** Increased to `416px` in both contexts (live camera and reference embeddings generation). The performance impact is acceptable for the Rokid CXR-S hardware.

---

### Bug 3 — Embeddings not rebuilt after registering a new profile

**Cause:** The `buildEmbeddings()` function — responsible for loading profile photos and generating reference vectors — was only called once on initial page load. When the user saved a new unknown profile via modal, the internal embeddings list **was not updated**.

**Effect before fix:** After registering a new person (or updating an existing profile's photo via Manager), the emulator continued without recognizing them until a full page reload.

**Fix:** Added `buildEmbeddings()` call immediately after `saveUnknown()` succeeds:

```js
// before
profiles.push(saved);
showToast("✅ "+name+" salvo! Adicione a foto no Manager.");

// after
profiles.push(saved);
showToast("✅ "+name+" salvo! Adicione a foto no Manager.");
buildEmbeddings();  // updates embeddings (works after photo is added)
```

---

### Bug 4 — Photo loading errors silenced

**Cause:** The `try/catch` block inside `buildEmbeddings()` used `catch(e){}` — completely empty. Any failure when loading a photo (CORS blocked, broken URL, face not detected in reference image) was discarded silently.

**Effect before fix:** Impossible to debug why certain profiles were never recognized. The badge could display "⬡ 2 faces" when there should be 5, with no indication of which 3 failed.

**Fix:** Added log output to the emulator console:

```js
// before
}catch(e){}

// after
}catch(e){
  lg("⚠ falha ao carregar foto: "+p.name+" — "+e, false);
}
```

The message appears in the emulator's internal log panel and in the browser `console` for DevTools debugging.

---

## 2026-06-30 — Gemini Budget Monitor Fix

**Commit:** `c5a9d76002`  
**Affected file:** `functions/fcProxy.ts`  
**Commit message:** `fix: conecta checkBudgetAlert ao fluxo ai_summary`

### Bug 5 — checkBudgetAlert defined but never called

**Cause:** The `checkBudgetAlert()` function was implemented to monitor monthly Gemini expenditure and return an alert when exceeding 80% of the R$100 budget. However, it was never invoked in the main `ai_summary` flow.

**Effect before fix:** The system recorded expenses in the database (`AIUsageLog`) correctly, but the `budget_alert` field never appeared in API responses — making the alert system completely non-functional.

**Fix:** Added `await checkBudgetAlert(db, usd)` call after usage logging, with result injected into response:

```ts
const budgetInfo = await checkBudgetAlert(db, usd);

return Response.json({
  summary: ...,
  cost_usd: ...,
  ...(budgetInfo.alert ? {
    budget_alert: true,
    budget_pct: budgetInfo.budget_pct,
    budget_remaining_brl: budgetInfo.budget_remaining_brl
  } : {})
}, { headers: CORS });
```

When monthly spend exceeds R$80 (80% of R$100), the response automatically includes `budget_alert: true` — allowing the emulator to show a visual warning.

---

## How to verify if fixes are active

Access the production emulator and open DevTools (F12 → Console):

1. **Badge shows "⬡ X faces"** with X > 0 → embeddings loaded ✅
2. **No red lines in internal log** → photos loading without CORS ✅
3. **Match appears when framing known face** → correct THRESH and inputSize ✅
4. **After saving unknown, badge updates** → buildEmbeddings working ✅

---

[🇬🇧 English](#english) | [🇧🇷 Português](#portuguese)

<a id="portuguese"></a>
# FaceContext — Histórico de Correções de Bugs

> Arquivo de registro das correções aplicadas ao projeto.  
> Repositório: [marcusalemao/facecontext](https://github.com/marcusalemao/facecontext)

---

## 30/06/2026 — Correções de Reconhecimento Facial

**Commit:** `dbea2b04fb`  
**Arquivo afetado:** `emulator/index.html`  
**Commit message:** `fix: reconhecimento facial — THRESH, inputSize, buildEmbeddings, catch log`

---

### Bug 1 — Threshold de reconhecimento muito restritivo

| Campo | Antes | Depois |
|-------|-------|--------|
| `THRESH` | `0.44` | `0.55` |

**Causa:** O sistema usava distância euclidiana < 0.44 para considerar um rosto como reconhecido. Esse valor é adequado para fotos tiradas em condições controladas, mas fotos de perfil (WhatsApp, LinkedIn) têm ângulo, iluminação e expressão diferentes da câmera ao vivo — o que gera distâncias maiores mesmo para a mesma pessoa.

**Efeito antes da correção:** O sistema detectava rostos no frame mas nunca confirmava um match, resultando em "nenhum reconhecido" mesmo com perfis cadastrados.

**Fix:** Aumentado para `0.55`, que é o threshold recomendado pelo repositório oficial do `face-api.js` para comparações entre foto de perfil e câmera ao vivo.

---

### Bug 2 — inputSize muito baixo na detecção em tempo real

| Campo | Antes | Depois |
|-------|-------|--------|
| `inputSize` (câmera ao vivo) | `224` | `416` |
| `scoreThreshold` (câmera) | `0.38` | `0.35` |
| `inputSize` (foto de perfil) | `224` | `416` |
| `scoreThreshold` (foto perfil) | `0.35` | `0.30` |

**Causa:** O `TinyFaceDetector` do `face-api.js` aceita `inputSize` de 128, 224, 320, 416 ou 608. O valor 224px é o mais rápido mas falha sistematicamente em:
- Rostos parcialmente enquadrados
- Rostos menores (pessoa a mais de ~1m da câmera)
- Frames com movimento

**Efeito antes da correção:** Detecção intermitente, especialmente em condições reais de uso. O badge mostrava "⬡ 0 rostos" mesmo com fotos válidas no banco.

**Fix:** Aumentado para `416px` em ambos os contextos (câmera ao vivo e construção dos embeddings de referência). O impacto de performance é aceitável para o hardware do Rokid CXR-S.

---

### Bug 3 — Embeddings não reconstruídos após cadastro de novo perfil

**Causa:** A função `buildEmbeddings()` — responsável por carregar as fotos dos perfis e gerar os vetores de referência — era chamada apenas uma vez no carregamento inicial da página. Quando o usuário salvava um novo perfil desconhecido via modal, a lista interna de embeddings **não era atualizada**.

**Efeito antes da correção:** Após cadastrar uma pessoa nova (ou atualizar a foto de um perfil existente via Manager), o emulador continuava sem reconhecê-la até o próximo reload completo da página.

**Fix:** Adicionada chamada `buildEmbeddings()` imediatamente após o sucesso do `saveUnknown()`:

```js
// antes
profiles.push(saved);
showToast("✅ "+name+" salvo! Adicione a foto no Manager.");

// depois
profiles.push(saved);
showToast("✅ "+name+" salvo! Adicione a foto no Manager.");
buildEmbeddings();  // atualiza embeddings (funciona após foto ser adicionada)
```

---

### Bug 4 — Erros de carregamento de foto silenciados

**Causa:** O bloco `try/catch` dentro de `buildEmbeddings()` usava `catch(e){}` — completamente vazio. Qualquer falha ao carregar uma foto (CORS bloqueado, URL quebrada, rosto não detectado na imagem de referência) era descartada silenciosamente.

**Efeito antes da correção:** Impossível depurar por que determinados perfis nunca eram reconhecidos. O badge podia mostrar "⬡ 2 rostos" quando deveriam ser 5, sem indicação de quais 3 falharam.

**Fix:** Adicionado log no console do emulador:

```js
// antes
}catch(e){}

// depois
}catch(e){
  lg("⚠ falha ao carregar foto: "+p.name+" — "+e, false);
}
```

A mensagem aparece no painel de log interno do emulador e no `console` do browser para debug via DevTools.

---

## 30/06/2026 — Correção no Monitor de Budget Gemini

**Commit:** `c5a9d76002`  
**Arquivo afetado:** `functions/fcProxy.ts`  
**Commit message:** `fix: conecta checkBudgetAlert ao fluxo ai_summary`

### Bug 5 — checkBudgetAlert definida mas nunca chamada

**Causa:** A função `checkBudgetAlert()` foi implementada para monitorar o gasto mensal do Gemini e retornar um alerta quando ultrapassasse 80% do budget de R$100. Porém, ela nunca era invocada no fluxo principal do `ai_summary`.

**Efeito antes da correção:** O sistema registrava os gastos no banco (`AIUsageLog`) corretamente, mas o campo `budget_alert` nunca aparecia nas respostas da API — tornando o sistema de alerta completamente inoperante.

**Fix:** Adicionada chamada `await checkBudgetAlert(db, usd)` após o registro de uso, com resultado injetado na resposta:

```ts
const budgetInfo = await checkBudgetAlert(db, usd);

return Response.json({
  summary: ...,
  cost_usd: ...,
  ...(budgetInfo.alert ? {
    budget_alert: true,
    budget_pct: budgetInfo.budget_pct,
    budget_remaining_brl: budgetInfo.budget_remaining_brl
  } : {})
}, { headers: CORS });
```

Quando o gasto mensal ultrapassa R$80 (80% de R$100), a resposta inclui automaticamente `budget_alert: true` — permitindo que o emulador exiba um aviso visual.

---

## Como verificar se as correções estão ativas

Acesse o emulador em produção e abra o DevTools (F12 → Console):

1. **Badge mostra "⬡ X rostos"** com X > 0 → embeddings carregados ✅
2. **Nenhuma linha vermelha no log interno** → fotos carregando sem CORS ✅
3. **Match aparece ao enquadrar rosto conhecido** → THRESH e inputSize corretos ✅
4. **Após salvar desconhecido, badge atualiza** → buildEmbeddings funcionando ✅

---

*Gerado em 30/06/2026 — FaceContext AI Social Memory Extension*
