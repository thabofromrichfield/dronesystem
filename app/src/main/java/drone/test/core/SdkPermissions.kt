package drone.test.core

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/** Runtime permissions the DJI SDK needs before registerApp can succeed. */
object SdkPermissions {

    const val REQUEST_CODE = 0x47

    fun required(): Array<String> {
        val base = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.RECORD_AUDIO
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            base.add(Manifest.permission.BLUETOOTH_SCAN)
            base.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        return base.toTypedArray()
    }

    fun missing(activity: Activity): Array<String> =
        required().filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

    fun request(activity: Activity) {
        val need = missing(activity)
        if (need.isNotEmpty()) ActivityCompat.requestPermissions(activity, need, REQUEST_CODE)
    }

    fun allGranted(activity: Activity): Boolean = missing(activity).isEmpty()
}
