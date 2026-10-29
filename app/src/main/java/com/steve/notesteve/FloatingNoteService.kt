package com.steve.notesteve

import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.text.Editable
import android.text.TextWatcher

class FloatingNoteService : Service() {
    private lateinit var windowManager: WindowManager
    private var bubble: ImageView? = null
    private var panel: LinearLayout? = null
    private var editor: EditText? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var movingBubble = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (bubble != null) return START_STICKY
        if (!android.provider.Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showBubble()
        return START_STICKY
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun roundedBackground(color: Int, stroke: Int = Color.TRANSPARENT): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(16).toFloat()
            if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
        }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun showBubble() {
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_demon)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(5), dp(5), dp(5), dp(5))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(20, 7, 10))
                setStroke(dp(2), Color.rgb(255, 35, 55))
            }
            contentDescription = "Ouvrir ou fermer Note-Steve V2"
        }

        val p = WindowManager.LayoutParams(
            dp(58), dp(58), overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(12)
            y = dp(180)
        }
        bubbleParams = p

        var downX = 0f
        var downY = 0f
        var originX = 0
        var originY = 0
        var moved = false

        icon.setOnTouchListener { _, event ->
            val params = bubbleParams ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    originX = params.x
                    originY = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (kotlin.math.abs(dx) > dp(5) || kotlin.math.abs(dy) > dp(5)) moved = true
                    if (moved) {
                        params.x = originX + dx
                        params.y = originY + dy
                        try { windowManager.updateViewLayout(icon, params) } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        if (panel == null) openPanel() else closePanel()
                    }
                    true
                }
                else -> true
            }
        }

        bubble = icon
        try {
            windowManager.addView(icon, p)
        } catch (e: Exception) {
            bubble = null
            Toast.makeText(
                this,
                "Bulle impossible à afficher : ${e.message ?: "erreur Android"}",
                Toast.LENGTH_LONG
            ).show()
            stopSelf()
        }
    }

    private fun openPanel() {
        val saved = Note("Note flottante", getSharedPreferences("floating_note", MODE_PRIVATE).getString("body", "") ?: "")
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(12))
            background = roundedBackground(Color.rgb(18, 18, 22), Color.rgb(255, 48, 63))
        }

        val header = TextView(this).apply {
            text = "😈  Note-Steve V2       ✕"
            textSize = 17f
            setTextColor(Color.rgb(255, 70, 82))
            setPadding(dp(4), dp(6), dp(4), dp(12))
            contentDescription = "Déplacer la fenêtre ou fermer"
            setOnClickListener { closePanel() }
        }
        box.addView(header, LinearLayout.LayoutParams(-1, dp(42)))

        val textEditor = EditText(this).apply {
            setText(saved.body)
            hint = "Écris ton texte ici..."
            setHintTextColor(Color.LTGRAY)
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.TOP or Gravity.START
            minLines = 5
            maxLines = 10
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = roundedBackground(Color.rgb(32, 32, 38), Color.rgb(95, 95, 105))
            setSelectAllOnFocus(false)
        }
        editor = textEditor
        box.addView(textEditor, LinearLayout.LayoutParams(-1, dp(190)))

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        fun actionButton(label: String, action: () -> Unit) {
            val button = Button(this).apply {
                text = label
                textSize = 11f
                isAllCaps = false
                setTextColor(Color.WHITE)
                background = roundedBackground(Color.rgb(47, 25, 30), Color.rgb(255, 48, 63))
                setOnClickListener { action() }
            }
            actions.addView(button, LinearLayout.LayoutParams(0, dp(46), 1f).apply {
                setMargins(dp(2), dp(5), dp(2), dp(2))
            })
        }

        actionButton("Copier") { copySelection(false) }
        actionButton("Couper") { copySelection(true) }
        actionButton("Coller") { pasteClipboard() }
        box.addView(actions, LinearLayout.LayoutParams(-1, dp(54)))

        val selectAll = Button(this).apply {
            text = "Tout sélectionner"
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = roundedBackground(Color.rgb(42, 42, 50), Color.rgb(255, 48, 63))
            setOnClickListener {
                textEditor.requestFocus()
                textEditor.selectAll()
            }
        }
        box.addView(selectAll, LinearLayout.LayoutParams(-1, dp(42)))

        val p = WindowManager.LayoutParams(
            minOf(dp(330), resources.displayMetrics.widthPixels - dp(24)),
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = dp(105)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        panelParams = p

        header.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0
            private var startY = 0
            private var touchX = 0f
            private var touchY = 0f
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                val params = panelParams ?: return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = params.x
                        startY = params.y
                        touchX = event.rawX
                        touchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = startX + (event.rawX - touchX).toInt()
                        params.y = startY + (event.rawY - touchY).toInt()
                        try { windowManager.updateViewLayout(box, params) } catch (_: Exception) {}
                        return true
                    }
                    MotionEvent.ACTION_UP -> return true
                }
                return false
            }
        })

        textEditor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                saveFloatingText(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        panel = box
        try {
            windowManager.addView(box, p)
            textEditor.requestFocus()
            textEditor.postDelayed({
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(textEditor, InputMethodManager.SHOW_IMPLICIT)
            }, 180)
        } catch (_: Exception) {
            panel = null
            editor = null
            Toast.makeText(this, "Impossible d'ouvrir la fenêtre flottante", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveFloatingText(body: String) {
        getSharedPreferences("floating_note", MODE_PRIVATE)
            .edit()
            .putString("body", body)
            .apply()
    }

    private fun copySelection(cut: Boolean) {
        val field = editor ?: return
        val text = field.text
        val start = field.selectionStart.coerceAtLeast(0)
        val end = field.selectionEnd.coerceAtLeast(0)
        val hasSelection = start != end
        val from = if (hasSelection) minOf(start, end) else 0
        val to = if (hasSelection) maxOf(start, end) else text.length
        val selectedText = text.substring(from, to)
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Note-Steve", selectedText))
        if (cut && selectedText.isNotEmpty()) {
            text.delete(from, to)
            field.setSelection(from.coerceAtMost(text.length))
        }
        Toast.makeText(this, if (cut) "Texte coupé" else "Texte copié", Toast.LENGTH_SHORT).show()
    }

    private fun pasteClipboard() {
        val field = editor ?: return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip == null || clip.itemCount == 0) {
            Toast.makeText(this, "Le presse-papiers est vide", Toast.LENGTH_SHORT).show()
            return
        }
        val paste = clip.getItemAt(0).coerceToText(this).toString()
        val start = field.selectionStart.coerceAtLeast(0)
        val end = field.selectionEnd.coerceAtLeast(0)
        val from = minOf(start, end)
        val to = maxOf(start, end)
        field.text.replace(from, to, paste)
        field.setSelection((from + paste.length).coerceAtMost(field.text.length))
    }

    private fun closePanel() {
        editor?.let { field ->
            saveFloatingText(field.text.toString())
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(field.windowToken, 0)
        }
        panel?.let { view ->
            try { windowManager.removeView(view) } catch (_: Exception) {}
        }
        panel = null
        editor = null
        panelParams = null
    }

    override fun onDestroy() {
        closePanel()
        bubble?.let { view ->
            try { windowManager.removeView(view) } catch (_: Exception) {}
        }
        bubble = null
        super.onDestroy()
    }
}
