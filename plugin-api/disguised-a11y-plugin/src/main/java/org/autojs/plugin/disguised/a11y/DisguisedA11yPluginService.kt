package org.autojs.plugin.disguised.a11y

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.google.android.accessibility.selecttospeak.SelectToSpeakService
import org.autojs.plugin.disguised.a11y.api.IDisguisedA11yPlugin
import org.autojs.plugin.disguised.a11y.api.NodeSnapshot
import org.autojs.plugin.disguised.a11y.api.PluginInfo

class DisguisedA11yPluginService : Service() {

    private val binder = object : IDisguisedA11yPlugin.Stub() {
        override fun getInfo(): PluginInfo = PluginInfo().apply {
            name = getString(R.string.app_name)
            description = getString(R.string.plugin_description)
            author = "AutoJs6"
            versionName = BuildConfig.VERSION_NAME
            versionCode = BuildConfig.VERSION_CODE.toLong()
            id = "disguised-a11y"
            engine = "disguised-a11y"
            variant = "select-to-speak"
        }

        override fun captureActiveWindow(): NodeSnapshot? {
            val root = SelectToSpeakService.rootInActiveWindowOrNull() ?: return null
            return try {
                NodeSnapshotBuilder.from(root)
            } finally {
                root.recycle()
            }
        }

        override fun isServiceConnected(): Boolean = SelectToSpeakService.isConnected()
    }

    override fun onBind(intent: Intent?): IBinder = binder
}
