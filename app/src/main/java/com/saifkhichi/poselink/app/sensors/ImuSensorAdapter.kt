package com.saifkhichi.poselink.app.ui.sensors

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.saifkhichi.poselink.R
import java.util.Locale
import kotlin.math.max

enum class ImuKind { ACC, GYRO, MAG }

data class ImuSensorItem(
    val kind: ImuKind,
    var available: Boolean,
    var unit: String,              // "m/s²", "rad/s", "µT"
    var vendor: String? = null,
    var name: String? = null,
    var resolution: Float? = null,
    var maxRange: Float? = null,
    var accuracy: Int? = null,
    var timestampNs: Long? = null,
    var values: FloatArray = floatArrayOf(0f, 0f, 0f)
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
        val footer: TextView = v.findViewById(R.id.tv_footer)
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
        }

        // Contextual descriptions for PoseLink
        h.desc.text = when (it.kind) {
            ImuKind.ACC -> "Measures linear acceleration (m/s²) along X, Y, Z. " +
                    "In PoseLink, used to detect body movement speed and direction."

            ImuKind.GYRO -> "Measures angular velocity (rad/s) around X, Y, Z. " +
                    "Helps track device rotation to maintain pose orientation."

            ImuKind.MAG -> "Measures magnetic field (µT) along X, Y, Z. " +
                    "Used as compass heading to stabilize orientation in global space."
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

        // Values + footer
        if (it.available) {
            h.x.text = String.format(Locale.US, "%.3f", it.values.getOrNull(0) ?: 0f)
            h.y.text = String.format(Locale.US, "%.3f", it.values.getOrNull(1) ?: 0f)
            h.z.text = String.format(Locale.US, "%.3f", it.values.getOrNull(2) ?: 0f)

            val acc = it.accuracy ?: -1
            val tSec = (it.timestampNs ?: 0L) / 1e9
            h.footer.text = "${it.unit} • acc $acc • t=${"%.3f".format(Locale.US, tSec)} s"

            h.card.alpha = 1.0f
        } else {
            h.x.text = "--"
            h.y.text = "--"
            h.z.text = "--"
            h.footer.text = "${it.unit} • not present"
            h.card.alpha = 0.5f
        }
    }

    override fun getItemCount(): Int = items.size

    fun update(kind: ImuKind, values: FloatArray, accuracy: Int, tNs: Long) {
        val idx = items.indexOfFirst { it.kind == kind }
        if (idx < 0) return
        val it = items[idx]
        if (!it.available) return
        val n = max(0, values.size.coerceAtMost(3))
        for (i in 0 until n) it.values[i] = values[i]
        it.accuracy = accuracy
        it.timestampNs = tNs
        notifyItemChanged(idx)
    }
}