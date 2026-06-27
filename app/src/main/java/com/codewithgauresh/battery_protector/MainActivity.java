package com.codewithgauresh.battery_protector;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Objects;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.switchmaterial.SwitchMaterial;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "BatteryProtectorPrefs";
    private static final String KEY_LIMIT = "battery_limit";
    private static final String KEY_ALARM_ENABLED = "alarm_enabled";
    private static final String KEY_RINGTONE_URI = "ringtone_uri";

    private TextView tvCurrentBattery, tvLimitValue, tvSelectedRingtone, tvChargingStatus;
    private SeekBar seekBarLimit;
    private SwitchMaterial switchAlarm;
    private Button btnStartStop;
    private boolean isServiceRunning = false;
    private Uri selectedRingtoneUri;
    private final Handler updateHandler = new Handler(Looper.getMainLooper());
    private final Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            updateBatteryUI();
            updateHandler.postDelayed(this, 1000); // Update every 1 second
        }
    };

    private final ActivityResultLauncher<Intent> ringtonePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    selectedRingtoneUri = result.getData().getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                    if (selectedRingtoneUri != null) {
                        Ringtone ringtone = RingtoneManager.getRingtone(this, selectedRingtoneUri);
                        tvSelectedRingtone.setText(ringtone.getTitle(this));
                    } else {
                        tvSelectedRingtone.setText(R.string.silent);
                    }
                    saveSettings();
                }
            }
    );

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int batteryPct = (int) ((level / (float) scale) * 100);
            tvCurrentBattery.setText(getString(R.string.current_battery, batteryPct));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        tvCurrentBattery = findViewById(R.id.tvCurrentBattery);
        tvChargingStatus = findViewById(R.id.tvChargingStatus);
        tvLimitValue = findViewById(R.id.tvLimitValue);
        tvSelectedRingtone = findViewById(R.id.tvSelectedRingtone);
        seekBarLimit = findViewById(R.id.seekBarLimit);
        switchAlarm = findViewById(R.id.switchAlarm);
        btnStartStop = findViewById(R.id.btnStartStop);
        Button btnSelectRingtone = findViewById(R.id.btnSelectRingtone);

        loadSettings();

        isServiceRunning = isMyServiceRunning(BatteryService.class);
        if (isServiceRunning) {
            btnStartStop.setText(R.string.stop_protection);
        }

        seekBarLimit.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvLimitValue.setText(getString(R.string.limit_value, progress));
                if (fromUser) saveSettings();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        switchAlarm.setOnCheckedChangeListener((buttonView, isChecked) -> saveSettings());

        btnSelectRingtone.setOnClickListener(v -> {
            Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getString(R.string.select_alarm_sound));
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, selectedRingtoneUri);
            ringtonePickerLauncher.launch(intent);
        });

        btnStartStop.setOnClickListener(v -> {
            if (isServiceRunning) {
                stopBatteryService();
            } else {
                checkPermissionAndStartService();
            }
        });

        // Initialize battery status immediately
        updateBatteryUI();
    }

    private void saveSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(KEY_LIMIT, seekBarLimit.getProgress());
        editor.putBoolean(KEY_ALARM_ENABLED, switchAlarm.isChecked());
        if (selectedRingtoneUri != null) {
            editor.putString(KEY_RINGTONE_URI, selectedRingtoneUri.toString());
        } else {
            editor.remove(KEY_RINGTONE_URI);
        }
        editor.apply();
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int limit = prefs.getInt(KEY_LIMIT, 80);
        boolean alarmEnabled = prefs.getBoolean(KEY_ALARM_ENABLED, true);
        String ringtoneUriStr = prefs.getString(KEY_RINGTONE_URI, null);

        seekBarLimit.setProgress(limit);
        tvLimitValue.setText(getString(R.string.limit_value, limit));
        switchAlarm.setChecked(alarmEnabled);

        if (ringtoneUriStr != null) {
            selectedRingtoneUri = Uri.parse(ringtoneUriStr);
            try {
                Ringtone ringtone = RingtoneManager.getRingtone(this, selectedRingtoneUri);
                if (ringtone != null) {
                    tvSelectedRingtone.setText(ringtone.getTitle(this));
                }
            } catch (Exception e) {
                tvSelectedRingtone.setText(R.string.default_alarm);
            }
        }
    }

    @SuppressWarnings({"deprecation", "SameParameterValue"})
    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (manager != null) {
            for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (Objects.equals(serviceClass.getName(), service.service.getClassName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private void updateBatteryUI() {
        BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        int batteryPct;
        if (bm != null) {
            batteryPct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        } else {
            batteryPct = -1;
        }

        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);

        if (batteryStatus != null) {
            int finalBatteryPct;
            if (batteryPct <= 0) { // Fallback if property is not supported
                int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (level != -1 && scale != -1) {
                    finalBatteryPct = (int) ((level / (float) scale) * 100);
                } else {
                    finalBatteryPct = batteryPct;
                }
            } else {
                finalBatteryPct = batteryPct;
            }

            if (finalBatteryPct != -1) {
                tvCurrentBattery.setText(getString(R.string.current_battery, finalBatteryPct));
            }

            int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            String statusString;
            switch (status) {
                case BatteryManager.BATTERY_STATUS_CHARGING:
                    statusString = getString(R.string.status_charging);
                    break;
                case BatteryManager.BATTERY_STATUS_DISCHARGING:
                    statusString = getString(R.string.status_discharging);
                    break;
                case BatteryManager.BATTERY_STATUS_FULL:
                    statusString = getString(R.string.status_full);
                    break;
                case BatteryManager.BATTERY_STATUS_NOT_CHARGING:
                    statusString = getString(R.string.status_not_charging);
                    break;
                default:
                    statusString = getString(R.string.status_unknown);
                    break;
            }
            tvChargingStatus.setText(getString(R.string.charging_status, statusString));
        }
    }

    private void checkPermissionAndStartService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            } else {
                startBatteryService();
            }
        } else {
            startBatteryService();
        }
    }

    @Override
    @SuppressWarnings("IfCanBeSwitch")
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startBatteryService();
            } else {
                Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startBatteryService() {
        Intent serviceIntent = new Intent(this, BatteryService.class);
        serviceIntent.putExtra("limit", seekBarLimit.getProgress());
        serviceIntent.putExtra("alarm", switchAlarm.isChecked());
        if (selectedRingtoneUri != null) {
            serviceIntent.putExtra("ringtoneUri", selectedRingtoneUri.toString());
        }
        ContextCompat.startForegroundService(this, serviceIntent);
        btnStartStop.setText(R.string.stop_protection);
        isServiceRunning = true;
    }

    private void stopBatteryService() {
        Intent serviceIntent = new Intent(this, BatteryService.class);
        stopService(serviceIntent);
        btnStartStop.setText(R.string.start_protection);
        isServiceRunning = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateBatteryUI();
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(batteryReceiver, filter);
        updateHandler.post(updateRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(batteryReceiver);
        updateHandler.removeCallbacks(updateRunnable);
    }
}
