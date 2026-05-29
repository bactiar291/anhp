package id.bactiar.anhp;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class MacroStore {
    private static final String FILE_NAME = "macro.json";

    private MacroStore() {
    }

    static final class MacroData {
        final ArrayList<MacroEvent> events = new ArrayList<>();
        int loopDelayMs;

        boolean isEmpty() {
            return events.isEmpty();
        }
    }

    static MacroData loadData(Context context) {
        MacroData data = new MacroData();
        File file = new File(context.getFilesDir(), FILE_NAME);
        if (!file.exists()) return data;
        try {
            String text = readAll(file);
            JSONObject root = new JSONObject(text);
            data.loopDelayMs = Math.max(0, Math.min(120000, root.optInt("loop_delay_ms", 250)));
            JSONArray arr = root.optJSONArray("events");
            if (arr == null) return data;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.optJSONObject(i);
                if (obj != null) data.events.add(MacroEvent.fromJson(obj));
            }
        } catch (Exception e) {
            Logx.e("Macro load failed", e);
        }
        return data;
    }

    static ArrayList<MacroEvent> load(Context context) {
        return loadData(context).events;
    }

    static boolean save(Context context, List<MacroEvent> events) {
        return save(context, events, 250);
    }

    static boolean save(Context context, List<MacroEvent> events, int loopDelayMs) {
        File dir = context.getFilesDir();
        File tmp = new File(dir, FILE_NAME + ".tmp");
        File dst = new File(dir, FILE_NAME);
        try {
            JSONArray arr = new JSONArray();
            for (MacroEvent event : events) arr.put(event.toJson());
            JSONObject root = new JSONObject();
            root.put("version", 2);
            root.put("created_utc", String.valueOf(System.currentTimeMillis()));
            root.put("loop_delay_ms", Math.max(0, Math.min(120000, loopDelayMs)));
            root.put("events", arr);
            OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8);
            try {
                writer.write(root.toString());
            } finally {
                writer.close();
            }
            if (dst.exists() && !dst.delete()) return false;
            return tmp.renameTo(dst);
        } catch (Exception e) {
            Logx.e("Macro save failed", e);
            return false;
        }
    }

    static int count(Context context) {
        return load(context).size();
    }

    private static String readAll(File file) throws Exception {
        StringBuilder sb = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
        try {
            char[] buf = new char[4096];
            int n;
            while ((n = reader.read(buf)) >= 0) sb.append(buf, 0, n);
        } finally {
            reader.close();
        }
        return sb.toString();
    }
}
