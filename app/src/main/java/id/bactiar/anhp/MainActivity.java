package id.bactiar.anhp;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
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
    private static final int REQ_CAPTURE = 501;
    private static final int REQ_NOTIFY = 502;

    private SettingsStore settings;
    private TextView status;
    private EditText apiKeyInput;
    private EditText conditionInput;
    private EditText modelsInput;
    private EditText speedInput;
    private EditText maxLoopsInput;
    private EditText delayInput;
    private CheckBox aiModeInput;
    private CheckBox loopPlayInput;
    private CheckBox confirmTwiceInput;
    private CheckBox autoModelsInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = new SettingsStore(this);
        requestNotificationPermission();
        buildUi();
        loadSettingsIntoUi();
        MacroOverlayService.startAction(this, MacroOverlayService.ACTION_SHOW_CONTROLS);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                Intent intent = new Intent(this, MacroOverlayService.class);
                intent.setAction(MacroOverlayService.ACTION_START_CAPTURE);
                intent.putExtra(MacroOverlayService.EXTRA_RESULT_CODE, resultCode);
                intent.putExtra(MacroOverlayService.EXTRA_CAPTURE_DATA, data);
                startServiceCompat(intent);
                setStatus("Screen capture izin OK. Floating P bisa AI loop.");
            } else {
                setStatus("Screen capture dibatalkan.");
            }
        }
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(16), dp(18), dp(24));
        scroll.addView(root);
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setText("AnHP");
        title.setTextSize(26);
        title.setTextColor(0xFF111827);
        title.setGravity(Gravity.LEFT);
        root.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("A = record/stop. P = play/stop. Groq AI mode checks screen after each loop.");
        subtitle.setTextColor(0xFF374151);
        subtitle.setTextSize(14);
        root.addView(subtitle, matchWrap());

        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(13);
        status.setPadding(dp(10), dp(8), dp(10), dp(8));
        status.setBackgroundColor(0xFF111827);
        LinearLayout.LayoutParams statusLp = matchWrap();
        statusLp.topMargin = dp(12);
        root.addView(status, statusLp);

        LinearLayout row1 = row();
        row1.addView(button("Accessibility", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        }));
        row1.addView(button("Overlay", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openOverlaySettings();
            }
        }));
        root.addView(row1);

        LinearLayout row2 = row();
        row2.addView(button("Screen Capture", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSettings();
                MediaProjectionManager manager =
                        (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                if (manager != null) startActivityForResult(manager.createScreenCaptureIntent(), REQ_CAPTURE);
            }
        }));
        row2.addView(button("Show A/P", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MacroOverlayService.startAction(MainActivity.this, MacroOverlayService.ACTION_SHOW_CONTROLS);
            }
        }));
        root.addView(row2);

        LinearLayout row3 = row();
        row3.addView(button("A Record", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSettings();
                MacroOverlayService.startAction(MainActivity.this, MacroOverlayService.ACTION_TOGGLE_RECORD);
            }
        }));
        row3.addView(button("P Play", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSettings();
                MacroOverlayService.startAction(MainActivity.this, MacroOverlayService.ACTION_TOGGLE_PLAY);
            }
        }));
        root.addView(row3);

        root.addView(label("Groq API key"));
        apiKeyInput = edit(false, 1);
        apiKeyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(apiKeyInput, matchWrap());

        LinearLayout row4 = row();
        row4.addView(button("Save Settings", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSettings();
            }
        }));
        row4.addView(button("Clear Key", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearKey();
            }
        }));
        root.addView(row4);

        root.addView(label("Stop condition"));
        conditionInput = edit(true, 4);
        root.addView(conditionInput, matchWrap());

        root.addView(label("Groq models"));
        modelsInput = edit(true, 3);
        root.addView(modelsInput, matchWrap());

        aiModeInput = check("AI mode on P", true);
        autoModelsInput = check("Auto fetch Groq models", true);
        loopPlayInput = check("Loop play without AI", false);
        confirmTwiceInput = check("Confirm AI stop twice", true);
        root.addView(aiModeInput);
        root.addView(autoModelsInput);
        root.addView(loopPlayInput);
        root.addView(confirmTwiceInput);

        LinearLayout numbers = row();
        speedInput = numberEdit("1.0");
        maxLoopsInput = numberEdit("20");
        delayInput = numberEdit("2");
        numbers.addView(fieldBox("Speed", speedInput));
        numbers.addView(fieldBox("Max loops", maxLoopsInput));
        numbers.addView(fieldBox("AI delay s", delayInput));
        root.addView(numbers);
    }

    private void loadSettingsIntoUi() {
        apiKeyInput.setText(SecurePrefs.loadGroqKey(this));
        conditionInput.setText(settings.getCondition());
        modelsInput.setText(settings.getModelsText());
        aiModeInput.setChecked(settings.isAiMode());
        autoModelsInput.setChecked(settings.isAutoFetchModels());
        loopPlayInput.setChecked(settings.isLoopPlay());
        confirmTwiceInput.setChecked(settings.isConfirmStopTwice());
        speedInput.setText(String.valueOf(settings.getSpeed()));
        maxLoopsInput.setText(String.valueOf(settings.getMaxLoops()));
        delayInput.setText(String.valueOf(settings.getCheckDelaySeconds()));
        refreshStatus();
    }

    private void saveSettings() {
        try {
            settings.setCondition(conditionInput.getText().toString());
            settings.setModelsText(modelsInput.getText().toString());
            settings.setAiMode(aiModeInput.isChecked());
            settings.setAutoFetchModels(autoModelsInput.isChecked());
            settings.setLoopPlay(loopPlayInput.isChecked());
            settings.setConfirmStopTwice(confirmTwiceInput.isChecked());
            settings.setSpeed(parseFloat(speedInput.getText().toString(), 1.0f));
            settings.setMaxLoops(parseInt(maxLoopsInput.getText().toString(), 20));
            settings.setCheckDelaySeconds(parseInt(delayInput.getText().toString(), 2));
            String key = apiKeyInput.getText().toString().trim();
            if (key.length() > 0) SecurePrefs.saveGroqKey(this, key);
            setStatus("Saved. Macro events: " + MacroStore.count(this));
            MacroOverlayService.startAction(this, MacroOverlayService.ACTION_SHOW_CONTROLS);
        } catch (Exception e) {
            Logx.e("save settings failed", e);
            setStatus("Save failed: " + e.getMessage());
        }
    }

    private void clearKey() {
        try {
            SecurePrefs.saveGroqKey(this, "");
            apiKeyInput.setText("");
            setStatus("Groq key cleared.");
        } catch (Exception e) {
            setStatus("Clear failed: " + e.getMessage());
        }
    }

    private void refreshStatus() {
        String key = SecurePrefs.loadGroqKey(this).length() > 0 ? "key OK" : "no key";
        String overlay = canDrawOverlay() ? "overlay OK" : "overlay OFF";
        String access = AnHpAccessibilityService.isReady() ? "access OK" : "access OFF";
        setStatus(access + " | " + overlay + " | " + key + " | macro " + MacroStore.count(this));
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

    private void startServiceCompat(Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent);
        else startService(intent);
    }

    private void setStatus(String text) {
        if (status != null) status.setText(text);
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
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

    private TextView label(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(0xFF111827);
        label.setTextSize(13);
        LinearLayout.LayoutParams lp = matchWrap();
        lp.topMargin = dp(14);
        label.setLayoutParams(lp);
        return label;
    }

    private Button button(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1);
        lp.rightMargin = dp(8);
        button.setLayoutParams(lp);
        return button;
    }

    private EditText edit(boolean multiline, int minLines) {
        EditText edit = new EditText(this);
        edit.setTextSize(14);
        edit.setSingleLine(!multiline);
        edit.setMinLines(minLines);
        edit.setGravity(Gravity.TOP | Gravity.LEFT);
        edit.setInputType(multiline
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                : InputType.TYPE_CLASS_TEXT);
        return edit;
    }

    private EditText numberEdit(String value) {
        EditText edit = edit(false, 1);
        edit.setText(value);
        edit.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        return edit;
    }

    private CheckBox check(String text, boolean checked) {
        CheckBox box = new CheckBox(this);
        box.setText(text);
        box.setTextSize(14);
        box.setChecked(checked);
        return box;
    }

    private LinearLayout fieldBox(String label, EditText edit) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(12);
        tv.setTextColor(0xFF374151);
        box.addView(tv);
        box.addView(edit, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        lp.rightMargin = dp(8);
        box.setLayoutParams(lp);
        return box;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
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
}
