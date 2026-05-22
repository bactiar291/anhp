package id.bactiar.anhp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class MacroOverlayService extends Service {
    static final String ACTION_SHOW_CONTROLS = "id.bactiar.anhp.SHOW_CONTROLS";
    static final String ACTION_TOGGLE_RECORD = "id.bactiar.anhp.TOGGLE_RECORD";
    static final String ACTION_TOGGLE_PLAY = "id.bactiar.anhp.TOGGLE_PLAY";
    static final String ACTION_START_CAPTURE = "id.bactiar.anhp.START_CAPTURE";
    static final String EXTRA_RESULT_CODE = "result_code";
    static final String EXTRA_CAPTURE_DATA = "capture_data";

    private static final String CHANNEL_ID = "anhp";
    private static final int NOTIFICATION_ID = 291;

    private final Handler main = new Handler();
    private HandlerThread workerThread;
    private Handler worker;
    private WindowManager windowManager;
    private ScreenCapture screenCapture;
    private View controlsView;
    private WindowManager.LayoutParams controlsParams;
    private View recordLayer;
    private LinearLayout controlsRoot;
    private TextView statusView;
    private TextView hintView;
    private Button recordButton;
    private Button playButton;
    private final ArrayList<MacroEvent> recordingEvents = new ArrayList<>();
    private boolean recording;
    private volatile boolean playing;
    private volatile boolean cancelRequested;
    private long lastRecordedAt;
    private float downX;
    private float downY;
    private long downTime;
    private int dragStartX;
    private int dragStartY;
    private float dragTouchX;
    private float dragTouchY;

    static void startAction(Context context, String action) {
        Intent intent = new Intent(context, MacroOverlayService.class);
        intent.setAction(action);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        screenCapture = new ScreenCapture(this);
        workerThread = new HandlerThread("anhp-worker");
        workerThread.start();
        worker = new Handler(workerThread.getLooper());
        startForegroundReady("Ready");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            showControls();
            return START_STICKY;
        }
        String action = intent.getAction();
        if (ACTION_SHOW_CONTROLS.equals(action)) {
            showControls();
        } else if (ACTION_TOGGLE_RECORD.equals(action)) {
            toggleRecording();
        } else if (ACTION_TOGGLE_PLAY.equals(action)) {
            togglePlayback();
        } else if (ACTION_START_CAPTURE.equals(action)) {
            int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
            Intent data = intent.getParcelableExtra(EXTRA_CAPTURE_DATA);
            startScreenCapture(resultCode, data);
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        cancelRequested = true;
        removeRecordLayer();
        removeControls();
        if (screenCapture != null) screenCapture.stop();
        if (workerThread != null) workerThread.quitSafely();
        super.onDestroy();
    }

    private void showControls() {
        if (!canDrawOverlay()) {
            setStatus("Overlay permission needed");
            toast("Allow overlay first");
            return;
        }
        if (controlsView != null) {
            updateButtons();
            return;
        }

        LinearLayout root = new LinearLayout(this);
        controlsRoot = root;
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(dp(10), dp(8), dp(10), dp(8));
        setPanelColor(0xEE14532D, 0xFF22C55E);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);

        statusView = new TextView(this);
        statusView.setTextColor(Color.WHITE);
        statusView.setTextSize(14);
        statusView.setText("READY");
        statusView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        statusView.setGravity(Gravity.CENTER_VERTICAL);
        copy.addView(statusView, new LinearLayout.LayoutParams(dp(154), dp(22)));

        hintView = new TextView(this);
        hintView.setTextColor(0xFFE5E7EB);
        hintView.setTextSize(11);
        hintView.setText("A rekam | P play");
        hintView.setGravity(Gravity.CENTER_VERTICAL);
        copy.addView(hintView, new LinearLayout.LayoutParams(dp(154), dp(20)));
        root.addView(copy, new LinearLayout.LayoutParams(dp(160), dp(48)));

        recordButton = makeButton("A", 0xFF34A853);
        playButton = makeButton("P", 0xFF0B57D0);
        root.addView(recordButton);
        root.addView(playButton);

        recordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleRecording();
            }
        });
        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePlayback();
            }
        });
        copy.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return handleDrag(event);
            }
        });

        controlsParams = overlayParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT);
        controlsParams.gravity = Gravity.TOP | Gravity.RIGHT;
        controlsParams.x = dp(12);
        controlsParams.y = dp(92);
        controlsView = root;
        try {
            windowManager.addView(controlsView, controlsParams);
        } catch (Exception e) {
            Logx.e("show controls failed", e);
            controlsView = null;
        }
        updateButtons();
    }

    private Button makeButton(String text, int color) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(18);
        button.setAllCaps(false);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(7));
        button.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(48), dp(42));
        lp.leftMargin = dp(6);
        button.setLayoutParams(lp);
        return button;
    }

    private boolean handleDrag(MotionEvent event) {
        if (controlsParams == null || controlsView == null) return false;
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            dragStartX = controlsParams.x;
            dragStartY = controlsParams.y;
            dragTouchX = event.getRawX();
            dragTouchY = event.getRawY();
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_MOVE) {
            controlsParams.x = Math.max(0, dragStartX - Math.round(event.getRawX() - dragTouchX));
            controlsParams.y = Math.max(0, dragStartY + Math.round(event.getRawY() - dragTouchY));
            windowManager.updateViewLayout(controlsView, controlsParams);
            return true;
        }
        return true;
    }

    private void toggleRecording() {
        if (recording) {
            stopRecording();
            return;
        }
        if (playing) {
            cancelRequested = true;
            setStatus("Stopping play");
            return;
        }
        if (!AnHpAccessibilityService.isReady()) {
            setStatus("Enable Accessibility");
            toast("Enable AnHP Control in Accessibility");
            return;
        }
        if (!canDrawOverlay()) {
            setStatus("Overlay permission needed");
            toast("Allow overlay first");
            return;
        }
        showControls();
        recordingEvents.clear();
        lastRecordedAt = 0;
        recording = true;
        addRecordLayer();
        setStatus("REC 0");
        updateButtons();
    }

    private void stopRecording() {
        recording = false;
        removeRecordLayer();
        boolean saved = MacroStore.save(this, recordingEvents);
        setStatus(saved ? "Saved " + recordingEvents.size() : "Save failed");
        updateButtons();
    }

    private void addRecordLayer() {
        removeRecordLayer();
        View oldControls = controlsView;
        WindowManager.LayoutParams oldParams = controlsParams;
        if (oldControls != null) {
            try {
                windowManager.removeView(oldControls);
            } catch (Exception ignored) {
            }
        }
        recordLayer = new View(this);
        recordLayer.setBackgroundColor(0x110F172A);
        recordLayer.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return handleRecordTouch(event);
            }
        });
        WindowManager.LayoutParams params = overlayParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.format = PixelFormat.TRANSLUCENT;
        try {
            windowManager.addView(recordLayer, params);
            if (oldControls != null) windowManager.addView(oldControls, oldParams);
        } catch (Exception e) {
            Logx.e("record layer failed", e);
            recordLayer = null;
            recording = false;
            if (oldControls != null && oldControls.getParent() == null) {
                try {
                    windowManager.addView(oldControls, oldParams);
                } catch (Exception ignored) {
                }
            }
            setStatus("Record layer failed");
        }
    }

    private boolean handleRecordTouch(MotionEvent event) {
        if (!recording) return false;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            downX = event.getRawX();
            downY = event.getRawY();
            downTime = SystemClock.uptimeMillis();
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            long now = SystemClock.uptimeMillis();
            int delay = lastRecordedAt == 0 ? 120 : (int) Math.min(120000, Math.max(0, now - lastRecordedAt));
            int duration = (int) Math.min(30000, Math.max(45, now - downTime));
            MacroEvent macroEvent = new MacroEvent(
                    delay,
                    Math.round(downX),
                    Math.round(downY),
                    Math.round(event.getRawX()),
                    Math.round(event.getRawY()),
                    duration);
            recordingEvents.add(macroEvent);
            lastRecordedAt = now;
            setStatus("REC " + recordingEvents.size());
            forwardLiveGesture(macroEvent);
            return true;
        }
        return true;
    }

    private void forwardLiveGesture(MacroEvent macroEvent) {
        if (!AnHpAccessibilityService.dispatchMacroGesture(macroEvent, new AnHpAccessibilityService.GestureDone() {
            @Override
            public void onDone(boolean ok) {
                if (!ok) setStatus("Gesture blocked");
            }
        })) {
            setStatus("Gesture not accepted");
        }
    }

    private void togglePlayback() {
        if (recording) return;
        if (playing) {
            cancelRequested = true;
            setStatus("Stopping");
            updateButtons();
            return;
        }
        final ArrayList<MacroEvent> macro = MacroStore.load(this);
        if (macro.isEmpty()) {
            setStatus("No macro");
            toast("Record first with A");
            return;
        }
        if (!AnHpAccessibilityService.isReady()) {
            setStatus("Enable Accessibility");
            toast("Enable AnHP Control in Accessibility");
            return;
        }
        playing = true;
        cancelRequested = false;
        setStatus("PLAY");
        updateButtons();
        worker.post(new Runnable() {
            @Override
            public void run() {
                try {
                    SettingsStore settings = new SettingsStore(MacroOverlayService.this);
                    if (settings.isAiMode()) runAiLoop(macro, settings);
                    else runPlainPlayback(macro, settings);
                } catch (Exception e) {
                    Logx.e("playback failed", e);
                    setStatus("Error: " + shortMsg(e));
                } finally {
                    playing = false;
                    cancelRequested = false;
                    if (!recording) setStatus("Ready");
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            updateButtons();
                        }
                    });
                }
            }
        });
    }

    private void runPlainPlayback(ArrayList<MacroEvent> macro, SettingsStore settings) throws Exception {
        int loops = settings.isLoopPlay() ? settings.getMaxLoops() : 1;
        for (int i = 1; i <= loops && !cancelRequested; i++) {
            setStatus("PLAY " + i + "/" + loops);
            if (!playMacroOnce(macro, settings.getSpeed())) return;
        }
        if (!cancelRequested) setStatus("Done");
    }

    private void runAiLoop(ArrayList<MacroEvent> macro, SettingsStore settings) throws Exception {
        int consecutiveStop = 0;
        int maxLoops = settings.getMaxLoops();
        boolean requireTwo = settings.isConfirmStopTwice();
        for (int loop = 1; loop <= maxLoops && !cancelRequested; loop++) {
            setStatus("AI play " + loop);
            if (!playMacroOnce(macro, settings.getSpeed())) return;
            if (!sleepCancelable(settings.getCheckDelaySeconds() * 1000L)) return;
            if (!screenCapture.isReady()) {
                setStatus("Start capture first");
                return;
            }
            setStatus("AI capture");
            String base64 = screenCapture.captureJpegBase64(1280, 72);
            if (cancelRequested) return;
            setStatus("AI Groq");
            GroqClient.Decision decision = GroqClient.checkStopCondition(this, settings, base64, loop);
            String type = decision.stopType == null ? "" : decision.stopType;
            setStatus("AI " + (decision.stop ? "stop" : "go") + " " + type);
            if (decision.stop) {
                consecutiveStop++;
                boolean terminal = "terminal_error".equalsIgnoreCase(type);
                if (terminal || !requireTwo || consecutiveStop >= 2) {
                    setStatus("AI stopped: " + shortText(decision.reason, 36));
                    return;
                }
                setStatus("AI confirm once");
            } else {
                consecutiveStop = 0;
            }
        }
        if (!cancelRequested) setStatus("Loop limit");
    }

    private boolean playMacroOnce(ArrayList<MacroEvent> macro, float speed) throws Exception {
        for (MacroEvent event : macro) {
            if (!sleepCancelable(adjustDelay(event.delayMs, speed))) return false;
            if (!dispatchSync(event)) {
                setStatus("Dispatch failed");
                return false;
            }
        }
        return true;
    }

    private boolean dispatchSync(final MacroEvent event) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final boolean[] ok = new boolean[]{false};
        main.post(new Runnable() {
            @Override
            public void run() {
                boolean accepted = AnHpAccessibilityService.dispatchMacroGesture(event, new AnHpAccessibilityService.GestureDone() {
                    @Override
                    public void onDone(boolean result) {
                        ok[0] = result;
                        latch.countDown();
                    }
                });
                if (!accepted) latch.countDown();
            }
        });
        long timeout = Math.max(1200, event.durationMs + 2200L);
        latch.await(timeout, TimeUnit.MILLISECONDS);
        return ok[0] && !cancelRequested;
    }

    private boolean sleepCancelable(long ms) throws InterruptedException {
        long until = SystemClock.uptimeMillis() + Math.max(0, ms);
        while (!cancelRequested && SystemClock.uptimeMillis() < until) {
            Thread.sleep(Math.min(80, Math.max(1, until - SystemClock.uptimeMillis())));
        }
        return !cancelRequested;
    }

    private int adjustDelay(int delayMs, float speed) {
        float safeSpeed = Math.max(0.2f, Math.min(5f, speed));
        return Math.max(8, Math.min(120000, Math.round(delayMs / safeSpeed)));
    }

    private void startScreenCapture(int resultCode, Intent data) {
        if (data == null || resultCode == 0) {
            setStatus("Capture denied");
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                        NOTIFICATION_ID,
                        buildNotification("Screen capture active"),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                                | ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            } else {
                startForeground(NOTIFICATION_ID, buildNotification("Screen capture active"));
            }
            screenCapture.start(resultCode, data);
            setStatus("Capture ready");
        } catch (Exception e) {
            Logx.e("screen capture start failed", e);
            setStatus("Capture error");
        }
    }

    private void startForegroundReady(String text) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                    NOTIFICATION_ID,
                    buildNotification(text),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, buildNotification(text));
        }
    }

    private WindowManager.LayoutParams overlayParams(int width, int height) {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                width,
                height,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        return params;
    }

    private boolean canDrawOverlay() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
    }

    private void removeRecordLayer() {
        if (recordLayer != null) {
            try {
                windowManager.removeView(recordLayer);
            } catch (Exception ignored) {
            }
            recordLayer = null;
        }
    }

    private void removeControls() {
        if (controlsView != null) {
            try {
                windowManager.removeView(controlsView);
            } catch (Exception ignored) {
            }
            controlsView = null;
        }
    }

    private void setStatus(final String value) {
        main.post(new Runnable() {
            @Override
            public void run() {
                if (statusView != null) statusView.setText(shortText(value, 20));
                if (hintView != null) hintView.setText(currentHint());
                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification(value));
            }
        });
    }

    private void updateButtons() {
        if (recording) {
            setPanelColor(0xEEFEE2E2, 0xFFDC2626);
            if (statusView != null) {
                statusView.setTextColor(0xFF7F1D1D);
                statusView.setText("REC " + recordingEvents.size());
            }
            if (hintView != null) {
                hintView.setTextColor(0xFF7F1D1D);
                hintView.setText("Tap target, A stop");
            }
            setButtonStyle(recordButton, "STOP", 0xFFDC2626);
            setButtonStyle(playButton, "P", 0xFF9CA3AF);
        } else if (playing) {
            setPanelColor(0xEEDBEAFE, 0xFF2563EB);
            if (statusView != null) {
                statusView.setTextColor(0xFF1E3A8A);
                statusView.setText("PLAY");
            }
            if (hintView != null) {
                hintView.setTextColor(0xFF1E3A8A);
                hintView.setText("P stop playback");
            }
            setButtonStyle(recordButton, "A", 0xFF9CA3AF);
            setButtonStyle(playButton, "STOP", 0xFF2563EB);
        } else {
            setPanelColor(0xEE14532D, 0xFF22C55E);
            if (statusView != null) statusView.setTextColor(Color.WHITE);
            if (hintView != null) {
                hintView.setTextColor(0xFFE5E7EB);
                hintView.setText(currentHint());
            }
            setButtonStyle(recordButton, "A", 0xFF34A853);
            setButtonStyle(playButton, "P", 0xFF0B57D0);
        }
    }

    private String currentHint() {
        if (recording) return "Tap target, A stop";
        if (playing) return "P stop playback";
        return "A rekam | P play";
    }

    private void setPanelColor(int fill, int stroke) {
        if (controlsRoot == null) return;
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(fill);
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(2), stroke);
        controlsRoot.setBackground(bg);
    }

    private void setButtonStyle(Button button, String text, int color) {
        if (button == null) return;
        button.setText(text);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(8));
        button.setBackground(bg);
    }

    private Notification buildNotification(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm != null) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "AnHP", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(channel);
        }
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                this,
                0,
                open,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder
                .setSmallIcon(R.drawable.ic_stat_anhp)
                .setContentTitle("AnHP")
                .setContentText(text)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void toast(final String text) {
        main.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MacroOverlayService.this, text, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String shortMsg(Exception e) {
        String msg = e.getMessage();
        if (msg == null || msg.length() == 0) msg = e.getClass().getSimpleName();
        return shortText(msg, 60);
    }

    private static String shortText(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 3)) + "...";
    }
}
