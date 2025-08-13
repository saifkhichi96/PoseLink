// calib/math/MagCal.kt
package com.saifkhichi.poselink.calib.math

import kotlin.math.sqrt

object MagCal {
    data class Accum(
        val xs: MutableList<Float> = mutableListOf(),
        val ys: MutableList<Float> = mutableListOf(),
        val zs: MutableList<Float> = mutableListOf()
    )

    fun accumulate(acc: Accum, v: FloatArray) {
        acc.xs += v[0]; acc.ys += v[1]; acc.zs += v[2]
    }

    fun mean(xs: List<Float>) = xs.sum() / xs.size.coerceAtLeast(1)
    fun std(xs: List<Float>, mu: Float): Double {
        var s = 0.0
        for (x in xs) { val d = x - mu; s += d*d }
        return sqrt(s / xs.size.coerceAtLeast(1).toDouble())
    }

    /** Simple hard-iron center + diagonal soft-iron scaling by equalizing std across axes. */
    fun solve(acc: Accum): Pair<DoubleArray, Array<DoubleArray>> {
        val cx = mean(acc.xs); val cy = mean(acc.ys); val cz = mean(acc.zs)
        val sx = std(acc.xs, cx); val sy = std(acc.ys, cy); val sz = std(acc.zs, cz)
        val sRef = (sx + sy + sz) / 3.0
        val S = arrayOf(
            doubleArrayOf(sRef / sx.coerceAtLeast(1e-6), 0.0, 0.0),
            doubleArrayOf(0.0, sRef / sy.coerceAtLeast(1e-6), 0.0),
            doubleArrayOf(0.0, 0.0, sRef / sz.coerceAtLeast(1e-6))
        )
        val h = doubleArrayOf(cx.toDouble(), cy.toDouble(), cz.toDouble())
        return h to S
    }
}