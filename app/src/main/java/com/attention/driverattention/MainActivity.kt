package com.attention.driverattention

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.util.Size
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.attention.driverattention.ui.main.MainFragment
import com.google.common.util.concurrent.ListenableFuture
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.concurrent.thread


class MainActivity : AppCompatActivity() {

    private lateinit var cameraProviderFuture : ListenableFuture<ProcessCameraProvider>
    private lateinit var analysisExecutor: Executor

    private var frameid: Int = 0
    private var auth_token: String = ""
    private var time_last: Long = 0

    private val interval = 1000
    private val api_url = "http://121.134.129.173:7296"

    private var state = ""
    private var state_id = ""

    private var unaware_count = 0


    fun permissionsGranted(): Boolean {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1) {
            val hasCameraPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasCameraPermission) {
                return false
            }
        }
        return true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                    .replace(R.id.container, MainFragment.newInstance())
                    .commitNow()
        }

        //check for camera permission and request it
        if (permissionsGranted() == false) {
            val permissions = arrayOf(
                Manifest.permission.CAMERA,
            )
            ActivityCompat.requestPermissions(this, permissions, 1)
        }


        cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            val cameraProvider = cameraProviderFuture.get()
            bindPreview(cameraProvider)
        }, ContextCompat.getMainExecutor(this))

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle("Input credentials")
        val layout = LinearLayout(this.applicationContext)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(50, 20, 50, 20)

        val sharedPref = this.getPreferences(Context.MODE_PRIVATE)

        val input_user = EditText(this)
        val input_pass = EditText(this)

        input_user.hint = "User"
        input_pass.hint = "Password"

        input_user.setText(sharedPref.getString("user", ""))
        input_pass.setText(sharedPref.getString("pass", ""))

        input_user.inputType = InputType.TYPE_CLASS_TEXT
        input_pass.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD

        layout.addView(input_user)
        layout.addView(input_pass)

        builder.setView(layout)

        builder.setPositiveButton("OK", DialogInterface.OnClickListener {
                dialog, which ->
            val user = input_user.text.toString()
            val pass = input_pass.text.toString()

            with (sharedPref.edit()) {
                putString("user", user)
                putString("pass", pass)
                apply()
            }

            thread {
                val url = URL("$api_url/auth")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Authorization", "application/json")
                conn.setRequestProperty("Content-Type", "application/json; utf-8")
                conn.setRequestProperty("Accept", "application/json")
                conn.doOutput = true

                val jsoninput: String = "{\"username\": \"$user\", \"password\": \"$pass\"}"
                try {
                    conn.outputStream.use { os ->
                        val input: ByteArray = jsoninput.toByteArray()
                        os.write(input, 0, input.size)
                    }


                    BufferedReader(
                        InputStreamReader(conn.inputStream, "utf-8")
                    ).use { br ->
                        val response = StringBuilder()
                        var responseLine: String? = null
                        while (br.readLine().also { responseLine = it } != null) {
                            response.append(responseLine!!.trim { it <= ' ' })
                        }
                        auth_token = JSONObject(response.toString()).getString("access_token")

                        Log.w("Token", auth_token)
                    }
                } catch (e: Exception) {
                    this.runOnUiThread(Runnable {
                        var tview = findViewById<TextView>(R.id.textView)
                        tview.setText("Authentication failed!")
                        tview.setBackgroundColor(Color.MAGENTA)
                    })
                }
            }
        })

        builder.show()
    }

    fun ImageProxy.toBitmap(): Bitmap {
        val yBuffer = planes[0].buffer // Y
        val vuBuffer = planes[2].buffer // VU

        val ySize = yBuffer.remaining()
        val vuSize = vuBuffer.remaining()

        val nv21 = ByteArray(ySize + vuSize)

        yBuffer.get(nv21, 0, ySize)
        vuBuffer.get(nv21, ySize, vuSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, this.width, this.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 50, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    class MultipartUtility(requestURL: String, auth_token: String, private val charset: String) {
        private val boundary: String = "===" + System.currentTimeMillis() + "==="
        private val httpConn: HttpURLConnection
        private val outputStream: OutputStream
        private val writer: PrintWriter

        fun addFormField(name: String, value: String?) {
            writer.append("--$boundary").append(LINE_FEED)
            writer.append("Content-Disposition: form-data; name=\"$name\"")
                .append(LINE_FEED)
            writer.append("Content-Type: text/plain; charset=$charset").append(
                LINE_FEED
            )
            writer.append(LINE_FEED)
            writer.append(value).append(LINE_FEED)
            writer.flush()
        }

        @Throws(IOException::class)
        fun addFilePart(fieldName: String, uploadFile: ByteArray) {
            val fileName: String = "frame.jpeg"
            writer.append("--$boundary").append(LINE_FEED)
            writer.append(
                "Content-Disposition: form-data; name=\"" + fieldName
                        + "\"; filename=\"" + fileName + "\""
            )
                .append(LINE_FEED)
            writer.append(("Content-Type: image/jpeg"))
                .append(LINE_FEED)
            writer.append("Content-Transfer-Encoding: binary").append(LINE_FEED)
            writer.append(LINE_FEED)
            writer.flush()
            Log.w("FILE", uploadFile.size.toString())
            outputStream.write(uploadFile, 0, uploadFile.size)
            outputStream.flush()

            writer.append(LINE_FEED)
            writer.flush()
        }

        fun addHeaderField(name: String, value: String) {
            writer.append("$name: $value").append(LINE_FEED)
            writer.flush()
        }

        @Throws(IOException::class)
        fun finish(): String {
            var response: String = ""
            writer.append(LINE_FEED).flush()
            writer.append("--$boundary--").append(LINE_FEED)
            writer.close()

            val status = httpConn.responseCode
            if (status == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(
                    InputStreamReader(
                        httpConn.inputStream
                    )
                )
                var line: String? = null
                while ((reader.readLine().also { line = it }) != null) {
                    response += line + "\n"
                }
                reader.close()
                httpConn.disconnect()
            } else {
                throw IOException("Server returned non-OK status: $status")
            }
            return response
        }

        companion object {
            private val LINE_FEED = "\r\n"
        }

        init {
            // creates a unique boundary based on time stamp
            val url = URL(requestURL)
            httpConn = url.openConnection() as HttpURLConnection
            httpConn.useCaches = false
            httpConn.doOutput = true // indicates POST method
            httpConn.doInput = true
            httpConn.setRequestProperty(
                "Content-Type",
                "multipart/form-data; boundary=$boundary"
            )
            httpConn.setRequestProperty("Authorization", auth_token)
            outputStream = httpConn.outputStream
            writer = PrintWriter(
                OutputStreamWriter(outputStream, charset),
                true
            )
        }
    }

    private fun bindPreview(cameraProvider: ProcessCameraProvider) {
        var preview : Preview = Preview.Builder()
            .build()

        var cameraSelector : CameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
            .build()

        var previewView = findViewById<PreviewView>(R.id.previewView0)

        preview.setSurfaceProvider(previewView.surfaceProvider)

        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(1080, 1920))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        analysisExecutor = Executors.newSingleThreadExecutor()

        imageAnalysis.setAnalyzer(analysisExecutor, ImageAnalysis.Analyzer { image ->
            if (System.currentTimeMillis() - time_last > interval) {
                time_last = System.currentTimeMillis()

                if (!auth_token.equals("")) {
                    val rotationDegrees = image.imageInfo.rotationDegrees
                    val bitmap = image.toBitmap()

                    val stream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                    val byteArray = stream.toByteArray()
                    val size = byteArray.size
                    var response: String = ""
                    var start = System.currentTimeMillis()

                    try {
                        val multipart =
                            MultipartUtility("$api_url/get_state", "JWT " + auth_token, "utf-8")

                        multipart.addFormField("frm_id", frameid.toString())
                        frameid += 1

                        multipart.addFilePart("img", byteArray)

                        response = multipart.finish()

                        var end = System.currentTimeMillis()
                        val parsed = JSONObject(response.toString())
                        state_id = parsed.getString("state_id")
                        state = parsed.getString("state")
                        Log.w("Response", state_id)
                        Log.w("Response", state)

                        this.runOnUiThread(Runnable {
                            var tview = findViewById<TextView>(R.id.textView)
                            tview.setText(state)

                            if (state_id.equals("0")) {
                                tview.setBackgroundColor(Color.RED)
                                unaware_count += 1
                            } else {
                                tview.setBackgroundColor(Color.GREEN)
                                unaware_count = 0
                            }

                            var time = end - start

                            var tview_debug = findViewById<TextView>(R.id.textView_debug)
                            var debug_text = ""
                            debug_text += "Image size: " + (size/1024) + "kiB, Latency: " + time + "ms"
                            tview_debug.setText(debug_text)
                        })
                    } catch (e: Exception) {
                        this.runOnUiThread(Runnable {
                            Log.w("Error", "Networking exception")
                            var tview = findViewById<TextView>(R.id.textView)
                            tview.setText("Networking error!")
                            tview.setBackgroundColor(Color.MAGENTA)
                        })
                    }
                }
            }

            image.close()
        })

        var camera = cameraProvider.bindToLifecycle(this as LifecycleOwner, cameraSelector, imageAnalysis, preview)

    }
}