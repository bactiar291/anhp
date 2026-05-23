package id.bactiar.anhp;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

public class AnHpAccessibilityService extends AccessibilityService {
    interface GestureDone {
        void onDone(boolean ok);
    }

    private static volatile AnHpAccessibilityService instance;
    private long lastKeyMs;

    static boolean isReady() {
        return instance != null;
    }

    static boolean dispatchMacroGesture(final MacroEvent event, final GestureDone done) {
        final AnHpAccessibilityService service = instance;
        if (service == null) {
            if (done != null) done.onDone(false);
            return false;
        }
        try {
            Path path = new Path();
            path.moveTo(event.startX, event.startY);
            if (event.isSwipe()) path.lineTo(event.endX, event.endY);
            int duration = Math.max(event.isSwipe() ? 80 : 45, Math.min(event.durationMs, 30000));
            GestureDescription.StrokeDescription stroke =
                    new GestureDescription.StrokeDescription(path, 0, duration);
            GestureDescription gesture = new GestureDescription.Builder().addStroke(stroke).build();
            return service.dispatchGesture(gesture, new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    if (done != null) done.onDone(true);
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    if (done != null) done.onDone(false);
                }
            }, null);
        } catch (Exception e) {
            Logx.e("dispatchGesture failed", e);
            if (done != null) done.onDone(false);
            return false;
        }
    }

    @Override
    protected void onServiceConnected() {
        instance = this;
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            setServiceInfo(info);
        }
        MacroOverlayService.startAction(this, MacroOverlayService.ACTION_SHOW_CONTROLS);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
        int code = event.getKeyCode();
        if (code != KeyEvent.KEYCODE_A && code != KeyEvent.KEYCODE_P && code != KeyEvent.KEYCODE_L) return false;
        long now = SystemClock.uptimeMillis();
        if (now - lastKeyMs < 350) return true;
        lastKeyMs = now;
        MacroOverlayService.startAction(
                this,
                code == KeyEvent.KEYCODE_A
                        ? MacroOverlayService.ACTION_TOGGLE_RECORD
                        : code == KeyEvent.KEYCODE_P
                        ? MacroOverlayService.ACTION_TOGGLE_PLAY
                        : MacroOverlayService.ACTION_TOGGLE_LOOP);
        return true;
    }

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }
}
