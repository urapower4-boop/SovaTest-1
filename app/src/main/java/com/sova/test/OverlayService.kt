package com.sova.test

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.*
import android.text.TextUtils
import android.provider.Settings
import android.util.Log
import android.view.*
import android.view.WindowManager.LayoutParams as WMP
import android.widget.*
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class OverlayService : Service() {

    companion object {
        const val CHANNEL_ID = "sova_overlay"
        const val NOTIF_ID = 7701
        @Volatile var instance: OverlayService? = null
    }

    private lateinit var wm: WindowManager
    private lateinit var panel: View
    private lateinit var answerView: AnswerOverlayView
    private lateinit var panelParams: WMP
    private lateinit var answerParams: WMP

    private lateinit var btnToggle: Button
    private lateinit var btnClose: Button
    private lateinit var btnMin: Button
    private lateinit var logView: TextView
    private lateinit var logScroll: ScrollView
    private lateinit var titleBar: View
    private lateinit var resizeHandle: View

    private var scanning = false
    private var miniBubble: ImageView? = null

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var loopJob: Job? = null
    private var gemini: GeminiClient? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        gemini = GeminiClient(getString(R.string.gemini_api_key))
        startInForeground()
        buildAnswerLayer()
        buildPanel()
        log("🦉 SovaTest готова. Режим: ${if (isA11yOn()) "АВТО" else "ПІДКАЗКА"}")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    // ---------- UI ----------

    @SuppressLint("ClickableViewAccessibility")
    private fun buildPanel() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xE6000010.toInt())
            setPadding(16, 16, 16, 16)
        }

        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val title = TextView(this).apply {
            text = "🦉 SovaTest"
            setTextColor(0xFF00FF88.toInt())
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        btnMin = Button(this).apply {
            text = "—"; setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF333333.toInt())
            layoutParams = LinearLayout.LayoutParams(80, 80)
        }
        btnClose = Button(this).apply {
            text = "×"; setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFFAA0000.toInt())
            layoutParams = LinearLayout.LayoutParams(80, 80).apply { leftMargin = 8 }
        }
        top.addView(title); top.addView(btnMin); top.addView(btnClose)

        btnToggle = Button(this).apply {
            text = "● СКАНУВАТИ"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF00AA55.toInt())
        }

        logView = TextView(this).apply {
            setTextColor(0xFFC0C0C0.toInt())
            textSize = 11f
            typeface = Typeface.MONOSPACE
        }
        logScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 400)
            addView(logView)
        }

        resizeHandle = View(this).apply {
            setBackgroundColor(0xFF555555.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 18).apply { topMargin = 8 }
        }

        root.addView(top)
        val tBarParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT)
        tBarParams.topMargin = 8
        root.addView(btnToggle, tBarParams)
        root.addView(logScroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = 8 })
        root.addView(resizeHandle)

        panel = root
        titleBar = top

        panelParams = WMP(
            780, WMP.WRAP_CONTENT,
            overlayType(),
            WMP.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40; y = 120
        }
        wm.addView(panel, panelParams)

        btnToggle.setOnClickListener { toggleScan
