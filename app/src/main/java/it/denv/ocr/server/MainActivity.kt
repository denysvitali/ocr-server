package it.denv.ocr.server

import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Bundle
import android.Manifest.permission.POST_NOTIFICATIONS
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)

        checkNotificationPermission()

        startService(Intent(this, OCRService::class.java))

        val tvStatus: TextView = findViewById(R.id.tv_service_status)
        val statusDot: View = findViewById(R.id.status_dot)
        val tvUrls: TextView = findViewById(R.id.tv_urls)
        val tvUptime: TextView = findViewById(R.id.tv_uptime)
        val tvProcessed: TextView = findViewById(R.id.tv_processed)
        val tvInFlight: TextView = findViewById(R.id.tv_in_flight)
        val tvFailed: TextView = findViewById(R.id.tv_failed)
        val tvAvgLatency: TextView = findViewById(R.id.tv_avg_latency)
        val tvLastLatency: TextView = findViewById(R.id.tv_last_latency)
        val tvBytes: TextView = findViewById(R.id.tv_bytes)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    ServerStats.state.collect { s ->
                        tvStatus.text = if (s.running) "Running" else "Stopped"
                        val dotColor = getColor(
                            if (s.running) R.color.status_dot_on else R.color.status_dot_off
                        )
                        statusDot.backgroundTintList = ColorStateList.valueOf(dotColor)
                        tvUrls.text = if (s.ips.isEmpty()) {
                            "https://<device-ip>:${s.port}\nhttp://<device-ip>:${OCRService.HTTP_PORT}"
                        } else {
                            s.ips.joinToString("\n") { ip ->
                                "https://$ip:${s.port}\nhttp://$ip:${OCRService.HTTP_PORT}"
                            }
                        }
                        tvProcessed.text = s.processed.toString()
                        tvInFlight.text = s.inFlight.toString()
                        tvFailed.text = s.failed.toString()
                        tvAvgLatency.text = if (s.processed > 0) "${s.avgLatencyMs} ms" else "—"
                        tvLastLatency.text = if (s.lastLatencyMs > 0) "${s.lastLatencyMs} ms" else "—"
                        tvBytes.text = formatBytes(s.totalBytes)
                    }
                }
                launch {
                    while (true) {
                        val s = ServerStats.state.value
                        tvUptime.text = if (s.running && s.startedAt > 0) {
                            formatDuration(System.currentTimeMillis() - s.startedAt)
                        } else "—"
                        delay(1000)
                    }
                }
            }
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        var v = bytes.toDouble()
        var i = 0
        while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
        return if (i == 0) "${bytes} B" else String.format("%.1f %s", v, units[i])
    }

    private fun formatDuration(ms: Long): String {
        val s = ms / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format("%dh %02dm %02ds", h, m, sec)
        else String.format("%dm %02ds", m, sec)
    }

    private fun checkNotificationPermission() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.areNotificationsEnabled()) {
            return
        }

        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (!isGranted) {
                Toast.makeText(
                    applicationContext, "Please allow notification permission", Toast.LENGTH_SHORT
                ).show()
            }
        }

        val permissionResult = ContextCompat.checkSelfPermission(
            applicationContext, POST_NOTIFICATIONS
        )

        if (permissionResult != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(POST_NOTIFICATIONS)
        }
    }
}
