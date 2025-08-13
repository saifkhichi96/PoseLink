// calib/CalibrationPagerAdapter.kt
package com.saifkhichi.poselink.calib

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.saifkhichi.poselink.calib.ui.*

class CalibrationPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    private val pages: List<() -> Fragment> = listOf(
        { WelcomeFragment() },
        { AccelFragment() },
        { GyroFragment() },
        { MagFragment() },
        { SummaryFragment() }
    )
    override fun getItemCount() = pages.size
    override fun createFragment(position: Int) = pages[position].invoke()
}