[🇬🇧 English](#english) | [🇧🇷 Português](#portuguese)

<p align="center"><img src="https://raw.githubusercontent.com/marcusalemao/VisualContext/main/icons/icon-512.png" width="130" alt="VisualContext"/></p>

<a id="english"></a>
# Vision

Visual assistant with episodic memory for Rokid glasses — assistive technology (ADHD and low vision).

> **Status:** Archived research version — the cognitive engine that originated the current project. The browser emulator runs the full face pipeline (face-api.js detection, enrollment and recognition end-to-end, wired to the Base44 backend). For the active, maintained version, see **[VisualContext](https://github.com/marcusalemao/VisualContext)** (Kotlin app for the glasses) and the [architecture evolution](#architecture) below.

## URLs
- **VisualContext** (user manager web app): https://visualcontext.base44.app
- **HUD Editor** (dev, legacy): https://fc.alema.io/hud-editor.html
- **Emulator** (dev, legacy): https://fc.alema.io

## Architecture
- **APK (RokidLive)** → Rokid YodaOS (glasses)
- **VisualContext** (web app) → end user (mobile/PC) — memories, people, encounters, and skills synced automatically
- **HUD Editor** → developer
- **Emulator** → dev/testing

**Architecture evolution:** Vision was the first generation (Java APK + HTML emulator + Base44 functions). The active pipeline is now VisualContext — Kotlin/Android on the glasses, Camera2 API, structured RAG episodic memory, and automatic sync with the VisualContext web app.

---

[🇬🇧 English](#english) | [🇧🇷 Português](#portuguese)

<a id="portuguese"></a>
# Vision

Assistente visual com memória episódica para os óculos Rokid — tecnologia assistiva (TDAH e baixa visão).

> **Status:** Versão de pesquisa arquivada — o motor cognitivo que originou o projeto atual. O emulador roda o pipeline facial completo (detecção, cadastro e reconhecimento de ponta a ponta com face-api.js, ligado ao backend Base44). Para a versão ativa e mantida, veja **[VisualContext](https://github.com/marcusalemao/VisualContext)** (app Kotlin para os óculos) e a [evolução da arquitetura](#arquitetura) abaixo.

## URLs
- **VisualContext** (manager do usuário): https://visualcontext.base44.app
- **HUD Editor** (dev, legado): https://fc.alema.io/hud-editor.html
- **Emulador** (dev, legado): https://fc.alema.io

## Arquitetura
- **APK (RokidLive)** → Rokid YodaOS (glasses)
- **VisualContext** (web app) → usuário final (celular/PC) — memórias, pessoas, encontros e skills sincronizados automaticamente
- **HUD Editor** → desenvolvedor
- **Emulador** → dev/testes

**Evolução da arquitetura:** o Vision foi a primeira geração (APK Java + emulador HTML + functions Base44). O pipeline ativo hoje é o VisualContext — Kotlin/Android nos óculos, API Camera2, memória episódica com RAG estruturado e sincronização automática com o webapp do VisualContext.
