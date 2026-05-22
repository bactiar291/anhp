package id.bactiar.anhp;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;

final class GroqClient {
    private static final String BASE_URL = "https://api.groq.com/openai/v1";

    static final class Decision {
        boolean stop;
        double confidence;
        String stopType;
        String visibleText;
        String evidence;
        String reason;
        String model;
    }

    private GroqClient() {
    }

    static Decision checkStopCondition(Context context, SettingsStore settings, String base64Image, int loop) throws Exception {
        String key = SecurePrefs.loadGroqKey(context);
        if (key.length() == 0) throw new IllegalStateException("Groq API key empty. Paste once in AnHP settings.");

        ArrayList<String> models = buildModelRotation(settings, key);
        Exception last = null;
        for (int i = 0; i < models.size(); i++) {
            String model = models.get(i);
            try {
                Decision decision = askVision(key, model, settings.getCondition(), base64Image, loop);
                decision.model = model;
                applyLocalSafetyHeuristics(decision);
                if (decision.stop && decision.confidence < 0.80 && !isTerminalStopType(decision.stopType)) {
                    decision.stop = false;
                    decision.reason = "Low confidence stop blocked: " + decision.reason;
                }
                return decision;
            } catch (Exception e) {
                last = e;
                if (i < models.size() - 1 && shouldRotateModel(e)) {
                    Logx.d("Groq model rotated: " + model + " -> " + models.get(i + 1));
                    continue;
                }
                throw e;
            }
        }
        throw last == null ? new IllegalStateException("Groq check failed") : last;
    }

    private static ArrayList<String> buildModelRotation(SettingsStore settings, String apiKey) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (settings.isAutoFetchModels()) {
            try {
                for (String id : fetchAvailableVisionModels(apiKey)) ids.add(id);
            } catch (Exception e) {
                Logx.e("Groq model list fetch failed", e);
            }
        }
        for (String id : settings.getConfiguredModels()) ids.add(id);
        if (ids.isEmpty()) ids.add("meta-llama/llama-4-maverick-17b-128e-instruct");
        return new ArrayList<>(ids);
    }

    private static ArrayList<String> fetchAvailableVisionModels(String apiKey) throws Exception {
        String response = request("GET", BASE_URL + "/models", apiKey, null);
        JSONObject root = new JSONObject(response);
        JSONArray data = root.optJSONArray("data");
        ArrayList<String> models = new ArrayList<>();
        if (data == null) return models;
        for (int i = 0; i < data.length(); i++) {
            JSONObject item = data.optJSONObject(i);
            if (item == null) continue;
            String id = item.optString("id", "");
            if (isVisionCandidate(id)) models.add(id);
        }
        return models;
    }

    private static boolean isVisionCandidate(String id) {
        String value = id == null ? "" : id.toLowerCase();
        return value.contains("vision")
                || value.contains("qwen3-vl")
                || value.contains("/vl")
                || value.contains("-vl")
                || value.contains("llama-4")
                || value.contains("maverick")
                || value.contains("scout");
    }

    private static Decision askVision(String apiKey, String model, String condition, String base64Image, int loop) throws Exception {
        String system =
                "You are AnHP's conservative Android screen-state verifier. Return JSON only. " +
                "Stop an automation loop only when the screen clearly reached success, OTP/code waiting, or terminal failure.";

        String prompt =
                "User stop condition: \"" + condition + "\"\n" +
                "Loop number: " + loop + "\n\n" +
                "Reasoning rules:\n" +
                "1. First OCR/read all visible text and status words on the Android screenshot.\n" +
                "2. Indonesian phrase 'sudah tidak error X' or 'tidak error X' means continue only while exact error X is visible. Stop when X is absent and the screen shows OTP, kode akses, verification code, enter code, waiting for code, code sent, success, verified, dashboard, logged in, or similar.\n" +
                "3. If exact error 'Unable to send a verification code' is still visible, return stop=false.\n" +
                "4. If the screen shows OTP/code input, waiting OTP, access code, verification code sent, or accepted number, return stop=true with stop_type='success'.\n" +
                "5. If the screen shows Banned, ban, blocked, suspended, disabled, restricted, too many attempts, or abuse protection, return stop=true with stop_type='terminal_error'.\n" +
                "6. If loading, blank, hidden, or ambiguous, return stop=false with stop_type='ambiguous'.\n" +
                "7. Do not guess. Quote visible evidence exactly when possible.\n\n" +
                "Return JSON object only with keys: stop boolean, confidence number 0..1, stop_type string ('success','terminal_error','continue','ambiguous'), visible_text string, evidence string, reason string.";

        JSONObject systemMessage = new JSONObject()
                .put("role", "system")
                .put("content", system);

        JSONArray content = new JSONArray();
        content.put(new JSONObject().put("type", "text").put("text", prompt));
        content.put(new JSONObject()
                .put("type", "image_url")
                .put("image_url", new JSONObject().put("url", "data:image/jpeg;base64," + base64Image)));

        JSONObject userMessage = new JSONObject()
                .put("role", "user")
                .put("content", content);

        JSONObject body = new JSONObject()
                .put("model", model)
                .put("messages", new JSONArray().put(systemMessage).put(userMessage))
                .put("temperature", 0)
                .put("max_completion_tokens", 420)
                .put("response_format", new JSONObject().put("type", "json_object"));

        String response = request("POST", BASE_URL + "/chat/completions", apiKey, body.toString());
        JSONObject root = new JSONObject(response);
        JSONArray choices = root.optJSONArray("choices");
        if (choices == null || choices.length() == 0) throw new IllegalStateException("Groq response missing choices");
        JSONObject message = choices.getJSONObject(0).optJSONObject("message");
        if (message == null) throw new IllegalStateException("Groq response missing message");
        String contentText = message.optString("content", "");
        JSONObject decisionJson = new JSONObject(extractJsonObject(contentText));
        Decision decision = new Decision();
        decision.stop = decisionJson.optBoolean("stop", false);
        decision.confidence = decisionJson.optDouble("confidence", 0);
        decision.stopType = decisionJson.optString("stop_type", "continue");
        decision.visibleText = decisionJson.optString("visible_text", "");
        decision.evidence = decisionJson.optString("evidence", "");
        decision.reason = decisionJson.optString("reason", "");
        return decision;
    }

    private static String request(String method, String url, String apiKey, String body) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(60000);
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        if (body != null) {
            conn.setDoOutput(true);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(bytes.length);
            OutputStream out = conn.getOutputStream();
            try {
                out.write(bytes);
            } finally {
                out.close();
            }
        }
        int code = conn.getResponseCode();
        String text = readAll(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream());
        conn.disconnect();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("Groq API error " + code + " " + truncate(text, 700));
        }
        return text;
    }

    private static String readAll(InputStream stream) throws Exception {
        if (stream == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        try {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int n;
            while ((n = reader.read(buf)) >= 0) sb.append(buf, 0, n);
            return sb.toString();
        } finally {
            reader.close();
        }
    }

    private static void applyLocalSafetyHeuristics(Decision decision) {
        String combined = (decision.visibleText + " " + decision.evidence + " " + decision.reason).toLowerCase();
        if (containsAny(combined, new String[]{"banned", " ban ", "blocked", "suspended", "disabled", "restricted", "too many attempts", "abuse"})) {
            decision.stop = true;
            decision.confidence = Math.max(decision.confidence, 0.95);
            decision.stopType = "terminal_error";
            if (decision.reason.length() == 0) decision.reason = "Terminal failure state detected locally.";
            return;
        }
        if (containsAny(combined, new String[]{"otp", "verification code", "access code", "kode akses", "kode otp", "enter code", "waiting for code", "code sent", "sms code"})) {
            decision.stop = true;
            decision.confidence = Math.max(decision.confidence, 0.88);
            decision.stopType = "success";
            if (decision.reason.length() == 0) decision.reason = "OTP/code waiting state detected locally.";
        }
    }

    private static boolean shouldRotateModel(Exception e) {
        String msg = e == null || e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        if (msg.contains("401") || msg.contains("invalid api key") || msg.contains("unauthorized")) return false;
        return containsAny(msg, new String[]{
                "400", "403", "404", "429", "500", "503",
                "rate limit", "rate_limit", "too many requests", "quota", "limit exceeded",
                "model", "unsupported", "does not support"
        });
    }

    private static boolean isTerminalStopType(String type) {
        return type != null && type.equalsIgnoreCase("terminal_error");
    }

    private static boolean containsAny(String text, String[] needles) {
        for (String needle : needles) {
            if (text.contains(needle)) return true;
        }
        return false;
    }

    private static String extractJsonObject(String text) {
        String value = text == null ? "{}" : text.trim();
        if (value.startsWith("```")) {
            int firstLine = value.indexOf('\n');
            if (firstLine >= 0) value = value.substring(firstLine + 1);
            int fence = value.lastIndexOf("```");
            if (fence >= 0) value = value.substring(0, fence);
            value = value.trim();
        }
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start >= 0 && end > start) return value.substring(start, end + 1);
        return value;
    }

    private static String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }
}
