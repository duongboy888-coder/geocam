package com.duong.geocam

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.materialswitch.MaterialSwitch

class SettingsActivity : AppCompatActivity() {

    private val prefs by lazy { Prefs(this) }
    private lateinit var etTitle: EditText
    private lateinit var etLines: EditText
    private lateinit var etNote: EditText
    private lateinit var swDate: MaterialSwitch
    private lateinit var swAddress: MaterialSwitch
    private lateinit var swCoords: MaterialSwitch
    private lateinit var swMap: MaterialSwitch
    private lateinit var swLogo: MaterialSwitch
    private lateinit var ivLogo: ImageView
    private lateinit var etFolder: EditText
    private lateinit var swFileName: MaterialSwitch

    private val pickLogo = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            if (Prefs.saveLogo(this, uri)) {
                swLogo.isChecked = true
                showLogo()
            } else Toast.makeText(this, "Không đọc được ảnh", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }
        etTitle = findViewById(R.id.etTitle)
        etLines = findViewById(R.id.etLines)
        etNote = findViewById(R.id.etNote)
        swDate = findViewById(R.id.swDate)
        swAddress = findViewById(R.id.swAddress)
        swCoords = findViewById(R.id.swCoords)
        swMap = findViewById(R.id.swMap)
        swLogo = findViewById(R.id.swLogo)
        ivLogo = findViewById(R.id.ivLogo)
        etFolder = findViewById(R.id.etFolder)
        swFileName = findViewById(R.id.swFileName)
        etFolder.setText(prefs.folder)
        swFileName.isChecked = prefs.nameByInfo

        etTitle.setText(prefs.title)
        etLines.setText(prefs.customLines)
        etNote.setText(prefs.note)
        swDate.isChecked = prefs.showDate
        swAddress.isChecked = prefs.showAddress
        swCoords.isChecked = prefs.showCoords
        swMap.isChecked = prefs.showMap
        swLogo.isChecked = prefs.showLogo
        showLogo()

        findViewById<Button>(R.id.btnPickLogo).setOnClickListener {
            pickLogo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        findViewById<Button>(R.id.btnRemoveLogo).setOnClickListener {
            Prefs.removeLogo(this)
            swLogo.isChecked = false
            showLogo()
        }
    }

    private fun showLogo() {
        val b = Prefs.loadLogo(this)
        if (b != null) ivLogo.setImageBitmap(b) else ivLogo.setImageDrawable(null)
    }

    override fun onPause() {
        super.onPause()
        prefs.title = etTitle.text.toString()
        prefs.customLines = etLines.text.toString()
        prefs.note = etNote.text.toString()
        prefs.showDate = swDate.isChecked
        prefs.showAddress = swAddress.isChecked
        prefs.showCoords = swCoords.isChecked
        prefs.showMap = swMap.isChecked
        prefs.folder = PhotoSaver.cleanFolder(etFolder.text.toString())
        prefs.nameByInfo = swFileName.isChecked
        prefs.showLogo = swLogo.isChecked && Prefs.loadLogo(this) != null
    }
}
