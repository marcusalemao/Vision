# FaceContext 👓

**AR emulator for Rokid smart glasses** — assistente de contexto social para TDAH.

## O que é

FaceContext é um HUD de realidade aumentada para os óculos Rokid AI. Quando você encontra alguém, os óculos mostram quem é a pessoa, empresa, cargo e contexto do último encontro.

## Display

- **Resolução:** 480×640px portrait (waveguide vertical)
- **Fundo:** #000000 = transparente no prisma AR
- **Cores:** monocromático verde

## Controles físicos (KeyEvents)

| Keycode | Botão | Ação |
|---------|-------|------|
| 26 | Power | Ativar Modo Captura |
| 4 | Back | Dispensar HUD |
| 82 (long) | Menu | Abrir contatos |
| 24 | Vol+ | Próximo perfil |
| 25 | Vol- | Perfil anterior |

## Fases de desenvolvimento

- [x] Webapp — gestão de perfis + HUD editor
- [x] Emulador web — testa o HUD no celular/desktop
- [ ] APK YodaOS — deploy nos óculos físicos
- [ ] TF Lite offline — reconhecimento facial no device
- [ ] Integração cloud — fallback AWS Rekognition

## Links

- 🌐 [Emulador ao vivo](https://marcusalemao.github.io/facecontext)
