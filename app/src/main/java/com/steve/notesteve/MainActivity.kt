package com.steve.notesteve

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    private lateinit var titleInput: EditText
    private lateinit var bodyInput: EditText
    private lateinit var noteSpinner: Spinner
    private var notes = mutableListOf<Note>()
    private var selected = 0

    private val exportFile = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) writeFile(uri) }

    private val importFile = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) readFile(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notes = NoteStore.load(this)
        buildUi()
        showNote(0)
    }

    override fun onResume() {
        super.onResume()
        if (android.provider.Settings.canDrawOverlays(this)) {
            startService(Intent(this, FloatingNoteService::class.java))
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 18)
            setBackgroundColor(Color.rgb(242, 246, 252))
        }

        root.addView(TextView(this).apply {
            text = "📒  Note - Steve"
            textSize = 26f
            setTextColor(Color.rgb(21, 101, 192))
            setPadding(0, 4, 0, 16)
        })

        noteSpinner = Spinner(this)
        root.addView(noteSpinner, ViewGroup.LayoutParams(-1, 52))
        noteSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?, view: android.view.View?,
                position: Int, id: Long
            ) {
                if (position in notes.indices) {
                    selected = position
                    showNote(position)
                }
            }
        }

        titleInput = EditText(this).apply {
            hint = "Titre de la note"
            textSize = 18f
            isSingleLine = true
        }
        root.addView(titleInput, ViewGroup.LayoutParams(-1, 58))

        bodyInput = EditText(this).apply {
            hint = "Écris ta note ici..."
            textSize = 17f
            gravity = android.view.Gravity.TOP
            minLines = 8
            setPadding(12, 12, 12, 12)
            setBackgroundColor(Color.WHITE)
        }
        root.addView(bodyInput, LinearLayout.LayoutParams(-1, 0, 1f))

        fun button(label: String, action: () -> Unit) {
            root.addView(Button(this).apply {
                text = label
                setOnClickListener { action() }
            }, ViewGroup.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        button("Enregistrer") { saveCurrent() }
        button("Nouvelle note") {
            saveCurrent()
            notes.add(Note("Nouvelle note", ""))
            selected = notes.lastIndex
            NoteStore.save(this, notes)
            refreshSpinner()
            showNote(selected)
        }
        button("Copier le texte") {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Note Steve", bodyInput.text))
            toast("Texte copié")
        }
        button("Coller le texte") {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            bodyInput.setText(clipboard.primaryClip?.getItemAt(0)?.coerceToText(this) ?: "")
        }
        button("Exporter les notes (JSON)") {
            saveCurrent()
            exportFile.launch("notes-steve.json")
        }
        button("Restaurer des notes (JSON)") {
            importFile.launch(arrayOf("application/json", "text/*"))
        }
        button("Ouvrir la fenêtre flottante") {
            if (android.provider.Settings.canDrawOverlays(this)) {
                startService(Intent(this, FloatingNoteService::class.java))
                toast("Fenêtre flottante lancée")
            } else {
                startActivity(Intent(
                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                ))
                toast("Autorise Note - Steve à s'afficher par-dessus les autres applications")
            }
        }
        button("Supprimer cette note") {
            if (notes.size > 1) {
                notes.removeAt(selected.coerceIn(notes.indices))
                selected = 0
                NoteStore.save(this, notes)
                refreshSpinner()
                showNote(0)
            } else {
                titleInput.setText("")
                bodyInput.setText("")
                toast("La note est vide. Enregistre pour la conserver.")
            }
        }

        setContentView(root)
        refreshSpinner()
    }

    private fun refreshSpinner() {
        noteSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            notes.mapIndexed { i, note -> "${i + 1}. ${note.title.ifBlank { "Sans titre" }}" }
        )
        if (notes.isNotEmpty()) noteSpinner.setSelection(selected.coerceIn(notes.indices))
    }

    private fun showNote(index: Int) {
        if (index !in notes.indices) return
        selected = index
        titleInput.setText(notes[index].title)
        bodyInput.setText(notes[index].body)
    }

    private fun saveCurrent() {
        if (notes.isEmpty()) notes.add(Note("", ""))
        selected = selected.coerceIn(notes.indices)
        notes[selected] = Note(titleInput.text.toString(), bodyInput.text.toString())
        NoteStore.save(this, notes)
        refreshSpinner()
        toast("Note enregistrée")
    }

    private fun writeFile(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                it.write(NoteStore.exportJson(this))
            }
            toast("Exportation terminée")
        } catch (e: Exception) {
            toast("Erreur d'exportation : ${e.message}")
        }
    }

    private fun readFile(uri: Uri) {
        try {
            val raw = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: return
            if (NoteStore.importJson(this, raw)) {
                notes = NoteStore.load(this)
                selected = 0
                refreshSpinner()
                showNote(0)
                toast("Notes restaurées")
            } else toast("Fichier JSON invalide")
        } catch (e: Exception) {
            toast("Erreur d'importation : ${e.message}")
        }
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    override fun onPause() {
        if (::titleInput.isInitialized) {
            selected = selected.coerceIn(notes.indices)
            if (notes.isNotEmpty()) {
                notes[selected] = Note(titleInput.text.toString(), bodyInput.text.toString())
                NoteStore.save(this, notes)
            }
        }
        super.onPause()
    }
}
