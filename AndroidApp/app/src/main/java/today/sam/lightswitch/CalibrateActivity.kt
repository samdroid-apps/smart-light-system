package today.sam.lightswitch

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.net.wifi.WifiManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v7.widget.Toolbar
import android.content.IntentFilter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Vibrator
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import link.fls.swipestack.SwipeStack


class CalibrateActivity : AppCompatActivity(), SwipeStack.SwipeStackListener {

    val mBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            receiveWifiScan()
        }
    }

    val mBackend = Backend()
    var mWifi: WifiManager? = null
    var mVibrator: Vibrator? = null
    var mAdapter: SwipeStackAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calibrate)
        mWifi = getSystemService(Context.WIFI_SERVICE) as WifiManager
        mVibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        val toolbar = findViewById(R.id.calibrate_toolbar) as Toolbar
        setSupportActionBar(toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.title = "Calibrate Location Sensor"

        if (mWifi!!.isWifiEnabled) {
            mWifi!!.isWifiEnabled = true
        }

        registerReceiver(mBroadcastReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

        mAdapter = SwipeStackAdapter(layoutInflater)
        val swipeStack = findViewById(R.id.train_swipe_stack) as SwipeStack;
        swipeStack.setAdapter(mAdapter)
        swipeStack.setListener(this)
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
        scan()
    }

    private fun receiveWifiScan() {
        var strings = mWifi!!.scanResults.map { "[${it.level}] ${it.SSID} (${it.BSSID})" }
        mAdapter!!.setScanLabel(strings.joinToString("\n"));
        mVibrator?.vibrate(100)
    }

    override fun onViewSwipedToLeft(position: Int) {
        mAdapter!!.advance()
        mBackend.sendNetworkScan(mWifi!!.scanResults, "inside")
        scan()
    }

    override fun onViewSwipedToRight(position: Int) {
        mAdapter!!.advance()
        mBackend.sendNetworkScan(mWifi!!.scanResults, "outside")
        scan()
    }

    private fun scan() {
        Log.d(TAG, "Triggered scan")
        mWifi!!.startScan()
    }

    override fun onStackEmpty() {
        return
    }

    class SwipeStackAdapter(layoutInflater: LayoutInflater) : BaseAdapter() {
        val mLayoutInflator: LayoutInflater = layoutInflater
        var mScanLabel: String? = null
        var mHead: Int = 0
        var mHeadTextView: TextView? = null

        fun advance() {
            mScanLabel = null
            mHeadTextView = null
            mHead++
            this.notifyDataSetChanged()
        }

        fun setScanLabel(value: String) {
            mScanLabel = value
            mHeadTextView?.text = mScanLabel
            this.notifyDataSetChanged()
        }

        override fun getCount(): Int {
            return mHead + 1
        }

        override fun getItem(position: Int): Int {
            return 0
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            var view = convertView?: mLayoutInflator.inflate(R.layout.card, parent, false)

            val textView = view.findViewById(R.id.card_text_view) as TextView
            if (position == mHead) {
                if (mScanLabel == null) {
                    mHeadTextView = textView
                } else {
                    mHeadTextView = null
                    textView.text = mScanLabel
                }
            }
            return view
        }
    }
}
