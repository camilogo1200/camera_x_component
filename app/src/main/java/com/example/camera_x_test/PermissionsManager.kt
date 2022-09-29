package ironark.com.charge.utils

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner


class PermissionsManager(
    private val activity: Activity,
    private val registry: ActivityResultRegistry
) : DefaultLifecycleObserver {
    private var grantedListener: (() -> Unit)? = null
    private var deniedListener: (() -> Unit)? = null

    override fun onCreate(owner: LifecycleOwner) {
        requestPermissionLauncher = registry.register(
            "PermissionManager",
            owner,
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                grantedListener?.invoke()
            } else {
                deniedListener?.invoke()
            }
        }
    }

    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>

    fun checkPermission(permissionString: String) = ContextCompat.checkSelfPermission(
        activity, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    fun checkPermissions(permissionArray: Iterable<String>) = permissionArray.all {
        checkPermission(it)
    }

    fun requestPermission(
        permission: String,
        rationaleListener: ((String) -> Unit)? = null,
        grantedListener: (() -> Unit)? = null,
        deniedListener: (() -> Unit)? = null
    ) {
        if (ActivityCompat.shouldShowRequestPermissionRationale(
                activity, permission
            )
        ) {
            rationaleListener?.invoke(permission)
        } else {
            requestPermission(permission, grantedListener, deniedListener)
        }
    }

    fun requestPermission(
        permission: String,
        grantedListener: (() -> Unit)? = null,
        deniedListener: (() -> Unit)? = null
    ) {
        this.grantedListener = grantedListener
        this.deniedListener = deniedListener
        requestPermissionLauncher.launch(permission)
    }
}
