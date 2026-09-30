package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R

class BelegQuickScanWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_beleg_quick_scan)

            // Intent: Scanner für Belege direkt starten
            val scanIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("EXTRA_START_TAB", "SCANNER")
                putExtra("EXTRA_QUICK_SCAN", true)
            }
            val scanPendingIntent = PendingIntent.getActivity(
                context,
                1001,
                scanIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_widget_scan, scanPendingIntent)

            // Intent: Dokumenten Tresor öffnen
            val dmsIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("EXTRA_START_TAB", "DOCANIZER")
            }
            val dmsPendingIntent = PendingIntent.getActivity(
                context,
                1002,
                dmsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_widget_dms, dmsPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_quick_scan_root, dmsPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
