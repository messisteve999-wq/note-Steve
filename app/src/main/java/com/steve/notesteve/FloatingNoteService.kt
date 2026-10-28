package com.steve.notesteve

import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class FloatingNoteService : Service() {
    private lateinit var windowManager: WindowManager
    private var panel: LinearLayout? = null
    private var editor: EditText? = null
    private var params: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (panel != null) return START_NOT_STICKY
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val note = NoteStore.load(this).firstOrNull() ?: Note("", "")
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 10, 14, 10)
            setBackgroundColor(0xFFF4F6FA.toInt())
        }

        val header = TextView(this).apply {
            text = "📒 Note - Steve   (déplacer ici)"
            textSize = 16f
            setPadding(4, 8, 4, 8)
        }
        box.addView(header)

        editor = EditText(this).apply {
            setText(note.body)
            hint = "Écris ici..."
            minLines = 4
            maxLines = 9
        }
        box.addView(editor, LinearLayout.LayoutParams(300, 180))

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(Button(this).apply {
            text = "Sauver"
            setOnClickListener {
                val list = NoteStore.load(this@FloatingNoteService)
                val title = list.firstOrNull()?.title ?: "Note flottante"
                if (list.isEmpty()) list.add(Note(title, editor?.text.toString()))
                else list[0] = Note(title, editor?.text.toString())
                NoteStore.save(this@FloatingNoteService, list)
            }
        })
        actions.addView(Button(this).apply {
            text = "Copier"
            setOnClickListener {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Note", editor?.text))
            }
        })
        actions.addView(Button(this).apply {
            text = "Fermer"
            setOnClickListener { stopSelf() }
        })
        box.addView(actions)

        val type = if (android.os.Build.VERSION.SDK_INT >= 26)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 30
            y = 120
        }

        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        header.setOnTouchListener { _, event ->
            val p = params ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = p.x
                    startY = p.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    p.x = startX + (event.rawX - touchX).toInt()
                    p.y = startY + (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(box, p)
                    true
                }
                else -> true
            }
        }

        panel = box
        windowManager.addView(box, params)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        panel?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        panel = null
        super.onDestroy()
    }
}
