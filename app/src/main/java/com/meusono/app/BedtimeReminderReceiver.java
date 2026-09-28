/*
 * Copyright (C) 2026 Higor Ramos
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.meusono.app;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.Calendar;
import java.util.Locale;

final class BedtimeReminderScheduler {
    static final String ACTION_REMIND = "com.meusono.app.ACTION_BEDTIME_REMINDER";
    static final String PREF_ENABLED = "bedtime_reminder_enabled";
    static final String PREF_WAKE_MINUTE = "bedtime_wake_minute";
    private static final String PREFS = "meu_sono_prefs";
    private static final int REQUEST_CODE = 5931;

    private BedtimeReminderScheduler() {}

    static int wakeMinute(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int value = prefs.getInt(PREF_WAKE_MINUTE, 7 * 60);
        return value >= 0 && value < 1440 ? value : 7 * 60;
    }

    static int bedtimeMinute(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int target = prefs.getInt("target_minutes", 8 * 60);
        if (target < 360 || target > 600) target = 8 * 60;
        return (wakeMinute(context) - target + 1440) % 1440;
    }

    static void scheduleNext(Context context) {
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarm == null) return;
        PendingIntent operation = pendingIntent(context);
        alarm.cancel(operation);

        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(PREF_ENABLED, false)) return;

        int bedtimeMinute = bedtimeMinute(context);
        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, bedtimeMinute / 60);
        next.set(Calendar.MINUTE, bedtimeMinute % 60);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (next.getTimeInMillis() <= System.currentTimeMillis()) {
            next.add(Calendar.DAY_OF_MONTH, 1);
        }

        if (Build.VERSION.SDK_INT >= 23) {
            // Inexact on purpose: this avoids requiring the separate "Alarms & reminders" access.
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), operation);
        } else {
            alarm.set(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), operation);
        }
    }

    static void cancel(Context context) {
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarm != null) alarm.cancel(pendingIntent(context));
    }

    private static PendingIntent pendingIntent(Context context) {
        Intent intent = new Intent(context, BedtimeReminderReceiver.class).setAction(ACTION_REMIND);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags);
    }

    static String formatTime(int minute) {
        return String.format(Locale.getDefault(), "%02d:%02d", minute / 60, minute % 60);
    }

    static String formatSleepTarget(int minutes) {
        int hours = minutes / 60;
        int remainder = minutes % 60;
        return remainder == 0 ? hours + "h" : hours + "h " + remainder + "min";
    }
}

public class BedtimeReminderReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "bedtime_reminder";
    private static final int NOTIFICATION_ID = 5932;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !BedtimeReminderScheduler.ACTION_REMIND.equals(intent.getAction())) return;
        SharedPreferences prefs = context.getSharedPreferences("meu_sono_prefs", Context.MODE_PRIVATE);
        if (!prefs.getBoolean(BedtimeReminderScheduler.PREF_ENABLED, false)) return;

        if (prefs.getLong("active_start", 0L) <= 0L) showReminder(context, prefs);
        BedtimeReminderScheduler.scheduleNext(context);
    }

    private void showReminder(Context context, SharedPreferences prefs) {
        if (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 24 && !manager.areNotificationsEnabled()) return;

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Lembrete para dormir", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Lembrete calculado pela hora de acordar e pela meta diária de sono.");
            manager.createNotificationChannel(channel);
        }

        int wake = BedtimeReminderScheduler.wakeMinute(context);
        int target = prefs.getInt("target_minutes", 8 * 60);
        if (target < 360 || target > 600) target = 8 * 60;
        String duration = BedtimeReminderScheduler.formatSleepTarget(target);

        Intent openApp = new Intent(context, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent contentIntent = PendingIntent.getActivity(context, 5933, openApp, pendingFlags);

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);
        Notification notification = builder
                .setSmallIcon(R.drawable.ic_sleep_notification)
                .setContentTitle("Hora de dormir")
                .setContentText("Durma agora para completar " + duration + " antes de acordar às "
                        + BedtimeReminderScheduler.formatTime(wake) + ".")
                .setStyle(new Notification.BigTextStyle().bigText(
                        "Sua meta de " + duration + " sugere este horário para dormir. "
                                + "Você definiu que precisa acordar às "
                                + BedtimeReminderScheduler.formatTime(wake) + "."))
                .setContentIntent(contentIntent)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE)
                .setPriority(Notification.PRIORITY_DEFAULT)
                .build();
        manager.notify(NOTIFICATION_ID, notification);
    }
}
