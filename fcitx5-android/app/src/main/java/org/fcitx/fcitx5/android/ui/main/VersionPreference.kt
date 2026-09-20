package org.fcitx.fcitx5.android.ui.main

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.SystemClock
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import org.fcitx.fcitx5.android.R
import kotlin.math.abs

internal class VersionPreference(context: Context, private val onHeld: () -> Unit) : Preference(context) {
    private val gesture = VersionHoldGesture()
    private var row: View? = null
    private var originX = 0f
    private var originY = 0f
    private var versionText = ""
    private var available = false
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val hold = Runnable {
        val view = row
        if (view != null && view.isAttachedToWindow && view.hasWindowFocus() &&
            gesture.fire(SystemClock.uptimeMillis())) {
            view.isPressed = false
            onHeld()
        } else cancelHold()
    }
    private val attachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) = Unit
        override fun onViewDetachedFromWindow(v: View) { cancelHold() }
    }

    fun showVersion(version: String, available: Boolean) {
        versionText = version
        this.available = available
        if (summary == null) summary = version
        if (title == null) title = context.getString(R.string.current_version)
        // Do not notifyChanged: a RecyclerView rebind would cancel an active eight-second hold.
        renderVersion()
    }

    private fun renderVersion() {
        val label = if (available) SpannableString("$versionText  ●").apply {
            setSpan(ForegroundColorSpan(Color.RED), length - 1, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        } else versionText
        row?.findViewById<TextView>(android.R.id.summary)?.text = label
        row?.contentDescription = "$title, $versionText" +
            if (available) ", ${context.getString(R.string.market_update_title)}" else ""
    }

    fun cancelHold() {
        row?.removeCallbacks(hold)
        row?.isPressed = false
        gesture.cancel()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        cancelHold()
        row?.removeOnAttachStateChangeListener(attachListener)
        row?.setOnTouchListener(null)
        super.onBindViewHolder(holder)
        row = holder.itemView
        holder.itemView.apply {
            isLongClickable = false
            renderVersion()
            addOnAttachStateChangeListener(attachListener)
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        cancelHold()
                        originX = event.x; originY = event.y
                        gesture.down(SystemClock.uptimeMillis())
                        view.isPressed = true
                        view.postDelayed(hold, 8_000L)
                    }
                    MotionEvent.ACTION_MOVE -> if (event.x < 0 || event.y < 0 ||
                        event.x >= view.width || event.y >= view.height ||
                        abs(event.x - originX) > slop || abs(event.y - originY) > slop) cancelHold()
                    MotionEvent.ACTION_UP -> {
                        view.removeCallbacks(hold)
                        // If the UI queue was delayed, still honour the elapsed threshold.
                        if (gesture.fire(SystemClock.uptimeMillis())) onHeld()
                        val click = gesture.up()
                        view.isPressed = false
                        if (click) view.performClick()
                    }
                    MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> cancelHold()
                }
                true
            }
        }
    }
}
