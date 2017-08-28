package today.sam.lightswitch

import android.net.wifi.ScanResult
import android.util.Log
import com.github.salomonbrys.kotson.*
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import okhttp3.*
import org.json.JSONArray
import java.io.IOException
import android.content.Intent
import android.content.IntentFilter



val JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8")
val ROOT_URL = "http://192.168.1.4:5000"

class Backend (changeCallback: (Backend) -> Unit = {},
               failCallback:   (String)  -> Unit = {}) {
    private val mClient = OkHttpClient()
    val mChangeCallback = changeCallback
    val mFailCallback = failCallback
    var insideRoom: Float? = null

    fun getUnixTime(): Long {
        return System.currentTimeMillis() / 1000
    }
    var timeout: Long = getUnixTime()
    val onFor: Long get() = maxOf(0L, timeout - getUnixTime())

    private fun serializeScanResults(scanResult: List<ScanResult>): JsonArray {
        return jsonArray(scanResult.map {
            jsonObject(
                    "ssid" to it.SSID,
                    "bssid" to it.BSSID,
                    "level" to it.level
            )
        })
    }

    fun sendNetworkScan(scanResult: List<ScanResult>, location: String) {
        val j = jsonObject(
                "wifi" to serializeScanResults(scanResult),
                "location" to location
        )
        val body = RequestBody.create(JSON_MEDIA_TYPE, j.toString())
        val req = Request.Builder()
                .url(ROOT_URL + "/send-network-scan")
                .post(body)
                .build()
        mClient.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call?, e: IOException?) {
                Log.d(TAG, "Request failure")
            }

            override fun onResponse(call: Call?, response: Response?) {
                Log.d(TAG, "Request response")
            }
        })
    }

    class StatusResponse {
        val insideRoom: Float? = null
        val timeout: Long = 0L
    }

    fun status(scanResult: List<ScanResult>? = null,
               setTimer: Long? = null,
               isCharging: Boolean? = null) {
        if (setTimer != null) {
            timeout = getUnixTime() + setTimer
            mChangeCallback(this)
        }

        val j = jsonObject(
                "wifi" to if (scanResult == null) null else serializeScanResults(scanResult),
                "deviceID" to "FIXME",
                "isCharging" to isCharging,
                "setTimer" to setTimer)
        val body = RequestBody.create(JSON_MEDIA_TYPE, j.toString())
        val req = Request.Builder()
                .url(ROOT_URL + "/status")
                .post(body)
                .build()
        mClient.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mFailCallback(e.toString())
            }

            override fun onResponse(call: Call, response: Response) {
                val jsonString = response.body()!!.string()
                val resp = try {
                    Gson().fromJson<StatusResponse>(jsonString)
                } catch (e: JsonSyntaxException) {
                    mFailCallback(e.toString())
                    return
                }

                if (resp.insideRoom != null) {
                    insideRoom = resp.insideRoom
                }
                timeout = resp.timeout
                Log.d(TAG, "Got response with $timeout, $onFor")

                mChangeCallback(this@Backend)
            }
        })
    }
}