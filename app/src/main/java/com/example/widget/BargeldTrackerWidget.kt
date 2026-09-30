package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R

class BargeldTrackerWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_bargeld_tracker)

            // Intent 1: Neue Bar-Ausgabe erfassen
            val addExpenseIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("EXTRA_START_TAB", "DASHBOARD")
                putExtra("EXTRA_OPEN_ACTION", "CASH_EXPENSE")
            }
            val expensePendingIntent = PendingIntent.getActivity(
                context,
                2001,
                addExpenseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_widget_add_expense, expensePendingIntent)

            // Intent 2: Bar-Beleg scannen
            val scanReceiptIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("EXTRA_START_TAB", "SCANNER")
                putExtra("EXTRA_QUICK_SCAN", true)
            }
            val receiptPendingIntent = PendingIntent.getActivity(
                context,
                2002,
                scanReceiptIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_widget_scan_receipt, receiptPendingIntent)

            // Intent 3: Ausgaben- & Beleg-Check öffnen
            val checkIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("EXTRA_START_TAB", "DASHBOARD")
                putExtra("EXTRA_OPEN_RECONCILIATION", true)
            }
            val checkPendingIntent = PendingIntent.getActivity(
                context,
                2003,
                checkIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_widget_check, checkPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_bargeld_root, checkPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
