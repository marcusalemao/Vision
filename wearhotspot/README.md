# WatchHotspot — Wear OS Hotspot App

App para Galaxy Watch 7 Pro que transforma o 5G do relógio em hotspot WiFi para os óculos Rokid.

## Requisitos
- Galaxy Watch 7 Pro (SM-L310/L300) com root (Magisk)
- Chip 5G ativo (Claro)
- ADB para instalar o APK

## Instalação
```bash
adb install -r wearhotspot.apk
```

## Uso
1. Abra o app no relógio
2. Toque no botão verde "LIGAR"
3. Conecte os óculos Rokid no WiFi "Watch5G" (senha: 12345678)

## Como funciona
O app usa root para:
1. Ativar o tethering nativo do Android (escondido pela Samsung)
2. Configurar IP forwarding + iptables NAT do 5G → WiFi
3. Fallback: hostapd + dnsmasq manual se o nativo falhar

## Config
- SSID: Watch5G
- Senha: 12345678
- IP do AP: 192.168.43.1
