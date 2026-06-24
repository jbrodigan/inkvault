package com.inkvault.penspike

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import kr.neolab.sdk.ink.structure.Dot
import kr.neolab.sdk.pen.PenCtrl
import kr.neolab.sdk.pen.penmsg.IPenDotListener
import kr.neolab.sdk.pen.penmsg.IPenMsgListener
import kr.neolab.sdk.pen.penmsg.PenMsg
import kr.neolab.sdk.pen.penmsg.PenMsgType

/**
 * PHASE 0 real spike — connects to ONE Neo/LAMY pen via the real SDK and logs live dots, to
 * produce the LAMY NWP-F80 GO/NO-GO. Run on each device (M1+ control, then LAMY). Steps:
 * android/docs/PHASE0_GO_NOGO.md.
 *
 * All SDK calls below are the VERIFIED kr.neolab.sdk surface (read from source 2026-06-21):
 *   PenCtrl.getInstance(); setContext(Context); setListener(IPenMsgListener);
 *   setDotListener(IPenDotListener); setAllowOfflineData(boolean); connect(String); reqOfflineDataList()
 *   IPenMsgListener.onReceiveMessage(String, PenMsg);  PenMsg.penMsgType vs PenMsgType.*
 *   IPenDotListener.onReceiveDot(String, Dot);  Dot{ x,y,pressure,dotType,sectionId,ownerId,noteId,pageId }
 */
class PenSpikeActivity : ComponentActivity() {

    private val tag = "SPIKE"
    private lateinit var out: TextView
    private val pen by lazy { PenCtrl.getInstance() }

    private val askPerms =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.all { it }) startScan() else log("permissions denied: $result")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        out = TextView(this).apply { setPadding(28, 28, 28, 28); textSize = 12f }
        setContentView(ScrollView(this).apply { addView(out) })

        // Init the SDK (setContext is required before connect) and register callbacks.
        pen.setContext(applicationContext)
        pen.setAllowOfflineData(true)
        pen.setListener(msgListener)
        pen.setDotListener(dotListener)

        log("Phase 0 spike. Requesting BLE permissions…")
        askPerms.launch(
            if (Build.VERSION.SDK_INT >= 31)
                arrayOf("android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT")
            else
                arrayOf("android.permission.ACCESS_FINE_LOCATION"),
        )
    }

    private val msgListener = IPenMsgListener { penAddress, msg: PenMsg ->
        runOnUiThread {
            log("MSG type=0x%02x from %s".format(msg.penMsgType, penAddress))
            when (msg.penMsgType) {
                PenMsgType.PEN_CONNECTION_SUCCESS -> log("BLE link up — handshaking…")
                PenMsgType.PEN_AUTHORIZED -> {
                    log("✅ AUTHORIZED. Write on the Ncode paper now. Probing offline store…")
                    pen.reqOfflineDataList()
                }
                PenMsgType.PEN_CONNECTION_FAILURE -> log("❌ connect/handshake FAILED")
                PenMsgType.PEN_DISCONNECTED -> log("disconnected")
                PenMsgType.PASSWORD_REQUEST ->
                    log("Pen is password-locked (set/clear it in the official app, or call inputPassword).")
                PenMsgType.OFFLINE_DATA_NOTE_LIST -> log("offline note list received")
            }
        }
    }

    private val dotListener = IPenDotListener { _, dot: Dot ->
        runOnUiThread {
            log(
                "DOT s=%d o=%d note=%d page=%d  x=%.1f y=%.1f  p=%d type=%d".format(
                    dot.sectionId, dot.ownerId, dot.noteId, dot.pageId,
                    dot.x, dot.y, dot.pressure, dot.dotType,
                ),
            )
        }
    }

    @SuppressLint("MissingPermission") // permissions are requested at runtime above
    private fun startScan() {
        val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) { log("Bluetooth off / no LE scanner"); return }
        log("scanning for a pen… (turn the pen on)")
        scanner.startScan(object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.device.name ?: return
                log("seen: $name  ${result.device.address}")
                if (looksLikePen(name)) {
                    scanner.stopScan(this)
                    log("connecting to $name …")
                    try {
                        pen.connect(result.device.address)
                    } catch (e: Exception) {
                        log("connect() threw: $e")
                    }
                }
            }
            override fun onScanFailed(errorCode: Int) { log("scan failed: $errorCode") }
        })
    }

    private fun looksLikePen(name: String): Boolean =
        listOf("neo", "pen", "lamy", "nwp", "dimo").any { name.contains(it, ignoreCase = true) }

    private fun log(line: String) {
        Log.d(tag, line)
        out.append(line + "\n")
    }

    override fun onDestroy() {
        try { pen.disconnect() } catch (_: Exception) {}
        super.onDestroy()
    }
}
