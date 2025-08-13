package com.saifkhichi.poselink.calib

import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.saifkhichi.poselink.R

class CalibrationActivity : AppCompatActivity() {

    private lateinit var pager: ViewPager2
    private lateinit var btnNext: MaterialButton
    private lateinit var btnBack: MaterialButton
    private val vm: CalibViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calibration)

        findViewById<MaterialToolbar>(R.id.topAppBar).setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        pager = findViewById(R.id.pager)
        pager.isUserInputEnabled = false
        pager.adapter = CalibrationPagerAdapter(this)

        btnBack = findViewById(R.id.btnBack)
        btnNext = findViewById(R.id.btnNext)

        btnBack.setOnClickListener { prev() }
        btnNext.setOnClickListener { next() }

        onBackPressedDispatcher.addCallback(this) { prevOrFinish() }

        vm.stepTitle.observe(this) { title ->
            findViewById<MaterialToolbar>(R.id.topAppBar).subtitle = title
        }
        vm.enableNext.observe(this) { btnNext.isEnabled = it }
        vm.setNextText.observe(this) { btnNext.text = getString(it) }
        vm.setBackVisible.observe(this) { btnBack.visibility = if (it) MaterialButton.VISIBLE else MaterialButton.GONE }
    }

    private fun next() {
        if (pager.currentItem < pager.adapter!!.itemCount - 1) {
            pager.currentItem += 1
        } else {
            finish()
        }
    }

    private fun prev() {
        if (pager.currentItem > 0) pager.currentItem -= 1 else finish()
    }

    private fun prevOrFinish() = prev()
}