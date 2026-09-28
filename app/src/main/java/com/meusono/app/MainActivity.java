/*
 * Copyright (C) 2026 Higor Ramos
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.meusono.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.Manifest;
import android.app.TimePickerDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.ColorFilter;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {

    private static final int REQUEST_NOTIFICATIONS = 41;
    private static final int REQUEST_BACKUP_CREATE = 51;
    private static final int REQUEST_BACKUP_RESTORE = 52;
    private static final int THEME_SYSTEM = 0;
    private static final int THEME_LIGHT = 1;
    private static final int THEME_DARK = 2;

    private int BG = Color.rgb(5, 9, 24);
    private int SURFACE = Color.rgb(16, 23, 42);
    private int SURFACE_2 = Color.rgb(23, 31, 55);
    private int PURPLE = Color.rgb(139, 108, 255);
    private int PURPLE_LIGHT = Color.rgb(177, 156, 255);
    private int TEXT = Color.rgb(248, 247, 255);
    private int MUTED = Color.rgb(168, 177, 207);
    private int GREEN = Color.rgb(101, 230, 160);
    private int YELLOW = Color.rgb(255, 196, 92);
    private int RED = Color.rgb(255, 113, 125);
    private int DIVIDER = Color.rgb(45, 56, 87);

    private FrameLayout contentHost;
    private LinearLayout navBar;
    private int selectedTab = 0;
    private int historyFilter = 0;
    private long selectedRecordId = -1L;
    private boolean showBedtimeConfig;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable ticker;
    private ProgressRingView progressRing;
    private SleepDb db;
    private SharedPreferences prefs;
    private int themeMode = THEME_DARK;
    private boolean lightTheme;
    private boolean pendingBackgroundEnable;
    private boolean pendingBedtimeEnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("meu_sono_prefs", MODE_PRIVATE);
        themeMode = prefs.getInt("theme_mode", THEME_DARK);
        lightTheme = resolveLightTheme(themeMode);
        setTheme(lightTheme ? R.style.AppThemeLight : R.style.AppTheme);
        applyPalette();
        if (savedInstanceState != null) {
            selectedTab = savedInstanceState.getInt("selected_tab", 0);
            selectedRecordId = savedInstanceState.getLong("selected_record", -1L);
            showBedtimeConfig = savedInstanceState.getBoolean("bedtime_config", false);
        }
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        updateSystemBarIcons();

        db = new SleepDb(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        contentHost = new FrameLayout(this);
        contentHost.setBackground(new NightBackdropDrawable());
        root.addView(contentHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        navBar = new LinearLayout(this);
        navBar.setOrientation(LinearLayout.HORIZONTAL);
        navBar.setGravity(Gravity.CENTER);
        navBar.setPadding(dp(8), dp(8), dp(8), dp(10));
        navBar.setBackground(roundedGradient(
                lightTheme
                        ? new int[]{Color.argb(247, 255, 255, 255), Color.argb(244, 246, 244, 252)}
                        : new int[]{Color.argb(242, 15, 22, 47), Color.argb(236, 8, 13, 31)},
                26, DIVIDER, 1));
        root.addView(navBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(76)));

        if (android.os.Build.VERSION.SDK_INT >= 35) {
            getWindow().setDecorFitsSystemWindows(false);
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().setNavigationBarColor(Color.TRANSPARENT);
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                view.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
                return insets;
            });
        }

        setContentView(root);
        render();
        BedtimeReminderScheduler.scheduleNext(this);
        if (isSleepActive() && backgroundTrackingEnabled()) startSleepTrackingService();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt("selected_tab", selectedTab);
        outState.putLong("selected_record", selectedRecordId);
        outState.putBoolean("bedtime_config", showBedtimeConfig);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQUEST_BACKUP_CREATE) {
            writeBackup(uri);
        } else if (requestCode == REQUEST_BACKUP_RESTORE) {
            readBackup(uri);
        }
    }

    private void chooseBackupLocation() {
        String fileName = "MeuSono-backup-" + new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date()) + ".json";
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, fileName);
        try {
            startActivityForResult(intent, REQUEST_BACKUP_CREATE);
        } catch (Exception e) {
            showBackupError("Não foi possível abrir o seletor para salvar o backup.");
        }
    }

    private void chooseBackupToRestore() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_BACKUP_RESTORE);
        } catch (Exception e) {
            showBackupError("Não foi possível abrir o seletor de arquivos.");
        }
    }

    private void writeBackup(Uri uri) {
        try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
            if (output == null) throw new IOException("Não foi possível gravar o arquivo.");
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(output, "UTF-8"));
            writer.write(createBackupJson().toString(2));
            writer.flush();
            Toast.makeText(this, "Backup salvo com sucesso.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            showBackupError("Não foi possível salvar o backup. Tente escolher outra pasta.");
        }
    }

    private JSONObject createBackupJson() throws JSONException {
        JSONObject root = new JSONObject();
        root.put("app", "Meu Sono");
        root.put("formatVersion", 1);
        root.put("createdAt", System.currentTimeMillis());
        root.put("activeStart", prefs.getLong("active_start", 0L));

        JSONObject settings = new JSONObject();
        settings.put("targetMinutes", targetMinutes());
        settings.put("themeMode", themeMode);
        settings.put("keepScreenOn", prefs.getBoolean("keep_screen_on", false));
        settings.put("backgroundTracking", backgroundTrackingEnabled());
        settings.put("bedtimeReminderEnabled", prefs.getBoolean(BedtimeReminderScheduler.PREF_ENABLED, false));
        settings.put("wakeMinute", BedtimeReminderScheduler.wakeMinute(this));
        root.put("settings", settings);

        JSONArray records = new JSONArray();
        for (SleepRecord record : db.getAll(50000)) {
            JSONObject item = new JSONObject();
            item.put("start", record.start);
            item.put("end", record.end);
            item.put("quality", record.quality == null ? "Bom" : record.quality);
            item.put("note", record.note == null ? "" : record.note);
            records.put(item);
        }
        root.put("records", records);
        return root;
    }

    private void readBackup(Uri uri) {
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("Não foi possível abrir o arquivo.");
            BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"));
            StringBuilder contents = new StringBuilder();
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                contents.append(buffer, 0, read);
                if (contents.length() > 10 * 1024 * 1024) throw new IOException("O arquivo de backup é muito grande.");
            }
            BackupPayload payload = parseBackup(new JSONObject(contents.toString()));
            showRestoreOptions(payload);
        } catch (Exception e) {
            showBackupError("O arquivo não parece ser um backup válido do Meu Sono.");
        }
    }

    private BackupPayload parseBackup(JSONObject root) throws JSONException, IOException {
        if (!"Meu Sono".equals(root.optString("app")) || root.optInt("formatVersion", -1) != 1) {
            throw new IOException("Formato de backup incompatível.");
        }
        JSONArray array = root.optJSONArray("records");
        if (array == null || array.length() > 50000) throw new IOException("Lista de registros inválida.");

        BackupPayload payload = new BackupPayload();
        long now = System.currentTimeMillis();
        long activeStart = root.optLong("activeStart", 0L);
        if (activeStart < 0 || activeStart > now + 5L * 60L * 1000L) throw new IOException("Estado do cronômetro inválido.");
        payload.activeStart = activeStart;
        Set<String> unique = new HashSet<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) throw new IOException("Há um registro inválido no arquivo.");
            long start = item.optLong("start", -1L);
            long end = item.optLong("end", -1L);
            String quality = item.optString("quality", "Bom");
            String note = item.optString("note", "");
            if (start <= 0 || end <= start || end - start > 48L * 60L * 60L * 1000L || end > now + 5L * 60L * 1000L) {
                throw new IOException("Um registro contém horários inválidos.");
            }
            if (!("Péssimo".equals(quality) || "Ruim".equals(quality) || "Regular".equals(quality)
                    || "Bom".equals(quality) || "Excelente".equals(quality)) || note.length() > 10000) {
                throw new IOException("Um registro contém dados fora do formato esperado.");
            }
            String key = start + ":" + end;
            if (!unique.add(key)) continue;
            SleepRecord record = new SleepRecord();
            record.start = start;
            record.end = end;
            record.quality = quality;
            record.note = note;
            payload.records.add(record);
        }

        JSONObject settings = root.optJSONObject("settings");
        if (settings != null) {
            int goal = settings.optInt("targetMinutes", 480);
            if (goal >= 360 && goal <= 600 && (goal - 360) % 30 == 0) payload.targetMinutes = goal;
            int mode = settings.optInt("themeMode", THEME_DARK);
            if (mode >= THEME_SYSTEM && mode <= THEME_DARK) payload.themeMode = mode;
            payload.keepScreenOn = settings.optBoolean("keepScreenOn", false);
            payload.backgroundTracking = settings.optBoolean("backgroundTracking", false);
            payload.bedtimeReminderEnabled = settings.optBoolean("bedtimeReminderEnabled", false);
            int wakeMinute = settings.optInt("wakeMinute", 7 * 60);
            if (wakeMinute >= 0 && wakeMinute < 1440) payload.wakeMinute = wakeMinute;
        }
        return payload;
    }

    private void showRestoreOptions(BackupPayload payload) {
        String count = payload.records.size() + (payload.records.size() == 1 ? " registro de sono" : " registros de sono");
        new AlertDialog.Builder(this)
                .setTitle("Restaurar backup")
                .setMessage("O arquivo contém " + count + ".\n\nMesclar adiciona noites que ainda não existem e mantém suas configurações atuais. Substituir troca os registros e as configurações pelos dados do backup.")
                .setNegativeButton("Cancelar", null)
                .setNeutralButton("Mesclar registros", (dialog, which) -> applyBackup(payload, false))
                .setPositiveButton("Substituir", (dialog, which) -> confirmBackupReplacement(payload))
                .show();
    }

    private void confirmBackupReplacement(BackupPayload payload) {
        new AlertDialog.Builder(this)
                .setTitle("Substituir registros atuais?")
                .setMessage("Os registros que estão neste aparelho serão removidos e trocados pelos " + payload.records.size() + " registros do backup.")
                .setNegativeButton("Voltar", null)
                .setPositiveButton("Substituir registros", (dialog, which) -> applyBackup(payload, true))
                .show();
    }

    private void applyBackup(BackupPayload payload, boolean replace) {
        try {
            int added = db.restoreRecords(payload.records, replace);
            boolean themeChanged = false;
            if (replace) {
                SharedPreferences.Editor editor = prefs.edit()
                        .putInt("target_minutes", payload.targetMinutes)
                        .putInt("theme_mode", payload.themeMode)
                        .putBoolean("keep_screen_on", payload.keepScreenOn)
                        .putBoolean("background_tracking", payload.backgroundTracking)
                        .putBoolean(BedtimeReminderScheduler.PREF_ENABLED, payload.bedtimeReminderEnabled)
                        .putInt(BedtimeReminderScheduler.PREF_WAKE_MINUTE, payload.wakeMinute);
                if (payload.activeStart > 0) editor.putLong("active_start", payload.activeStart);
                else editor.remove("active_start");
                editor.apply();
                BedtimeReminderScheduler.scheduleNext(this);
                if (isSleepActive() && backgroundTrackingEnabled()) startSleepTrackingService();
                else stopSleepTrackingService();
                updateKeepScreenOn();
                selectedRecordId = -1L;
                themeMode = payload.themeMode;
                themeChanged = true;
            }
            Toast.makeText(this, added + (added == 1 ? " registro restaurado." : " registros restaurados."), Toast.LENGTH_LONG).show();
            if (themeChanged) recreate();
            else render();
        } catch (Exception e) {
            showBackupError("Não foi possível aplicar o backup. Nenhum registro foi alterado.");
        }
    }

    private void showBackupError(String message) {
        new AlertDialog.Builder(this)
                .setTitle("Backup")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    static class BackupPayload {
        final List<SleepRecord> records = new ArrayList<>();
        int targetMinutes = 480;
        int themeMode = THEME_DARK;
        long activeStart;
        boolean keepScreenOn;
        boolean backgroundTracking;
        boolean bedtimeReminderEnabled;
        int wakeMinute = 7 * 60;
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateKeepScreenOn();
        BedtimeReminderScheduler.scheduleNext(this);
    }

    @Override
    protected void onPause() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        stopTicker();
        if (db != null) db.close();
        super.onDestroy();
    }

    private void render() {
        stopTicker();
        contentHost.removeAllViews();
        View page;
        if (showBedtimeConfig) page = buildBedtimeSettings();
        else if (selectedRecordId > 0) page = buildDetail();
        else if (selectedTab == 1) page = buildHistory();
        else if (selectedTab == 2) page = buildStats();
        else if (selectedTab == 3) page = buildSettings();
        else page = buildToday();
        contentHost.addView(page);
        buildNav();
        updateKeepScreenOn();
    }

    private void applyPalette() {
        if (lightTheme) {
            BG = Color.rgb(246, 244, 251);
            SURFACE = Color.rgb(255, 255, 255);
            SURFACE_2 = Color.rgb(239, 236, 248);
            PURPLE = Color.rgb(105, 77, 202);
            PURPLE_LIGHT = Color.rgb(105, 77, 202);
            TEXT = Color.rgb(31, 28, 45);
            MUTED = Color.rgb(99, 96, 114);
            GREEN = Color.rgb(27, 125, 83);
            YELLOW = Color.rgb(164, 94, 14);
            RED = Color.rgb(184, 53, 67);
            DIVIDER = Color.rgb(224, 220, 235);
        } else {
            BG = Color.rgb(5, 9, 24);
            SURFACE = Color.rgb(16, 23, 42);
            SURFACE_2 = Color.rgb(23, 31, 55);
            PURPLE = Color.rgb(139, 108, 255);
            PURPLE_LIGHT = Color.rgb(177, 156, 255);
            TEXT = Color.rgb(248, 247, 255);
            MUTED = Color.rgb(168, 177, 207);
            GREEN = Color.rgb(101, 230, 160);
            YELLOW = Color.rgb(255, 196, 92);
            RED = Color.rgb(255, 113, 125);
            DIVIDER = Color.rgb(45, 56, 87);
        }
    }

    private boolean resolveLightTheme(int mode) {
        if (mode == THEME_LIGHT) return true;
        if (mode == THEME_DARK) return false;
        int current = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return current != android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private void updateSystemBarIcons() {
        if (Build.VERSION.SDK_INT >= 23) {
            int flags = lightTheme ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR : 0;
            if (Build.VERSION.SDK_INT >= 26 && lightTheme) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }

    private boolean isSleepActive() {
        return prefs != null && prefs.getLong("active_start", 0L) > 0L;
    }

    private boolean backgroundTrackingEnabled() {
        return prefs != null && prefs.getBoolean("background_tracking", false);
    }

    private void updateKeepScreenOn() {
        if (getWindow() == null) return;
        boolean keep = prefs != null
                && prefs.getBoolean("keep_screen_on", false)
                && prefs.getLong("active_start", 0L) > 0L;
        if (keep) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void startSleepTrackingService() {
        if (!isSleepActive() || !backgroundTrackingEnabled()) return;
        Intent intent = new Intent(this, SleepTrackingService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent);
        else startService(intent);
    }

    private void stopSleepTrackingService() {
        stopService(new Intent(this, SleepTrackingService.class));
    }

    @Override
    public void onBackPressed() {
        if (showBedtimeConfig) {
            showBedtimeConfig = false;
            selectedTab = 0;
            render();
            return;
        }
        if (selectedRecordId > 0) {
            selectedRecordId = -1L;
            render();
            return;
        }
        if (selectedTab != 0) {
            selectedTab = 0;
            render();
            return;
        }
        super.onBackPressed();
    }

    private void buildNav() {
        navBar.removeAllViews();
        String[] icons = {"home", "history", "stats", "settings"};
        String[] labels = {"Hoje", "Histórico", "Estatísticas", "Ajustes"};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(4), dp(5), dp(4), dp(4));
            if (i == selectedTab) {
                int[] activeColors = lightTheme
                        ? new int[]{Color.rgb(239, 234, 255), Color.rgb(231, 224, 252)}
                        : new int[]{Color.rgb(56, 48, 112), Color.rgb(37, 35, 81)};
                item.setBackground(ripple(roundedGradient(activeColors, 18, Color.TRANSPARENT, 0), PURPLE_LIGHT));
            } else {
                item.setBackground(ripple(roundedBg(Color.TRANSPARENT, 18, Color.TRANSPARENT, 0), PURPLE_LIGHT));
            }
            AppIconView icon = new AppIconView(icons[i], i == selectedTab ? PURPLE_LIGHT : MUTED, .78f);
            icon.setContentDescription(labels[i]);
            TextView label = tv(labels[i], 11, i == selectedTab ? PURPLE_LIGHT : MUTED, i == selectedTab);
            label.setGravity(Gravity.CENTER);
            item.addView(icon, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(28)));
            item.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(22)));
            item.setContentDescription(labels[i]);
            item.setClickable(true);
            item.setFocusable(true);
            item.setOnClickListener(v -> {
                showBedtimeConfig = false;
                selectedRecordId = -1L;
                selectedTab = index;
                render();
            });
            navBar.addView(item, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }
    }

    private View buildToday() {
        ScrollView scroll = baseScroll();
        LinearLayout page = pageContainer();
        scroll.addView(page);

        addBrand(page, true);
        long activeStart = prefs.getLong("active_start", 0L);
        Calendar now = Calendar.getInstance();
        int hour = now.get(Calendar.HOUR_OF_DAY);
        SleepRecord last = firstOrNull(db.getAll(1));

        if (activeStart > 0) {
            addTitle(page, "Dormindo", "Registro em andamento");
            int pct = (int)Math.min(100L, Math.max(0L, (System.currentTimeMillis() - activeStart) * 100L / (targetMinutes() * 60_000L)));
            progressRing = new ProgressRingView(this,
                    "00:00:00", "de sono", "desde " + formatClock(activeStart),
                    "Meta " + formatHoursMinutes(targetMinutes()) + "  •  previsão " + formatClock(activeStart + targetMinutes() * 60_000L), pct, true);
            page.addView(progressRing, matchMargin(dp(308), 2));

            LinearLayout infoRow = horizontal();
            infoRow.addView(smallStatCard("target", "Meta diária", formatHoursMinutes(targetMinutes())), weighted());
            infoRow.addView(space(dp(10)), new LinearLayout.LayoutParams(dp(10), 1));
            long forecast = activeStart + targetMinutes() * 60_000L;
            infoRow.addView(smallStatCard("clock", "Previsão", formatClock(forecast)), weighted());
            page.addView(infoRow, matchWrapMargin(12));

            TextView stop = primaryButton("■   Acordei");
            stop.setOnClickListener(v -> finishSleep());
            page.addView(stop, buttonMargin(12));

            TextView cancel = textButton("Cancelar registro");
            cancel.setOnClickListener(v -> confirmCancelTracking());
            page.addView(cancel, matchMargin(dp(48), 2));
            startTicker(activeStart);
        } else {
            String greeting = hour < 12 ? "Bom dia" : (hour < 18 ? "Boa tarde" : "Boa noite");
            addTitle(page, greeting, "Acompanhe seu sono");
            String duration = last == null ? "--" : formatDuration(last.durationMinutes());
            int pct = last == null ? 0 : Math.min(100, (int)Math.round(last.durationMinutes() * 100.0 / targetMinutes()));
            String range = last == null ? "Registre sua primeira noite" : shortNightDate(last.end) + "  •  " + formatClock(last.start) + " → " + formatClock(last.end);
            String goal = "Meta: " + formatHoursMinutes(targetMinutes());
            progressRing = new ProgressRingView(this, duration, last == null ? "de sono" : "na última noite", range, goal, pct, false);
            page.addView(progressRing, matchMargin(dp(308), 2));

            TextView start = primaryButton("▶   Iniciar sono");
            start.setOnClickListener(v -> {
                prefs.edit().putLong("active_start", System.currentTimeMillis()).apply();
                startSleepTrackingService();
                render();
            });
            page.addView(start, buttonMargin(12));

            TextView manual = secondaryButton("＋   Adicionar noite manualmente");
            manual.setOnClickListener(v -> showManualDialog());
            page.addView(manual, buttonMargin(8));

            List<SleepRecord> seven = recordsLastDays(7);
            LinearLayout statsRow = horizontal();
            statsRow.addView(smallStatCard("history", "Média semanal", seven.isEmpty() ? "--" : formatDuration(averageDuration(seven))), weighted());
            statsRow.addView(space(dp(10)), new LinearLayout.LayoutParams(dp(10), 1));
            statsRow.addView(smallStatCard("star", "Meta atingida", hitsTarget(seven) + " de " + seven.size() + " dias"), weighted());
            page.addView(statsRow, matchWrapMargin(16));

            if (last != null) {
                LinearLayout lastCard = card();
                lastCard.setClickable(true);
                lastCard.setFocusable(true);
                lastCard.setBackground(ripple(lastCard.getBackground(), PURPLE_LIGHT));
                LinearLayout heading = horizontal();
                FrameLayout moonBadge = iconBadge("moon", PURPLE_LIGHT, 30);
                heading.addView(moonBadge, new LinearLayout.LayoutParams(dp(32), dp(32)));
                TextView lastTitle = tv("Última noite", 17, TEXT, true);
                LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
                titleParams.leftMargin = dp(10);
                heading.addView(lastTitle, titleParams);
                heading.addView(new AppIconView("chevron", MUTED), new LinearLayout.LayoutParams(dp(22), dp(28)));
                lastCard.addView(heading, matchWrap(40));
                lastCard.addView(divider());
                lastCard.addView(detailRow("Deitei", formatClock(last.start), TEXT));
                lastCard.addView(detailRow("Levantei", formatClock(last.end), TEXT));
                lastCard.addView(detailRow("Qualidade", last.quality, qualityColor(last.quality)));
                lastCard.setOnClickListener(v -> {
                    selectedRecordId = last.id;
                    selectedTab = 1;
                    render();
                });
                page.addView(lastCard, matchWrapMargin(12));
            }
        }

        page.addView(space(dp(28)), new LinearLayout.LayoutParams(1, dp(28)));
        return scroll;
    }

    private View buildHistory() {
        ScrollView scroll = baseScroll();
        LinearLayout page = pageContainer();
        scroll.addView(page);
        addBrand(page);
        addTitle(page, "Histórico", "Seus registros de sono");

        LinearLayout filters = horizontal();
        String[] filterNames = {"Semana", "Mês", "Tudo"};
        for (int i = 0; i < filterNames.length; i++) {
            final int index = i;
            TextView chip = tv(filterNames[i], 14, historyFilter == i ? Color.WHITE : MUTED, historyFilter == i);
            chip.setGravity(Gravity.CENTER);
            chip.setBackground(ripple(roundedBg(historyFilter == i
                            ? (lightTheme ? Color.rgb(105, 77, 202) : Color.rgb(101, 81, 201))
                            : (lightTheme ? Color.rgb(240, 238, 247) : Color.argb(80, 25, 32, 61)), 22,
                    historyFilter == i ? Color.TRANSPARENT : DIVIDER, 1), PURPLE_LIGHT));
            chip.setOnClickListener(v -> { historyFilter = index; render(); });
            filters.addView(chip, new LinearLayout.LayoutParams(0, dp(42), 1f));
            if (i < 2) filters.addView(space(dp(7)), new LinearLayout.LayoutParams(dp(7), 1));
        }
        page.addView(filters, matchWrapMargin(12));

        List<SleepRecord> source = db.getAll(5000);
        List<SleepRecord> all = new ArrayList<>();
        long cutoff = Long.MIN_VALUE;
        if (historyFilter == 0) {
            Calendar c = Calendar.getInstance(); c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
            c.add(Calendar.DAY_OF_MONTH, -6); cutoff = c.getTimeInMillis();
        } else if (historyFilter == 1) {
            Calendar c = Calendar.getInstance(); c.set(Calendar.DAY_OF_MONTH, 1); c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
            cutoff = c.getTimeInMillis();
        }
        for (SleepRecord r : source) if (r.end >= cutoff) all.add(r);
        List<SleepRecord> summaryRecords = historyFilter == 2 ? source : all;
        LinearLayout summary = card();
        String summaryTitle = historyFilter == 2 ? "Média geral" : (historyFilter == 1 ? "Média do mês" : "Média da semana");
        summary.addView(iconHeading("history", summaryTitle, MUTED, 15), matchContent());
        summary.addView(tv(summaryRecords.isEmpty() ? "--" : formatDuration(averageDuration(summaryRecords)), 32, TEXT, true), matchWrap(48));
        TextView meta = tv("Meta em " + hitsTarget(summaryRecords) + " de " + summaryRecords.size() + " noites", 13, summaryRecords.isEmpty() ? MUTED : GREEN, false);
        meta.setPadding(0, dp(3), 0, dp(3));
        summary.addView(meta, matchContent());
        page.addView(summary, matchWrapMargin(8));

        TextView add = secondaryButton("＋   Adicionar noite manualmente");
        add.setOnClickListener(v -> showManualDialog());
        page.addView(add, buttonMargin(10));

        if (all.isEmpty()) {
            LinearLayout empty = card();
            empty.setGravity(Gravity.CENTER_HORIZONTAL);
            empty.addView(new AppIconView("moon", PURPLE_LIGHT), matchWrap(54));
            TextView t = tv("Seu histórico aparecerá aqui", 18, TEXT, true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(3), 0, dp(3));
            empty.addView(t, matchContent());
            TextView s = tv("Adicione uma noite ou inicie o cronômetro.", 14, MUTED, false);
            s.setGravity(Gravity.CENTER);
            s.setPadding(0, dp(6), 0, dp(8));
            empty.addView(s, matchContent());
            page.addView(empty, matchWrapMargin(16));
        } else {
            for (SleepRecord r : all) {
                LinearLayout c = card();
                c.setClickable(true); c.setFocusable(true);
                c.setBackground(ripple(c.getBackground(), PURPLE_LIGHT));
                LinearLayout top = horizontal();
                LinearLayout left = new LinearLayout(this);
                left.setOrientation(LinearLayout.VERTICAL);
                TextView dateLabel = tv(formatDate(r.end), 16, TEXT, true);
                dateLabel.setPadding(0, dp(2), 0, dp(3));
                left.addView(dateLabel, matchContent());
                TextView timeLabel = tv(formatClock(r.start) + "  →  " + formatClock(r.end), 14, MUTED, false);
                timeLabel.setPadding(0, dp(2), 0, dp(2));
                left.addView(timeLabel, matchContent());
                top.addView(left, weighted());
                TextView q = chip(r.quality, qualityColor(r.quality));
                top.addView(q, new LinearLayout.LayoutParams(dp(94), dp(36)));
                top.setMinimumHeight(dp(58));
                c.addView(top, matchContent());
                c.addView(divider());
                LinearLayout bottom = horizontal();
                bottom.addView(tv("Tempo de sono", 13, MUTED, false), weighted());
                TextView durationLabel = tv(formatDuration(r.durationMinutes()), 19, TEXT, true);
                durationLabel.setMaxLines(2);
                bottom.addView(durationLabel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                bottom.addView(new AppIconView("chevron", MUTED), new LinearLayout.LayoutParams(dp(24), dp(32)));
                bottom.setMinimumHeight(dp(50));
                c.addView(bottom, matchContent());
                if (r.note != null && !r.note.trim().isEmpty()) {
                    TextView preview = tv("“" + r.note.trim() + "”", 13, MUTED, false);
                    preview.setMaxLines(3);
                    preview.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    preview.setPadding(0, dp(6), 0, dp(3));
                    c.addView(preview, matchContent());
                }
                c.setOnClickListener(v -> { selectedRecordId = r.id; render(); });
                page.addView(c, matchWrapMargin(8));
            }
        }
        page.addView(space(dp(24)), new LinearLayout.LayoutParams(1, dp(24)));
        return scroll;
    }

    private View buildDetail() {
        SleepRecord record = db.getById(selectedRecordId);
        if (record == null) { selectedRecordId = -1L; return buildHistory(); }
        ScrollView scroll = baseScroll();
        LinearLayout page = pageContainer();
        scroll.addView(page);
        addBrand(page);
        TextView back = textButton("‹   Voltar ao histórico");
        back.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        back.setOnClickListener(v -> { selectedRecordId = -1L; render(); });
        page.addView(back, matchWrap(42));
        addTitle(page, "Detalhes da noite", formatDateLong(record.end));

        LinearLayout hero = card();
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.addView(new AppIconView("moon", PURPLE_LIGHT), matchWrap(40));
        TextView total = tv(formatDuration(record.durationMinutes()), 38, TEXT, true); total.setGravity(Gravity.CENTER);
        total.setMinHeight(dp(56));
        hero.addView(total, matchContent());
        TextView descriptor = tv("Tempo total de sono", 14, MUTED, false); descriptor.setGravity(Gravity.CENTER);
        hero.addView(descriptor, matchWrap(28));
        int pct = (int)Math.min(100, Math.round(record.durationMinutes() * 100.0 / targetMinutes()));
        TextView progressLabel = tv("Meta diária  •  " + pct + "%", 13, PURPLE_LIGHT, true);
        hero.addView(progressLabel, matchWrap(28));
        hero.addView(goalProgress(pct), matchMargin(dp(10), 4));
        page.addView(hero, matchWrapMargin(12));

        LinearLayout details = card();
        details.addView(iconHeading("moon", "Detalhes da noite", TEXT, 18), matchContent());
        details.addView(divider());
        details.addView(detailRow("Deitei", formatClock(record.start), TEXT));
        details.addView(divider());
        details.addView(detailRow("Levantei", formatClock(record.end), TEXT));
        details.addView(divider());
        details.addView(detailRow("Tempo na cama", formatDuration(record.durationMinutes()), TEXT));
        details.addView(divider());
        details.addView(detailRow("Meta diária", formatHoursMinutes(targetMinutes()), TEXT));
        details.addView(divider());
        details.addView(detailRow("Qualidade", record.quality, qualityColor(record.quality)));
        if (record.note != null && !record.note.trim().isEmpty()) {
            details.addView(divider());
            details.addView(tv("Observações", 13, MUTED, true), matchWrap(30));
            TextView note = tv(record.note.trim(), 15, TEXT, false);
            note.setGravity(Gravity.TOP | Gravity.START);
            note.setPadding(0, dp(5), 0, dp(6));
            details.addView(note, matchContent());
        }
        page.addView(details, matchWrapMargin(12));

        TextView edit = primaryButton("✎   Editar registro");
        edit.setOnClickListener(v -> showEditRecordDialog(record));
        page.addView(edit, buttonMargin(12));
        TextView delete = secondaryButton("Excluir registro"); delete.setTextColor(RED);
        delete.setOnClickListener(v -> confirmDelete(record.id));
        page.addView(delete, buttonMargin(8));
        page.addView(space(dp(24)), new LinearLayout.LayoutParams(1, dp(24)));
        return scroll;
    }

    private View buildStats() {
        ScrollView scroll = baseScroll();
        LinearLayout page = pageContainer();
        scroll.addView(page);
        addBrand(page);
        addTitle(page, "Estatísticas", "Tendências do seu sono");

        List<SleepRecord> all = db.getAll(30);
        List<SleepRecord> seven = recordsLastDays(7);
        SleepRecord best = null;
        for (SleepRecord r : all) if (best == null || r.durationMinutes() > best.durationMinutes()) best = r;

        LinearLayout row1 = horizontal();
        row1.addView(smallStatCard("history", "Média semanal", seven.isEmpty() ? "--" : formatDuration(averageDuration(seven))), weighted());
        row1.addView(space(dp(10)), new LinearLayout.LayoutParams(dp(10), 1));
        row1.addView(smallStatCard("star", "Melhor noite", best == null ? "--" : formatDuration(best.durationMinutes())), weighted());
        page.addView(row1, matchWrapMargin(8));

        LinearLayout row2 = horizontal();
        row2.addView(smallStatCard("moon", "Dorme às", seven.isEmpty() ? "--" : formatMinutesClock(averageBedtime(seven))), weighted());
        row2.addView(space(dp(10)), new LinearLayout.LayoutParams(dp(10), 1));
        row2.addView(smallStatCard("target", "Meta atingida", hitsTarget(seven) + " / " + seven.size()), weighted());
        page.addView(row2, matchWrapMargin(8));

        if (seven.size() >= 2) {
            LinearLayout regularity = card();
            regularity.addView(iconHeading("clock", "Regularidade do horário", MUTED, 15), matchContent());
            regularity.addView(tv("± " + bedtimeVariation(seven) + " min", 28, TEXT, true), matchWrap(42));
            TextView hint = tv("Menor valor indica maior regularidade.", 13, MUTED, false);
            hint.setPadding(0, dp(4), 0, dp(2));
            regularity.addView(hint, matchContent());
            page.addView(regularity, matchWrapMargin(8));
        }

        LinearLayout barCard = card();
        barCard.addView(iconHeading("history", "Últimos 7 registros", TEXT, 18), matchContent());
        SleepBarChartView bars = new SleepBarChartView(this, seven, targetMinutes());
        barCard.addView(bars, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(235)));
        page.addView(barCard, matchWrapMargin(14));

        LinearLayout lineCard = card();
        lineCard.addView(iconHeading("moon", "Horário de dormir", TEXT, 18), matchContent());
        BedtimeLineChartView line = new BedtimeLineChartView(this, seven);
        lineCard.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(230)));
        page.addView(lineCard, matchWrapMargin(8));

        if (all.size() >= 3) {
            long avg = averageDuration(seven);
            int diff = (int) (avg - targetMinutes());
            LinearLayout insight = card();
            insight.addView(iconHeading("star", "Insight", PURPLE_LIGHT, 17), matchContent());
            String msg;
            if (diff >= 0) msg = "Média semanal " + formatDuration(diff) + " acima da meta.";
            else msg = "Média semanal " + formatDuration(Math.abs(diff)) + " abaixo da meta.";
            TextView insightText = tv(msg, 14, MUTED, false);
            insightText.setPadding(0, dp(6), 0, dp(3));
            insight.addView(insightText, matchContent());
            page.addView(insight, matchWrapMargin(12));
        }

        page.addView(space(dp(24)), new LinearLayout.LayoutParams(1, dp(24)));
        return scroll;
    }

    private View buildBedtimeSettings() {
        ScrollView scroll = baseScroll();
        LinearLayout page = pageContainer();
        scroll.addView(page);
        addBrand(page);

        TextView back = textButton("‹   Voltar para Hoje");
        back.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        back.setOnClickListener(v -> {
            showBedtimeConfig = false;
            selectedTab = 0;
            render();
        });
        page.addView(back, matchWrap(42));
        addTitle(page, "Lembrete de dormir", "Defina quando precisa acordar.");
        addBedtimeConfiguration(page);
        page.addView(space(dp(24)), new LinearLayout.LayoutParams(1, dp(24)));
        return scroll;
    }

    private void addBedtimeConfiguration(LinearLayout page) {
        LinearLayout bedtimeToggle = settingToggle(
                "Aviso diário",
                "Calculado pela hora de acordar e sua meta de sono.",
                prefs.getBoolean(BedtimeReminderScheduler.PREF_ENABLED, false),
                (button, checked) -> handleBedtimeReminderSwitch(checked));
        page.addView(bedtimeToggle, matchWrapMargin(10));

        LinearLayout wakeTime = card();
        wakeTime.addView(tv("Hora de acordar", 17, TEXT, true), matchContent());
        TextView wakeButton = secondaryButton("Acordar às "
                + BedtimeReminderScheduler.formatTime(BedtimeReminderScheduler.wakeMinute(this)));
        wakeButton.setContentDescription("Alterar hora em que precisa acordar");
        TextView calculatedBedtime = tv(bedtimeSummary(), 14, PURPLE_LIGHT, true);
        calculatedBedtime.setPadding(0, dp(8), 0, dp(2));
        wakeButton.setOnClickListener(v -> {
            int current = BedtimeReminderScheduler.wakeMinute(this);
            new TimePickerDialog(this, (picker, hour, minute) -> {
                int wakeMinute = hour * 60 + minute;
                prefs.edit().putInt(BedtimeReminderScheduler.PREF_WAKE_MINUTE, wakeMinute).apply();
                wakeButton.setText("Acordar às " + BedtimeReminderScheduler.formatTime(wakeMinute));
                calculatedBedtime.setText(bedtimeSummary());
                BedtimeReminderScheduler.scheduleNext(this);
            }, current / 60, current % 60, true).show();
        });
        wakeTime.addView(wakeButton, buttonMargin(10));
        wakeTime.addView(calculatedBedtime, matchContent());
        page.addView(wakeTime, matchWrapMargin(10));
    }

    private View buildSettings() {
        ScrollView scroll = baseScroll();
        LinearLayout page = pageContainer();
        scroll.addView(page);
        addBrand(page);
        addTitle(page, "Ajustes", "Preferências do aplicativo");

        LinearLayout appearance = card();
        appearance.addView(tv("Aparência", 18, TEXT, true), matchContent());
        LinearLayout themeChoices = horizontal();
        String[] themeLabels = {"Sistema", "Claro", "Escuro"};
        for (int i = 0; i < themeLabels.length; i++) {
            final int mode = i;
            boolean selected = themeMode == mode;
            TextView option = tv(themeLabels[i], 13, selected ? (lightTheme ? PURPLE : PURPLE_LIGHT) : MUTED, true);
            option.setGravity(Gravity.CENTER);
            option.setPadding(dp(4), 0, dp(4), 0);
            option.setBackground(roundedBg(
                    selected ? (lightTheme ? Color.rgb(237, 232, 255) : Color.rgb(49, 42, 97)) : SURFACE_2,
                    18, selected ? PURPLE : DIVIDER, 1));
            option.setOnClickListener(v -> {
                prefs.edit().putInt("theme_mode", mode).apply();
                selectedTab = 3;
                recreate();
            });
            LinearLayout.LayoutParams optionParams = new LinearLayout.LayoutParams(0, dp(42), 1f);
            if (i > 0) optionParams.leftMargin = dp(8);
            themeChoices.addView(option, optionParams);
        }
        appearance.addView(themeChoices, matchWrapMargin(8));
        page.addView(appearance, matchWrapMargin(8));

        LinearLayout screenCard = settingToggle(
                "Tela sempre ativa",
                "Mantém a tela ligada durante o registro com o app aberto.",
                prefs.getBoolean("keep_screen_on", false),
                (button, checked) -> {
                    prefs.edit().putBoolean("keep_screen_on", checked).apply();
                    updateKeepScreenOn();
                });
        page.addView(screenCard, matchWrapMargin(10));

        LinearLayout backgroundCard = settingToggle(
                "Acompanhamento em segundo plano",
                "Mostra uma notificação contínua enquanto o sono é registrado.",
                backgroundTrackingEnabled(),
                (button, checked) -> handleBackgroundTrackingSwitch(checked));
        page.addView(backgroundCard, matchWrapMargin(10));

        LinearLayout target = card();
        target.addView(tv("Meta diária de sono", 18, TEXT, true), matchContent());
        TextView value = tv(formatHoursMinutes(targetMinutes()), 31, PURPLE_LIGHT, true);
        target.addView(value, matchWrap(50));
        TextView helper = tv("De 6h a 10h", 13, MUTED, false);
        target.addView(helper, matchWrap(28));
        SeekBar seek = new SeekBar(this);
        seek.setMax(8);
        seek.setProgress(Math.max(0, Math.min(8, (targetMinutes() - 360) / 30)));
        seek.setProgressTintList(android.content.res.ColorStateList.valueOf(PURPLE));
        seek.setThumbTintList(android.content.res.ColorStateList.valueOf(PURPLE_LIGHT));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int mins = 360 + progress * 30;
                value.setText(formatHoursMinutes(mins));
                if (fromUser) prefs.edit().putInt("target_minutes", mins).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                BedtimeReminderScheduler.scheduleNext(MainActivity.this);
                render();
            }
        });
        target.addView(seek, matchWrap(48));
        page.addView(target, matchWrapMargin(8));

        LinearLayout backup = card();
        backup.addView(tv("Backup e restauração", 18, TEXT, true), matchContent());
        TextView backupHint = tv("Salve seus registros e ajustes em um arquivo.", 13, MUTED, false);
        backupHint.setPadding(0, dp(3), 0, dp(4));
        backup.addView(backupHint, matchContent());
        TextView saveBackup = secondaryButton("↓   Criar backup");
        saveBackup.setOnClickListener(v -> chooseBackupLocation());
        backup.addView(saveBackup, buttonMargin(8));
        TextView restoreBackup = secondaryButton("↶   Restaurar backup");
        restoreBackup.setOnClickListener(v -> chooseBackupToRestore());
        backup.addView(restoreBackup, buttonMargin(8));
        page.addView(backup, matchWrapMargin(10));

        LinearLayout storage = card();
        storage.addView(tv("Dados e privacidade", 18, TEXT, true), matchContent());
        TextView storageHint = tv("Dados no aparelho, sem conta ou envio a servidores.", 13, MUTED, false);
        storageHint.setPadding(0, dp(3), 0, dp(4));
        storage.addView(storageHint, matchContent());
        TextView export = secondaryButton("↗   Exportar CSV");
        export.setOnClickListener(v -> exportCsv());
        storage.addView(export, buttonMargin(10));
        TextView clear = secondaryButton("Apagar todos os registros");
        clear.setTextColor(RED);
        clear.setOnClickListener(v -> confirmClear());
        storage.addView(clear, buttonMargin(8));
        page.addView(storage, matchWrapMargin(10));

        LinearLayout about = card();
        about.addView(tv("Meu Sono", 19, TEXT, true), matchContent());
        about.addView(tv("Versão 1.7.0", 13, MUTED, false), matchWrap(26));
        TextView aboutHint = tv("Estatísticas pessoais de sono; não fazem diagnóstico.", 13, MUTED, false);
        aboutHint.setPadding(0, dp(4), 0, dp(2));
        about.addView(aboutHint, matchContent());
        page.addView(about, matchWrapMargin(8));
        page.addView(space(dp(24)), new LinearLayout.LayoutParams(1, dp(24)));
        return scroll;
    }

    private LinearLayout settingToggle(String title, String description, boolean checked,
                                       CompoundButton.OnCheckedChangeListener listener) {
        LinearLayout c = card();
        c.setClickable(true);
        c.setFocusable(true);

        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = tv(title, 16, TEXT, true);
        titleView.setMinHeight(dp(28));
        titleView.setPadding(0, 0, 0, dp(4));
        copy.addView(titleView, matchContent());
        TextView helper = tv(description, 12, MUTED, false);
        helper.setGravity(Gravity.TOP);
        copy.addView(helper, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch control = new Switch(this);
        control.setContentDescription(title);
        control.setChecked(checked);
        int[][] states = new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{-android.R.attr.state_checked}
        };
        control.setThumbTintList(new ColorStateList(states, new int[]{Color.WHITE, Color.rgb(218, 216, 226)}));
        control.setTrackTintList(new ColorStateList(states, new int[]{
                lightTheme ? Color.rgb(157, 135, 224) : Color.rgb(112, 91, 177),
                lightTheme ? Color.rgb(205, 202, 214) : Color.rgb(69, 74, 97)
        }));
        control.setOnCheckedChangeListener(listener);
        LinearLayout.LayoutParams switchParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        switchParams.leftMargin = dp(10);
        row.addView(control, switchParams);
        c.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        c.setOnClickListener(v -> control.setChecked(!control.isChecked()));
        return c;
    }

    private void handleBackgroundTrackingSwitch(boolean enabled) {
        if (!enabled) {
            prefs.edit().putBoolean("background_tracking", false).apply();
            stopSleepTrackingService();
            Toast.makeText(this, "Acompanhamento em segundo plano desativado", Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pendingBackgroundEnable = true;
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
            return;
        }
        enableBackgroundTracking();
    }

    private void handleBedtimeReminderSwitch(boolean enabled) {
        if (!enabled) {
            prefs.edit().putBoolean(BedtimeReminderScheduler.PREF_ENABLED, false).apply();
            BedtimeReminderScheduler.cancel(this);
            Toast.makeText(this, "Lembrete de dormir desativado", Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pendingBedtimeEnable = true;
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
            return;
        }
        enableBedtimeReminder();
    }

    private void enableBedtimeReminder() {
        prefs.edit().putBoolean(BedtimeReminderScheduler.PREF_ENABLED, true).apply();
        BedtimeReminderScheduler.scheduleNext(this);
        Toast.makeText(this, "Aviso diário ativado para "
                + BedtimeReminderScheduler.formatTime(BedtimeReminderScheduler.bedtimeMinute(this)), Toast.LENGTH_LONG).show();
    }

    private void enableBackgroundTracking() {
        prefs.edit().putBoolean("background_tracking", true).apply();
        if (isSleepActive()) startSleepTrackingService();
        Toast.makeText(this, isSleepActive()
                ? "Acompanhamento em segundo plano ativado"
                : "Será ativado ao iniciar o próximo registro", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_NOTIFICATIONS) return;
        boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (pendingBackgroundEnable) {
            pendingBackgroundEnable = false;
            if (granted) enableBackgroundTracking();
            else {
                prefs.edit().putBoolean("background_tracking", false).apply();
                Toast.makeText(this, "Permita notificações para manter o aviso do registro ativo.", Toast.LENGTH_LONG).show();
            }
        }
        if (pendingBedtimeEnable) {
            pendingBedtimeEnable = false;
            if (granted) enableBedtimeReminder();
            else {
                prefs.edit().putBoolean(BedtimeReminderScheduler.PREF_ENABLED, false).apply();
                BedtimeReminderScheduler.cancel(this);
                Toast.makeText(this, "Permita notificações para receber o lembrete de dormir.", Toast.LENGTH_LONG).show();
            }
        }
        render();
    }

    private void addBrand(LinearLayout page) {
        addBrand(page, false);
    }

    private void addBrand(LinearLayout page, boolean showBedtimeButton) {
        LinearLayout row = horizontal();
        AppIconView moon = new AppIconView("moon", PURPLE_LIGHT, .64f);
        row.addView(moon, new LinearLayout.LayoutParams(dp(42), dp(42)));
        TextView brand = tv("Meu Sono", 22, TEXT, true);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(brand, weighted());
        if (showBedtimeButton) {
            boolean enabled = prefs.getBoolean(BedtimeReminderScheduler.PREF_ENABLED, false);
            FrameLayout reminder = new FrameLayout(this);
            int fill = enabled
                    ? (lightTheme ? Color.rgb(238, 232, 255) : Color.rgb(54, 47, 105))
                    : SURFACE_2;
            reminder.setBackground(ripple(roundedBg(fill, 15, enabled ? PURPLE : DIVIDER, 1), PURPLE_LIGHT));
            reminder.setClickable(true);
            reminder.setFocusable(true);
            reminder.setContentDescription(enabled
                    ? "Lembrete de dormir ativado. Configurar"
                    : "Configurar lembrete de dormir");
            AppIconView bell = new AppIconView("bell", enabled ? PURPLE_LIGHT : MUTED, .72f);
            reminder.addView(bell, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            if (enabled) {
                View indicator = new View(this);
                indicator.setBackground(roundedBg(GREEN, 10, fill, 1));
                FrameLayout.LayoutParams indicatorParams = new FrameLayout.LayoutParams(dp(8), dp(8), Gravity.TOP | Gravity.END);
                indicatorParams.topMargin = dp(3);
                indicatorParams.rightMargin = dp(3);
                reminder.addView(indicator, indicatorParams);
            }
            reminder.setOnClickListener(v -> {
                selectedTab = 0;
                selectedRecordId = -1L;
                showBedtimeConfig = true;
                render();
            });
            LinearLayout.LayoutParams reminderParams = new LinearLayout.LayoutParams(dp(44), dp(44));
            reminderParams.leftMargin = dp(8);
            row.addView(reminder, reminderParams);
        }
        page.addView(row, matchWrap(52));
    }

    private void addTitle(LinearLayout page, String title, String subtitle) {
        TextView heading = tv(title, 34, TEXT, true);
        heading.setMinHeight(dp(50));
        heading.setPadding(0, dp(2), 0, dp(2));
        page.addView(heading, matchContent());
        TextView hint = tv(subtitle, 16, MUTED, false);
        hint.setMinHeight(dp(34));
        hint.setPadding(0, 0, 0, dp(4));
        page.addView(hint, matchContent());
    }

    private FrameLayout iconBadge(String icon, int tint, int sizeDp) {
        FrameLayout badge = new FrameLayout(this);
        badge.setMinimumWidth(dp(sizeDp));
        badge.setMinimumHeight(dp(sizeDp));
        badge.setBackground(roundedGradient(
                lightTheme
                        ? new int[]{Color.rgb(240, 236, 255), Color.rgb(229, 223, 250)}
                        : new int[]{Color.rgb(59, 51, 121), Color.rgb(43, 42, 94)},
                24, Color.TRANSPARENT, 0));
        AppIconView glyph = new AppIconView(icon, tint, .64f);
        badge.addView(glyph, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return badge;
    }

    private LinearLayout iconHeading(String icon, String title, int color, int textSize) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(30));
        row.addView(new AppIconView(icon, PURPLE_LIGHT, .82f), new LinearLayout.LayoutParams(dp(22), dp(24)));
        TextView label = tv(title, textSize, color, true);
        label.setPadding(0, dp(2), 0, dp(2));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelParams.leftMargin = dp(8);
        row.addView(label, labelParams);
        return row;
    }

    private String iconForDetail(String label) {
        if (label == null) return "moon";
        if (label.contains("Deitei")) return "bed";
        if (label.contains("Levantei")) return "sun";
        if (label.contains("Qualidade")) return "star";
        return "clock";
    }

    private LinearLayout smallStatCard(String icon, String label, String value) {
        LinearLayout c = card();
        c.setMinimumHeight(dp(108));
        c.setPadding(dp(12), dp(12), dp(12), dp(12));

        LinearLayout header = horizontal();
        FrameLayout badge = iconBadge(icon, PURPLE_LIGHT, 34);
        header.addView(badge, new LinearLayout.LayoutParams(dp(34), dp(34)));
        TextView caption = tv(label, 12, MUTED, false);
        caption.setMaxLines(2);
        LinearLayout.LayoutParams captionParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        captionParams.leftMargin = dp(8);
        header.addView(caption, captionParams);
        c.addView(header, matchContent());

        TextView amount = tv(value, 18, TEXT, true);
        amount.setMinHeight(dp(32));
        amount.setMaxLines(2);
        amount.setGravity(Gravity.BOTTOM | Gravity.START);
        if (Build.VERSION.SDK_INT >= 26) {
            amount.setAutoSizeTextTypeUniformWithConfiguration(15, 18, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        }
        LinearLayout.LayoutParams amountParams = matchContent();
        amountParams.topMargin = dp(7);
        c.addView(amount, amountParams);
        return c;
    }

    private View detailRow(String label, String value, int valueColor) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));
        row.setMinimumHeight(dp(48));
        FrameLayout icon = iconBadge(iconForDetail(label), PURPLE_LIGHT, 30);
        row.addView(icon, new LinearLayout.LayoutParams(dp(30), dp(30)));
        TextView l = tv(label, 14, MUTED, false);
        l.setMaxLines(2);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelParams.leftMargin = dp(10);
        row.addView(l, labelParams);
        TextView v = tv(value, 15, valueColor, true);
        v.setMaxLines(2);
        v.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        row.addView(v, new LinearLayout.LayoutParams(dp(112), ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private void startTicker(long start) {
        ticker = new Runnable() {
            @Override public void run() {
                if (progressRing != null) progressRing.updateElapsed(start);
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(ticker);
    }

    private void stopTicker() {
        if (ticker != null) handler.removeCallbacks(ticker);
        ticker = null;
        progressRing = null;
    }

    private void finishSleep() {
        long start = prefs.getLong("active_start", 0L);
        if (start <= 0) return;
        long end = System.currentTimeMillis();
        if (end <= start) return;
        long id = db.insert(start, end, "Bom", "");
        prefs.edit().remove("active_start").apply();
        stopSleepTrackingService();
        updateKeepScreenOn();
        showQualityDialog(id);
    }

    private void confirmCancelTracking() {
        new AlertDialog.Builder(this)
                .setTitle("Cancelar registro?")
                .setMessage("O cronômetro atual será descartado.")
                .setNegativeButton("Voltar", null)
                .setPositiveButton("Cancelar registro", (d, w) -> {
                    prefs.edit().remove("active_start").apply();
                    stopSleepTrackingService();
                    render();
                }).show();
    }

    private void showManualDialog() {
        Calendar now = Calendar.getInstance();
        int currentMins = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        final int[] startMins = {23 * 60};
        final int[] endMins = {Math.min(currentMins, 8 * 60)};
        if (endMins[0] < 60) endMins[0] = currentMins;

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(10), dp(16), dp(10));
        box.setBackground(dialogPanelBackground(20));

        TextView info = tv("Informe a data em que levantou e os horários. Se deitou mais tarde, consideramos o dia anterior.", 13, MUTED, false);
        info.setPadding(0, dp(4), 0, dp(12));
        box.addView(info, matchContent());
        final Calendar wakeDate = Calendar.getInstance();
        TextView dateBtn = secondaryButton("Data: " + new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(wakeDate.getTime()));
        dateBtn.setOnClickListener(v -> new DatePickerDialog(this, (view, year, month, day) -> {
            wakeDate.set(Calendar.YEAR, year);
            wakeDate.set(Calendar.MONTH, month);
            wakeDate.set(Calendar.DAY_OF_MONTH, day);
            dateBtn.setText("Data: " + new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(wakeDate.getTime()));
        }, wakeDate.get(Calendar.YEAR), wakeDate.get(Calendar.MONTH), wakeDate.get(Calendar.DAY_OF_MONTH)).show());
        box.addView(dateBtn, matchWrap(52));
        TextView startBtn = secondaryButton("Deitei: " + formatMinutesClock(startMins[0]));
        TextView endBtn = secondaryButton("Levantei: " + formatMinutesClock(endMins[0]));
        box.addView(startBtn, matchWrap(52));
        box.addView(endBtn, matchWrap(52));

        startBtn.setOnClickListener(v -> new TimePickerDialog(this, (view, h, m) -> {
            startMins[0] = h * 60 + m;
            startBtn.setText("Deitei: " + formatMinutesClock(startMins[0]));
        }, startMins[0] / 60, startMins[0] % 60, true).show());
        endBtn.setOnClickListener(v -> new TimePickerDialog(this, (view, h, m) -> {
            endMins[0] = h * 60 + m;
            endBtn.setText("Levantei: " + formatMinutesClock(endMins[0]));
        }, endMins[0] / 60, endMins[0] % 60, true).show());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Adicionar noite manualmente")
                .setView(box)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Salvar", null)
                .create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            Calendar end = (Calendar) wakeDate.clone();
            end.set(Calendar.HOUR_OF_DAY, endMins[0] / 60);
            end.set(Calendar.MINUTE, endMins[0] % 60);
            end.set(Calendar.SECOND, 0);
            end.set(Calendar.MILLISECOND, 0);
            Calendar start = (Calendar) end.clone();
            start.set(Calendar.HOUR_OF_DAY, startMins[0] / 60);
            start.set(Calendar.MINUTE, startMins[0] % 60);
            if (startMins[0] >= endMins[0]) start.add(Calendar.DAY_OF_MONTH, -1);
            long duration = end.getTimeInMillis() - start.getTimeInMillis();
            if (duration <= 0 || duration > 24L * 60L * 60L * 1000L || end.getTimeInMillis() > System.currentTimeMillis()) {
                Toast.makeText(this, "Confira os horários informados.", Toast.LENGTH_SHORT).show();
                return;
            }
            long id = db.insert(start.getTimeInMillis(), end.getTimeInMillis(), "Bom", "");
            dialog.dismiss();
            showQualityDialog(id);
        }));
        dialog.show();
    }

    private void showQualityDialog(long recordId) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(20), dp(20), dp(18));
        box.setBackground(dialogPanelBackground(24));
        box.addView(tv("Registro concluído", 23, TEXT, true), matchWrap(38));
        box.addView(tv("Como você dormiu?", 15, MUTED, false), matchWrap(34));
        QualityPicker picker = new QualityPicker("Bom");
        box.addView(picker, matchWrap(76));

        EditText note = new EditText(this);
        note.setHint("Observações (opcional)");
        note.setTextColor(TEXT);
        note.setHintTextColor(MUTED);
        note.setBackground(roundedBg(SURFACE_2, 14, DIVIDER, 1));
        note.setPadding(dp(12), dp(10), dp(12), dp(10));
        note.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        note.setMinLines(2);
        note.setMaxLines(4);
        box.addView(note, matchWrap(88));
        TextView save = primaryButton("Salvar noite");
        save.setOnClickListener(v -> {
            db.update(recordId, picker.getQuality(), note.getText().toString().trim());
            Toast.makeText(this, "Noite salva no histórico", Toast.LENGTH_SHORT).show();
            Dialog d = (Dialog)box.getTag(); if (d != null) d.dismiss();
        });
        box.addView(save, matchMargin(dp(50), 14));
        TextView later = textButton("Agora não");
        later.setOnClickListener(v -> { Dialog d = (Dialog)box.getTag(); if (d != null) d.dismiss(); });
        box.addView(later, matchMargin(dp(42), 2));

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(box);
        box.setTag(dialog);
        dialog.setCancelable(true);
        dialog.setOnDismissListener(d -> render());
        styleDialogWindow(dialog);
        dialog.show();
        styleDialogWindow(dialog);
    }

    private void styleDialogWindow(Dialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams lp = window.getAttributes();
        lp.width = Math.min(getResources().getDisplayMetrics().widthPixels - dp(28), dp(440));
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        lp.dimAmount = .72f;
        window.setAttributes(lp);
    }

    private GradientDrawable dialogPanelBackground(int radius) {
        return roundedGradient(
                lightTheme
                        ? new int[]{Color.rgb(255, 255, 255), Color.rgb(246, 244, 251)}
                        : new int[]{Color.rgb(22, 29, 56), Color.rgb(11, 17, 38)},
                radius, DIVIDER, 1);
    }

    private String qualityEmoji(String quality) {
        if ("Péssimo".equals(quality)) return "😫";
        if ("Ruim".equals(quality)) return "😕";
        if ("Regular".equals(quality)) return "😐";
        if ("Excelente".equals(quality)) return "😴";
        return "🙂";
    }

    private class QualityPicker extends LinearLayout {
        private final String[] choices = {"Péssimo", "Ruim", "Regular", "Bom", "Excelente"};
        private final TextView[] cells = new TextView[choices.length];
        private String selected;

        QualityPicker(String initial) {
            super(MainActivity.this);
            selected = initial == null ? "Bom" : initial;
            setOrientation(HORIZONTAL);
            for (int i = 0; i < choices.length; i++) {
                final String value = choices[i];
                TextView cell = tv(qualityEmoji(value) + "\n" + value, 10, MUTED, false);
                cell.setGravity(Gravity.CENTER);
                cell.setPadding(dp(2), dp(5), dp(2), dp(5));
                cell.setOnClickListener(v -> { selected = value; refresh(); });
                cells[i] = cell;
                LayoutParams params = new LayoutParams(0, dp(68), 1f);
                if (i > 0) params.leftMargin = dp(4);
                addView(cell, params);
            }
            refresh();
        }

        String getQuality() { return selected; }

        private void refresh() {
            for (int i = 0; i < choices.length; i++) {
                boolean active = choices[i].equals(selected);
                cells[i].setTextColor(active ? PURPLE_LIGHT : MUTED);
                cells[i].setTypeface(Typeface.create("sans", active ? Typeface.BOLD : Typeface.NORMAL));
                int activeFill = lightTheme ? Color.rgb(237, 232, 255) : Color.rgb(49, 42, 97);
                int idleFill = lightTheme ? Color.rgb(243, 241, 249) : Color.argb(95, 25, 33, 63);
                cells[i].setBackground(roundedBg(active ? activeFill : idleFill, 14,
                        active ? PURPLE : DIVIDER, active ? 2 : 1));
            }
        }
    }

    private void showEditRecordDialog(SleepRecord record) {
        Calendar endCal = Calendar.getInstance();
        endCal.setTimeInMillis(record.end);
        Calendar startCal = Calendar.getInstance();
        startCal.setTimeInMillis(record.start);
        final int[] startMins = {startCal.get(Calendar.HOUR_OF_DAY) * 60 + startCal.get(Calendar.MINUTE)};
        final int[] endMins = {endCal.get(Calendar.HOUR_OF_DAY) * 60 + endCal.get(Calendar.MINUTE)};
        final Calendar wakeDate = (Calendar) endCal.clone();

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(8), dp(18), dp(8));
        box.setBackground(dialogPanelBackground(20));

        TextView dateBtn = secondaryButton("Data: " + new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(wakeDate.getTime()));
        dateBtn.setOnClickListener(v -> new DatePickerDialog(this, (view, y, m, d) -> {
            wakeDate.set(Calendar.YEAR, y); wakeDate.set(Calendar.MONTH, m); wakeDate.set(Calendar.DAY_OF_MONTH, d);
            dateBtn.setText("Data: " + new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(wakeDate.getTime()));
        }, wakeDate.get(Calendar.YEAR), wakeDate.get(Calendar.MONTH), wakeDate.get(Calendar.DAY_OF_MONTH)).show());
        box.addView(dateBtn, matchWrap(50));

        TextView startBtn = secondaryButton("Deitei: " + formatMinutesClock(startMins[0]));
        startBtn.setOnClickListener(v -> new TimePickerDialog(this, (view, h, m) -> {
            startMins[0] = h * 60 + m; startBtn.setText("Deitei: " + formatMinutesClock(startMins[0]));
        }, startMins[0] / 60, startMins[0] % 60, true).show());
        box.addView(startBtn, matchWrap(50));

        TextView endBtn = secondaryButton("Levantei: " + formatMinutesClock(endMins[0]));
        endBtn.setOnClickListener(v -> new TimePickerDialog(this, (view, h, m) -> {
            endMins[0] = h * 60 + m; endBtn.setText("Levantei: " + formatMinutesClock(endMins[0]));
        }, endMins[0] / 60, endMins[0] % 60, true).show());
        box.addView(endBtn, matchWrap(50));

        box.addView(tv("Qualidade do sono", 13, MUTED, true), matchWrap(28));
        QualityPicker picker = new QualityPicker(record.quality);
        box.addView(picker, matchWrap(76));

        EditText note = new EditText(this);
        note.setText(record.note == null ? "" : record.note);
        note.setHint("Observações"); note.setTextColor(TEXT); note.setHintTextColor(MUTED);
        note.setBackground(roundedBg(SURFACE_2, 14, DIVIDER, 1)); note.setPadding(dp(12), dp(10), dp(12), dp(10));
        note.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        box.addView(note, matchWrap(86));

        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Editar registro").setView(box)
                .setNegativeButton("Cancelar", null).setPositiveButton("Salvar", null).create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            Calendar end = (Calendar) wakeDate.clone();
            end.set(Calendar.HOUR_OF_DAY, endMins[0] / 60); end.set(Calendar.MINUTE, endMins[0] % 60); end.set(Calendar.SECOND, 0); end.set(Calendar.MILLISECOND, 0);
            Calendar start = (Calendar) end.clone();
            start.set(Calendar.HOUR_OF_DAY, startMins[0] / 60); start.set(Calendar.MINUTE, startMins[0] % 60);
            if (startMins[0] >= endMins[0]) start.add(Calendar.DAY_OF_MONTH, -1);
            long duration = end.getTimeInMillis() - start.getTimeInMillis();
            if (duration <= 0 || duration > 24L * 60L * 60L * 1000L || end.getTimeInMillis() > System.currentTimeMillis()) { Toast.makeText(this, "Confira os horários.", Toast.LENGTH_SHORT).show(); return; }
            db.updateFull(record.id, start.getTimeInMillis(), end.getTimeInMillis(), picker.getQuality(), note.getText().toString().trim());
            dialog.dismiss(); render();
        }));
        dialog.show();
    }

    private View goalProgress(int pct) {
        int safe = Math.max(0, Math.min(100, pct));
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackground(roundedBg(SURFACE_2, 8, Color.TRANSPARENT, 0));
        View fill = new View(this);
        fill.setBackground(roundedBg(PURPLE, 8, Color.TRANSPARENT, 0));
        bar.addView(fill, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, safe));
        View rest = new View(this);
        bar.addView(rest, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 100 - safe));
        return bar;
    }

    private int bedtimeVariation(List<SleepRecord> list) {
        if (list.size() < 2) return 0;
        int avg = averageBedtime(list);
        long total = 0;
        for (SleepRecord r : list) {
            Calendar c = Calendar.getInstance(); c.setTimeInMillis(r.start);
            int mins = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
            int diff = Math.abs(mins - avg); diff = Math.min(diff, 1440 - diff);
            total += diff;
        }
        return (int)Math.round(total / (double)list.size());
    }

    private void confirmDelete(long id) {
        new AlertDialog.Builder(this)
                .setTitle("Excluir registro?")
                .setMessage("Esta ação não pode ser desfeita.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Excluir", (d, w) -> {
                    db.delete(id);
                    render();
                }).show();
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle("Excluir todo o histórico?")
                .setMessage("Todos os registros de sono serão apagados deste aparelho.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Excluir tudo", (d, w) -> {
                    db.clear();
                    Toast.makeText(this, "Histórico apagado", Toast.LENGTH_SHORT).show();
                    render();
                }).show();
    }

    private void exportCsv() {
        List<SleepRecord> all = db.getAll(5000);
        if (all.isEmpty()) {
            Toast.makeText(this, "Não há registros para exportar.", Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("data,deitei,levantei,duracao_minutos,qualidade,observacoes\n");
        SimpleDateFormat day = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        for (SleepRecord r : all) {
            sb.append(day.format(new Date(r.end))).append(',')
                    .append(formatClock(r.start)).append(',')
                    .append(formatClock(r.end)).append(',')
                    .append(r.durationMinutes()).append(',')
                    .append(csv(r.quality)).append(',')
                    .append(csv(r.note)).append('\n');
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/csv");
        send.putExtra(Intent.EXTRA_SUBJECT, "Registros do Meu Sono");
        send.putExtra(Intent.EXTRA_TEXT, sb.toString());
        startActivity(Intent.createChooser(send, "Compartilhar registros"));
    }

    private String csv(String value) {
        if (value == null) return "\"\"";
        return "\"" + value.replace("\"", "\"\"").replace("\n", " ") + "\"";
    }

    private int targetMinutes() {
        return prefs.getInt("target_minutes", 8 * 60);
    }

    private String bedtimeSummary() {
        return "Aviso às " + BedtimeReminderScheduler.formatTime(BedtimeReminderScheduler.bedtimeMinute(this))
                + "  •  meta " + formatHoursMinutes(targetMinutes());
    }

    private List<SleepRecord> recordsLastDays(int days) {
        Calendar from = Calendar.getInstance();
        from.set(Calendar.HOUR_OF_DAY, 0); from.set(Calendar.MINUTE, 0); from.set(Calendar.SECOND, 0); from.set(Calendar.MILLISECOND, 0);
        from.add(Calendar.DAY_OF_MONTH, -(Math.max(1, days) - 1));
        long start = from.getTimeInMillis(), now = System.currentTimeMillis();
        List<SleepRecord> matches = new ArrayList<>();
        for (SleepRecord r : db.getAll(5000)) if (r.end >= start && r.end <= now) matches.add(r);
        return matches;
    }

    private long averageDuration(List<SleepRecord> list) {
        if (list.isEmpty()) return 0;
        long total = 0;
        for (SleepRecord r : list) total += r.durationMinutes();
        return Math.round(total / (double) list.size());
    }

    private int hitsTarget(List<SleepRecord> list) {
        int count = 0;
        int target = targetMinutes();
        for (SleepRecord r : list) if (r.durationMinutes() >= target) count++;
        return count;
    }

    private int averageBedtime(List<SleepRecord> list) {
        if (list.isEmpty()) return 0;
        long total = 0;
        for (SleepRecord r : list) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(r.start);
            int mins = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
            if (mins < 12 * 60) mins += 24 * 60;
            total += mins;
        }
        return (int) Math.round(total / (double) list.size()) % (24 * 60);
    }

    private String formatDuration(long minutes) {
        long m = Math.max(0, minutes);
        long h = m / 60;
        long rem = m % 60;
        if (h == 0) return rem + "min";
        if (rem == 0) return h + "h";
        return h + "h " + rem + "min";
    }

    private String formatHoursMinutes(int minutes) {
        return formatDuration(minutes);
    }

    private String formatClock(long timestamp) {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(timestamp));
    }

    private String formatMinutesClock(int mins) {
        int m = ((mins % 1440) + 1440) % 1440;
        return String.format(Locale.getDefault(), "%02d:%02d", m / 60, m % 60);
    }

    private String formatDate(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM", new Locale("pt", "BR"));
        String s = sdf.format(new Date(timestamp));
        return s.substring(0, 1).toUpperCase(Locale.getDefault()) + s.substring(1);
    }

    private String shortNightDate(long timestamp) {
        Calendar midnight = Calendar.getInstance();
        midnight.set(Calendar.HOUR_OF_DAY, 0);
        midnight.set(Calendar.MINUTE, 0);
        midnight.set(Calendar.SECOND, 0);
        midnight.set(Calendar.MILLISECOND, 0);
        if (timestamp >= midnight.getTimeInMillis()) return "Hoje";
        midnight.add(Calendar.DAY_OF_MONTH, -1);
        if (timestamp >= midnight.getTimeInMillis()) return "Ontem";
        return formatDate(timestamp);
    }

    private String formatDateLong(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("EEEE, dd 'de' MMMM", new Locale("pt", "BR"));
        String s = sdf.format(new Date(timestamp));
        return s.substring(0, 1).toUpperCase(new Locale("pt", "BR")) + s.substring(1);
    }

    private int qualityColor(String q) {
        if (q == null) return MUTED;
        if (q.equals("Excelente") || q.equals("Bom")) return GREEN;
        if (q.equals("Regular")) return YELLOW;
        return RED;
    }

    private SleepRecord firstOrNull(List<SleepRecord> list) {
        return list.isEmpty() ? null : list.get(0);
    }

    private ScrollView baseScroll() {
        ScrollView s = new ScrollView(this);
        s.setFillViewport(true);
        s.setBackgroundColor(Color.TRANSPARENT);
        s.setClipToPadding(false);
        return s;
    }

    private LinearLayout pageContainer() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(16), dp(18), dp(16));
        page.setBackgroundColor(Color.TRANSPARENT);
        return page;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(15), dp(16), dp(15));
        c.setBackground(roundedGradient(
                lightTheme
                        ? new int[]{Color.rgb(255, 255, 255), Color.rgb(249, 247, 253)}
                        : new int[]{Color.argb(234, 19, 27, 52), Color.argb(229, 12, 19, 40)},
                20, lightTheme ? DIVIDER : Color.rgb(38, 47, 78), 1));
        return c;
    }

    private LinearLayout horizontal() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private TextView tv(String text, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setIncludeFontPadding(false);
        t.setTypeface(Typeface.create("sans", bold ? Typeface.BOLD : Typeface.NORMAL));
        return t;
    }

    private TextView primaryButton(String text) {
        TextView b = tv("", 17, Color.WHITE, true);
        b.setGravity(Gravity.CENTER);
        b.setClickable(true);
        b.setFocusable(true);
        setButtonLabel(b, text, Color.WHITE);
        b.setBackground(ripple(roundedGradient(
                lightTheme
                        ? new int[]{Color.rgb(100, 73, 207), Color.rgb(126, 99, 220)}
                        : new int[]{Color.rgb(109, 82, 246), Color.rgb(142, 112, 255)},
                28, Color.TRANSPARENT, 0), Color.WHITE));
        b.setMinHeight(dp(54));
        b.setPadding(dp(14), dp(10), dp(14), dp(10));
        b.setMaxLines(2);
        b.setElevation(dp(2));
        return b;
    }

    private TextView secondaryButton(String text) {
        TextView b = tv("", 15, PURPLE_LIGHT, true);
        b.setGravity(Gravity.CENTER);
        b.setClickable(true);
        b.setFocusable(true);
        setButtonLabel(b, text, PURPLE_LIGHT);
        b.setBackground(ripple(roundedBg(SURFACE_2, 19, lightTheme ? DIVIDER : Color.rgb(65, 62, 122), 1), PURPLE_LIGHT));
        b.setMinHeight(dp(48));
        b.setPadding(dp(12), dp(10), dp(12), dp(10));
        b.setMaxLines(2);
        return b;
    }

    private TextView textButton(String text) {
        TextView b = tv("", 14, PURPLE_LIGHT, true);
        b.setGravity(Gravity.CENTER);
        b.setClickable(true);
        b.setFocusable(true);
        setButtonLabel(b, text, PURPLE_LIGHT);
        return b;
    }

    private Drawable ripple(Drawable content, int tint) {
        if (Build.VERSION.SDK_INT >= 21) {
            int color = Color.argb(42, Color.red(tint), Color.green(tint), Color.blue(tint));
            return new RippleDrawable(ColorStateList.valueOf(color), content, null);
        }
        return content;
    }

    private void setButtonLabel(TextView button, String text, int iconTint) {
        String icon = null;
        String label = text;
        if (text != null && !text.isEmpty()) {
            char lead = text.charAt(0);
            if (lead == '▶') icon = "play";
            else if (lead == '■') icon = "stop";
            else if (lead == '＋') icon = "plus";
            else if (lead == '✎') icon = "edit";
            else if (lead == '↗') icon = "export";
            else if (lead == '↓') icon = "download";
            else if (lead == '↶') icon = "restore";
            else if (lead == '‹') icon = "arrowLeft";
            if (icon != null) label = text.substring(1).trim();
        }
        button.setText(label == null ? "" : label);
        if (icon != null) {
            IconGlyphDrawable drawable = new IconGlyphDrawable(icon, iconTint, dp(19));
            drawable.setBounds(0, 0, dp(19), dp(19));
            button.setCompoundDrawablesRelative(drawable, null, null, null);
            button.setCompoundDrawablePadding(dp(9));
        } else {
            button.setCompoundDrawablesRelative(null, null, null, null);
        }
    }

    private class AppIconView extends View {
        private final String icon;
        private final int tint;
        private final float iconScale;
        private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        AppIconView(String icon, int tint) {
            this(icon, tint, .62f);
        }

        AppIconView(String icon, int tint, float iconScale) {
            super(MainActivity.this);
            this.icon = icon;
            this.tint = tint;
            this.iconScale = iconScale;
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float side = Math.min(getWidth(), getHeight()) * iconScale;
            drawAppIcon(canvas, icon, tint, getWidth() / 2f, getHeight() / 2f, side, iconPaint, 255);
        }
    }

    private class IconGlyphDrawable extends Drawable {
        private final String icon;
        private final int tint;
        private final int size;
        private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int alpha = 255;

        IconGlyphDrawable(String icon, int tint, int size) {
            this.icon = icon;
            this.tint = tint;
            this.size = size;
        }

        @Override public void draw(Canvas canvas) {
            RectF b = new RectF(getBounds());
            drawAppIcon(canvas, icon, tint, b.centerX(), b.centerY(), Math.min(b.width(), b.height()), iconPaint, alpha);
        }
        @Override public int getIntrinsicWidth() { return size; }
        @Override public int getIntrinsicHeight() { return size; }
        @Override public void setAlpha(int alpha) { this.alpha = Math.max(0, Math.min(255, alpha)); invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter colorFilter) { invalidateSelf(); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    private void drawAppIcon(Canvas canvas, String name, int color, float centerX, float centerY,
                             float size, Paint p, int alpha) {
        if (size <= 0) return;
        int tint = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
        canvas.save();
        canvas.translate(centerX - size / 2f, centerY - size / 2f);
        canvas.scale(size / 24f, size / 24f);
        p.setColor(tint);
        p.setStrokeWidth(1.9f);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStyle(Paint.Style.STROKE);
        RectF rect = new RectF();
        Path path = new Path();
        switch (name) {
            case "home":
                path.moveTo(3, 11); path.lineTo(12, 3.5f); path.lineTo(21, 11);
                path.moveTo(5.5f, 10); path.lineTo(5.5f, 20); path.lineTo(18.5f, 20); path.lineTo(18.5f, 10);
                canvas.drawPath(path, p);
                canvas.drawRoundRect(new RectF(10, 14, 14, 20), 1, 1, p);
                break;
            case "history":
                p.setStyle(Paint.Style.FILL);
                canvas.drawRoundRect(new RectF(4, 12, 8, 20), 1.5f, 1.5f, p);
                canvas.drawRoundRect(new RectF(10, 7, 14, 20), 1.5f, 1.5f, p);
                canvas.drawRoundRect(new RectF(16, 3, 20, 20), 1.5f, 1.5f, p);
                break;
            case "stats":
                rect.set(3, 3, 21, 21);
                canvas.drawArc(rect, -90, 105, true, p);
                canvas.drawArc(rect, 25, 245, true, p);
                canvas.drawLine(12, 12, 12, 3, p);
                break;
            case "settings":
                canvas.drawCircle(12, 12, 7, p);
                canvas.drawCircle(12, 12, 2.6f, p);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * i / 4;
                    float x1 = 12 + (float)Math.cos(a) * 8.4f, y1 = 12 + (float)Math.sin(a) * 8.4f;
                    float x2 = 12 + (float)Math.cos(a) * 10.5f, y2 = 12 + (float)Math.sin(a) * 10.5f;
                    canvas.drawLine(x1, y1, x2, y2, p);
                }
                break;
            case "moon":
                p.setStyle(Paint.Style.FILL);
                path.moveTo(16.7f, 3.1f);
                path.cubicTo(9.2f, 3.4f, 4, 8.2f, 4, 14.2f);
                path.cubicTo(4, 20.1f, 9.2f, 23.2f, 14.7f, 21.2f);
                path.cubicTo(10.5f, 18.2f, 9.8f, 13.2f, 11.8f, 9.1f);
                path.cubicTo(13, 6.7f, 14.6f, 4.7f, 16.7f, 3.1f);
                path.close(); canvas.drawPath(path, p);
                break;
            case "bed":
                canvas.drawLine(3, 7, 3, 19, p); canvas.drawLine(3, 14, 21, 14, p);
                canvas.drawLine(4, 10, 10, 10, p); canvas.drawArc(new RectF(4, 8, 10, 13), 180, 180, false, p);
                canvas.drawLine(10, 10, 20, 10, p); canvas.drawLine(20, 10, 20, 19, p);
                canvas.drawLine(5, 14, 5, 19, p); canvas.drawLine(19, 14, 19, 19, p);
                break;
            case "sun":
                canvas.drawCircle(12, 12, 4.4f, p);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * i / 4;
                    canvas.drawLine(12 + (float)Math.cos(a) * 7.1f, 12 + (float)Math.sin(a) * 7.1f,
                            12 + (float)Math.cos(a) * 10.2f, 12 + (float)Math.sin(a) * 10.2f, p);
                }
                break;
            case "star":
                p.setStyle(Paint.Style.FILL);
                for (int i = 0; i < 10; i++) {
                    double a = -Math.PI / 2 + i * Math.PI / 5;
                    float r = i % 2 == 0 ? 10 : 4.5f;
                    float x = 12 + (float)Math.cos(a) * r, y = 12 + (float)Math.sin(a) * r;
                    if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
                }
                path.close(); canvas.drawPath(path, p);
                break;
            case "target":
                canvas.drawCircle(12, 12, 9, p); canvas.drawCircle(12, 12, 5, p); canvas.drawCircle(12, 12, 1.5f, p);
                break;
            case "clock":
                canvas.drawCircle(12, 12, 9, p); canvas.drawLine(12, 6, 12, 12, p); canvas.drawLine(12, 12, 16, 14.5f, p);
                break;
            case "chevron":
                canvas.drawLine(9, 5, 16, 12, p); canvas.drawLine(16, 12, 9, 19, p);
                break;
            case "arrowLeft":
                canvas.drawLine(19, 12, 5, 12, p); canvas.drawLine(5, 12, 11, 6, p); canvas.drawLine(5, 12, 11, 18, p);
                break;
            case "plus":
                canvas.drawLine(12, 5, 12, 19, p); canvas.drawLine(5, 12, 19, 12, p);
                break;
            case "play":
                p.setStyle(Paint.Style.FILL);
                path.moveTo(7, 4); path.lineTo(20, 12); path.lineTo(7, 20); path.close(); canvas.drawPath(path, p);
                break;
            case "stop":
                p.setStyle(Paint.Style.FILL); canvas.drawRoundRect(new RectF(5, 5, 19, 19), 2, 2, p);
                break;
            case "edit":
                path.moveTo(4, 17); path.lineTo(15.8f, 5.2f); path.lineTo(19, 8.4f); path.lineTo(7.2f, 20.2f); path.lineTo(4, 21); path.close();
                canvas.drawPath(path, p); canvas.drawLine(13.7f, 7.3f, 17, 10.6f, p);
                break;
            case "export":
                canvas.drawLine(12, 15, 12, 3, p); canvas.drawLine(7, 8, 12, 3, p); canvas.drawLine(17, 8, 12, 3, p);
                canvas.drawLine(5, 14, 5, 20, p); canvas.drawLine(5, 20, 19, 20, p); canvas.drawLine(19, 20, 19, 14, p);
                break;
            case "download":
                canvas.drawLine(12, 3, 12, 15, p); canvas.drawLine(7, 10, 12, 15, p); canvas.drawLine(17, 10, 12, 15, p);
                canvas.drawLine(4, 17, 4, 21, p); canvas.drawLine(4, 21, 20, 21, p); canvas.drawLine(20, 21, 20, 17, p);
                break;
            case "restore":
                canvas.drawLine(12, 4, 12, 16, p); canvas.drawLine(7, 11, 12, 16, p); canvas.drawLine(17, 11, 12, 16, p);
                canvas.drawLine(4, 18, 4, 21, p); canvas.drawLine(4, 21, 20, 21, p); canvas.drawLine(20, 21, 20, 18, p);
                break;
            case "bell":
                path.moveTo(5, 17); path.lineTo(7, 15); path.lineTo(7, 10); path.cubicTo(7, 3, 17, 3, 17, 10);
                path.lineTo(17, 15); path.lineTo(19, 17); path.close(); canvas.drawPath(path, p);
                canvas.drawArc(new RectF(9, 17, 15, 21), 0, 180, false, p);
                break;
            default:
                canvas.drawCircle(12, 12, 8, p);
        }
        canvas.restore();
    }

    private TextView chip(String text, int color) {
        TextView c = tv(text, 13, color, true);
        c.setGravity(Gravity.CENTER);
        int fill = Color.argb(lightTheme ? 24 : 40, Color.red(color), Color.green(color), Color.blue(color));
        c.setBackground(roundedBg(fill, 18, color, 1));
        return c;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(DIVIDER);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        return v;
    }

    private View space(int px) {
        return new View(this);
    }

    private GradientDrawable roundedBg(int fill, int radiusDp, int stroke, int strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0 && stroke != Color.TRANSPARENT) g.setStroke(dp(strokeDp), stroke);
        return g;
    }

    private GradientDrawable roundedGradient(int[] colors, int radiusDp, int stroke, int strokeDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors);
        g.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0 && stroke != Color.TRANSPARENT) g.setStroke(dp(strokeDp), stroke);
        return g;
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private LinearLayout.LayoutParams matchWrap(int height) {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(height));
    }

    private LinearLayout.LayoutParams matchContent() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams buttonMargin(int top) {
        return matchWrapMargin(top);
    }

    private LinearLayout.LayoutParams matchWrapMargin(int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(top);
        return p;
    }

    private LinearLayout.LayoutParams matchMargin(int height, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
        p.topMargin = dp(top);
        return p;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private class NightBackdropDrawable extends Drawable {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        @Override public void draw(Canvas canvas) {
            RectF b = new RectF(getBounds());
            float w = b.width(), h = b.height();
            if (lightTheme) {
                drawDayBackdrop(canvas, w, h);
                return;
            }
            p.setShader(new LinearGradient(0, 0, 0, h,
                    new int[]{Color.rgb(8, 15, 42), Color.rgb(10, 17, 43), BG}, null, Shader.TileMode.CLAMP));
            canvas.drawRect(b, p);
            p.setShader(null);

            float d = getResources().getDisplayMetrics().density;
            for (int i = 0; i < 48; i++) {
                float x = ((i * 73 + 31) % 997) / 997f * w;
                float y = ((i * 47 + 19) % 383) / 383f * h * .47f;
                p.setColor(Color.argb(80 + (i % 4) * 35, 205, 213, 255));
                canvas.drawCircle(x, y, (i % 7 == 0 ? 1.25f : .7f) * d, p);
            }

            float moonX = w * .84f, moonY = h * .16f, moonR = 22 * d;
            p.setColor(Color.argb(30, 197, 166, 255));
            canvas.drawCircle(moonX, moonY, moonR * 1.9f, p);
            p.setColor(Color.rgb(211, 195, 255));
            canvas.drawCircle(moonX, moonY, moonR, p);
            p.setColor(Color.rgb(15, 23, 56));
            canvas.drawCircle(moonX + moonR * .42f, moonY - moonR * .22f, moonR * .84f, p);

            Path far = new Path();
            far.moveTo(0, h * .39f); far.lineTo(w * .15f, h * .31f); far.lineTo(w * .28f, h * .37f);
            far.lineTo(w * .47f, h * .285f); far.lineTo(w * .63f, h * .36f); far.lineTo(w * .82f, h * .29f);
            far.lineTo(w, h * .35f); far.lineTo(w, h * .54f); far.lineTo(0, h * .54f); far.close();
            p.setColor(Color.rgb(19, 28, 67)); canvas.drawPath(far, p);

            Path near = new Path();
            near.moveTo(0, h * .43f); near.lineTo(w * .18f, h * .36f); near.lineTo(w * .32f, h * .43f);
            near.lineTo(w * .53f, h * .34f); near.lineTo(w * .70f, h * .41f); near.lineTo(w * .88f, h * .33f);
            near.lineTo(w, h * .39f); near.lineTo(w, h * .59f); near.lineTo(0, h * .59f); near.close();
            p.setColor(Color.rgb(13, 23, 55)); canvas.drawPath(near, p);

            p.setShader(new LinearGradient(0, h * .45f, 0, h * .70f,
                    new int[]{Color.rgb(18, 29, 68), Color.rgb(7, 14, 37), Color.rgb(5, 9, 24)}, null, Shader.TileMode.CLAMP));
            canvas.drawRect(0, h * .45f, w, h * .74f, p); p.setShader(null);
            p.setColor(Color.argb(45, 149, 127, 255));
            canvas.drawLine(0, h * .455f, w, h * .455f, p);
            for (int i = 0; i < 9; i++) {
                float yy = h * (.49f + i * .023f);
                float x1 = w * (.68f - i * .025f), x2 = w * (.94f + i * .006f);
                p.setColor(Color.argb(Math.max(5, 35 - i * 3), 227, 165, 201));
                p.setStrokeWidth((i % 2 == 0 ? 1.2f : .7f) * d);
                canvas.drawLine(x1, yy, x2, yy, p);
            }

            p.setColor(Color.rgb(7, 13, 32));
            drawPine(canvas, w * .06f, h * .54f, 42 * d);
            drawPine(canvas, w * .12f, h * .56f, 31 * d);
            drawPine(canvas, w * .91f, h * .55f, 38 * d);
            drawPine(canvas, w * .97f, h * .57f, 27 * d);
        }

        private void drawDayBackdrop(Canvas canvas, float w, float h) {
            float d = getResources().getDisplayMetrics().density;
            p.setShader(new LinearGradient(0, 0, 0, h,
                    new int[]{Color.rgb(249, 247, 255), Color.rgb(242, 243, 251), BG},
                    null, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, p);
            p.setShader(null);

            float sunX = w * .83f, sunY = h * .18f, sunR = 25 * d;
            p.setColor(Color.argb(25, 157, 130, 220));
            canvas.drawCircle(sunX, sunY, sunR * 2.4f, p);
            p.setColor(Color.rgb(247, 222, 164));
            canvas.drawCircle(sunX, sunY, sunR, p);

            Path far = new Path();
            far.moveTo(0, h * .40f); far.lineTo(w * .17f, h * .32f); far.lineTo(w * .33f, h * .38f);
            far.lineTo(w * .50f, h * .30f); far.lineTo(w * .68f, h * .37f); far.lineTo(w * .83f, h * .31f);
            far.lineTo(w, h * .36f); far.lineTo(w, h * .55f); far.lineTo(0, h * .55f); far.close();
            p.setColor(Color.rgb(221, 219, 239)); canvas.drawPath(far, p);

            Path near = new Path();
            near.moveTo(0, h * .45f); near.lineTo(w * .19f, h * .37f); near.lineTo(w * .34f, h * .44f);
            near.lineTo(w * .54f, h * .35f); near.lineTo(w * .72f, h * .43f); near.lineTo(w * .89f, h * .36f);
            near.lineTo(w, h * .42f); near.lineTo(w, h * .60f); near.lineTo(0, h * .60f); near.close();
            p.setColor(Color.rgb(205, 207, 231)); canvas.drawPath(near, p);

            p.setShader(new LinearGradient(0, h * .47f, 0, h * .78f,
                    new int[]{Color.rgb(225, 228, 245), Color.rgb(236, 237, 248), BG},
                    null, Shader.TileMode.CLAMP));
            canvas.drawRect(0, h * .47f, w, h * .78f, p);
            p.setShader(null);
            p.setColor(Color.argb(70, 145, 126, 199));
            canvas.drawLine(0, h * .475f, w, h * .475f, p);
            for (int i = 0; i < 8; i++) {
                float yy = h * (.51f + i * .026f);
                float x1 = w * (.65f - i * .025f), x2 = w * (.94f + i * .006f);
                p.setColor(Color.argb(Math.max(10, 48 - i * 4), 156, 145, 197));
                p.setStrokeWidth((i % 2 == 0 ? 1.2f : .7f) * d);
                canvas.drawLine(x1, yy, x2, yy, p);
            }
            p.setColor(Color.rgb(177, 182, 210));
            drawPine(canvas, w * .06f, h * .55f, 42 * d);
            drawPine(canvas, w * .12f, h * .57f, 31 * d);
            drawPine(canvas, w * .91f, h * .56f, 38 * d);
            drawPine(canvas, w * .97f, h * .58f, 27 * d);
        }

        private void drawPine(Canvas canvas, float x, float y, float height) {
            Path tree = new Path();
            tree.moveTo(x, y - height); tree.lineTo(x - height * .22f, y - height * .52f);
            tree.lineTo(x - height * .11f, y - height * .52f); tree.lineTo(x - height * .31f, y - height * .20f);
            tree.lineTo(x + height * .31f, y - height * .20f); tree.lineTo(x + height * .11f, y - height * .52f);
            tree.lineTo(x + height * .22f, y - height * .52f); tree.close();
            canvas.drawPath(tree, p);
        }

        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(android.graphics.ColorFilter colorFilter) {}
        @Override public int getOpacity() { return android.graphics.PixelFormat.OPAQUE; }
    }

    private class ProgressRingView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private String mainText, labelText, detailText, goalText;
        private int progress;
        private final boolean running;

        ProgressRingView(Context context, String main, String label, String detail, String goal, int percent, boolean isRunning) {
            super(context);
            mainText = main; labelText = label; detailText = detail; goalText = goal;
            progress = Math.max(0, Math.min(100, percent)); running = isRunning;
            setContentDescription("Acompanhamento do sono: " + main + ", " + label);
        }

        void updateElapsed(long start) {
            long sec = Math.max(0, (System.currentTimeMillis() - start) / 1000L);
            long h = sec / 3600, m = (sec % 3600) / 60, s = sec % 60;
            mainText = String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s);
            progress = (int)Math.min(100L, sec * 100L / (targetMinutes() * 60L));
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float d = getResources().getDisplayMetrics().density;
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float radius = Math.min(getWidth() - 18 * d, getHeight() - 20 * d) / 2f;
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(14 * d); p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(lightTheme ? Color.rgb(225, 221, 239) : Color.argb(165, 47, 49, 91));
            canvas.drawCircle(cx, cy, radius, p);
            if (progress > 0) {
                p.setShader(new LinearGradient(cx - radius, cy - radius, cx + radius, cy + radius,
                        new int[]{PURPLE, PURPLE_LIGHT, lightTheme ? Color.rgb(126, 105, 220) : Color.rgb(126, 108, 255)}, null, Shader.TileMode.CLAMP));
                RectF arc = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
                canvas.drawArc(arc, -90, Math.min(359.8f, 3.6f * progress), false, p);
                p.setShader(null);
            }
            p.setStrokeCap(Paint.Cap.BUTT); p.setStyle(Paint.Style.FILL);
            p.setColor(lightTheme ? Color.argb(244, 255, 255, 255) : Color.argb(190, 10, 16, 39));
            canvas.drawCircle(cx, cy, radius - 13 * d, p);

            drawBedIcon(canvas, cx, cy - 66 * d, d);
            p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.create("sans", Typeface.BOLD));
            p.setColor(TEXT); p.setTextSize((running ? 30 : 39) * d);
            drawFittedText(canvas, mainText, cx, cy + 8 * d, radius * 1.6f);
            p.setTypeface(Typeface.create("sans", Typeface.NORMAL)); p.setColor(MUTED); p.setTextSize(14 * d);
            drawFittedText(canvas, labelText, cx, cy + 34 * d, radius * 1.65f);
            p.setColor(lightTheme ? Color.argb(120, 182, 171, 218) : Color.argb(80, 177, 156, 255)); p.setStrokeWidth(1 * d);
            canvas.drawLine(cx - radius * .53f, cy + 51 * d, cx + radius * .53f, cy + 51 * d, p);
            p.setColor(MUTED); p.setTextSize(13 * d);
            drawFittedText(canvas, detailText, cx, cy + 72 * d, radius * 1.65f);
            p.setColor(PURPLE_LIGHT); p.setTypeface(Typeface.create("sans", Typeface.BOLD)); p.setTextSize(12 * d);
            drawFittedText(canvas, goalText, cx, cy + 95 * d, radius * 1.65f);
            p.setTextAlign(Paint.Align.LEFT);
        }

        private void drawFittedText(Canvas canvas, String value, float x, float y, float maxWidth) {
            if (value == null || value.isEmpty()) return;
            float originalSize = p.getTextSize();
            float width = p.measureText(value);
            if (width > maxWidth && maxWidth > 0) p.setTextSize(originalSize * maxWidth / width);
            canvas.drawText(value, x, y, p);
            p.setTextSize(originalSize);
        }

        private void drawBedIcon(Canvas canvas, float cx, float y, float d) {
            p.setColor(PURPLE_LIGHT); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.6f * d); p.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawLine(cx - 22*d, y - 4*d, cx - 22*d, y + 15*d, p);
            canvas.drawLine(cx - 22*d, y + 6*d, cx + 22*d, y + 6*d, p);
            canvas.drawLine(cx - 18*d, y + 7*d, cx - 18*d, y + 15*d, p);
            canvas.drawLine(cx + 19*d, y + 7*d, cx + 19*d, y + 15*d, p);
            canvas.drawLine(cx - 2*d, y + 6*d, cx - 2*d, y - 1*d, p);
            canvas.drawLine(cx - 2*d, y - 1*d, cx + 20*d, y - 1*d, p);
            canvas.drawLine(cx + 20*d, y - 1*d, cx + 20*d, y + 6*d, p);
            p.setStyle(Paint.Style.FILL); p.setTypeface(Typeface.create("sans", Typeface.BOLD)); p.setTextSize(13*d);
            canvas.drawText("z", cx + 2*d, y - 7*d, p); p.setTextSize(10*d); canvas.drawText("z", cx + 10*d, y - 15*d, p);
            p.setTypeface(Typeface.create("sans", Typeface.NORMAL));
        }
    }

    static class SleepRecord {
        long id;
        long start;
        long end;
        String quality;
        String note;

        long durationMinutes() {
            return Math.max(0, Math.round((end - start) / 60000.0));
        }
    }

    static class SleepDb extends SQLiteOpenHelper {
        SleepDb(Context context) {
            super(context, "meu_sono.db", null, 1);
        }

        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE sleep_records (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "start_time INTEGER NOT NULL," +
                    "end_time INTEGER NOT NULL," +
                    "quality TEXT NOT NULL DEFAULT 'Bom'," +
                    "note TEXT NOT NULL DEFAULT '')");
        }

        @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}

        long insert(long start, long end, String quality, String note) {
            ContentValues cv = new ContentValues();
            cv.put("start_time", start);
            cv.put("end_time", end);
            cv.put("quality", quality == null ? "Bom" : quality);
            cv.put("note", note == null ? "" : note);
            return getWritableDatabase().insert("sleep_records", null, cv);
        }

        void update(long id, String quality, String note) {
            ContentValues cv = new ContentValues();
            cv.put("quality", quality == null ? "Bom" : quality);
            cv.put("note", note == null ? "" : note);
            getWritableDatabase().update("sleep_records", cv, "id=?", new String[]{String.valueOf(id)});
        }

        void updateFull(long id, long start, long end, String quality, String note) {
            ContentValues cv = new ContentValues();
            cv.put("start_time", start); cv.put("end_time", end);
            cv.put("quality", quality == null ? "Bom" : quality);
            cv.put("note", note == null ? "" : note);
            getWritableDatabase().update("sleep_records", cv, "id=?", new String[]{String.valueOf(id)});
        }

        void delete(long id) {
            getWritableDatabase().delete("sleep_records", "id=?", new String[]{String.valueOf(id)});
        }

        void clear() {
            getWritableDatabase().delete("sleep_records", null, null);
        }

        int restoreRecords(List<SleepRecord> records, boolean replace) {
            SQLiteDatabase database = getWritableDatabase();
            int inserted = 0;
            Set<String> existing = new HashSet<>();
            database.beginTransaction();
            try {
                if (replace) database.delete("sleep_records", null, null);
                else {
                    Cursor current = database.query("sleep_records", new String[]{"start_time", "end_time"},
                            null, null, null, null, null);
                    try {
                        while (current.moveToNext()) existing.add(current.getLong(0) + ":" + current.getLong(1));
                    } finally {
                        current.close();
                    }
                }
                for (SleepRecord record : records) {
                    String key = record.start + ":" + record.end;
                    if (!replace && !existing.add(key)) continue;
                    ContentValues values = new ContentValues();
                    values.put("start_time", record.start);
                    values.put("end_time", record.end);
                    values.put("quality", record.quality == null ? "Bom" : record.quality);
                    values.put("note", record.note == null ? "" : record.note);
                    if (database.insertOrThrow("sleep_records", null, values) < 0) {
                        throw new IllegalStateException("Falha ao gravar um registro.");
                    }
                    inserted++;
                }
                database.setTransactionSuccessful();
            } finally {
                database.endTransaction();
            }
            return inserted;
        }

        List<SleepRecord> getAll(int limit) {
            List<SleepRecord> out = new ArrayList<>();
            Cursor c = getReadableDatabase().query(
                    "sleep_records",
                    new String[]{"id", "start_time", "end_time", "quality", "note"},
                    null, null, null, null,
                    "end_time DESC",
                    String.valueOf(limit));
            try {
                while (c.moveToNext()) {
                    SleepRecord r = new SleepRecord();
                    r.id = c.getLong(0);
                    r.start = c.getLong(1);
                    r.end = c.getLong(2);
                    r.quality = c.getString(3);
                    r.note = c.getString(4);
                    out.add(r);
                }
            } finally {
                c.close();
            }
            return out;
        }

        SleepRecord getById(long id) {
            Cursor c = getReadableDatabase().query("sleep_records",
                    new String[]{"id", "start_time", "end_time", "quality", "note"},
                    "id=?", new String[]{String.valueOf(id)}, null, null, null, "1");
            try {
                if (!c.moveToFirst()) return null;
                SleepRecord r = new SleepRecord();
                r.id = c.getLong(0); r.start = c.getLong(1); r.end = c.getLong(2);
                r.quality = c.getString(3); r.note = c.getString(4);
                return r;
            } finally { c.close(); }
        }
    }

    class SleepBarChartView extends View {
        private final List<SleepRecord> records;
        private final int targetMinutes;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int purple = PURPLE;
        private final int muted = MUTED;
        private final int grid = DIVIDER;
        private final int text = TEXT;

        SleepBarChartView(Context c, List<SleepRecord> source, int targetMinutes) {
            super(c);
            this.records = new ArrayList<>(source);
            Collections.reverse(this.records);
            this.targetMinutes = targetMinutes;
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float d = getResources().getDisplayMetrics().density;
            float left = 34 * d, right = getWidth() - 10 * d, top = 22 * d, bottom = getHeight() - 34 * d;
            p.setTypeface(Typeface.create("sans", Typeface.NORMAL));
            p.setStrokeWidth(d);
            p.setTextSize(10 * d);
            p.setColor(grid);
            for (int h = 0; h <= 10; h += 2) {
                float y = bottom - (h / 10f) * (bottom - top);
                canvas.drawLine(left, y, right, y, p);
                p.setColor(muted);
                canvas.drawText(h + "h", 2 * d, y + 3 * d, p);
                p.setColor(grid);
            }
            if (records.isEmpty()) {
                p.setTextSize(14 * d); p.setColor(muted);
                canvas.drawText("Registre algumas noites para ver o gráfico", left, getHeight()/2f, p);
                return;
            }
            int n = records.size();
            float area = right - left;
            float gap = 8 * d;
            float barW = Math.max(15 * d, (area - gap * (n - 1)) / n);
            SimpleDateFormat day = new SimpleDateFormat("EEE", new Locale("pt", "BR"));
            for (int i = 0; i < n; i++) {
                SleepRecord r = records.get(i);
                float hours = Math.min(10f, r.durationMinutes() / 60f);
                float x = left + i * (barW + gap);
                float y = bottom - (hours / 10f) * (bottom - top);
                p.setColor(purple);
                canvas.drawRoundRect(x, y, x + barW, bottom, 7*d, 7*d, p);
                p.setTextSize(9 * d); p.setColor(text);
                String value = String.format(Locale.getDefault(), "%.1fh", r.durationMinutes()/60f);
                canvas.drawText(value, x, Math.max(top + 10*d, y - 5*d), p);
                p.setTextSize(10 * d); p.setColor(muted);
                String label = day.format(new Date(r.end));
                canvas.drawText(label, x, bottom + 20*d, p);
            }
            float targetH = Math.min(10f, targetMinutes / 60f);
            float ty = bottom - (targetH / 10f) * (bottom - top);
            p.setColor(Color.argb(190, Color.red(GREEN), Color.green(GREEN), Color.blue(GREEN)));
            p.setStrokeWidth(1.5f*d);
            canvas.drawLine(left, ty, right, ty, p);
        }
    }

    class BedtimeLineChartView extends View {
        private final List<SleepRecord> records;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int purple = PURPLE_LIGHT;
        private final int muted = MUTED;
        private final int grid = DIVIDER;

        BedtimeLineChartView(Context c, List<SleepRecord> source) {
            super(c);
            records = new ArrayList<>(source);
            Collections.reverse(records);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float d = getResources().getDisplayMetrics().density;
            float left = 42*d, right = getWidth()-10*d, top = 24*d, bottom = getHeight()-36*d;
            p.setTypeface(Typeface.create("sans", Typeface.NORMAL));
            p.setTextSize(10*d);
            int[] labels = {22, 23, 24, 25};
            for (int h : labels) {
                float y = bottom - ((h - 22) / 3f) * (bottom-top);
                p.setColor(grid); p.setStrokeWidth(d);
                canvas.drawLine(left, y, right, y, p);
                p.setColor(muted);
                String text = h == 24 ? "00:00" : (h == 25 ? "01:00" : String.format(Locale.getDefault(), "%02d:00", h));
                canvas.drawText(text, 0, y+3*d, p);
            }
            if (records.isEmpty()) {
                p.setTextSize(14*d); p.setColor(muted);
                canvas.drawText("Sem dados suficientes", left, getHeight()/2f, p);
                return;
            }
            int n = records.size();
            float step = n == 1 ? 0 : (right-left)/(n-1f);
            Path path = new Path();
            SimpleDateFormat day = new SimpleDateFormat("EEE", new Locale("pt", "BR"));
            for (int i=0;i<n;i++) {
                SleepRecord r = records.get(i);
                Calendar c = Calendar.getInstance(); c.setTimeInMillis(r.start);
                int mins = c.get(Calendar.HOUR_OF_DAY)*60 + c.get(Calendar.MINUTE);
                if (mins < 12*60) mins += 24*60;
                float h = mins/60f;
                h = Math.max(22f, Math.min(25f, h));
                float x = left + (n==1 ? (right-left)/2f : i*step);
                float y = bottom - ((h-22f)/3f)*(bottom-top);
                if (i==0) path.moveTo(x,y); else path.lineTo(x,y);
            }
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3*d); p.setColor(purple);
            canvas.drawPath(path,p);
            p.setStyle(Paint.Style.FILL);
            for (int i=0;i<n;i++) {
                SleepRecord r = records.get(i);
                Calendar c = Calendar.getInstance(); c.setTimeInMillis(r.start);
                int mins = c.get(Calendar.HOUR_OF_DAY)*60 + c.get(Calendar.MINUTE);
                int drawMins = mins < 12*60 ? mins + 24*60 : mins;
                float h = Math.max(22f, Math.min(25f, drawMins/60f));
                float x = left + (n==1 ? (right-left)/2f : i*step);
                float y = bottom - ((h-22f)/3f)*(bottom-top);
                p.setColor(purple); canvas.drawCircle(x,y,4*d,p);
                p.setTextSize(9*d); p.setColor(muted);
                canvas.drawText(String.format(Locale.getDefault(), "%02d:%02d", mins/60, mins%60), x-14*d, y-9*d,p);
                canvas.drawText(day.format(new Date(r.start)), x-9*d, bottom+20*d,p);
            }
        }
    }
}
