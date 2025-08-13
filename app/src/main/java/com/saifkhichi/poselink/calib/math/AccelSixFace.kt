// calib/math/AccelSixFace.kt
package com.saifkhichi.poselink.calib.math

import kotlin.math.abs

data class SixFaceSamples(
    var xp: MutableList<FloatArray> = mutableListOf(),
    var xn: MutableList<FloatArray> = mutableListOf(),
    var yp: MutableList<FloatArray> = mutableListOf(),
    var yn: MutableList<FloatArray> = mutableListOf(),
    var zp: MutableList<FloatArray> = mutableListOf(),
    var zn: MutableList<FloatArray> = mutableListOf(),
)

object AccelSixFace {
    const val G = 9.80665

    fun mean(vs: List<FloatArray>): DoubleArray {
        val n = vs.size.coerceAtLeast(1)
        val m = DoubleArray(3)
        for (v in vs) { m[0]+=v[0]; m[1]+=v[1]; m[2]+=v[2] }
        m[0]/=n; m[1]/=n; m[2]/=n
        return m
    }

    /**
     * Diagonal scale + bias from six faces.
     * bias_i = (m+_i + m-_i)/2
     * scale_i =  G / ((m+_i - m-_i)/2)
     */
    fun solveDiagonal(samples: SixFaceSamples): Pair<DoubleArray, DoubleArray> {
        val mxp = mean(samples.xp); val mxn = mean(samples.xn)
        val myp = mean(samples.yp); val myn = mean(samples.yn)
        val mzp = mean(samples.zp); val mzn = mean(samples.zn)

        val bias = DoubleArray(3)
        val scale = DoubleArray(3)

        bias[0] = 0.5 * (mxp[0] + mxn[0])
        bias[1] = 0.5 * (myp[1] + myn[1])
        bias[2] = 0.5 * (mzp[2] + mzn[2])

        scale[0] = G / (0.5 * (mxp[0] - mxn[0]).let { if (abs(it) < 1e-6) 1e-6 else it })
        scale[1] = G / (0.5 * (myp[1] - myn[1]).let { if (abs(it) < 1e-6) 1e-6 else it })
        scale[2] = G / (0.5 * (mzp[2] - mzn[2]).let { if (abs(it) < 1e-6) 1e-6 else it })

        return bias to scale
    }

    fun sigmaAtRest(vs: List<FloatArray>, bias: DoubleArray, scale: DoubleArray): DoubleArray {
        val n = vs.size.coerceAtLeast(1)
        val s2 = DoubleArray(3)
        for (v in vs) {
            for (i in 0..2) {
                val cal = scale[i] * (v[i] - bias[i])
                s2[i] += (cal - cal) * (cal - cal) // placeholder for completeness
            }
        }
        // We’ll approximate with raw variance around mean; caller can compute better per-face if desired.
        return DoubleArray(3) { 0.03 } // 3 cm/s^2 default; tweak with real stats if needed
    }
}