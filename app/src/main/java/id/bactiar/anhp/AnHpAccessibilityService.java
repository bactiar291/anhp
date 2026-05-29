package id.bactiar.anhp;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Button;
import android.widget.LinearLayout;

import java.util.List;
import java.util.Locale;

public class AnHpAccessibilityService extends AccessibilityService {
    interface GestureDone {
        void onDone(boolean ok);
    }

    private static volatile AnHpAccessibilityService instance;
    private final Handler main = new Handler();
    private WindowManager windowManager;
    private View controlsView;
    private WindowManager.LayoutParams controlsParams;
    private int dragStartX;
    private int dragStartY;
    private float dragTouchX;
    private float dragTouchY;
    private long lastKeyMs;
    private boolean destroyed;
    private final Runnable controlsWatchdog = new Runnable() {
        @Override
        public void run() {
            if (destroyed) return;
            showAccessControls();
            main.postDelayed(this, 1000);
        }
    };

    static boolean isReady() {
        return instance != null;
    }

    static String getVisibleTextLower() {
        final AnHpAccessibilityService service = instance;
        if (service == null) return "";
        StringBuilder out = new StringBuilder();
        try {
            appendNodeText(service.getRootInActiveWindow(), out);
            List<AccessibilityWindowInfo> windows = service.getWindows();
            if (windows != null) {
                for (AccessibilityWindowInfo window : windows) {
                    if (window != null) appendNodeText(window.getRoot(), out);
                }
            }
        } catch (Exception e) {
            Logx.e("screen text scan failed", e);
        }
        return out.toString().toLowerCase(Locale.US);
    }

    private static void appendNodeText(AccessibilityNodeInfo node, StringBuilder out) {
        if (node == null) return;
        try {
            appendText(node.getText(), out);
            appendText(node.getContentDescription(), out);
            for (int i = 0; i < node.getChildCount(); i++) {
                appendNodeText(node.getChild(i), out);
            }
        } finally {
            node.recycle();
        }
    }

    private static void appendText(CharSequence text, StringBuilder out) {
        if (text == null || text.length() == 0) return;
        out.append(' ').append(text);
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
        destroyed = false;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            setServiceInfo(info);
        }
        showAccessControls();
        main.removeCallbacks(controlsWatchdog);
        main.postDelayed(controlsWatchdog, 1000);
        MacroOverlayService.startAction(this, MacroOverlayService.ACTION_SHOW_CONTROLS);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        showAccessControls();
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

    private void showAccessControls() {
        if (windowManager == null) return;
        if (controlsView != null) {
            ensureAccessControlsAttached();
            return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(dp(8), dp(7), dp(8), dp(7));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xEE111827);
        bg.setCornerRadius(dp(9));
        bg.setStroke(dp(2), 0xFF22C55E);
        root.setBackground(bg);

        Button record = makeAccessButton("A", 0xFF16A34A);
        Button play = makeAccessButton("P", 0xFF2563EB);
        Button loop = makeAccessButton("L", 0xFF64748B);
        root.addView(record);
        root.addView(play);
        root.addView(loop);

        record.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MacroOverlayService.startAction(AnHpAccessibilityService.this, MacroOverlayService.ACTION_TOGGLE_RECORD);
            }
        });
        play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MacroOverlayService.startAction(AnHpAccessibilityService.this, MacroOverlayService.ACTION_TOGGLE_PLAY);
            }
        });
        loop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MacroOverlayService.startAction(AnHpAccessibilityService.this, MacroOverlayService.ACTION_TOGGLE_LOOP);
            }
        });
        root.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return handleAccessDrag(event);
            }
        });

        controlsParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        controlsParams.gravity = Gravity.TOP | Gravity.RIGHT;
        controlsParams.x = dp(12);
        controlsParams.y = dp(38);
        controlsView = root;
        if (!ensureAccessControlsAttached()) {
            controlsView = null;
            controlsParams = null;
        }
    }

    private Button makeAccessButton(String text, int color) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(16);
        button.setAllCaps(false);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(7));
        button.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(50), dp(40));
        lp.leftMargin = dp(5);
        lp.rightMargin = dp(1);
        button.setLayoutParams(lp);
        return button;
    }

    private boolean handleAccessDrag(MotionEvent event) {
        if (controlsParams == null || controlsView == null) return false;
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            dragStartX = controlsParams.x;
            dragStartY = controlsParams.y;
            dragTouchX = event.getRawX();
            dragTouchY = event.getRawY();
            return false;
        }
        if (event.getAction() == MotionEvent.ACTION_MOVE) {
            controlsParams.x = Math.max(0, dragStartX - Math.round(event.getRawX() - dragTouchX));
            controlsParams.y = Math.max(0, dragStartY + Math.round(event.getRawY() - dragTouchY));
            try {
                windowManager.updateViewLayout(controlsView, controlsParams);
            } catch (Exception e) {
                Logx.e("access controls drag failed", e);
            }
            return true;
        }
        return false;
    }

    private boolean ensureAccessControlsAttached() {
        if (controlsView == null || controlsParams == null) return false;
        if (controlsView.getParent() != null) return true;
        try {
            windowManager.addView(controlsView, controlsParams);
            return true;
        } catch (Exception e) {
            Logx.e("access controls attach failed", e);
            return false;
        }
    }

    private void removeAccessControls() {
        if (controlsView != null) {
            try {
                windowManager.removeView(controlsView);
            } catch (Exception ignored) {
            }
            controlsView = null;
            controlsParams = null;
        }
    }

    @Override
    public void onDestroy() {
        destroyed = true;
        main.removeCallbacks(controlsWatchdog);
        removeAccessControls();
        if (instance == this) instance = null;
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
