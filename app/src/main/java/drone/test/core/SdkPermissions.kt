package drone.test.core

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Runtime permissions the DJI SDK needs before registerApp can succeed.
 *
 * Every permission returned by [required] must also be declared in
 * AndroidManifest.xml for the running API level. Requesting one the manifest
 * does not declare returns DENIED without ever showing a dialog, so it can
 * never be granted — [allGranted] stays false forever and the boot screen never
 * reaches registerApp. That deadlocked the app on Android 11+, where storage
 * permissions were requested unconditionally while the manifest capped them at
 * API 29 (WRITE) and API 32 (READ).
 */
object SdkPermissions {

    const val REQUEST_CODE = 0x47

    fun required(): Array<String> {
        val sdk = Build.VERSION.SDK_INT
        val base = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.RECORD_AUDIO
        )
        // Storage only where the manifest still declares it. On Android 11+
        // WRITE is undeclared and on Android 13+ so is READ; asking for either
        // is a permanent DENIED.
        if (sdk <= 29) base.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (sdk <= 32) base.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        if (sdk >= Build.VERSION_CODES.S) {
            base.add(Manifest.permission.BLUETOOTH_SCAN)
            base.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        return base.toTypedArray()
    }

    fun missing(activity: Activity): Array<String> =
        required().filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

    /** Short names of what is still missing, for the boot screen log. */
    fun missingNames(activity: Activity): String =
        missing(activity).joinToString(", ") { it.substringAfterLast('.') }
            .ifEmpty { "none" }

    fun request(activity: Activity) {
        val need = missing(activity)
        if (need.isNotEmpty()) ActivityCompat.requestPermissions(activity, need, REQUEST_CODE)
    }

    fun allGranted(activity: Activity): Boolean = missing(activity).isEmpty()
}
