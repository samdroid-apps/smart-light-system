package today.sam.lightswitch

import android.os.Vibrator
import android.util.Log
import java.util.*

class HapticManager(vibrator: Vibrator) {
    val mVibrator = vibrator
    val TOTAL_LENGTH = 100L

    var mFrac = 0.0f
    var mEnabled = false

    var mTimer: Timer? = null

    fun setFrac(frac: Float) {
        if (mTimer == null) {
            mTimer = Timer()
            val task = object : TimerTask() {
                override fun run() {
                    if (mEnabled) {
                        val onMs = maxOf((TOTAL_LENGTH * mFrac).toLong(), 5)
                        mVibrator.vibrate(onMs)
                    }
                }
            }
            mTimer!!.scheduleAtFixedRate(task, 0, TOTAL_LENGTH)
        }
        mFrac = frac
        mEnabled = true
    }

    fun stop() {
        mEnabled = false
        mVibrator.cancel()
        mTimer?.cancel()
        mTimer = null
    }
}