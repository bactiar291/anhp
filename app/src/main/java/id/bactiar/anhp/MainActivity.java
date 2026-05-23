package id.bactiar.anhp;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQ_NOTIFY = 502;

    private SettingsStore settings;
    private UiColors colors;
    private TextView status;
    private EditText speedInput;
    private EditText maxLoopsInput;
    private CheckBox loopPlayInput;
    private CheckBox saveRecordingInput;
    private CheckBox autoLoadInput;
    private CheckBox darkThemeInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = new SettingsStore(this);
        colors = UiColors.from(settings.isDarkTheme());
        requestNotificationPermission();
        buildUi();
        loadSettingsIntoUi();
        MacroOverlayService.startAction(this, MacroOverlayService.ACTION_SHOW_CONTROLS);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
        if (canDrawOverlay()) MacroOverlayService.startAction(this, MacroOverlayService.ACTION_SHOW_CONTROLS);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(colors.background);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(24));
        scroll.addView(root);
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setText("AnHP Macro Replay");
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(colors.text);
        title.setGravity(Gravity.LEFT);
        root.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("Rekam tap/swipe sekali, lalu replay manual atau loop statis offline.");
        subtitle.setTextColor(colors.muted);
        subtitle.setTextSize(14);
        root.addView(subtitle, matchWrap());

        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(13);
        status.setPadding(dp(12), dp(9), dp(12), dp(9));
        status.setBackground(rounded(colors.status, colors.accent, 8, 1));
        LinearLayout.LayoutParams statusLp = matchWrap();
        statusLp.topMargin = dp(14);
        root.addView(status, statusLp);

        TextView guide = panelText(
                "Cara cepat:\n" +
                "1. Aktifkan Accessibility dan Overlay.\n" +
                "2. Tekan Show Panel sampai tombol mengambang muncul.\n" +
                "3. Tekan A untuk rekam, tap/swipe target, lalu A lagi untuk stop.\n" +
                "4. Tekan P untuk replay. Tekan L untuk loop ON/OFF.\n" +
                "5. Simpan rekaman boleh dimatikan; kalau dimatikan, replay hanya berlaku selama app masih hidup.");
        LinearLayout.LayoutParams guideLp = matchWrap();
        guideLp.topMargin = dp(12);
        root.addView(guide, guideLp);

        root.addView(sectionTitle("Izin wajib"));
        LinearLayout permissionRow = row();
        permissionRow.addView(button("Accessibility", colors.primary, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        }));
        permissionRow.addView(button("Overlay", colors.secondary, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openOverlaySettings();
            }
        }));
        root.addView(permissionRow);

        root.addView(sectionTitle("Kontrol"));
        LinearLayout controlRow = row();
        controlRow.addView(button("Show Panel", colors.primary, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MacroOverlayService.startAction(MainActivity.this, MacroOverlayService.ACTION_SHOW_CONTROLS);
            }
        }));
        controlRow.addView(button("Load Saved", colors.secondary, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MacroOverlayService.startAction(MainActivity.this, MacroOverlayService.ACTION_LOAD_SAVED);
                setStatus("Minta load rekaman tersimpan.");
            }
        }));
        root.addView(controlRow);

        LinearLayout actionRow = row();
        actionRow.addView(button("A Record", colors.good, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSettings(false);
                MacroOverlayService.startAction(MainActivity.this, MacroOverlayService.ACTION_TOGGLE_RECORD);
            }
        }));
        actionRow.addView(button("P Play", colors.primary, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSettings(false);
                MacroOverlayService.startAction(MainActivity.this, MacroOverlayService.ACTION_TOGGLE_PLAY);
            }
        }));
        root.addView(actionRow);

        LinearLayout loopRow = row();
        loopRow.addView(button("L Loop", colors.warn, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loopPlayInput.setChecked(!loopPlayInput.isChecked());
                saveSettings(false);
                setStatus(loopPlayInput.isChecked() ? "Loop ON" : "Loop OFF");
            }
        }));
        loopRow.addView(button("Save Settings", colors.secondary, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSettings(true);
            }
        }));
        root.addView(loopRow);

        root.addView(sectionTitle("Mode replay"));
        loopPlayInput = check("Loop replay (L): ulang sampai limit atau STOP", false);
        saveRecordingInput = check("Simpan rekaman setelah stop A", true);
        autoLoadInput = check("Auto load rekaman tersimpan saat P ditekan", true);
        darkThemeInput = check("Dark theme", colors.dark);
        darkThemeInput.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                settings.setDarkTheme(darkThemeInput.isChecked());
                saveSettings(false);
                recreate();
            }
        });
        root.addView(loopPlayInput);
        root.addView(saveRecordingInput);
        root.addView(autoLoadInput);
        root.addView(darkThemeInput);

        LinearLayout numbers = row();
        speedInput = numberEdit("1.0", true);
        maxLoopsInput = numberEdit("20", false);
        numbers.addView(fieldBox("Speed", "0.2 - 5.0", speedInput));
        numbers.addView(fieldBox("Max loops", "0 = terus", maxLoopsInput));
        root.addView(numbers);
    }

    private void loadSettingsIntoUi() {
        loopPlayInput.setChecked(settings.isLoopPlay());
        saveRecordingInput.setChecked(settings.isSaveRecording());
        autoLoadInput.setChecked(settings.isAutoLoadSaved());
        darkThemeInput.setChecked(settings.isDarkTheme());
        speedInput.setText(String.valueOf(settings.getSpeed()));
        maxLoopsInput.setText(String.valueOf(settings.getMaxLoops()));
        refreshStatus();
    }

    private void saveSettings(boolean toast) {
        try {
            settings.setLoopPlay(loopPlayInput.isChecked());
            settings.setSaveRecording(saveRecordingInput.isChecked());
            settings.setAutoLoadSaved(autoLoadInput.isChecked());
            settings.setDarkTheme(darkThemeInput.isChecked());
            settings.setSpeed(parseFloat(speedInput.getText().toString(), 1.0f));
            settings.setMaxLoops(parseInt(maxLoopsInput.getText().toString(), 20));
            setStatus("Saved | macro tersimpan " + MacroStore.count(this));
            MacroOverlayService.startAction(this, MacroOverlayService.ACTION_SHOW_CONTROLS);
            if (toast) showToast("Setting tersimpan.");
        } catch (Exception e) {
            Logx.e("save settings failed", e);
            setStatus("Save failed: " + shortMsg(e));
            if (toast) showToast("Save failed: " + shortMsg(e));
        }
    }

    private void refreshStatus() {
        String overlay = canDrawOverlay() ? "overlay OK" : "overlay OFF";
        String access = AnHpAccessibilityService.isReady() ? "access OK" : "access OFF";
        String loop = settings.isLoopPlay() ? "loop ON" : "loop OFF";
        String save = settings.isSaveRecording() ? "save ON" : "save OFF";
        setStatus(access + " | " + overlay + " | " + loop + " | " + save + " | macro " + MacroStore.count(this));
    }

    private void openOverlaySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }
    }

    private boolean canDrawOverlay() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFY);
        }
    }

    private void setStatus(String text) {
        if (status != null) status.setText(text);
    }

    private void showToast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private TextView sectionTitle(String text) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextColor(colors.text);
        title.setTextSize(15);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lp = matchWrap();
        lp.topMargin = dp(18);
        title.setLayoutParams(lp);
        return title;
    }

    private TextView panelText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(colors.text);
        view.setTextSize(14);
        view.setLineSpacing(0, 1.08f);
        view.setPadding(dp(12), dp(11), dp(12), dp(11));
        view.setBackground(rounded(colors.surface, colors.border, 8, 1));
        return view;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = matchWrap();
        lp.topMargin = dp(10);
        row.setLayoutParams(lp);
        return row;
    }

    private Button button(String text, int color, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        button.setBackground(rounded(color, darker(color), 7, 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1);
        lp.rightMargin = dp(8);
        button.setLayoutParams(lp);
        return button;
    }

    private EditText numberEdit(String value, boolean decimal) {
        EditText edit = new EditText(this);
        edit.setText(value);
        edit.setTextColor(colors.text);
        edit.setHintTextColor(colors.muted);
        edit.setTextSize(14);
        edit.setSingleLine(true);
        edit.setPadding(dp(10), 0, dp(10), 0);
        edit.setInputType(decimal
                ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                : InputType.TYPE_CLASS_NUMBER);
        edit.setBackground(rounded(colors.input, colors.border, 7, 1));
        return edit;
    }

    private CheckBox check(String text, boolean checked) {
        CheckBox box = new CheckBox(this);
        box.setText(text);
        box.setTextColor(colors.text);
        box.setTextSize(14);
        box.setChecked(checked);
        return box;
    }

    private LinearLayout fieldBox(String label, String hint, EditText edit) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView tv = new TextView(this);
        tv.setText(label + " (" + hint + ")");
        tv.setTextSize(12);
        tv.setTextColor(colors.muted);
        box.addView(tv);
        LinearLayout.LayoutParams editLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        editLp.topMargin = dp(4);
        box.addView(edit, editLp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        lp.rightMargin = dp(8);
        box.setLayoutParams(lp);
        return box;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private GradientDrawable rounded(int fill, int stroke, int radiusDp, int strokeDp) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(fill);
        bg.setCornerRadius(dp(radiusDp));
        bg.setStroke(dp(strokeDp), stroke);
        return bg;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static int darker(int color) {
        int r = Math.max(0, Math.round(Color.red(color) * 0.78f));
        int g = Math.max(0, Math.round(Color.green(color) * 0.78f));
        int b = Math.max(0, Math.round(Color.blue(color) * 0.78f));
        return Color.rgb(r, g, b);
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static float parseFloat(String value, float fallback) {
        try {
            return Float.parseFloat(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String shortMsg(Exception e) {
        String msg = e.getMessage();
        if (msg == null || msg.length() == 0) msg = e.getClass().getSimpleName();
        return msg.length() <= 60 ? msg : msg.substring(0, 57) + "...";
    }

    private static final class UiColors {
        final boolean dark;
        final int background;
        final int surface;
        final int input;
        final int text;
        final int muted;
        final int border;
        final int status;
        final int primary;
        final int secondary;
        final int good;
        final int warn;
        final int accent;

        private UiColors(
                boolean dark,
                int background,
                int surface,
                int input,
                int text,
                int muted,
                int border,
                int status,
                int primary,
                int secondary,
                int good,
                int warn,
                int accent) {
            this.dark = dark;
            this.background = background;
            this.surface = surface;
            this.input = input;
            this.text = text;
            this.muted = muted;
            this.border = border;
            this.status = status;
            this.primary = primary;
            this.secondary = secondary;
            this.good = good;
            this.warn = warn;
            this.accent = accent;
        }

        static UiColors from(boolean dark) {
            if (dark) {
                return new UiColors(
                        true,
                        0xFF0B1220,
                        0xFF111827,
                        0xFF182235,
                        0xFFF8FAFC,
                        0xFFCBD5E1,
                        0xFF334155,
                        0xFF020617,
                        0xFF2563EB,
                        0xFF7C3AED,
                        0xFF059669,
                        0xFFD97706,
                        0xFF22D3EE);
            }
            return new UiColors(
                    false,
                    0xFFF8FAFC,
                    0xFFFFFFFF,
                    0xFFFFFFFF,
                    0xFF0F172A,
                    0xFF475569,
                    0xFFE2E8F0,
                    0xFF111827,
                    0xFF2563EB,
                    0xFF7C3AED,
                    0xFF16A34A,
                    0xFFF59E0B,
                    0xFF06B6D4);
        }
    }
}
