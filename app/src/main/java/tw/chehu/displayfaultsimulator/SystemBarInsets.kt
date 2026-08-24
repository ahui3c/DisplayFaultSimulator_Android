package tw.chehu.displayfaultsimulator

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowInsets

/**
 * Keeps settings screens outside system bars on Android versions that enforce
 * edge-to-edge layouts. The fault overlay is a service window and intentionally
 * does not use this helper, so effects can still cover the entire display.
 */
fun Activity.applySettingsSystemBarInsets(root: View) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return

    val initialLeft = root.paddingLeft
    val initialTop = root.paddingTop
    val initialRight = root.paddingRight
    val initialBottom = root.paddingBottom

    root.setOnApplyWindowInsetsListener { view, windowInsets ->
        val safeInsets = windowInsets.getInsets(
            WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
        )
        view.setPadding(
            initialLeft + safeInsets.left,
            initialTop + safeInsets.top,
            initialRight + safeInsets.right,
            initialBottom + safeInsets.bottom
        )
        windowInsets
    }
    root.requestApplyInsets()
}
