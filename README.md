[🇬🇧 English](#english) | [🇧🇷 Português](#portuguese)

<p align="center"><img src="https://raw.githubusercontent.com/marcusalemao/LiveCompanion/main/icons/icon-512.png" width="130" alt="Live Companion"/></p>

<a id="english"></a>
# Vision

Visual assistant with episodic memory for Rokid glasses — assistive technology (ADHD and low vision).

> **Status:** Archived research version — the cognitive engine that originated the current project. It works standalone for study (the browser emulator runs offline with face-api.js), but the full memory pipeline requires the Base44 backend. For the active, maintained version, see **[LiveCompanion](https://github.com/marcusalemao/LiveCompanion)** (Kotlin app for the glasses) and the [architecture evolution](#architecture) below.

## URLs
- **Live Companion** (user manager): https://live-companion.base44.app
- **HUD Editor** (dev, legacy): https://fc.alema.io/hud-editor.html
- **Emulator** (dev, legacy): https://fc.alema.io

## Architecture
- **APK (RokidLive)** → Rokid YodaOS (glasses)
- **Live Companion** → end user (mobile/PC) — memories, people, encounters, and skills synced automatically
- **HUD Editor** → developer
- **Emulator** → dev/testing

**Architecture evolution:** Vision was the first generation (Java APK + HTML emulator + Base44 functions). The active pipeline is now LiveCompanion — Kotlin/Android on the glasses, Camera2 API, structured RAG episodic memory, and automatic sync with the Live Companion web app.

---

[🇬🇧 English](#english) | [🇧🇷 Português](#portuguese)

<a id="portuguese"></a>
# Vision

Assistente visual com memória episódica para os óculos Rokid — tecnologia assistiva (TDAH e baixa visão).

> **Status:** Versão de pesquisa arquivada — o motor cognitivo que originou o projeto atual. Funciona de forma independente para estudo (o emulador roda offline no navegador com face-api.js), mas o pipeline completo de memória requer o backend Base44. Para a versão ativa e mantida, veja **[LiveCompanion](https://github.com/marcusalemao/LiveCompanion)** (app Kotlin para os óculos) e a [evolução da arquitetura](#arquitetura) abaixo.

## URLs
- **Live Companion** (manager do usuário): https://live-companion.base44.app
- **HUD Editor** (dev, legado): https://fc.alema.io/hud-editor.html
- **Emulador** (dev, legado): https://fc.alema.io

## Arquitetura
- **APK (RokidLive)** → Rokid YodaOS (glasses)
- **Live Companion** → usuário final (celular/PC) — memórias, pessoas, encontros e skills sincronizados automaticamente
- **HUD Editor** → desenvolvedor
- **Emulador** → dev/testes

**Evolução da arquitetura:** o Vision foi a primeira geração (APK Java + emulador HTML + functions Base44). O pipeline ativo hoje é o LiveCompanion — Kotlin/Android nos óculos, API Camera2, memória episódica com RAG estruturado e sincronização automática com o webapp Live Companion.
