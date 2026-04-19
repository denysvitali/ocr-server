package it.denv.ocr.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.os.BatteryManager
import android.os.IBinder
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import io.ktor.http.*
import io.ktor.http.content.TextContent
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import it.denv.ocr.server.responses.BatteryState
import it.denv.ocr.server.responses.BatteryStatus
import it.denv.ocr.server.responses.OcrResultProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.math.BigInteger
import java.net.NetworkInterface
import java.net.SocketException
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.Security
import java.security.cert.X509Certificate
import java.util.Collections
import java.util.Date


class OCRService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var server: EmbeddedServer<*, *>? = null

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        setupNotificationChannels()
        val ips = getIpAddr().toList()
        val ip = ips.joinToString(",")
        ServerStats.markStarted(ips, SERVER_PORT)

        serviceScope.launch {
            start(SERVER_PORT)
        }

        val notification: Notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("OCR Service").setContentText("Running on ${ip}:${SERVER_PORT}")
            .setSmallIcon(R.drawable.ic_launcher_foreground).build()
        startForeground(NOTIFICATION_ID, notification)
        Log.d(TAG, "Service starting...")
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        server?.stop(1000, 2000)
        serviceScope.cancel()
        ServerStats.markStopped()
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
    }

    private suspend fun unableToProcessRequest(call: ApplicationCall) {
        call.respond(HttpStatusCode.InternalServerError, "Unable to process request")
    }

    private fun getOrGeneratePassword(): CharArray {
        val prefs = getSharedPreferences("ocr_server_prefs", Context.MODE_PRIVATE)
        val existing = prefs.getString("keystore_password", null)
        if (existing != null) {
            return existing.toCharArray()
        }

        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        val password = Base64.encodeToString(bytes, Base64.NO_WRAP)
        prefs.edit().putString("keystore_password", password).apply()
        return password.toCharArray()
    }

    private fun start(serverPort: Int) {
        Security.removeProvider("BC")
        Security.insertProviderAt(BouncyCastleProvider(), 1)

        val keyStoreFile = File(filesDir, "keystore.p12")
        val password = getOrGeneratePassword()

        if (!keyStoreFile.exists()) {
            generateSelfSignedCert(keyStoreFile, password)
        }

        val keyStore = KeyStore.Builder.newInstance(
            "PKCS12",
            BouncyCastleProvider(),
            keyStoreFile,
            KeyStore.PasswordProtection(password)
        ).keyStore

        server = embeddedServer(
            Netty,
            serverConfig {
                module {
                    install(CORS) {
                        anyHost()
                        allowHeader(HttpHeaders.ContentType)
                        allowMethod(HttpMethod.Post)
                        allowMethod(HttpMethod.Get)
                    }

                    install(ContentNegotiation) {
                        json(Json {
                            prettyPrint = true
                            isLenient = true
                        })
                    }

                    routing {
                        get("/healthz", healthz())
                        post("/api/v1/ocr", handleOcr())
                        get("/api/v1/battery", handleBattery())
                    }
                }
            }
        ) {
            connector {
                port = HTTP_PORT
            }
            sslConnector(
                keyStore = keyStore,
                keyAlias = "alias",
                keyStorePassword = { password },
                privateKeyPassword = { password }
            ) {
                port = serverPort
                keyStorePath = File(filesDir, "keystore.p12")
            }
        }.start(wait = true)
    }

    private fun generateSelfSignedCert(keyStoreFile: File, password: CharArray) {
        val keyGen = KeyPairGenerator.getInstance("RSA", "BC")
        keyGen.initialize(2048, SecureRandom())
        val keyPair = keyGen.generateKeyPair()

        val issuer = X500Name("CN=OCR Server")
        val builder = JcaX509v3CertificateBuilder(
            issuer,
            BigInteger.valueOf(System.currentTimeMillis()),
            Date(System.currentTimeMillis() - 86_400_000L),
            Date(System.currentTimeMillis() + 365L * 24 * 3600 * 1000 * 10),
            issuer,
            keyPair.public
        )
        val signer = JcaContentSignerBuilder("SHA256WithRSAEncryption")
            .setProvider("BC")
            .build(keyPair.private)
        val cert: X509Certificate = JcaX509CertificateConverter()
            .setProvider("BC")
            .getCertificate(builder.build(signer))

        val ks = KeyStore.getInstance("PKCS12", "BC")
        ks.load(null, null)
        ks.setKeyEntry("alias", keyPair.private, password, arrayOf(cert))
        keyStoreFile.outputStream().use { ks.store(it, password) }

        Log.d(TAG, "Generated self-signed certificate at ${keyStoreFile.absolutePath}")
    }

    private fun healthz(): suspend RoutingContext.() -> Unit = {
        call.respond(
            TextContent(
                "OK", contentType = ContentType.Text.Plain, status = HttpStatusCode.OK
            )
        )
    }

    private fun handleBattery(): suspend RoutingContext.() -> Unit = {
        val batteryStatus = getBatteryStatus(applicationContext)
        call.respond(HttpStatusCode.OK, batteryStatus)
    }

    private fun handleOcr(): suspend RoutingContext.() -> Unit = handler@{
        val startMs = ServerStats.requestStarted()
        var succeeded = false
        try {
            val contentLength = call.request.contentLength() ?: 0L
            if (contentLength > MAX_UPLOAD_BYTES) {
                call.respond(HttpStatusCode.PayloadTooLarge, "Image must be under 10MB")
                return@handler
            }

            val contentType = call.request.contentType()
            if (contentType.match(ContentType.Image.Any)) {
                val inputStream: InputStream = call.receiveStream()
                val bis = BufferedInputStream(inputStream)
                withContext(Dispatchers.IO) {
                    val bmp = BitmapFactory.decodeStream(bis)
                    if (bmp == null) {
                        Log.e(TAG, "bmp is null")
                        unableToProcessRequest(call)
                    } else {
                        val recognizer: TextRecognizer = TextRecognition.getClient(
                            TextRecognizerOptions.DEFAULT_OPTIONS
                        )

                        val barcodeScannerOptions =
                            BarcodeScannerOptions.Builder().setBarcodeFormats(
                                Barcode.FORMAT_QR_CODE, Barcode.FORMAT_AZTEC
                            ).build()

                        val scanner = BarcodeScanning.getClient(barcodeScannerOptions)
                        val inputImage = InputImage.fromBitmap(bmp, 0)
                        val barcodeTask = scanner.process(inputImage)
                        val ocrTask = recognizer.process(inputImage)

                        val combinedTask = Tasks.whenAllComplete(barcodeTask, ocrTask)
                        Tasks.await(combinedTask)

                        call.respond(
                            OcrResultProcessor.process(
                                barcodeTask.result, ocrTask.result
                            )
                        )
                        succeeded = true
                        ServerStats.requestSucceeded(startMs, contentLength)
                    }
                }
            } else {
                call.respond(
                    status = HttpStatusCode.BadRequest, "Please specify an image MIME type"
                )
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Unhandled exception:", ex)
            call.respond(
                status = HttpStatusCode.InternalServerError, "Unhandled exception"
            )
        } finally {
            if (!succeeded) ServerStats.requestFailed()
        }
    }

    private fun getBatteryStatus(context: Context): BatteryStatus {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val batteryStatusIntent: Intent? =
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val batteryState =
            when (batteryStatusIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1) {
                BatteryManager.BATTERY_STATUS_CHARGING -> BatteryState.CHARGING
                BatteryManager.BATTERY_STATUS_DISCHARGING -> BatteryState.DISCHARGING
                BatteryManager.BATTERY_STATUS_FULL -> BatteryState.FULL
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> BatteryState.NOT_CHARGING
                else -> BatteryState.UNKNOWN
            }
        return BatteryStatus(
            state = batteryState,
            level = batteryLevel
        )
    }

    private fun getIpAddr(): Array<String> {
        val ips = mutableListOf<String>()
        try {
            val interfaces: List<NetworkInterface> =
                Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (addr.hostAddress != null) {
                        if (!addr.isLoopbackAddress && addr.hostAddress!!.indexOf(':') < 0) {
                            ips.add(addr.hostAddress!!)
                        }
                    }
                }
            }
        } catch (e: SocketException) {
            Log.e(TAG, "Error getting IP addresses", e)
        }
        return ips.toTypedArray()
    }

    private fun setupNotificationChannels() {
        val name = getString(R.string.channel_name)
        val description = getString(R.string.channel_description)
        val importance = NotificationManager.IMPORTANCE_LOW
        val mChannel = NotificationChannel(CHANNEL_ID, name, importance)
        mChannel.description = description
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(mChannel)
        Log.d(TAG, "channel setup complete")
    }

    companion object {
        const val TAG = "OCRService"
        const val CHANNEL_ID = "ocr_service_channel"
        const val NOTIFICATION_ID = 1
        const val SERVER_PORT = 8443
        const val HTTP_PORT = 8080
        private const val MAX_UPLOAD_BYTES = 10L * 1024 * 1024
    }
}
