package com.saifkhichi.poselink.app

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Range
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.saifkhichi.poselink.R
import com.saifkhichi.poselink.app.camera.DesiredCameraSetting

class SettingsFragment : PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener {
    /**
     * Checks that a preference is a valid numerical value
     */
    var checkISOListener: Preference.OnPreferenceChangeListener =
        Preference.OnPreferenceChangeListener { preference: Preference?, newValue: Any? ->
            checkIso(newValue!!)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings, rootKey)

        PreferenceManager.getDefaultSharedPreferences(
            requireActivity()
        ).registerOnSharedPreferenceChangeListener(this)

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(
            requireActivity()
        )

        val cameraList = preferenceManager.findPreference<ListPreference?>("prefCamera")
        val cameraList2 = preferenceManager.findPreference<ListPreference?>("prefCamera2")
        val cameraRez = preferenceManager.findPreference<ListPreference?>("prefSizeRaw")

        // val cameraFocus: ListPreference? = preferenceManager?.findPreference("prefFocusDistance");
        val prefISO = preferenceScreen.findPreference<EditTextPreference?>("prefISO")
        val prefExposureTime =
            preferenceScreen.findPreference<EditTextPreference?>("prefExposureTime")

        checkNotNull(prefISO)
        prefISO.onPreferenceChangeListener = checkISOListener

        try {
            val activity: Activity? = getActivity()
            val manager = activity!!.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraSize = manager.cameraIdList.size
            val entries = arrayOfNulls<CharSequence>(cameraSize)
            val entriesValues = arrayOfNulls<CharSequence>(cameraSize)
            for (i in 0..<cameraSize) {
                val cameraId = manager.cameraIdList[i]
                val characteristics = manager.getCameraCharacteristics(cameraId)
                try {
                    if (characteristics.get(CameraCharacteristics.LENS_FACING) ==
                        CameraMetadata.LENS_FACING_BACK
                    ) {
                        entries[i] = "$cameraId - Lens Facing Back"
                    } else if (characteristics.get(CameraCharacteristics.LENS_FACING) ==
                        CameraMetadata.LENS_FACING_FRONT
                    ) {
                        entries[i] = "$cameraId - Lens Facing Front"
                    } else {
                        entries[i] = "$cameraId - Lens External"
                    }
                } catch (e: NullPointerException) {
                    e.printStackTrace()
                    entries[i] = "$cameraId - Lens Facing Unknown"
                }
                entriesValues[i] = cameraId
            }

            // Update our settings entry
            checkNotNull(cameraList)
            cameraList.entries = entries
            cameraList.entryValues = entriesValues
            cameraList.setDefaultValue(entriesValues[0])

            if (sharedPreferences.getString("prefCamera", "None") == "None") {
                cameraList.setValue(entriesValues[0] as String?)
            }

            checkNotNull(cameraList2)
            if (cameraSize > 1) {
                cameraList2.entries = entries
                cameraList2.entryValues = entriesValues
                cameraList2.setDefaultValue(entriesValues[1])

                if (sharedPreferences.getString("prefCamera2", "None") == "None") {
                    cameraList2.setValue(entriesValues[1] as String?)
                }
            } else {
                cameraList2.isEnabled = false
            }


            // Do not call "cameraList.setValueIndex(0)" which will invoke onSharedPreferenceChanged
            // if the previous camera is not 0, and cause null pointer exception.

            // Right now we have selected the first camera, so lets populate the resolution list
            // We should just use the default if there is not a shared setting yet
            val characteristics = manager.getCameraCharacteristics(
                sharedPreferences.getString("prefCamera", entriesValues[0].toString())!!
            )
            val streamConfigurationMap = characteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
            )
            val sizes =
                streamConfigurationMap!!.getOutputSizes(MediaRecorder::class.java)

            val rezSize = sizes.size
            val rez = arrayOfNulls<CharSequence>(rezSize)
            val rezValues = arrayOfNulls<CharSequence>(rezSize)
            var defaultIndex = 0
            for (i in sizes.indices) {
                rez[i] = sizes[i]!!.width.toString() + "x" + sizes[i]!!.height
                rezValues[i] = sizes[i]!!.width.toString() + "x" + sizes[i]!!.height
                if (sizes[i]!!.width + sizes[i]!!.height ==
                    DesiredCameraSetting.mDesiredFrameWidth +
                    DesiredCameraSetting.mDesiredFrameHeight
                ) {
                    defaultIndex = i
                }
            }

            checkNotNull(cameraRez)
            cameraRez.entries = rez
            cameraRez.entryValues = rezValues
            cameraRez.setDefaultValue(rezValues[defaultIndex])

            val isoRange = characteristics.get(
                CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE
            )
            if (isoRange != null) {
                val rangeStr = "[" + isoRange.getLower() + "," + isoRange.getUpper() + "] (1)"
                prefISO.dialogTitle = "Adjust ISO in range $rangeStr"
            }

            val exposureTimeRangeNs = characteristics.get(
                CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE
            )
            if (exposureTimeRangeNs != null) {
                val exposureTimeRangeMs = Range(
                    (exposureTimeRangeNs.getLower()!!.toFloat() / 1e6).toFloat(),
                    (exposureTimeRangeNs.getUpper()!!.toFloat() / 1e6).toFloat()
                )
                val rangeStr = "[" + exposureTimeRangeMs.getLower() + "," +
                        exposureTimeRangeMs.getUpper() + "] (ms)"
                checkNotNull(prefExposureTime)
                prefExposureTime.dialogTitle = "Adjust exposure time in range $rangeStr"
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        } catch (e: NullPointerException) {
            e.printStackTrace()
        }
    }

    private fun checkIso(newValue: Any): Boolean {
        if (newValue.toString() != "" && newValue.toString().matches("\\d*".toRegex())) {
            return true
        } else {
            Toast.makeText(
                activity,
                "$newValue is not a valid number!", Toast.LENGTH_SHORT
            ).show()
            return false
        }
    }

    private fun switchPrefCameraValues(main: String?, secondaryKey: String) {
        val list: ListPreference? =
            checkNotNull(preferenceManager.findPreference(secondaryKey))
        val entryValues = list!!.entryValues
        val index = listOf(*entryValues).indexOf(main)

        if (index + 1 < entryValues.size) {
            list.setValue(entryValues[index + 1] as String?)
        } else {
            list.setValue(entryValues[index - 1] as String?)
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (key == "prefCamera") {
            try {
                val cameraId: String = sharedPreferences.getString("prefCamera", "0")!!
                val camera2Id: String = sharedPreferences.getString("prefCamera2", "1")!!

                if (cameraId == camera2Id) {
                    switchPrefCameraValues(cameraId, "prefCamera2")
                }

                val activity: Activity? = getActivity()
                val manager = activity!!.getSystemService(Context.CAMERA_SERVICE) as CameraManager

                val characteristics: CameraCharacteristics = manager.getCameraCharacteristics(cameraId)
                characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
                )?.let { streamConfigurationMap ->
                    val sizes = streamConfigurationMap.getOutputSizes(MediaRecorder::class.java)

                    val rezSize = sizes.size
                    val rez = arrayOfNulls<CharSequence>(rezSize)
                    val rezValues = arrayOfNulls<CharSequence>(rezSize)
                    var defaultIndex = 0
                    for (i in sizes.indices) {
                        rez[i] = sizes[i].width.toString() + "x" + sizes[i].height
                        rezValues[i] = sizes[i].width.toString() + "x" + sizes[i].height
                        if (sizes[i]!!.width + sizes[i].height ==
                            DesiredCameraSetting.mDesiredFrameWidth +
                            DesiredCameraSetting.mDesiredFrameHeight
                        ) {
                            defaultIndex = i
                        }
                    }
                    val cameraRez: ListPreference = checkNotNull(
                        preferenceManager.findPreference("prefSizeRaw")
                    )
                    cameraRez.entries = rez
                    cameraRez.entryValues = rezValues
                    cameraRez.setValueIndex(defaultIndex)
                }
            } catch (e: CameraAccessException) {
                e.printStackTrace()
            } catch (e: NullPointerException) {
                e.printStackTrace()
            }
        } else if (key == "prefCamera2") {
            val cameraId: String = sharedPreferences.getString("prefCamera", "0")!!
            val camera2Id: String = sharedPreferences.getString("prefCamera2", "1")!!

            if (cameraId == camera2Id) {
                switchPrefCameraValues(camera2Id, "prefCamera")
            }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context !is OnFragmentInteractionListener) {
            throw RuntimeException(
                context.toString()
                        + " must implement OnFragmentInteractionListener"
            )
        }
    }

    interface OnFragmentInteractionListener
}