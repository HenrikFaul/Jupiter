package com.jupiter.filemanager.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * AppWidget receiver that binds the [FavoritesWidget] Glance implementation to the
 * platform AppWidget host. Registered in AndroidManifest.xml with the
 * APPWIDGET_UPDATE intent filter and the widget provider meta-data.
 */
class FavoritesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FavoritesWidget()
}
