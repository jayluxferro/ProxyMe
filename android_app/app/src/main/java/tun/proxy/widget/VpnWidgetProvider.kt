package tun.proxy.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import tun.proxy.MainActivity
import tun.proxy.R
import tun.proxy.service.Tun2SocksVpnService

class VpnWidgetProvider : AppWidgetProvider() {

    companion object {
        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, VpnWidgetProvider::class.java))
            for (id in ids) {
                updateWidget(context, manager, id)
            }
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_vpn)
            val isActive = Tun2SocksVpnService.isActive

            views.setTextViewText(
                R.id.widget_status,
                if (isActive) context.getString(R.string.status_connected)
                else context.getString(R.string.status_disconnected)
            )
            views.setTextColor(
                R.id.widget_status,
                context.getColor(if (isActive) R.color.colorSuccess else R.color.colorError)
            )
            views.setInt(
                R.id.widget_dot,
                "setColorFilter",
                context.getColor(if (isActive) R.color.colorSuccess else R.color.colorError)
            )

            // Tap widget → open app
            val openIntent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            manager.updateAppWidget(widgetId, views)
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        for (id in widgetIds) {
            updateWidget(context, manager, id)
        }
    }
}
