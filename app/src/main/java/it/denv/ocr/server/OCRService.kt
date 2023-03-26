package it.denv.ocr.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.IBinder
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
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import it.denv.ocr.server.responses.OcrResultProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.net.NetworkInterface
import java.net.SocketException
import java.security.KeyStore
import java.util.*


class OCRService : Service() {
    override fun onBind(p0: Intent?): IBinder? {
        // Not used
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        setupNotificationChannels()
        val ip = getIpAddr().joinToString(",")
        val port = 8443
        GlobalScope.launch {
            start(port).run {}
        }

        // Notice start
        val notification: Notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("OCR Service")
            .setContentText("Running on ${ip}:${port}")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
        startForeground(NOTIFICATION_ID, notification)
        Log.d(TAG, "Service starting...")
        return super.onStartCommand(intent, flags, startId)
    }

    private suspend fun unableToProcessRequest(call: ApplicationCall) {
        call.respond(HttpStatusCode.InternalServerError, "Unable to process request")
    }

    private fun start(serverPort: Int) {
        val keyStoreResource = resources.openRawResource(R.raw.keystore)
        val keyStoreFile = File.createTempFile("keystore", ".p12")
        keyStoreFile.deleteOnExit()
        keyStoreResource.copyTo(keyStoreFile.outputStream())


        val keyStore = KeyStore.Builder.newInstance(
            "PKCS12",
            BouncyCastleProvider(),
            keyStoreFile,
            KeyStore.PasswordProtection("changeit".toCharArray())
        ).keyStore


        val environment = applicationEngineEnvironment {
            log = LoggerFactory.getLogger("ktor")
            sslConnector(
                keyStore = keyStore,
                keyAlias = "alias",
                keyStorePassword = { "changeit".toCharArray() },
                privateKeyPassword = { "changeit".toCharArray() }) {
                port = serverPort
                keyStorePath = File(filesDir, "keystore.p12")
            }
            module {
                intercept(ApplicationCallPipeline.Plugins) {
                    call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
                    call.response.header(HttpHeaders.AccessControlAllowHeaders, "*")
                    call.response.header(HttpHeaders.AccessControlAllowMethods, "*")
                }
                routing {
                    get("/healthz", healthz())
                    post("/api/v1/ocr", handleOcr())
                }
            }
        }
        embeddedServer(Netty, environment).start(wait = true)
    }

    private fun healthz(): suspend PipelineContext<Unit, ApplicationCall>.(Unit) -> Unit =
        {
            call.respond(
                TextContent(
                    "OK",
                    contentType = ContentType.Text.Plain,
                    status = HttpStatusCode.OK
                )
            )
        }

    private fun handleOcr(): suspend PipelineContext<Unit, ApplicationCall>.(Unit) -> Unit =
        {
            try {
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

                            val barcodeScannerOptions = BarcodeScannerOptions.Builder()
                                .setBarcodeFormats(
                                    Barcode.FORMAT_QR_CODE,
                                    Barcode.FORMAT_AZTEC
                                )
                                .build()

                            val scanner = BarcodeScanning.getClient(barcodeScannerOptions)
                            val inputImage = InputImage.fromBitmap(bmp, 0)
                            val barcodeTask = scanner.process(inputImage)
                            val ocrTask = recognizer.process(inputImage)

                            val combinedTask = Tasks.whenAllComplete(barcodeTask, ocrTask)
                            Tasks.await(combinedTask)

                            val json = Json.encodeToString(
                                OcrResultProcessor.process(
                                    barcodeTask.result,
                                    ocrTask.result
                                )
                            )
                            call.respond(json)
                        }
                    }
                } else {
                    call.respond(
                        status = HttpStatusCode.BadRequest,
                        "Please specify an image MIME type"
                    )
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Unhandled exception:", ex)
                call.respond(
                    status = HttpStatusCode.InternalServerError,
                    "Unhandled exception"
                )
            }
        }

    fun module() {

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
            e.printStackTrace()
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
    }
}