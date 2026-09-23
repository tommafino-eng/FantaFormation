package it.fantaformation;

import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class FantaFormationScheduleReceiver extends BroadcastReceiver {

    private static final String ACTION_BOOT = Intent.ACTION_BOOT_COMPLETED;
    private static final String ACTION_REPLACED = Intent.ACTION_MY_PACKAGE_REPLACED;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        String action = intent.getAction();

        if (Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || ACTION_BOOT.equals(action)
                || ACTION_REPLACED.equals(action)) {
            boolean enabled = context.getSharedPreferences("fantaformation_app", Context.MODE_PRIVATE)
                    .getBoolean("auto_weekly", false);
            if (enabled) AutomationScheduler.scheduleWeekly(context);
            return;
        }

        if (!AutomationScheduler.ACTION_SCHEDULED_AUTO.equals(action)) return;

        // L'exact alarm è una delle eccezioni ammesse per l'avvio di un FGS
        // da background. Il service tiene vivo il processo mentre si apre il flusso.
        try {
            Intent serviceIntent = new Intent(context, FantaAutomationService.class);
            serviceIntent.setAction(AutomationScheduler.ACTION_SCHEDULED_AUTO);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        } catch (Throwable e) {
            Log.d("FANTA_DEBUG", "FGS scheduled non avviato: " + e.getMessage());
        }

        // Android 14/15 richiede opt-in BAL per PendingIntent creati dall'app.
        Intent activityIntent = new Intent(context, MainActivity.class);
        activityIntent.setAction(AutomationScheduler.ACTION_SCHEDULED_AUTO);
        activityIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
        );

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;

        PendingIntent activityPendingIntent = PendingIntent.getActivity(
                context,
                24052,
                activityIntent,
                flags,
                creatorOptions()
        );

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ActivityOptions senderOptions = ActivityOptions.makeBasic()
                        .setPendingIntentBackgroundActivityStartMode(
                                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                        );
                activityPendingIntent.send(context, 0, null, null, null, null, senderOptions.toBundle());
            } else {
                activityPendingIntent.send();
            }
        } catch (PendingIntent.CanceledException e) {
            Log.d("FANTA_DEBUG", "Avvio MainActivity scheduled annullato: " + e.getMessage());
        } catch (Throwable e) {
            Log.d("FANTA_DEBUG", "Avvio MainActivity scheduled fallito: " + e.getMessage());
        }

        // Una singola esecuzione non deve spegnere la pianificazione.
        AutomationScheduler.scheduleWeekly(context);
    }

    private static android.os.Bundle creatorOptions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return ActivityOptions.makeBasic()
                    .setPendingIntentCreatorBackgroundActivityStartMode(
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    )
                    .toBundle();
        }
        return null;
    }
}
