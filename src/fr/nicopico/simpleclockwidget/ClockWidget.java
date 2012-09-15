package fr.nicopico.simpleclockwidget;

import java.util.Date;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

public class ClockWidget extends AppWidgetProvider {
	private static final String TAG = ClockWidget.class.getSimpleName();
	private AlarmManager alarmManager = null;
	private PendingIntent pendingRefresh = null;
	
	@Override
	public void onEnabled(Context context) {
		super.onEnabled(context);
		alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		int update_interval_ms = context.getResources().getInteger(R.integer.update_interval_ms);
		
		Intent intent = new Intent(context, ClockWidget.class);
		intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
		intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[0]);
		pendingRefresh = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
		
		alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + update_interval_ms, update_interval_ms, pendingRefresh);
	}

	@Override
	public void onDisabled(Context context) {
		super.onDisabled(context);
		alarmManager.cancel(pendingRefresh);
	}
	
	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
		super.onUpdate(context, appWidgetManager, appWidgetIds);
		ComponentName me = new ComponentName(context, ClockWidget.class);
		appWidgetManager.updateAppWidget(me, buildUpdate(context, appWidgetIds));
	}

	private RemoteViews buildUpdate(Context context, int[] appWidgetIds) {
		// Adapt layout if using AM/PM format
		RemoteViews remoteViews;
		if (DateFormat.is24HourFormat(context)) {
			remoteViews = new RemoteViews(context.getPackageName(), R.layout.widget_layout_24);
		}
		else {
			remoteViews = new RemoteViews(context.getPackageName(), R.layout.widget_layout_12);
		}
		
		Date now = new Date();
		
		java.text.DateFormat timeFormat = DateFormat.getTimeFormat(context);
		String hourString = timeFormat.format(now);
		if (hourString.length() == 4) {
			hourString = "0" + hourString;
		}
		remoteViews.setTextViewText(R.id.txtHour, hourString);
		
		// Display next alarm if any
		displayNextAlarm(context, remoteViews, R.id.txtAlarm);
		
		// Open alarm screen on click
		PendingIntent onClickIntent = getOpenAlarmClockIntent(context);
		if (onClickIntent != null) {
			remoteViews.setOnClickPendingIntent(R.id.widgetClock, onClickIntent);
		}
		
		String dateFormatString = context.getString(R.string.date_format_all);
		CharSequence dateString = DateFormat.format(dateFormatString, now);
		remoteViews.setTextViewText(R.id.txtDate, dateString);
		
		return(remoteViews);
	}
	
	private boolean displayNextAlarm(Context context, RemoteViews remoteViews, int textAlarmID) {
		String nextAlarmString = Settings.System.getString(
				context.getContentResolver(), 
				Settings.System.NEXT_ALARM_FORMATTED);
		
        if (nextAlarmString.length() > 0) {
        	remoteViews.setViewVisibility(R.id.blockAlarm, View.VISIBLE);
        	remoteViews.setTextViewText(textAlarmID, nextAlarmString);
        	return true;
        }
        else {
        	remoteViews.setViewVisibility(R.id.blockAlarm, View.INVISIBLE);
        	return false;
        }
	}
	
	private PendingIntent getOpenAlarmClockIntent(Context context) {
		//TODO Add a delayed pending intent to refresh the clock if alarms are changed
		PackageManager packageManager = context.getPackageManager();
		Intent alarmClockIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
		
        try {
        	ComponentName cn = new ComponentName("com.android.deskclock", "com.android.deskclock.AlarmClock");
			packageManager.getActivityInfo(cn, PackageManager.GET_META_DATA);
			alarmClockIntent.setComponent(cn);
			
			PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, alarmClockIntent, 0);
			return pendingIntent;
		}
		catch (NameNotFoundException e) {
			Log.e(TAG, "");
		}
        return null;
	}
}
