package id.bactiar.anhp;

import org.json.JSONException;
import org.json.JSONObject;

final class MacroEvent {
    int delayMs;
    int startX;
    int startY;
    int endX;
    int endY;
    int durationMs;

    MacroEvent() {
    }

    MacroEvent(int delayMs, int startX, int startY, int endX, int endY, int durationMs) {
        this.delayMs = Math.max(0, delayMs);
        this.startX = startX;
        this.startY = startY;
        this.endX = endX;
        this.endY = endY;
        this.durationMs = Math.max(35, durationMs);
    }

    boolean isSwipe() {
        int dx = endX - startX;
        int dy = endY - startY;
        return (dx * dx + dy * dy) > 256;
    }

    JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("delay_ms", delayMs);
        obj.put("start_x", startX);
        obj.put("start_y", startY);
        obj.put("end_x", endX);
        obj.put("end_y", endY);
        obj.put("duration_ms", durationMs);
        return obj;
    }

    static MacroEvent fromJson(JSONObject obj) {
        MacroEvent event = new MacroEvent();
        event.delayMs = Math.max(0, obj.optInt("delay_ms", 0));
        event.startX = obj.optInt("start_x", 0);
        event.startY = obj.optInt("start_y", 0);
        event.endX = obj.optInt("end_x", event.startX);
        event.endY = obj.optInt("end_y", event.startY);
        event.durationMs = Math.max(35, obj.optInt("duration_ms", 60));
        return event;
    }
}

