package com.facecontext.hotspot;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Enumeration;

/**
 * Gerencia o hotspot WiFi usando root.
 * 
 * Estratégia:
 * 1. Usa o tethering nativo do Android via cmd connectivity ( Wear OS 6 / Android 16)
 * 2. Fallback: configura manualmente com hostapd + dnsmasq + iptables
 * 3. Garante que o tráfego do 5G (rmnet) é roteado para o WiFi AP
 */
public class HotspotManager {
    private static final String TAG = "WatchHotspot";

    // Config do AP
    private static final String AP_SSID = "Watch5G";
    private static final String AP_PASS = "12345678";
    private static final String AP_INTERFACE = "wlan0";
    private static final String AP_IP = "192.168.43.1";
    private static final String DHCP_RANGE = "192.168.43.10,192.168.43.50,12h";

    // Interfaces celulares possíveis (varia por dispositivo)
    private static final String[] CELLULAR_IFACES = { "rmnet0", "rmnet_data0", "rmnet_r_0", "ccmni0", "wlan1" };

    private final Context context;
    private boolean isRunning = false;

    public HotspotManager(Context context) {
        this.context = context;
    }

    public boolean isRunning() {
        return isRunning;
    }

    /**
     * Liga o hotspot — tenta método nativo primeiro, depois manual.
     */
    public synchronized boolean start() {
        if (isRunning) return true;

        Log.i(TAG, "Iniciando hotspot...");

        // Método 1: Tethering nativo do Android (mais limpo, usa softap do sistema)
        if (tryNativeTethering()) {
            isRunning = true;
            Log.i(TAG, "Hotspot ativo via tethering nativo");
            return true;
        }

        // Método 2: Configuração manual com hostapd + dnsmasq + iptables
        Log.i(TAG, "Tethering nativo falhou, tentando método manual...");
        if (tryManualHotspot()) {
            isRunning = true;
            Log.i(TAG, "Hotspot ativo via método manual");
            return true;
        }

        Log.e(TAG, "Falha ao iniciar hotspot");
        return false;
    }

    /**
     * Desliga o hotspot.
     */
    public synchronized boolean stop() {
        if (!isRunning) return true;

        Log.i(TAG, "Desligando hotspot...");

        // Desliga tethering nativo
        RootExecutor.exec("cmd connectivity tethering wifi disable");
        RootExecutor.exec("service call connectivity 45 s16 null"); // stopTethering

        // Desliga manual
        RootExecutor.exec("pkill hostapd 2>/dev/null");
        RootExecutor.exec("pkill dnsmasq 2>/dev/null");

        // Limpa iptables
        RootExecutor.exec("iptables -t nat -D POSTROUTING -j MASQUERADE 2>/dev/null");
        RootExecutor.exec("iptables -D FORWARD -j ACCEPT 2>/dev/null");
        RootExecutor.exec("echo 0 > /proc/sys/net/ipv4/ip_forward");

        isRunning = false;
        Log.i(TAG, "Hotspot desligado");
        return true;
    }

    /**
     * MÉTODO 1: Usa o tethering nativo do Android via root.
     * O Wear OS tem a funcionalidade escondida — só precisamos ativar via cmd.
     */
    private boolean tryNativeTethering() {
        // Remove restrições de tethering do operador
        RootExecutor.exec("settings put global tether_dun_required 0");
        RootExecutor.exec("settings put global softap_enabled 1");

        // Habilita tethering WiFi
        RootExecutor.Result r = RootExecutor.exec("cmd connectivity tethering wifi enable");
        if (r.success) {
            // Aguarda o AP subir
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}

            // Verifica se o AP subiu
            RootExecutor.Result check = RootExecutor.exec("ip addr show wlan0 | grep 'inet '");
            if (check.success && !check.output.isEmpty()) {
                Log.i(TAG, "AP nativo ativo: " + check.output);

                // Garante IP forwarding e NAT para o 5G
                setupRouting();
                return true;
            }
        }

        // Tenta via service call (método alternativo)
        r = RootExecutor.exec("service call connectivity 33 i32 0"); // startTethering(TETHERING_WIFI, 0)
        if (r.success) {
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            RootExecutor.Result check = RootExecutor.exec("ip addr show wlan0 | grep 'inet '");
            if (check.success && !check.output.isEmpty()) {
                setupRouting();
                return true;
            }
        }

        return false;
    }

    /**
     * MÉTODO 2: Configura o hotspot manualmente.
     * Cria o AP com hostapd, DHCP com dnsmasq, e roteia com iptables.
     */
    private boolean tryManualHotspot() {
        String cellularIface = detectCellularInterface();
        Log.i(TAG, "Interface celular detectada: " + cellularIface);

        if (cellularIface == null) {
            Log.e(TAG, "Nenhuma interface celular encontrada");
            return false;
        }

        // Cria config do hostapd
        String hostapdConf = "interface=" + AP_INTERFACE + "\n"
            + "driver=nl80211\n"
            + "ssid=" + AP_SSID + "\n"
            + "hw_mode=g\n"
            + "channel=6\n"
            + "wmm_enabled=0\n"
            + "auth_algs=1\n"
            + "wpa=2\n"
            + "wpa_passphrase=" + AP_PASS + "\n"
            + "wpa_key_mgmt=WPA-PSK\n"
            + "rsn_pairwise=CCMP\n";

        String dnsmasqConf = "interface=" + AP_INTERFACE + "\n"
            + "dhcp-range=" + DHCP_RANGE + "\n"
            + "dhcp-option=3," + AP_IP + "\n"   // gateway
            + "dhcp-option=6,8.8.8.8,8.8.4.4\n"; // DNS

        // Sequência de comandos para criar o AP
        String[] commands = {
            // Limpa processos anteriores
            "pkill hostapd 2>/dev/null",
            "pkill dnsmasq 2>/dev/null",

            // Desliga wlan0 momentaneamente
            "ip link set " + AP_INTERFACE + " down",

            // Escreve configs
            "echo '" + hostapdConf + "' > /data/local/tmp/hostapd.conf",
            "echo '" + dnsmasqConf + "' > /data/local/tmp/dnsmasq.conf",

            // Configura IP no AP
            "ip addr add " + AP_IP + "/24 dev " + AP_INTERFACE,
            "ip link set " + AP_INTERFACE + " up",

            // Inicia hostapd (em background)
            "hostapd -B /data/local/tmp/hostapd.conf 2>&1",

            // Inicia dnsmasq (em background)
            "dnsmasq -C /data/local/tmp/dnsmasq.conf 2>&1",

            // Liga IP forwarding
            "echo 1 > /proc/sys/net/ipv4/ip_forward",

            // Limpa regras iptables
            "iptables -F",
            "iptables -t nat -F",

            // NAT do 5G para o WiFi
            "iptables -t nat -A POSTROUTING -o " + cellularIface + " -j MASQUERADE",
            "iptables -A FORWARD -i " + AP_INTERFACE + " -o " + cellularIface + " -j ACCEPT",
            "iptables -A FORWARD -i " + cellularIface + " -o " + AP_INTERFACE + " -m state --state RELATED,ESTABLISHED -j ACCEPT",
        };

        RootExecutor.Result r = RootExecutor.execBatch(commands);
        if (r.success) {
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            RootExecutor.Result check = RootExecutor.exec("ip addr show " + AP_INTERFACE + " | grep 'inet '");
            if (check.success && check.output.contains(AP_IP)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Configura roteamento e NAT após o tethering nativo.
     */
    private void setupRouting() {
        String cellularIface = detectCellularInterface();
        if (cellularIface == null) {
            Log.w(TAG, "Sem interface celular para rotear");
            return;
        }

        String[] cmds = {
            "echo 1 > /proc/sys/net/ipv4/ip_forward",
            "iptables -C -t nat -A POSTROUTING -o " + cellularIface + " -j MASQUERADE 2>/dev/null || iptables -t nat -A POSTROUTING -o " + cellularIface + " -j MASQUERADE",
            "iptables -C FORWARD -j ACCEPT 2>/dev/null || iptables -A FORWARD -j ACCEPT",
        };
        RootExecutor.execBatch(cmds);
        Log.i(TAG, "Roteamento configurado para " + cellularIface);
    }

    /**
     * Detecta qual interface celular está ativa.
     */
    private String detectCellularInterface() {
        for (String iface : CELLULAR_IFACES) {
            RootExecutor.Result r = RootExecutor.exec("ip addr show " + iface + " 2>/dev/null | grep 'inet '");
            if (r.success && !r.output.isEmpty()) {
                return iface;
            }
        }

        // Fallback: lista todas as interfaces e procura uma com IP que não seja wlan0/lo
        RootExecutor.Result r = RootExecutor.exec("ip -o addr show | grep -v 'wlan0\\|lo\\|dummy' | grep 'inet '");
        if (r.success && !r.output.isEmpty()) {
            String[] parts = r.output.split("\\s+");
            if (parts.length > 1) return parts[1];
        }

        return null;
    }

    /**
     * Retorna informações de status para a UI.
     */
    public String getStatus() {
        StringBuilder sb = new StringBuilder();

        // Verifica IP do AP
        RootExecutor.Result ap = RootExecutor.exec("ip addr show " + AP_INTERFACE + " 2>/dev/null | grep 'inet '");
        sb.append("AP: ").append(ap.success && !ap.output.isEmpty() ? "ATIVO" : "OFF").append("\n");

        // Verifica IP forwarding
        RootExecutor.Result fwd = RootExecutor.exec("cat /proc/sys/net/ipv4/ip_forward");
        sb.append("Forward: ").append(fwd.success && fwd.output.trim().equals("1") ? "ON" : "OFF").append("\n");

        // Interface celular
        String cell = detectCellularInterface();
        sb.append("5G: ").append(cell != null ? cell : "---").append("\n");

        // Clientes conectados
        RootExecutor.Result clients = RootExecutor.exec("cat /proc/net/arp 2>/dev/null | grep -v '00:00:00:00:00:00' | grep -v IP | wc -l");
        sb.append("Clientes: ").append(clients.success ? clients.output.trim() : "0");

        return sb.toString();
    }

    public String getSSID() { return AP_SSID; }
    public String getPass() { return AP_PASS; }
    public String getIP() { return AP_IP; }
}
