package it.fantaformation;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.util.Calendar;

public final class AutomationScheduler {
    public static final String ACTION_SCHEDULED_AUTO = "it.fantaformation.ACTION_SCHEDULED_AUTO";
    public static final int WEEKLY_ALARM_REQUEST = 24051;

    private static final String PREFS_APP = "fantaformation_app";
    private static final String PREF_AUTO_WEEKLY = "auto_weekly";
    private static final String PREF_AUTO_HOUR = "auto_hour";
    private static final String PREF_AUTO_MINUTE = "auto_minute";

    private AutomationScheduler() {}

    public static boolean scheduleWeekly(Context context) {
        if (context == null) return false;

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && !alarmManager.canScheduleExactAlarms()) {
            return false;
        }

        int hour = context.getSharedPreferences(PREFS_APP, Context.MODE_PRIVATE)
                .getInt(PREF_AUTO_HOUR, 18);
        int minute = context.getSharedPreferences(PREFS_APP, Context.MODE_PRIVATE)
                .getInt(PREF_AUTO_MINUTE, 30);

        Calendar next = Calendar.getInstance();
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        next.set(Calendar.HOUR_OF_DAY, hour);
        next.set(Calendar.MINUTE, minute);

        int daysUntilFriday = (Calendar.FRIDAY - next.get(Calendar.DAY_OF_WEEK) + 7) % 7;
        if (daysUntilFriday == 0 && next.getTimeInMillis() <= System.currentTimeMillis()) {
            daysUntilFriday = 7;
        }
        next.add(Calendar.DAY_OF_YEAR, daysUntilFriday);

        Intent intent = new Intent(context, FantaFormationScheduleReceiver.class);
        intent.setAction(ACTION_SCHEDULED_AUTO);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, WEEKLY_ALARM_REQUEST, intent, flags);

        alarmManager.cancel(pendingIntent);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        next.getTimeInMillis(),
                        pendingIntent
                );
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        next.getTimeInMillis(),
                        pendingIntent
                );
            } else {
                alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        next.getTimeInMillis(),
                        pendingIntent
                );
            }

            Log.d("FANTA_DEBUG", "NEXT AUTO = " + next.getTime());
            return true;
        } catch (SecurityException e) {
            Log.d("FANTA_DEBUG", "Exact alarm non autorizzata: " + e.getMessage());
            return false;
        }
    }

    public static void cancel(Context context) {
        if (context == null) return;

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, FantaFormationScheduleReceiver.class);
        intent.setAction(ACTION_SCHEDULED_AUTO);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, WEEKLY_ALARM_REQUEST, intent, flags);
        alarmManager.cancel(pendingIntent);
    }
}
