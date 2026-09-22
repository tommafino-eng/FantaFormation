package it.fantaformation;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

/**
 * Keeps the FantaFormation process alive while an automation that was already
 * launched from MainActivity continues with the screen locked/off.
 * The WebView remains owned by MainActivity; this service only keeps the process
 * and CPU alive. It never wakes the display.
 */
public class FantaAutomationService extends Service {
    private static final String CHANNEL_ID = "automation_running";
    private static final int NOTIFICATION_ID = 9002;
    private PowerManager.WakeLock wakeLock;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("⚡ FantaFormation")
                .setContentText("Automazione formazione in esecuzione")
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setPriority(Notification.PRIORITY_LOW);

        startForeground(NOTIFICATION_ID, b.build());

        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        getPackageName() + ":FantaFormationService"
                );
                wakeLock.setReferenceCounted(false);
                wakeLock.acquire(30 * 60 * 1000L);
            }
        } catch (Throwable ignored) {
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        "Automazione FantaFormation",
                        NotificationManager.IMPORTANCE_LOW
                );
                channel.setDescription("Mantiene attiva l'automazione con schermo spento");
                nm.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        } catch (Throwable ignored) {
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
