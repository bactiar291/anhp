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
    static final String ACTION_TOGGLE_LOOP = "id.bactiar.anhp.TOGGLE_LOOP";
    static final String ACTION_LOAD_SAVED = "id.bactiar.anhp.LOAD_SAVED";

    private static final String CHANNEL_ID = "anhp";
    private static final int NOTIFICATION_ID = 291;

    private final Handler main = new Handler();
    private HandlerThread workerThread;
    private Handler worker;
    private WindowManager windowManager;
    private View controlsView;
    private WindowManager.LayoutParams controlsParams;
    private View recordLayer;
    private LinearLayout controlsRoot;
    private TextView statusView;
    private TextView hintView;
    private Button recordButton;
    private Button playButton;
    private Button loopButton;
    private final ArrayList<MacroEvent> recordingEvents = new ArrayList<>();
    private final ArrayList<MacroEvent> activeMacro = new ArrayList<>();
    private boolean hasActiveMacro;
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
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception e) {
            Logx.e("start overlay service failed", e);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
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
        } else if (ACTION_TOGGLE_LOOP.equals(action)) {
            toggleLoop();
        } else if (ACTION_LOAD_SAVED.equals(action)) {
            loadSavedMacro();
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
        if (workerThread != null) workerThread.quitSafely();
        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        showControls();
    }

    private void showControls() {
        if (!canDrawOverlay()) {
            setStatus("Overlay permission needed");
            toast("Allow overlay first");
            return;
        }
        if (controlsView != null) {
            ensureControlsAttached();
            updateButtons();
            return;
        }

        LinearLayout root = new LinearLayout(this);
        controlsRoot = root;
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(dp(10), dp(8), dp(10), dp(8));
        setPanelColor(0xEE0F172A, 0xFF22C55E);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);

        statusView = new TextView(this);
        statusView.setTextColor(Color.WHITE);
        statusView.setTextSize(14);
        statusView.setText("READY");
        statusView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        statusView.setGravity(Gravity.CENTER_VERTICAL);
        copy.addView(statusView, new LinearLayout.LayoutParams(dp(150), dp(22)));

        hintView = new TextView(this);
        hintView.setTextColor(0xFFE5E7EB);
        hintView.setTextSize(11);
        hintView.setText("A rec | P play | L loop");
        hintView.setGravity(Gravity.CENTER_VERTICAL);
        copy.addView(hintView, new LinearLayout.LayoutParams(dp(150), dp(20)));
        root.addView(copy, new LinearLayout.LayoutParams(dp(156), dp(48)));

        recordButton = makeButton("A", 0xFF16A34A);
        playButton = makeButton("P", 0xFF2563EB);
        loopButton = makeButton("L", 0xFF64748B);
        root.addView(recordButton);
        root.addView(playButton);
        root.addView(loopButton);

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
        loopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleLoop();
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
        if (!ensureControlsAttached()) {
            controlsView = null;
            controlsParams = null;
        }
        updateButtons();
    }

    private Button makeButton(String text, int color) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(16);
        button.setAllCaps(false);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(7));
        button.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(54), dp(42));
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

    private void toggleLoop() {
        SettingsStore settings = new SettingsStore(this);
        boolean next = !settings.isLoopPlay();
        settings.setLoopPlay(next);
        setStatus(next ? "Loop ON" : "Loop OFF");
        updateButtons();
    }

    private void loadSavedMacro() {
        ArrayList<MacroEvent> loaded = MacroStore.load(this);
        if (loaded.isEmpty()) {
            setStatus("No saved macro");
            toast("Belum ada rekaman tersimpan.");
            return;
        }
        activeMacro.clear();
        activeMacro.addAll(loaded);
        hasActiveMacro = true;
        setStatus("Loaded " + activeMacro.size());
        updateButtons();
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
        if (recordingEvents.isEmpty()) {
            setStatus("No action recorded");
            updateButtons();
            return;
        }

        activeMacro.clear();
        activeMacro.addAll(recordingEvents);
        hasActiveMacro = true;

        SettingsStore settings = new SettingsStore(this);
        if (settings.isSaveRecording()) {
            boolean saved = MacroStore.save(this, recordingEvents);
            setStatus(saved ? "Saved " + recordingEvents.size() : "Save failed");
        } else {
            setStatus("Temp " + recordingEvents.size());
        }
        updateButtons();
    }

    private void addRecordLayer() {
        removeRecordLayer();
        final View oldControls = controlsView;
        final WindowManager.LayoutParams oldParams = controlsParams;
        if (oldControls != null && oldControls.getParent() != null) {
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
            if (oldControls != null && oldParams != null) {
                windowManager.addView(oldControls, oldParams);
            }
        } catch (Exception e) {
            Logx.e("record layer failed", e);
            removeRecordLayer();
            recording = false;
            ensureControlsAttached();
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

    private void forwardLiveGesture(final MacroEvent macroEvent) {
        removeRecordLayer();
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!recording) return;
                boolean accepted = AnHpAccessibilityService.dispatchMacroGesture(macroEvent, new AnHpAccessibilityService.GestureDone() {
                    @Override
                    public void onDone(boolean ok) {
                        if (!ok && recording) setStatus("Gesture blocked");
                        main.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (recording && recordLayer == null) addRecordLayer();
                                updateButtons();
                            }
                        }, 80);
                    }
                });
                if (!accepted) {
                    setStatus("Gesture not accepted");
                    if (recording && recordLayer == null) addRecordLayer();
                    updateButtons();
                }
            }
        }, 55);
    }

    private void togglePlayback() {
        if (recording) return;
        if (playing) {
            cancelRequested = true;
            setStatus("Stopping");
            updateButtons();
            return;
        }

        final SettingsStore settings = new SettingsStore(this);
        final ArrayList<MacroEvent> macro = getMacroForPlayback(settings);
        if (macro.isEmpty()) {
            setStatus(settings.isAutoLoadSaved() ? "No macro" : "Load saved first");
            toast(settings.isAutoLoadSaved() ? "Record first with A." : "Tekan Load Saved atau rekam dengan A.");
            return;
        }
        if (!AnHpAccessibilityService.isReady()) {
            setStatus("Enable Accessibility");
            toast("Enable AnHP Control in Accessibility");
            return;
        }
        playing = true;
        cancelRequested = false;
        setStatus(settings.isLoopPlay() ? "LOOP" : "PLAY");
        updateButtons();
        worker.post(new Runnable() {
            @Override
            public void run() {
                try {
                    runStaticPlayback(macro, settings);
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

    private ArrayList<MacroEvent> getMacroForPlayback(SettingsStore settings) {
        if (hasActiveMacro && !activeMacro.isEmpty()) return new ArrayList<>(activeMacro);
        if (!settings.isAutoLoadSaved()) return new ArrayList<>();
        ArrayList<MacroEvent> loaded = MacroStore.load(this);
        if (!loaded.isEmpty()) {
            activeMacro.clear();
            activeMacro.addAll(loaded);
            hasActiveMacro = true;
        }
        return loaded;
    }

    private void runStaticPlayback(ArrayList<MacroEvent> macro, SettingsStore settings) throws Exception {
        boolean loop = settings.isLoopPlay();
        int maxLoops = loop ? settings.getMaxLoops() : 1;
        int count = 0;
        while (!cancelRequested && (!loop || maxLoops == 0 || count < maxLoops)) {
            count++;
            if (loop) {
                setStatus(maxLoops == 0 ? "LOOP " + count : "LOOP " + count + "/" + maxLoops);
            } else {
                setStatus("PLAY");
            }
            if (!playMacroOnce(macro, settings.getSpeed())) return;
            if (!loop) break;
        }
        if (!cancelRequested) setStatus(loop ? "Loop done" : "Done");
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
        return new WindowManager.LayoutParams(
                width,
                height,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
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

    private boolean ensureControlsAttached() {
        if (controlsView == null || controlsParams == null) return false;
        if (controlsView.getParent() != null) return true;
        try {
            windowManager.addView(controlsView, controlsParams);
            return true;
        } catch (Exception e) {
            Logx.e("attach controls failed", e);
            return false;
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
        SettingsStore settings = new SettingsStore(this);
        boolean loop = settings.isLoopPlay();
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
            setButtonStyle(playButton, "P", 0xFF94A3B8);
            setButtonStyle(loopButton, loop ? "L ON" : "L", loop ? 0xFFF59E0B : 0xFF64748B);
        } else if (playing) {
            setPanelColor(0xEEDBEAFE, 0xFF2563EB);
            if (statusView != null) {
                statusView.setTextColor(0xFF1E3A8A);
                statusView.setText(loop ? "LOOP" : "PLAY");
            }
            if (hintView != null) {
                hintView.setTextColor(0xFF1E3A8A);
                hintView.setText("P stop playback");
            }
            setButtonStyle(recordButton, "A", 0xFF94A3B8);
            setButtonStyle(playButton, "STOP", 0xFF2563EB);
            setButtonStyle(loopButton, loop ? "L ON" : "L", loop ? 0xFFF59E0B : 0xFF64748B);
        } else {
            if (loop) {
                setPanelColor(0xEEFDE68A, 0xFFF59E0B);
                if (statusView != null) statusView.setTextColor(0xFF713F12);
                if (hintView != null) hintView.setTextColor(0xFF713F12);
            } else {
                setPanelColor(0xEE0F172A, 0xFF22C55E);
                if (statusView != null) statusView.setTextColor(Color.WHITE);
                if (hintView != null) hintView.setTextColor(0xFFE5E7EB);
            }
            if (hintView != null) hintView.setText(currentHint());
            setButtonStyle(recordButton, "A", 0xFF16A34A);
            setButtonStyle(playButton, "P", 0xFF2563EB);
            setButtonStyle(loopButton, loop ? "L ON" : "L", loop ? 0xFFF59E0B : 0xFF64748B);
        }
    }

    private String currentHint() {
        if (recording) return "Tap target, A stop";
        if (playing) return "P stop playback";
        return new SettingsStore(this).isLoopPlay() ? "Loop ON | P play" : "Loop OFF | L toggle";
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
