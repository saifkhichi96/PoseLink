package com.saifkhichi.poselink.app.ui.sensors

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.saifkhichi.poselink.R
import java.util.Locale
import kotlin.math.min

enum class ImuKind {
    ACC,        // Accelerometer
    GYRO,       // Gyroscope
    MAG,        // Magnetometer
    ROT,        // Rotation vector (quaternion xyzw)
    GYRO_UNC,   // Uncalibrated gyroscope (xyz + bias)
    ACC_UNC,    // Uncalibrated accelerometer (xyz + bias)
    STAT        // Stationarity diagnostics
}

data class ImuSensorItem(
    val kind: ImuKind,
    var available: Boolean,
    var unit: String,              // "m/s²", "rad/s", "µT", "quat", "-"
    var vendor: String? = null,
    var name: String? = null,
    var resolution: Float? = null,
    var maxRange: Float? = null,
    var accuracy: Int? = null,     // also used as "samples" for STAT
    var timestampNs: Long? = null,
    // Values layout:
    // ACC / GYRO / MAG: [x,y,z]
    // ROT: [x,y,z,w]
    // *_UNC: [x,y,z,bx,by,bz]
    // STAT: [gyroNorm, accDev, stationaryFlag(1f or 0f)]
    var values: FloatArray = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ImuSensorItem

        if (available != other.available) return false
        if (resolution != other.resolution) return false
        if (maxRange != other.maxRange) return false
        if (accuracy != other.accuracy) return false
        if (timestampNs != other.timestampNs) return false
        if (kind != other.kind) return false
        if (unit != other.unit) return false
        if (vendor != other.vendor) return false
        if (name != other.name) return false
        if (!values.contentEquals(other.values)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = available.hashCode()
        result = 31 * result + (resolution?.hashCode() ?: 0)
        result = 31 * result + (maxRange?.hashCode() ?: 0)
        result = 31 * result + (accuracy ?: 0)
        result = 31 * result + (timestampNs?.hashCode() ?: 0)
        result = 31 * result + kind.hashCode()
        result = 31 * result + unit.hashCode()
        result = 31 * result + (vendor?.hashCode() ?: 0)
        result = 31 * result + (name?.hashCode() ?: 0)
        result = 31 * result + values.contentHashCode()
        return result
    }
}

class ImuSensorAdapter(
    private val items: MutableList<ImuSensorItem>
) : RecyclerView.Adapter<ImuSensorAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val card: MaterialCardView = v as MaterialCardView
        val title: TextView = v.findViewById(R.id.tv_title)
        val status: TextView = v.findViewById(R.id.tv_status)
        val meta: TextView = v.findViewById(R.id.tv_meta)
        val desc: TextView = v.findViewById(R.id.tv_desc)
        val x: TextView = v.findViewById(R.id.tv_x)
        val y: TextView = v.findViewById(R.id.tv_y)
        val z: TextView = v.findViewById(R.id.tv_z)
        val w: TextView = v.findViewById(R.id.tv_w)
        val bx: TextView = v.findViewById(R.id.tv_bx)
        val by: TextView = v.findViewById(R.id.tv_by)
        val bz: TextView = v.findViewById(R.id.tv_bz)
        val footer: TextView = v.findViewById(R.id.tv_footer)
        val labelW: TextView = v.findViewById(R.id.label_w)
        val labelBx: TextView = v.findViewById(R.id.label_bx)
        val labelBy: TextView = v.findViewById(R.id.label_by)
        val labelBz: TextView = v.findViewById(R.id.label_bz)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.imu_sensor_row, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, pos: Int) {
        val it = items[pos]

        h.title.text = when (it.kind) {
            ImuKind.ACC -> "Accelerometer"
            ImuKind.GYRO -> "Gyroscope"
            ImuKind.MAG -> "Magnetometer"
            ImuKind.ROT -> "Rotation Vector"
            ImuKind.GYRO_UNC -> "Uncalibrated Gyroscope"
            ImuKind.ACC_UNC -> "Uncalibrated Accelerometer"
            ImuKind.STAT -> "Stationarity Diagnostics"
        }

        // Contextual descriptions for PoseLink
        h.desc.text = when (it.kind) {
            ImuKind.ACC -> "Linear acceleration along X, Y, Z."
            ImuKind.GYRO -> "Angular velocity around X, Y, Z."
            ImuKind.MAG -> "Magnetic field along X, Y, Z."
            ImuKind.ROT -> "Device orientation as quaternion (x, y, z, w)."
            ImuKind.GYRO_UNC -> "Raw angular velocity with estimated bias."
            ImuKind.ACC_UNC -> "Raw acceleration with estimated bias."
            ImuKind.STAT -> "Real-time stillness estimate from gyro+acc."
        }

        // Status chip
        if (it.available) {
            h.status.text = "Available"
            h.status.setBackgroundColor("#4CAF50".toColorInt())
        } else {
            h.status.text = "Unavailable"
            h.status.setBackgroundColor("#9E9E9E".toColorInt())
        }

        // Meta
        val vendor = it.vendor ?: "-"
        val name = it.name ?: "-"
        val res = it.resolution?.let { r -> "Res $r" } ?: "Res -"
        val range = it.maxRange?.let { m -> "Range $m" } ?: "Range -"
        h.meta.text = "$vendor • $name • $res • $range"

        // Defaults: hide optional fields
        val showW = it.kind == ImuKind.ROT
        val showBias = it.kind == ImuKind.GYRO_UNC || it.kind == ImuKind.ACC_UNC
        h.labelW.isVisible = showW
        h.w.isVisible = showW
        h.labelBx.isVisible = showBias
        h.bx.isVisible = showBias
        h.labelBy.isVisible = showBias
        h.by.isVisible = showBias
        h.labelBz.isVisible = showBias
        h.bz.isVisible = showBias

        if (it.available) {
            fun f(v: Float) = String.format(Locale.US, "%.3f", v)
            val v = it.values
            h.x.text = f(v.getOrNull(0) ?: 0f)
            h.y.text = f(v.getOrNull(1) ?: 0f)
            h.z.text = f(v.getOrNull(2) ?: 0f)
            if (showW) h.w.text = f(v.getOrNull(3) ?: 0f)
            if (showBias) {
                h.bx.text = f(v.getOrNull(3) ?: 0f)
                h.by.text = f(v.getOrNull(4) ?: 0f)
                h.bz.text = f(v.getOrNull(5) ?: 0f)
            }

            val tSec = (it.timestampNs ?: 0L) / 1e9
            val footerUnit = it.unit
            h.footer.text = when (it.kind) {
                ImuKind.STAT -> {
                    val stationary = (v.getOrNull(2) ?: 0f) >= 0.5f
                    val samples = it.accuracy ?: 0
                    "stationary=$stationary • samples=$samples"
                }

                else -> "$footerUnit • acc ${it.accuracy ?: -1} • t=${
                    "%.3f".format(
                        Locale.US,
                        tSec
                    )
                } s"
            }
        } else {
            h.x.text = "--"; h.y.text = "--"; h.z.text = "--"
            h.w.isGone = true; h.labelW.isGone = true
            h.bx.isGone = true; h.by.isGone = true; h.bz.isGone = true
            h.labelBx.isGone = true; h.labelBy.isGone = true; h.labelBz.isGone = true
            h.footer.text = "${it.unit} • not present"
        }
    }

    override fun getItemCount(): Int = items.size

    fun update(kind: ImuKind, values: FloatArray, accuracy: Int, tNs: Long) {
        val idx = items.indexOfFirst { it.kind == kind }
        if (idx < 0) return
        val it = items[idx]
        if (!it.available) return
        val n = min(values.size, it.values.size)
        for (i in 0 until n) it.values[i] = values[i]
        it.accuracy = accuracy
        it.timestampNs = tNs
        notifyItemChanged(idx)
    }
}