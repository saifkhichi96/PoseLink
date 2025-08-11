package io.a3dv.VIRec

/**
 * Helper class for providing content for ImuViewFragment.
 *
 */
object ImuViewContent {
    val ITEMS: MutableList<SingleAxis?> = ArrayList<SingleAxis?>()

    /**
     * A map of items, by ID.
     */
    val ITEM_MAP: MutableMap<String?, SingleAxis?> = HashMap<String?, SingleAxis?>()

    init {
        addItem(
            SingleAxis(
                "Accel X", 0.0.toString(),
                "X axis of accelerometer", "m/s<sup><small>2</small></sup>"
            )
        )
        addItem(
            SingleAxis(
                "Accel Y", 0.0.toString(),
                "Y axis of accelerometer", "m/s<sup><small>2</small></sup>"
            )
        )
        addItem(
            SingleAxis(
                "Accel Z", 0.0.toString(),
                "Z axis of accelerometer", "m/s<sup><small>2</small></sup>"
            )
        )

        addItem(
            SingleAxis(
                "Gyro X", 0.0.toString(),
                "X axis of gyroscope", "rad/s"
            )
        )
        addItem(
            SingleAxis(
                "Gyro Y", 0.0.toString(),
                "Y axis of gyroscope", "rad/s"
            )
        )
        addItem(
            SingleAxis(
                "Gyro Z", 0.0.toString(),
                "Z axis of gyroscope", "rad/s"
            )
        )

        addItem(
            SingleAxis(
                "Mag X", 0.0.toString(),
                "X axis of magnetometer", "&mu T"
            )
        )
        addItem(
            SingleAxis(
                "Mag Y", 0.0.toString(),
                "Y axis of magnetometer", "&mu T"
            )
        )
        addItem(
            SingleAxis(
                "Mag Z", 0.0.toString(),
                "Z axis of magnetometer", "&mu T"
            )
        )
    }

    private fun addItem(item: SingleAxis) {
        ITEMS.add(item)
        ITEM_MAP.put(item.id, item)
    }

    class SingleAxis(
        val id: String?,
        var content: String,
        val details: String?,
        val unit: String?
    ) {
        override fun toString(): String {
            return content
        }
    }
}
