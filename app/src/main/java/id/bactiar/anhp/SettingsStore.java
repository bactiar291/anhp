package id.bactiar.anhp;

import android.content.Context;
import android.content.SharedPreferences;

final class SettingsStore {
    private static final String PREFS = "settings";
    private final SharedPreferences prefs;

    SettingsStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    boolean isLoopPlay() {
        return prefs.getBoolean("loop_play", false);
    }

    void setLoopPlay(boolean value) {
        prefs.edit().putBoolean("loop_play", value).apply();
    }

    boolean isSaveRecording() {
        return prefs.getBoolean("save_recording", true);
    }

    void setSaveRecording(boolean value) {
        prefs.edit().putBoolean("save_recording", value).apply();
    }

    boolean isAutoLoadSaved() {
        return prefs.getBoolean("auto_load_saved", true);
    }

    void setAutoLoadSaved(boolean value) {
        prefs.edit().putBoolean("auto_load_saved", value).apply();
    }

    boolean isDarkTheme() {
        return prefs.getBoolean("dark_theme", false);
    }

    void setDarkTheme(boolean value) {
        prefs.edit().putBoolean("dark_theme", value).apply();
    }

    int getMaxLoops() {
        return clamp(prefs.getInt("max_loops", 20), 0, 1000);
    }

    void setMaxLoops(int value) {
        prefs.edit().putInt("max_loops", clamp(value, 0, 1000)).apply();
    }

    float getSpeed() {
        return clampFloat(prefs.getFloat("speed", 1.0f), 0.2f, 5.0f);
    }

    void setSpeed(float value) {
        prefs.edit().putFloat("speed", clampFloat(value, 0.2f, 5.0f)).apply();
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    static float clampFloat(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
