package drone.test.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.SurfaceView
import drone.test.core.LogBus

/**
 * Stand-in for the `full` flavour's video surface. Same package and class name,
 * so `activity_control.xml` inflates unchanged in either build.
 *
 * It renders nothing: without the DJI SDK there is no decoder and no feed. The
 * HUD draws over the black surface, which is what makes the interface
 * reviewable on an emulator.
 */
class FpvSurface @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : SurfaceView(context, attrs, defStyle) {

    fun start() = LogBus.info("fpv: sim flavour, surface stays black")

    fun stop() = Unit

    fun release() = Unit
}
