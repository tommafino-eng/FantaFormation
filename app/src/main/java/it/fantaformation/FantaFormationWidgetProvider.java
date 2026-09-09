package it.fantaformation;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

public class FantaFormationWidgetProvider extends AppWidgetProvider {
    private static final String ACTION_WIDGET_AUTO = "it.fantaformation.ACTION_WIDGET_AUTO";

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        for (int id : appWidgetIds) updateWidget(context, manager, id);
    }

    private void updateWidget(Context context, AppWidgetManager manager, int widgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_fantaformation);
        Intent intent = new Intent(context, MainActivity.class);
        intent.setAction(ACTION_WIDGET_AUTO);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (android.os.Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(context, 12001, intent, flags);
        views.setOnClickPendingIntent(R.id.widget_auto_button, pi);
        manager.updateAppWidget(widgetId, views);
    }
}
