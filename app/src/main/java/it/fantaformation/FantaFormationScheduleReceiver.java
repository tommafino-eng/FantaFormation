package it.fantaformation;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class FantaFormationScheduleReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !"it.fantaformation.ACTION_SCHEDULED_AUTO".equals(intent.getAction())) return;
        Intent launch = new Intent(context, MainActivity.class);
        launch.setAction("it.fantaformation.ACTION_SCHEDULED_AUTO");
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(launch);
    }
}
