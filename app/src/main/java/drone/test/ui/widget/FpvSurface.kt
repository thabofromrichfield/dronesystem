package drone.test.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import drone.test.core.LogBus
import dji.midware.usb.P3.UsbAccessoryService
import dji.sdk.camera.VideoFeeder
import dji.sdk.codec.DJICodecManager

/**
 * Live camera feed. Owns a [DJICodecManager] bound to this surface and pumps the
 * primary video feed into it. start()/stop() are idempotent and safe to call from
 * the activity lifecycle.
 */
class FpvSurface @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : SurfaceView(context, attrs, defStyle) {

    private var codecManager: DJICodecManager? = null
    private var streaming = false

    private val dataListener = object : VideoFeeder.VideoDataListener {
        override fun onReceive(videoBuffer: ByteArray?, size: Int) {
            val buffer = videoBuffer ?: return
            codecManager?.sendDataToDecoder(
                buffer,
                size,
                UsbAccessoryService.VideoStreamSource.Camera.index
            )
        }
    }

    private val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            if (codecManager == null) {
                codecManager = DJICodecManager(
                    context,
                    holder,
                    width,
                    height,
                    UsbAccessoryService.VideoStreamSource.Camera
                )
                LogBus.info("decoder bound ${width}x$height")
            }
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
            // DJICodecManager handles its own resizing through the codec.
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            codecManager?.cleanSurface()
            codecManager?.destroyCodec()
            codecManager = null
        }
    }

    init {
        holder.addCallback(surfaceCallback)
    }

    /** Attach to the primary video feed. Call after the aircraft is linked. */
    fun start() {
        if (streaming) return
        val feed = runCatching { VideoFeeder.getInstance().primaryVideoFeed }.getOrNull()
        if (feed == null) {
            LogBus.warn("video: primary feed unavailable")
            return
        }
        runCatching { feed.addVideoDataListener(dataListener) }
            .onSuccess {
                streaming = true
                LogBus.ok("video: primary feed streaming")
            }
            .onFailure { LogBus.err("video: ${it.message}") }
    }

    fun stop() {
        if (!streaming) return
        runCatching { VideoFeeder.getInstance().primaryVideoFeed.removeVideoDataListener(dataListener) }
            .onFailure { LogBus.err("video stop: ${it.message}") }
        streaming = false
        LogBus.info("video: feed detached")
    }

    fun release() {
        stop()
        holder.removeCallback(surfaceCallback)
        codecManager?.cleanSurface()
        codecManager?.destroyCodec()
        codecManager = null
    }
}
