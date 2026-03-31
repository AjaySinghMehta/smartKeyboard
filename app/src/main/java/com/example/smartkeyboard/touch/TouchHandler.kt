package com.example.smartkeyboard.touch

import android.view.View
import android.widget.Button
import kotlin.math.hypot

class TouchHandler(private val keyboardView: View) {

    private val keyIds = listOf(
        // Row 1
        com.example.smartkeyboard.R.id.key_q,
        com.example.smartkeyboard.R.id.key_w,
        com.example.smartkeyboard.R.id.key_e,
        com.example.smartkeyboard.R.id.key_r,
        com.example.smartkeyboard.R.id.key_t,
        com.example.smartkeyboard.R.id.key_y,
        com.example.smartkeyboard.R.id.key_u,
        com.example.smartkeyboard.R.id.key_i,
        com.example.smartkeyboard.R.id.key_o,
        com.example.smartkeyboard.R.id.key_p,

        // Row 2
        com.example.smartkeyboard.R.id.key_a,
        com.example.smartkeyboard.R.id.key_s,
        com.example.smartkeyboard.R.id.key_d,
        com.example.smartkeyboard.R.id.key_f,
        com.example.smartkeyboard.R.id.key_g,
        com.example.smartkeyboard.R.id.key_h,
        com.example.smartkeyboard.R.id.key_j,
        com.example.smartkeyboard.R.id.key_k,
        com.example.smartkeyboard.R.id.key_l,

        // Row 3
        com.example.smartkeyboard.R.id.key_z,
        com.example.smartkeyboard.R.id.key_x,
        com.example.smartkeyboard.R.id.key_c,
        com.example.smartkeyboard.R.id.key_v,
        com.example.smartkeyboard.R.id.key_b,
        com.example.smartkeyboard.R.id.key_n,
        com.example.smartkeyboard.R.id.key_m
    )

    fun findNearestKey(x: Float, y: Float): Button? {
        val MAX_DISTANCE = 150f
        var closest: Button? = null
        var minDistance = Float.MAX_VALUE

        for (id in keyIds) {
            val button = keyboardView.findViewById<Button>(id)

            val location = IntArray(2)
            button.getLocationOnScreen(location)

            val centerX = location[0] + button.width / 2
            val centerY = location[1] + button.height / 2

            val distance = hypot(
                (x - centerX).toDouble(),
                (y - centerY).toDouble()
            ).toFloat()
        }

        return if (minDistance < MAX_DISTANCE) closest else null
    }
}