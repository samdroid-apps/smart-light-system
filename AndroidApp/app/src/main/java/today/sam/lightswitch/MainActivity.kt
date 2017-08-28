package today.sam.lightswitch

import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Vibrator
import android.support.v4.view.MotionEventCompat
import android.view.MotionEvent
import android.support.animation.SpringAnimation
import android.support.animation.DynamicAnimation
import android.support.animation.SpringForce
import android.support.v4.content.res.ResourcesCompat
import android.support.v7.widget.Toolbar
import android.util.Log;
import android.view.Menu
import android.view.MenuItem
import android.view.View.OnTouchListener
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

val TAG = "MainActivity"

class MainActivity : AppCompatActivity() {

    private val mBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            receiveWifiScan()
        }
    }

    private val mBackend = Backend(
            { backend ->
                runOnUiThread {
                    Log.d(TAG, "backend data changed")
                    val progress = findViewById(R.id.info_center_progress) as ProgressBar
                    progress.isIndeterminate = backend.insideRoom == null
                    progress.progress = (progress.max * (backend.insideRoom ?: 0f)).toInt()

                    val occupied = findViewById(R.id.info_center_occupied)
                    occupied.animate().setDuration(300).alpha(backend.insideRoom ?: .5f).start()
                    val unoccupied = findViewById(R.id.info_center_unoccupied)
                    unoccupied.animate().setDuration(300).alpha(1f - occupied.alpha).start()

                    updateButton()
                }
            },
            { desc: String ->
                runOnUiThread {
                    Toast.makeText(applicationContext, desc, Toast.LENGTH_LONG)
                }
            }
    )

    private var mHaptic: HapticManager? = null
    private var mWifi: WifiManager? = null
    private var mSpringAnim: SpringAnimation? = null

    private var mRelativeY = 0f
    private var mViewBottomY = -1f
    private val mTouchListener = OnTouchListener { v, event ->
        val action = MotionEventCompat.getActionMasked(event)
        val durationGroup = findViewById(R.id.light_switch_duration_group)
        val infoCenterGroup = findViewById(R.id.info_center_group)

        val y = event.rawY + mRelativeY
        // 0 = where the button was initially
        // 1 = almost top of screen
        val frac = 1f - maxOf(minOf((y / mViewBottomY), 1f), 0f)

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                // we don't set the final position during onCreate, beause the button
                // position is not fully known then
                if (mSpringAnim!!.spring == null) {
                    mViewBottomY = v.y
                    mSpringAnim!!.spring = SpringForce(v.y)
                }

                mRelativeY = v.y - event.rawY
                mSpringAnim!!.cancel()

                mHaptic?.setFrac(frac)

                setDurationLabel(frac)
                infoCenterGroup.animate().setDuration(150).alpha(0f).start()
                durationGroup.animate().setDuration(300).alpha(1f).start()
                transitionToColor(ResourcesCompat.getColor(resources, R.color.draggable_start_color, null), 150)
            }
            MotionEvent.ACTION_MOVE -> {
                v.animate().y(y).setDuration(0).start()

                val color = ArgbEvaluator()
                        .evaluate(frac,
                                  ResourcesCompat.getColor(resources, R.color.draggable_start_color, null),
                                  ResourcesCompat.getColor(resources, R.color.draggable_end_color, null));
                setColor(color as Int)
                // animation may not have finished... but since we are setting the background
                // color, it looks weird to have the white image on top of it
                infoCenterGroup.animate().cancel()
                infoCenterGroup.alpha = 0f

                setDurationLabel(frac)
                mHaptic?.setFrac(frac)
            }
            MotionEvent.ACTION_UP -> {
                mSpringAnim!!.start()

                transitionToColor(Color.WHITE)
                durationGroup.animate().setDuration(300).alpha(0f).start()
                infoCenterGroup.animate().setDuration(150).setStartDelay(150).alpha(1f).start()

                val duration = getDurationForFrac(frac)
                if (duration <= mBackend.onFor) {
                    mBackend.status(setTimer = 0L)
                } else {
                    mBackend.status(setTimer = duration)
                }

                mHaptic?.stop()
                updateButton()
            }
            else -> {}
        }
        true
    }

    private fun setDurationLabel(frac: Float) {
        val durationLabel = findViewById(R.id.light_switch_duration_label) as TextView
        val durationOn = findViewById(R.id.light_switch_duration_on)

        var onAlpha = 0f
        val duration = getDurationForFrac(frac)
        if (duration < mBackend.onFor) {
            durationLabel.text = "Turn Off"
        } else {
            onAlpha = 1f
            durationLabel.text = durationToString(duration)
        }
        if (onAlpha != durationOn.alpha) {
            durationOn.alpha = onAlpha
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mHaptic = HapticManager(getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
        mWifi = getSystemService(Context.WIFI_SERVICE) as WifiManager
        registerReceiver(mBroadcastReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))

        val toolbar = findViewById(R.id.main_toolbar) as Toolbar
        setSupportActionBar(toolbar)

        updateButton()
        val button = findViewById(R.id.light_switch_button) as android.widget.Button
        mSpringAnim = SpringAnimation(button, DynamicAnimation.Y)
        button.setOnTouchListener(mTouchListener)

        mBackend.status()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "startScan")
        refreshInfo()
    }

    private fun refreshInfo() {
        mWifi!!.startScan()
        val progress = findViewById(R.id.info_center_progress) as ProgressBar
        progress.isIndeterminate = true
    }

    private fun getIsCharging(): Boolean {
        val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = applicationContext.registerReceiver(null, ifilter)
        // Are we charging / charged?
        val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
               status == BatteryManager.BATTERY_STATUS_FULL

    }

    private fun receiveWifiScan() {
        Log.d(TAG, "receiveWifiScan")
        mBackend.status(
                scanResult = mWifi!!.scanResults,
                isCharging = getIsCharging())
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_location_calibration -> {
                val intent = Intent(this, CalibrateActivity::class.java)
                startActivity(intent)
                return true
            }
            R.id.action_refresh -> {
                refreshInfo()
                return true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }


    private fun updateButton() {
        val button = findViewById(R.id.light_switch_button) as android.widget.Button
        if (mBackend.onFor < 30L) {
            button.text = "turn on"
        } else {
            button.text = "on for next ${durationToString(mBackend.onFor)}"
        }
    }


    private var mCurrentColor = Color.WHITE;
    private var mCurrentColorTrans: ObjectAnimator? = null
    private fun transitionToColor(color: Int, delay: Long = 0) {
        mCurrentColorTrans?.cancel()

        val screen = findViewById(R.id.activity_main_screen)
        if (screen.background is ColorDrawable) {
            mCurrentColor = (screen.background as ColorDrawable).color
        }

        mCurrentColorTrans = ObjectAnimator.ofObject(
                screen, "backgroundColor",
                ArgbEvaluator(),
                mCurrentColor,
                color)
        mCurrentColorTrans!!.startDelay = delay
        mCurrentColorTrans!!.start()
        mCurrentColor = color
    }
    private fun setColor(color: Int) {
        mCurrentColorTrans?.cancel()
        val screen = findViewById(R.id.activity_main_screen)
        screen.setBackgroundColor(color)
    }

    private fun getDurationForFrac(frac: Float): Long {
        if (frac > 0.9) {
            return Long.MAX_VALUE
        }
        // max is 12h (720m)
        // time is in seconds
        return maxOf(5f, frac*frac*720f).toLong() * 60
    }

    private fun durationToString(duration: Long): String {
        // RIP: i18n
        if (duration == Long.MAX_VALUE) return "forever"

        val minutes = duration / 60;
        if (minutes < 60) return "${minutes} minutes"

        val hours = (minutes / 60f).toLong()
        if (hours <= 1) return "${hours} hour"
        return "${hours} hours"
    }
}
