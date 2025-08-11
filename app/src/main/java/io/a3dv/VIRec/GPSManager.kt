package io.a3dv.VIRec

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.widget.TextView
import timber.log.Timber
import java.io.BufferedWriter
import java.io.FileWriter
import java.io.IOException
import java.util.Date
import kotlin.concurrent.Volatile

class GPSManager(private val activity: Activity) : LocationListener {
    private var mGpsStatusText: TextView

    private class LocationPacket(// nanoseconds
        var timestamp: Long, // milliseconds
        var unixTime: Long,
        var latitude: Double,
        var longitude: Double,
        var altitude: Double,
        var speed: Float
    ) {
        override fun toString(): String {
            val delimiter = ","
            return timestamp.toString() +
                    delimiter + latitude +
                    delimiter + longitude +
                    delimiter + altitude +
                    delimiter + speed +
                    delimiter + unixTime + "000000"
        }
    }

    private val mGpsManager: LocationManager

    @Volatile
    private var mRecordingLocationData = false
    private var mDataWriter: BufferedWriter? = null

    //    private final Deque<LocationPacket> mLocationData = new ArrayDeque<>();
    init {
        mGpsManager = activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        mGpsStatusText = activity.findViewById<View?>(R.id.gps_status) as TextView
        if (mGpsManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            mGpsStatusText.setText(R.string.gpsLooking)
        } else {
            mGpsStatusText.setText(R.string.gpsStatusDisabled)
        }
    }

    @SuppressLint("MissingPermission")
    fun startRecording(captureResultFile: String) {
        mGpsManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0f, this)

        try {
            mDataWriter = BufferedWriter(FileWriter(captureResultFile, false))
            mDataWriter!!.write(GpsHeader)
            mRecordingLocationData = true
        } catch (err: IOException) {
            Timber.e(
                err, "IOException in opening location data writer at %s",
                captureResultFile
            )
        }
    }

    fun stopRecording() {
        if (mRecordingLocationData) {
            mRecordingLocationData = false
            try {
                mDataWriter!!.flush()
                mDataWriter!!.close()
            } catch (err: IOException) {
                Timber.e(err, "IOException in closing location data writer")
            }
            mDataWriter = null
        }
    }

    @Deprecated("")
    override fun onStatusChanged(provider: String, status: Int, extras: Bundle?) {
        Timber.d("GPS status changed | provider: %s, status: %i", provider, status)
    }

    override fun onProviderEnabled(provider: String) {
        Timber.d("GPS provider enabled | provider: %s", provider)

        mGpsStatusText = activity.findViewById<View?>(R.id.gps_status) as TextView
        mGpsStatusText.setText(R.string.gpsLooking)
    }

    override fun onProviderDisabled(provider: String) {
        Timber.d("GPS provider disabled | provider: %s", provider)

        mGpsStatusText = activity.findViewById<View?>(R.id.gps_status) as TextView
        mGpsStatusText.setText(R.string.gpsStatusDisabled)
    }

    @SuppressLint("SetTextI18n")
    override fun onLocationChanged(location: Location) {
        val unixTime = System.currentTimeMillis()

        val latitude = location.latitude
        val longitude = location.longitude
        val altitude = location.altitude
        val speed = location.speed

        val lp = LocationPacket(
            location.elapsedRealtimeNanos,
            unixTime,
            latitude,
            longitude,
            altitude,
            speed
        )

        //        mLocationData.add(lp);
        if (mRecordingLocationData) {
            try {
                mDataWriter!!.write(lp.toString() + "\n")
            } catch (ioe: IOException) {
                Timber.e(ioe)
            }
        }

        val date = Date(location.time)

        mGpsStatusText = activity.findViewById<View?>(R.id.gps_status) as TextView
        mGpsStatusText.setText(
            ("Latitude: " + latitude + "\nLongitude: " + longitude
                    + "\nTime: " + DateFormat.format("yyyy-MM-dd HH:mm:ss", date)
                    + "\nAccuracy: " + location.accuracy + "m")
        )
    }

    @SuppressLint("MissingPermission")
    fun register() {
        mGpsManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0f, this)
    }

    fun unregister() {
        mGpsManager.removeUpdates(this)
    }

    companion object {
        var GpsHeader: String =
            "Timestamp[nanosecond],latitude[degrees],longitude[degrees],altitude[meters],speed[meters/second],Unix time[nanosecond]\n"
    }
}
