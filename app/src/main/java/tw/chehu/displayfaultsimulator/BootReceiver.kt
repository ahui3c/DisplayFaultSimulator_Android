package tw.chehu.displayfaultsimulator

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in SUPPORTED_ACTIONS) return
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) OnlineUpdateActivity.cleanupAfterUpdate(context)
        val settings = LineSettings(context)
        if (!settings.startOnBoot || !settings.serviceEnabled || !Settings.canDrawOverlays(context)) return

        val service = Intent(context, LineOverlayService::class.java)
            .setAction(LineOverlayService.ACTION_RESTORE)
        context.startForegroundService(service)
    }

    companion object {
        private val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED
        )
    }
}
