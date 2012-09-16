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
import android.provider.Settings;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

public class ClockWidget extends AppWidgetProvider {
	private static final String TAG = ClockWidget.class.getSimpleName();
	
	// AlarmClock component change depending on the implementation
	// see http://stackoverflow.com/questions/3590955/intent-to-launch-the-clock-application-on-android
	private static final String[][] ALARM_CLOCK_IMPL = {
		{"Standard Alarm Clock", "com.android.deskclock", "com.android.deskclock.AlarmClock"},
		{"Nexus Alarm Clock", "com.google.android.deskclock", "com.android.deskclock.AlarmClock"},
		{"HTC Alarm Clock", "com.htc.android.worldclock", "com.htc.android.worldclock.WorldClockTabControl" },
        {"Moto Blur Alarm Clock", "com.motorola.blur.alarmclock",  "com.motorola.blur.alarmclock.AlarmClock"},
        {"Samsung Galaxy Clock", "com.sec.android.app.clockpackage","com.sec.android.app.clockpackage.ClockPackage"}
	};
	private static final int ALARM_CLOCK_IMPL_NAME = 0;
	private static final int ALARM_CLOCK_IMPL_PACKAGE = 1;
	private static final int ALARM_CLOCK_IMPL_CLASS = 2;
	
	private static AlarmManager alarmManager = null;
	private static PendingIntent pendingRefresh = null;
	private static boolean checkedOpenAlarm = false;
	private static PendingIntent pendingOpenAlarmScreen = null;
	
	@Override
	public void onEnabled(Context context) {
		super.onEnabled(context);
		
		if (alarmManager == null) {
			alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
			int update_interval_ms = context.getResources().getInteger(R.integer.update_interval_ms);
			
			Intent intent = new Intent(context, ClockWidget.class);
			intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
			intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[] {1});	// appWidgetIds must have a size > 1 for onUpdate to be called
			pendingRefresh = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
			
			// Set an alarm to run on top of each minute
			long currentTime = new Date().getTime();
			long millisToNextTopMinute = 60000 - (currentTime % 60000) + 1000;
			alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME, millisToNextTopMinute, update_interval_ms, pendingRefresh);
		}
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		Log.i(TAG, "Received intent " + intent.getAction());
		super.onReceive(context, intent);
	}

	@Override
	public void onDisabled(Context context) {
		if (alarmManager != null) alarmManager.cancel(pendingRefresh);
		super.onDisabled(context);
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
		
		String dateFormatString;
		if (DateFormat.getDateFormatOrder(context)[0] == DateFormat.DATE) {
			// General format: day month year
			dateFormatString = context.getString(R.string.date_format_all);
		}
		else {
			// US Format: month day year
			dateFormatString = context.getString(R.string.date_format_us);
		}
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
		if (pendingOpenAlarmScreen == null && !checkedOpenAlarm) {
			//TODO Add a delayed pending intent to refresh the clock if alarms are changed
			checkedOpenAlarm = true;
			PackageManager packageManager = context.getPackageManager();
			Intent alarmClockIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
			
			ComponentName cn = null;
			for (String[] alarmClockImpl : ALARM_CLOCK_IMPL) {
				try {
					cn = new ComponentName(alarmClockImpl[ALARM_CLOCK_IMPL_PACKAGE], alarmClockImpl[ALARM_CLOCK_IMPL_CLASS]);
					packageManager.getActivityInfo(cn, PackageManager.GET_META_DATA);
				}
				catch (NameNotFoundException e) {
					// Try another implementation
					continue;
				}
				Log.d(TAG, String.format("Using %s implementation", alarmClockImpl[ALARM_CLOCK_IMPL_NAME]));
				break;
			}
			if (cn != null) {
				alarmClockIntent.setComponent(cn);
				pendingOpenAlarmScreen = PendingIntent.getActivity(context, 0, alarmClockIntent, 0);
			}
			else {
				Log.e(TAG, "Unable to find correct AlarmClock implementation on this device");
			}
		}
        return pendingOpenAlarmScreen;
	}

}
