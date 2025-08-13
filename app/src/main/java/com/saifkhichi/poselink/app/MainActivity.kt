package com.saifkhichi.poselink.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.saifkhichi.poselink.app.camera.CameraActivity
import com.saifkhichi.poselink.calib.CalibRepository
import com.saifkhichi.poselink.calib.CalibrationActivity
import com.saifkhichi.poselink.databinding.MainActivityBinding
import java.util.Arrays

/**
 * MainActivity is the launcher and entry point of the app.
 *
 * This activity checks for required permissions (e.g., camera access) on startup.
 * If all permissions are granted, it immediately redirects the user to CameraActivity.
 * If any permission is missing, it requests them and exits the app if not granted.
 *
 * This ensures users cannot proceed without granting essential permissions, providing
 * a secure and predictable experience from the start.
 */
class MainActivity : Activity() {
    private lateinit var binding: MainActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = MainActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        checkPermissions()
    }

    /**
     * Checks the dynamically-controlled permissions and requests missing permissions from end user.
     * see https://developer.here.com/documentation/android-starter/dev_guide/topics/request-android-permissions.html
     */
    private fun checkPermissions() {
        val missingPermissions: MutableList<String?> = ArrayList<String?>()
        // check all required dynamic permissions
        for (permission in REQUIRED_SDK_PERMISSIONS) {
            val result = ContextCompat.checkSelfPermission(this, permission)
            if (result != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission)
            }
        }
        if (!missingPermissions.isEmpty()) {
            // request all missing permissions
            val permissions = missingPermissions
                .toTypedArray<String?>()
            ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE_ASK_PERMISSIONS)
        } else {
            val grantResults = IntArray(REQUIRED_SDK_PERMISSIONS.size)
            Arrays.fill(grantResults, PackageManager.PERMISSION_GRANTED)
            onRequestPermissionsResult(
                REQUEST_CODE_ASK_PERMISSIONS, REQUIRED_SDK_PERMISSIONS,
                grantResults
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_ASK_PERMISSIONS) {
            for (index in permissions.indices.reversed()) {
                if (grantResults[index] != PackageManager.PERMISSION_GRANTED) {
                    // exit the app if one permission is not granted
                    Toast.makeText(
                        this, ("Required permission '" + permissions[index]
                                + "' not granted, exiting"), Toast.LENGTH_LONG
                    ).show()
                    finish()
                    return
                }
            }
            // all permissions were granted
            proceedAfterPermissions()
        }
    }

    private fun proceedAfterPermissions() {
        // Check if calibration JSON exists
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val calibJson = prefs.getString(CalibRepository.KEY_CALIB_JSON, null)

        if (calibJson.isNullOrEmpty()) {
            // No calibration yet – prompt the user to calibrate
            AlertDialog.Builder(this)
                .setTitle("Device Calibration")
                .setMessage("Run one-time IMU calibration now? You can redo it later from settings.")
                .setPositiveButton("Calibrate") { _, _ ->
                    val intent = Intent(this, CalibrationActivity::class.java)
                    startActivity(intent)
                }
                .setNegativeButton("Skip") { _, _ ->
                    // Proceed straight to the camera if the user skips
                    startCameraActivity()
                }
                .show()
        } else {
            // Calibration exists – go straight to the camera
            startCameraActivity()
        }
    }

    private fun startCameraActivity() {
        val intent = Intent(this, CameraActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK
            )
        }
        startActivity(intent)
    }

    companion object {
        private val REQUIRED_SDK_PERMISSIONS = arrayOf<String>(
            Manifest.permission.CAMERA,
        )

        private const val REQUEST_CODE_ASK_PERMISSIONS = 5947
    }
}
