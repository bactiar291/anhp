package id.bactiar.anhp;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.LinkedHashSet;

final class SettingsStore {
    static final String DEFAULT_CONDITION =
            "berhenti kalau sudah tidak error Unable to send a verification code; " +
            "kalau nomor berhasil masuk dan layar menunggu OTP/kode akses maka berhenti; " +
            "kalau muncul Banned/Blocked/Suspended juga berhenti supaya loop tidak lanjut";

    static final String DEFAULT_MODELS =
            "meta-llama/llama-4-maverick-17b-128e-instruct, " +
            "meta-llama/llama-4-scout-17b-16e-instruct, " +
            "qwen/qwen3-vl-32b-instruct";

    private static final String PREFS = "settings";
    private final SharedPreferences prefs;

    SettingsStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    String getCondition() {
        return prefs.getString("condition", DEFAULT_CONDITION);
    }

    void setCondition(String value) {
        prefs.edit().putString("condition", emptyToDefault(value, DEFAULT_CONDITION)).apply();
    }

    String getModelsText() {
        return prefs.getString("models", DEFAULT_MODELS);
    }

    void setModelsText(String value) {
        prefs.edit().putString("models", emptyToDefault(value, DEFAULT_MODELS)).apply();
    }

    boolean isAiMode() {
        return prefs.getBoolean("ai_mode", true);
    }

    void setAiMode(boolean value) {
        prefs.edit().putBoolean("ai_mode", value).apply();
    }

    boolean isLoopPlay() {
        return prefs.getBoolean("loop_play", false);
    }

    void setLoopPlay(boolean value) {
        prefs.edit().putBoolean("loop_play", value).apply();
    }

    boolean isConfirmStopTwice() {
        return prefs.getBoolean("confirm_stop_twice", true);
    }

    void setConfirmStopTwice(boolean value) {
        prefs.edit().putBoolean("confirm_stop_twice", value).apply();
    }

    boolean isAutoFetchModels() {
        return prefs.getBoolean("auto_fetch_models", true);
    }

    void setAutoFetchModels(boolean value) {
        prefs.edit().putBoolean("auto_fetch_models", value).apply();
    }

    int getMaxLoops() {
        return clamp(prefs.getInt("max_loops", 20), 1, 1000);
    }

    void setMaxLoops(int value) {
        prefs.edit().putInt("max_loops", clamp(value, 1, 1000)).apply();
    }

    int getCheckDelaySeconds() {
        return clamp(prefs.getInt("check_delay_seconds", 2), 1, 120);
    }

    void setCheckDelaySeconds(int value) {
        prefs.edit().putInt("check_delay_seconds", clamp(value, 1, 120)).apply();
    }

    float getSpeed() {
        return clampFloat(prefs.getFloat("speed", 1.0f), 0.2f, 5.0f);
    }

    void setSpeed(float value) {
        prefs.edit().putFloat("speed", clampFloat(value, 0.2f, 5.0f)).apply();
    }

    ArrayList<String> getConfiguredModels() {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        String value = getModelsText();
        String[] raw = value.split("[,;\\n\\r]+");
        for (String item : raw) {
            String model = item.trim();
            if (model.length() > 0) set.add(model);
        }
        if (set.isEmpty()) set.add("meta-llama/llama-4-maverick-17b-128e-instruct");
        return new ArrayList<>(set);
    }

    private static String emptyToDefault(String value, String fallback) {
        if (value == null || value.trim().length() == 0) return fallback;
        return value.trim();
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    static float clampFloat(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}

