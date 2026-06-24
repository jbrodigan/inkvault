package com.inkvault.spike

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * PHASE 0 DE-RISK SPIKE — dual-pen connection + live dot stream.
 *
 * Purpose: give an explicit GO/NO-GO on the LAMY safari NWP-F80 (the #1 unknown — it is not in
 * the SDK's documented model list). Run on EACH device:
 *   - an Android 14+ phone + Neo M1+  (NWP-F51)
 *   - an Android tablet  + LAMY safari (NWP-F80)
 * and confirm: pairs, handshakes, and streams dots. If the LAMY fails, capture WHERE
 * (pairing / handshake / dot format) and record it in android/DESIGN.md.
 *
 * IMPORTANT: a real GO/NO-GO requires the GPL `kr.neolab.sdk` module + the physical pen; it
 * cannot be produced in CI or without hardware. The exact, VERIFIED real-SDK sequence to run on
 * hardware is documented below. This Activity is wired to the project's pen abstraction so it
 * compiles and runs the fake path; swap NeoSdkAdapter in to drive a real pen.
 *
 * Verified real-SDK sequence (kr.neolab.sdk, confirmed against source — see DESIGN.md):
 *
 *   val pen = PenCtrl.getInstance()
 *   pen.setContext(applicationContext)
 *   pen.setListener(object : IPenMsgListener {
 *       override fun onReceivedMessage(mac: String?, msg: PenMsg) {
 *           // PEN_CONNECTION_SUCCESS / PEN_CONNECTION_FAILURE / PEN_AUTHORIZED ...
 *           Log.d("SPIKE", "msg ${msg.penMsgType} from $mac")
 *       }
 *   })
 *   pen.setDotListener { mac, dot ->        // IPenDotListener.onReceiveDot(mac, dot)
 *       Log.d("SPIKE", "dot s=${dot.sectionId} o=${dot.ownerId} note=${dot.noteId} " +
 *                       "page=${dot.pageId} x=${dot.x} y=${dot.y} p=${dot.pressure} t=${dot.dotType}")
 *   }
 *   pen.connect(macAddress)                 // throws BLENotSupportedException
 *   // After PEN_AUTHORIZED, optionally probe offline data:
 *   pen.setAllowOfflineData(true)
 *   pen.reqOfflineDataList()                // exercises the offline path too
 *
 * Build/run steps are in android/README.md → "Phase 0 spike".
 */
class PenConnectSpikeActivity : ComponentActivity() {

    private val log = mutableStateListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Fake path so the spike is runnable without the SDK module. Replace with NeoSdkAdapter()
        // (and the verified sequence above) for a real pen.
        val pen = com.inkvault.pen.FakeNeoPenSdk()
        pen.setListener { msg -> line("msg: $msg") }
        pen.setDotListener { dot ->
            line("dot note=${dot.book} page=${dot.page} x=${dot.x} y=${dot.y} phase=${dot.phase}")
        }
        pen.setOfflineListener { b -> line("offline: pen=${b.penAddress} strokes=${b.strokes.size}") }

        // Simulate a session so the screen shows the shape of real output.
        pen.emitConnected("DE:MO:00:00:00:01")
        repeat(3) { i ->
            pen.emitDot(
                com.inkvault.pen.PenDot(
                    section = 3, owner = 27, book = 603, page = 1,
                    x = i.toFloat(), y = i.toFloat(), pressure = 0.5f,
                    phase = com.inkvault.pen.PenDot.Phase.MOVE, timestamp = i.toLong(),
                    color = 0xFF000000.toInt(),
                ),
            )
        }

        setContent {
            MaterialTheme {
                Surface {
                    androidx.compose.foundation.layout.Column(
                        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
                    ) {
                        Text("Phase 0 spike — connect & stream (fake path)")
                        log.forEach { Text(it) }
                    }
                }
            }
        }
    }

    private fun line(s: String) {
        Log.d("SPIKE", s)
        log.add(s)
    }
}
