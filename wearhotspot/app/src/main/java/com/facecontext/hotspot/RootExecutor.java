package com.facecontext.hotspot;

import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;

/**
 * Executa comandos como root via su.
 * Cada comando é executado em uma sessão su separada.
 */
public class RootExecutor {
    private static final String TAG = "WatchHotspot";

    public static class Result {
        public boolean success;
        public String output;
        public String error;

        public Result(boolean success, String output, String error) {
            this.success = success;
            this.output = output != null ? output : "";
            this.error = error != null ? error : "";
        }

        @Override
        public String toString() {
            return success ? "OK: " + output : "FAIL: " + error;
        }
    }

    /**
     * Executa um único comando como root.
     */
    public static Result exec(String command) {
        try {
            ProcessBuilder pb = new ProcessBuilder("su", "-c", command);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
            int exit = p.waitFor();
            String output = sb.toString().trim();
            Log.d(TAG, "$ " + command + " -> " + exit + ": " + output);
            return new Result(exit == 0, output, exit == 0 ? "" : output);
        } catch (Exception e) {
            Log.e(TAG, "exec failed: " + command, e);
            return new Result(false, "", e.getMessage());
        }
    }

    /**
     * Executa múltiplos comandos como root em uma única sessão su.
     * Mais eficiente para sequências de comandos.
     */
    public static Result execBatch(String[] commands) {
        try {
            Process p = Runtime.getRuntime().exec("su");
            OutputStream os = p.getOutputStream();
            for (String cmd : commands) {
                os.write((cmd + "\n").getBytes());
            }
            os.write("exit\n".getBytes());
            os.flush();
            os.close();

            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) out.append(line).append("\n");

            BufferedReader er = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            StringBuilder err = new StringBuilder();
            while ((line = er.readLine()) != null) err.append(line).append("\n");

            int exit = p.waitFor();
            Log.d(TAG, "batch exit=" + exit + " out=" + out.toString().trim() + " err=" + err.toString().trim());
            return new Result(exit == 0, out.toString().trim(), err.toString().trim());
        } catch (Exception e) {
            Log.e(TAG, "execBatch failed", e);
            return new Result(false, "", e.getMessage());
        }
    }

    /**
     * Verifica se o root está disponível.
     */
    public static boolean hasRoot() {
        Result r = exec("id");
        return r.success && r.output.contains("uid=0");
    }
}
