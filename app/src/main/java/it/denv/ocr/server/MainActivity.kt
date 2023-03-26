package it.denv.ocr.server

import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.Manifest.permission.POST_NOTIFICATIONS
import android.os.Build
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

const val TAG = "OCR_Server"

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)

        checkNotificationPermission()

        System.setProperty("javax.net.debug", "ssl")

        val tvServiceStatus: TextView = findViewById(R.id.tv_service_status)
        tvServiceStatus.text = "Starting service..."

        val serviceIntent = Intent(this, OCRService::class.java)

        startService(serviceIntent)
        tvServiceStatus.text = "Service started"
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