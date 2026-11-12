package com.duong.geocam

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.view.Gravity
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.ScaleGestureDetector
import android.widget.LinearLayout
import androidx.camera.core.Camera
import androidx.camera.core.CameraState
import androidx.camera.core.FocusMeteringAction
import android.view.Surface
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var tvOverlay: TextView
    private lateinit var ivMap: ImageView
    private lateinit var ivThumb: ImageView
    private lateinit var btnShot: View
    private lateinit var btnFlash: TextView
    private lateinit var zoomRow: LinearLayout
    private var camera: Camera? = null
    private var zoomBuiltFor: Camera? = null
    private val zoomChips = mutableListOf<Pair<Float, TextView>>()
    private var scaling = false
    private val flashLabels = arrayOf("⚡ Tắt", "⚡ Tự động", "⚡ Bật", "🔦 Đèn pin")

    private val prefs by lazy { Prefs(this) }
    private var imageCapture: ImageCapture? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private val captureExec = Executors.newSingleThreadExecutor()
    private val netExec = Executors.newSingleThreadExecutor()

    private lateinit var fused: FusedLocationProviderClient
    @Volatile private var lastLoc: Location? = null
    @Volatile private var address: String? = null
    @Volatile private var street: String? = null
    @Volatile private var mapBmp: Bitmap? = null
    private var infoLoc: Location? = null
    private var infoTime = 0L
    @Volatile private var infoBusy = false
    private var lastUri: Uri? = null

    private val ui = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() { updateOverlay(); ui.postDelayed(this, 1000) }
    }

    private val orientation by lazy {
        object : OrientationEventListener(this) {
            override fun onOrientationChanged(o: Int) {
                if (o == ORIENTATION_UNKNOWN) return
                imageCapture?.targetRotation = when (o) {
                    in 45 until 135 -> Surface.ROTATION_270
                    in 135 until 225 -> Surface.ROTATION_180
                    in 225 until 315 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
            }
        }
    }

    private val locCb = object : LocationCallback() {
        override fun onLocationResult(r: LocationResult) { r.lastLocation?.let { onLocation(it) } }
    }

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (has(Manifest.permission.CAMERA)) startCamera()
        else toast("Cần quyền camera để chụp ảnh")
        if (hasLocation()) startLocation() else toast("Không có quyền vị trí: ảnh sẽ không có toạ độ")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }
        previewView = findViewById(R.id.preview)
        tvOverlay = findViewById(R.id.tvOverlay)
        ivMap = findViewById(R.id.ivMap)
        ivThumb = findViewById(R.id.ivThumb)
        btnShot = findViewById(R.id.btnShot)
        btnFlash = findViewById(R.id.btnFlash)
        zoomRow = findViewById(R.id.zoomRow)
        btnFlash.setOnClickListener {
            prefs.flashMode = (prefs.flashMode + 1) % flashLabels.size
            applyFlash()
        }
        setupGestures()
        fused = LocationServices.getFusedLocationProviderClient(this)

        btnShot.setOnClickListener { takePhoto() }
        findViewById<View>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<View>(R.id.btnSwitch).setOnClickListener {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
                CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
            startCamera()
        }
        ivThumb.setOnClickListener {
            lastUri?.let {
                startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(it, "image/jpeg")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }
        }

        val need = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (need.all { has(it) }) startCamera() else permLauncher.launch(need)
    }

    override fun onResume() {
        super.onResume()
        if (hasLocation()) startLocation()
        if (orientation.canDetectOrientation()) orientation.enable()
        ui.post(ticker)
        // Vừa bật bản đồ / địa chỉ trong cài đặt -> tải lại
        lastLoc?.let { if ((prefs.showMap && mapBmp == null) || (prefs.showAddress && address == null)) refreshInfo(it) }
    }

    override fun onPause() {
        super.onPause()
        fused.removeLocationUpdates(locCb)
        orientation.disable()
        ui.removeCallbacks(ticker)
    }

    override fun onDestroy() {
        super.onDestroy()
        captureExec.shutdown()
        netExec.shutdown()
    }

    private fun has(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
    private fun hasLocation() = has(Manifest.permission.ACCESS_FINE_LOCATION) || has(Manifest.permission.ACCESS_COARSE_LOCATION)
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    // ---------- Camera ----------
    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                .build()
            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            try {
                camera?.let {
                    it.cameraInfo.zoomState.removeObservers(this)
                    it.cameraInfo.cameraState.removeObservers(this)
                }
                provider.unbindAll()
                val cam = provider.bindToLifecycle(this, selector, preview, capture)
                camera = cam
                imageCapture = capture
                zoomBuiltFor = null
                cam.cameraInfo.zoomState.observe(this) { z ->
                    if (zoomBuiltFor !== cam) buildZoomChips(cam, z.minZoomRatio, z.maxZoomRatio)
                    highlightZoom(z.zoomRatio)
                }
                // Đèn pin mất khi camera đóng/mở lại -> áp lại mỗi lần camera OPEN
                cam.cameraInfo.cameraState.observe(this) { st ->
                    if (st.type == CameraState.Type.OPEN) applyFlash()
                }
                applyFlash()
            } catch (e: Exception) {
                toast("Không mở được camera: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // ---------- Flash ----------
    private fun applyFlash() {
        val cam = camera ?: return
        val ic = imageCapture ?: return
        val has = cam.cameraInfo.hasFlashUnit()
        btnFlash.visibility = if (has) View.VISIBLE else View.GONE
        if (!has) return
        val m = prefs.flashMode
        cam.cameraControl.enableTorch(m == 3)
        ic.flashMode = when (m) {
            1 -> ImageCapture.FLASH_MODE_AUTO
            2 -> ImageCapture.FLASH_MODE_ON
            else -> ImageCapture.FLASH_MODE_OFF
        }
        btnFlash.text = flashLabels[m]
    }

    // ---------- Zoom xa/gần ----------
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun fmtZoom(r: Float): String =
        if (kotlin.math.abs(r - Math.round(r)) < 0.05f) "${Math.round(r)}x"
        else String.format(Locale.US, "%.1fx", r)

    private fun buildZoomChips(cam: Camera, min: Float, max: Float) {
        zoomBuiltFor = cam
        zoomRow.removeAllViews()
        zoomChips.clear()
        val presets = mutableListOf<Float>()
        if (min < 0.95f) presets += min          // góc rộng (ultra-wide) nếu máy hỗ trợ
        presets += 1f
        listOf(2f, 5f, 10f).filter { it <= max + 0.01f }.forEach { presets += it }
        for (r in presets) {
            val tv = TextView(this).apply {
                text = fmtZoom(r)
                setTextColor(Color.WHITE)
                textSize = 13f
                gravity = Gravity.CENTER
                background = ContextCompat.getDrawable(this@MainActivity, R.drawable.round_btn)
                setOnClickListener { cam.cameraControl.setZoomRatio(r) }
            }
            zoomRow.addView(tv, LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                marginStart = dp(6); marginEnd = dp(6)
            })
            zoomChips += r to tv
        }
        zoomRow.visibility = if (max > 1.05f || min < 0.95f) View.VISIBLE else View.GONE
    }

    private fun highlightZoom(cur: Float) {
        if (zoomChips.isEmpty()) return
        val sel = zoomChips.lastOrNull { it.first <= cur + 0.05f } ?: zoomChips.first()
        for (chip in zoomChips) {
            val (r, tv) = chip
            if (chip === sel) { tv.text = fmtZoom(cur); tv.setTextColor(Color.rgb(255, 213, 79)) }
            else { tv.text = fmtZoom(r); tv.setTextColor(Color.WHITE) }
        }
    }

    /** Chụm 2 ngón để zoom, chạm 1 lần để lấy nét. */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupGestures() {
        val scaleDet = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(d: ScaleGestureDetector): Boolean { scaling = true; return true }
            override fun onScale(d: ScaleGestureDetector): Boolean {
                val cam = camera ?: return false
                val z = cam.cameraInfo.zoomState.value ?: return false
                cam.cameraControl.setZoomRatio((z.zoomRatio * d.scaleFactor).coerceIn(z.minZoomRatio, z.maxZoomRatio))
                return true
            }
        })
        previewView.setOnTouchListener { v, e ->
            scaleDet.onTouchEvent(e)
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> scaling = false
                MotionEvent.ACTION_UP -> {
                    if (!scaling) {
                        camera?.let { cam ->
                            val pt = previewView.meteringPointFactory.createPoint(e.x, e.y)
                            cam.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(pt).build())
                        }
                        v.performClick()
                    }
                }
            }
            true
        }
    }

    private fun takePhoto() {
        val ic = imageCapture ?: return
        btnShot.isEnabled = false
        btnShot.alpha = 0.4f
        val time = System.currentTimeMillis()
        val loc = lastLoc
        val info = Watermark.buildInfo(prefs, loc, address, time)
        val baseName = PhotoSaver.buildName(prefs, time, street)
        val folder = prefs.folder
        val map = if (prefs.showMap && loc != null) mapBmp else null
        val mirror = lensFacing == CameraSelector.LENS_FACING_FRONT

        previewView.animate().alpha(0.2f).setDuration(60).withEndAction {
            previewView.animate().alpha(1f).setDuration(120).start()
        }.start()

        ic.takePicture(captureExec, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    val rot = image.imageInfo.rotationDegrees
                    var bmp = image.toBitmap()
                    image.close()
                    if (rot != 0 || mirror) {
                        val m = Matrix()
                        if (rot != 0) m.postRotate(rot.toFloat())
                        if (mirror) m.postScale(-1f, 1f)
                        val r = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                        if (r !== bmp) bmp.recycle()
                        bmp = r
                    }
                    val logo = if (prefs.showLogo) Prefs.loadLogo(this@MainActivity) else null
                    val out = Watermark.draw(bmp, info, map, logo)
                    val uri = PhotoSaver.save(this@MainActivity, out, time, loc, info.asText(), baseName, folder)
                    val thumbH = 160
                    val thumb = Bitmap.createScaledBitmap(out, thumbH * out.width / out.height, thumbH, true)
                    out.recycle()
                    runOnUiThread {
                        lastUri = uri
                        ivThumb.setImageBitmap(thumb)
                        toast(if (uri != null) "Đã lưu vào DCIM/$folder" else "Lưu ảnh thất bại")
                        doneShot()
                    }
                } catch (e: Throwable) {
                    runOnUiThread { toast("Lỗi xử lý ảnh: ${e.message}"); doneShot() }
                }
            }

            override fun onError(e: ImageCaptureException) {
                runOnUiThread { toast("Chụp lỗi: ${e.message}"); doneShot() }
            }
        })
    }

    private fun doneShot() {
        btnShot.isEnabled = true
        btnShot.alpha = 1f
    }

    // ---------- Vị trí ----------
    @SuppressLint("MissingPermission")
    private fun startLocation() {
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
            .setMinUpdateIntervalMillis(1000)
            .build()
        fused.requestLocationUpdates(req, locCb, Looper.getMainLooper())
        fused.lastLocation.addOnSuccessListener { if (it != null && lastLoc == null) onLocation(it) }
    }

    private fun onLocation(l: Location) {
        lastLoc = l
        val prev = infoLoc
        val now = System.currentTimeMillis()
        val moved = prev == null || prev.distanceTo(l) > 30f
        val retry = (address == null || (prefs.showMap && mapBmp == null)) && now - infoTime > 15000
        if (moved || retry) refreshInfo(l)
        updateOverlay()
    }

    /** Lấy địa chỉ + bản đồ ở nền. Không có mạng thì giữ toạ độ thôi. */
    private fun refreshInfo(l: Location) {
        if (infoBusy) return
        infoBusy = true
        infoTime = System.currentTimeMillis()
        val far = infoLoc?.let { it.distanceTo(l) > 200f } ?: true
        infoLoc = l
        val wantMap = prefs.showMap
        val wantAddr = prefs.showAddress
        netExec.execute {
            try {
                if (wantAddr) {
                    val a = geocode(l)
                    if (a != null) { address = a.first; street = a.second }
                    else if (far) { address = null; street = null }
                }
                if (wantMap) {
                    val m = MapTiles.fetch(l.latitude, l.longitude)
                    if (m != null) mapBmp = m else if (far) mapBmp = null
                }
            } finally {
                infoBusy = false
                runOnUiThread { updateOverlay() }
            }
        }
    }

    private val plusCode = Regex("^[23456789CFGHJMPQRVWX]{4,8}\\+[23456789CFGHJMPQRVWX]{2,3},?\\s*")

    /** Trả về (địa chỉ đầy đủ, tên đường). */
    @Suppress("DEPRECATION")
    private fun geocode(l: Location): Pair<String, String?>? = try {
        if (!Geocoder.isPresent()) null
        else Geocoder(this, Locale("vi", "VN")).getFromLocation(l.latitude, l.longitude, 1)
            ?.firstOrNull()?.let { a ->
                val full = (a.getAddressLine(0)
                    ?: listOfNotNull(a.thoroughfare, a.subLocality, a.subAdminArea, a.adminArea).joinToString(", "))
                    .replace(plusCode, "")
                    .removeSuffix(", Việt Nam").removeSuffix(", Vietnam")
                full to (a.thoroughfare ?: a.subLocality ?: a.locality)
            }
    } catch (e: Exception) {
        null
    }

    private fun updateOverlay() {
        val info = Watermark.buildInfo(prefs, lastLoc, address, System.currentTimeMillis())
        tvOverlay.text = info.asText()
        val m = if (prefs.showMap) mapBmp else null
        ivMap.visibility = if (m != null) View.VISIBLE else View.GONE
        if (m != null) ivMap.setImageBitmap(m)
    }
}
