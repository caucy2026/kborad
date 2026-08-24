/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard.aquarium

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES30
import android.util.Log
import android.view.Surface
import android.view.TextureView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.Calendar
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.LockSupport
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private enum class AquariumTouchAction {
    DOWN,
    MOVE,
    UP
}

private data class AquariumTouchCommand(
    val action: AquariumTouchAction,
    val x: Float = 0f,
    val y: Float = 0f
)

/**
 * A self-contained GLES 3 aquarium surface used only by the desktop keyboard.
 * Android key Views stay above this surface and keep full ownership of hit testing.
 */
class DesktopAquariumView(context: Context) : TextureView(context),
    TextureView.SurfaceTextureListener {

    private val touchCommands = ConcurrentLinkedQueue<AquariumTouchCommand>()
    private var renderThread: AquariumRenderThread? = null
    private var active = false

    init {
        isOpaque = true
        isClickable = false
        isFocusable = false
        surfaceTextureListener = this
    }

    fun activate() {
        active = true
        if (isAvailable) {
            surfaceTexture?.let { startRenderer(it, width, height) }
        }
    }

    fun deactivate() {
        active = false
        stopRenderer()
        touchCommands.clear()
    }

    fun touchDownAt(normalizedX: Float, normalizedY: Float) {
        enqueueTouch(AquariumTouchAction.DOWN, normalizedX, normalizedY)
    }

    fun moveTouchTo(normalizedX: Float, normalizedY: Float) {
        enqueueTouch(AquariumTouchAction.MOVE, normalizedX, normalizedY)
    }

    fun releaseTouch() {
        enqueueTouch(AquariumTouchAction.UP, 0f, 0f)
    }

    private fun enqueueTouch(action: AquariumTouchAction, normalizedX: Float, normalizedY: Float) {
        if (!active) return
        while (touchCommands.size >= MAX_PENDING_INTERACTIONS) touchCommands.poll()
        touchCommands.offer(
            AquariumTouchCommand(
                action,
                normalizedX.coerceIn(0f, 1f),
                normalizedY.coerceIn(0f, 1f)
            )
        )
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        if (active) startRenderer(surface, width, height)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        configureSurfaceBuffer(surface, width, height)
        renderThread?.resize(width, height)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        stopRenderer()
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    private fun startRenderer(surface: SurfaceTexture, width: Int, height: Int) {
        if (renderThread?.isAlive == true || width <= 0 || height <= 0) return
        configureSurfaceBuffer(surface, width, height)
        renderThread = AquariumRenderThread(surface, width, height, touchCommands).also { it.start() }
    }

    private fun configureSurfaceBuffer(surface: SurfaceTexture, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val bufferWidth = min(width, MAX_RENDER_WIDTH)
        surface.setDefaultBufferSize(
            bufferWidth,
            (height * bufferWidth.toFloat() / width).toInt().coerceAtLeast(1)
        )
    }

    private fun stopRenderer() {
        renderThread?.requestStop()
        renderThread = null
    }

    private companion object {
        const val MAX_PENDING_INTERACTIONS = 12
        const val MAX_RENDER_WIDTH = 1080
    }
}

private class AquariumRenderThread(
    surfaceTexture: SurfaceTexture,
    initialWidth: Int,
    initialHeight: Int,
    private val commands: ConcurrentLinkedQueue<AquariumTouchCommand>
) : Thread("kboard-aquarium-gl") {

    private val surface = Surface(surfaceTexture)
    private val running = AtomicBoolean(true)
    @Volatile private var requestedWidth = initialWidth
    @Volatile private var requestedHeight = initialHeight

    fun resize(width: Int, height: Int) {
        requestedWidth = width
        requestedHeight = height
    }

    fun requestStop() {
        running.set(false)
        interrupt()
    }

    override fun run() {
        var egl: EglWindow? = null
        try {
            egl = EglWindow(surface)
            val engine = AquariumEngine()
            engine.create()
            var appliedWidth = 0
            var appliedHeight = 0
            while (running.get()) {
                val frameStart = System.nanoTime()
                val width = min(requestedWidth, MAX_RENDER_WIDTH)
                val height = (requestedHeight * width.toFloat() / requestedWidth.coerceAtLeast(1))
                    .toInt().coerceAtLeast(1)
                if (width != appliedWidth || height != appliedHeight) {
                    engine.resize(width, height)
                    appliedWidth = width
                    appliedHeight = height
                }
                drainTouchCommands(engine)
                engine.draw(frameStart)
                if (!egl.swapBuffers()) break
                val remaining = TARGET_FRAME_NS - (System.nanoTime() - frameStart)
                if (remaining > 0) LockSupport.parkNanos(remaining)
            }
            engine.destroy()
        } catch (error: Throwable) {
            Log.e(TAG, "Aquarium renderer stopped", error)
        } finally {
            egl?.release()
            surface.release()
        }
    }

    private fun drainTouchCommands(engine: AquariumEngine) {
        while (true) {
            val command = commands.poll() ?: break
            when (command.action) {
                AquariumTouchAction.DOWN -> engine.touchDown(command.x, command.y)
                AquariumTouchAction.MOVE -> engine.touchMove(command.x, command.y)
                AquariumTouchAction.UP -> engine.touchUp()
            }
        }
    }

    private companion object {
        const val TAG = "KBoardAquarium"
        const val MAX_RENDER_WIDTH = 1080
        const val TARGET_FRAME_NS = 33_333_334L
    }
}

private class EglWindow(private val nativeSurface: Surface) {
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var surface: EGLSurface = EGL14.EGL_NO_SURFACE

    init {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display != EGL14.EGL_NO_DISPLAY) { "No EGL display" }
        val versions = IntArray(2)
        check(EGL14.eglInitialize(display, versions, 0, versions, 1)) { "EGL init failed" }
        val configAttributes = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT_KHR,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_DEPTH_SIZE, 16,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        check(EGL14.eglChooseConfig(display, configAttributes, 0, configs, 0, 1, count, 0)) {
            "EGL config selection failed"
        }
        val config = checkNotNull(configs[0])
        context = EGL14.eglCreateContext(
            display,
            config,
            EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE),
            0
        )
        check(context != EGL14.EGL_NO_CONTEXT) { "GLES 3 context creation failed" }
        surface = EGL14.eglCreateWindowSurface(
            display,
            config,
            nativeSurface,
            intArrayOf(EGL14.EGL_NONE),
            0
        )
        check(surface != EGL14.EGL_NO_SURFACE) { "EGL window surface creation failed" }
        check(EGL14.eglMakeCurrent(display, surface, surface, context)) { "EGL makeCurrent failed" }
        EGL14.eglSwapInterval(display, 1)
    }

    fun swapBuffers(): Boolean = EGL14.eglSwapBuffers(display, surface)

    fun release() {
        if (display == EGL14.EGL_NO_DISPLAY) return
        EGL14.eglMakeCurrent(
            display,
            EGL14.EGL_NO_SURFACE,
            EGL14.EGL_NO_SURFACE,
            EGL14.EGL_NO_CONTEXT
        )
        if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
        if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
        EGL14.eglTerminate(display)
        display = EGL14.EGL_NO_DISPLAY
        surface = EGL14.EGL_NO_SURFACE
        context = EGL14.EGL_NO_CONTEXT
    }

    private companion object {
        const val EGL_OPENGL_ES3_BIT_KHR = 0x40
    }
}

private class AquariumEngine {

    private enum class FishBehavior {
        ROUTE,
        FOLLOW,
        PLAY
    }

    private enum class WeekdayIntroPhase {
        FORM,
        HOLD,
        DEPART,
        DONE
    }

    private data class Fish(
        var x: Float,
        var y: Float,
        var heading: Float,
        var forwardSpeed: Float,
        var angularSpeed: Float,
        var swimPhase: Float,
        var finPhase: Float,
        var tailDrive: Float,
        var leftFinDrive: Float,
        var rightFinDrive: Float,
        var turnDrive: Float,
        var fastTurnLatched: Boolean,
        var completedTailStrokes: Int,
        var brakeDrive: Float,
        var bank: Float,
        val depth: Float,
        val scale: Float,
        val cruiseSpeed: Float,
        val tailTempo: Float,
        val thrustScale: Float,
        val maxForwardSpeed: Float,
        val phase: Float,
        val seed: Float,
        val baseColor: FloatArray,
        val patchColor: FloatArray,
        val accentColor: FloatArray,
        val pattern: Float,
        val schoolId: Int,
        val routeCenterX: Float,
        val routeCenterY: Float,
        val routeRadiusX: Float,
        val routeRadiusY: Float,
        val routeDirection: Float,
        val routeShape: Float,
        var routeProgress: Float,
        var behavior: FishBehavior,
        var behaviorStep: Int,
        var behaviorUntil: Float
    )

    private data class FishPalette(
        val base: FloatArray,
        val patch: FloatArray,
        val accent: FloatArray
    )

    private data class Ripple(var x: Float = 0f, var y: Float = 0f, var start: Float = -100f)

    private data class DigitStroke(
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float
    )

    private val random = Random(0x4B4F49)
    private val fish = MutableList(MAX_FISH) { index -> createFish(index) }
    private val ripples = Array(MAX_RIPPLES) { Ripple() }
    private var nextRipple = 0
    private var attractionX = 0f
    private var attractionY = 0f
    private var attractionUntil = -1f
    private var attractionStartedAt = -1f
    private var touchHeld = false
    private var scatterUntil = -1f
    private val scatterTargetX = FloatArray(MAX_FISH)
    private val scatterTargetY = FloatArray(MAX_FISH)
    private var nextFeedReportAt = -1f
    private var activeFishCount = MAX_FISH
    private var sparkleFishIndex = -1
    private var previousSparkleFishIndex = -1
    private var sparkleStartedAt = -1f
    private var sparkleUntil = -1f
    private var nextSparkleAt = 1.2f
    private var interactionVariant = -1
    private var touchSparkleFishIndex = -1
    private val weekdayDigit = currentWeekdayDigit()
    private val weekdayDigitTargets = createWeekdayDigitTargets(weekdayDigit)
    private val weekdayDigitTargetForFish = IntArray(MAX_FISH) { it }
    private var weekdayIntroPhase = WeekdayIntroPhase.FORM
    private var weekdayIntroPhaseStartedAt = 0f

    private var waterProgram = 0
    private var fishProgram = 0
    private var waterVao = 0
    private var waterVbo = 0
    private var fishVao = 0
    private var fishVbo = 0
    private var fishVertexCount = 0
    private var waterTimeLocation = -1
    private var waterResolutionLocation = -1
    private var waterRipplesLocation = -1
    private var fishAspectLocation = -1
    private var fishPositionLocation = -1
    private var fishHeadingLocation = -1
    private var fishScaleLocation = -1
    private var fishSwimPhaseLocation = -1
    private var fishFinPhaseLocation = -1
    private var fishTailDriveLocation = -1
    private var fishLeftFinDriveLocation = -1
    private var fishRightFinDriveLocation = -1
    private var fishTurnDriveLocation = -1
    private var fishBrakeDriveLocation = -1
    private var fishSeedLocation = -1
    private var fishAlphaLocation = -1
    private var fishSpeedLocation = -1
    private var fishBankLocation = -1
    private var fishBaseColorLocation = -1
    private var fishPatchColorLocation = -1
    private var fishAccentColorLocation = -1
    private var fishPatternLocation = -1
    private var fishTimeLocation = -1
    private var fishSparkleLocation = -1
    private val rippleUniforms = FloatArray(MAX_RIPPLES * 4)
    private var width = 1
    private var height = 1
    private var startNanos = 0L
    private var previousNanos = 0L
    private var reportStartNanos = 0L
    private var reportFrames = 0
    private var healthyReports = 0

    fun create() {
        startNanos = System.nanoTime()
        previousNanos = startNanos
        reportStartNanos = startNanos
        assignWeekdayDigitTargets()
        waterProgram = createProgram(WATER_VERTEX_SHADER, WATER_FRAGMENT_SHADER)
        fishProgram = createProgram(FISH_VERTEX_SHADER, FISH_FRAGMENT_SHADER)
        waterTimeLocation = GLES30.glGetUniformLocation(waterProgram, "uTime")
        waterResolutionLocation = GLES30.glGetUniformLocation(waterProgram, "uResolution")
        waterRipplesLocation = GLES30.glGetUniformLocation(waterProgram, "uRipples[0]")
        fishAspectLocation = GLES30.glGetUniformLocation(fishProgram, "uAspect")
        fishPositionLocation = GLES30.glGetUniformLocation(fishProgram, "uPosition")
        fishHeadingLocation = GLES30.glGetUniformLocation(fishProgram, "uHeading")
        fishScaleLocation = GLES30.glGetUniformLocation(fishProgram, "uScale")
        fishSwimPhaseLocation = GLES30.glGetUniformLocation(fishProgram, "uSwimPhase")
        fishFinPhaseLocation = GLES30.glGetUniformLocation(fishProgram, "uFinPhase")
        fishTailDriveLocation = GLES30.glGetUniformLocation(fishProgram, "uTailDrive")
        fishLeftFinDriveLocation = GLES30.glGetUniformLocation(fishProgram, "uLeftFinDrive")
        fishRightFinDriveLocation = GLES30.glGetUniformLocation(fishProgram, "uRightFinDrive")
        fishTurnDriveLocation = GLES30.glGetUniformLocation(fishProgram, "uTurnDrive")
        fishBrakeDriveLocation = GLES30.glGetUniformLocation(fishProgram, "uBrakeDrive")
        fishSeedLocation = GLES30.glGetUniformLocation(fishProgram, "uSeed")
        fishAlphaLocation = GLES30.glGetUniformLocation(fishProgram, "uAlpha")
        fishSpeedLocation = GLES30.glGetUniformLocation(fishProgram, "uSpeed")
        fishBankLocation = GLES30.glGetUniformLocation(fishProgram, "uBank")
        fishBaseColorLocation = GLES30.glGetUniformLocation(fishProgram, "uBaseColor")
        fishPatchColorLocation = GLES30.glGetUniformLocation(fishProgram, "uPatchColor")
        fishAccentColorLocation = GLES30.glGetUniformLocation(fishProgram, "uAccentColor")
        fishPatternLocation = GLES30.glGetUniformLocation(fishProgram, "uPattern")
        fishTimeLocation = GLES30.glGetUniformLocation(fishProgram, "uTime")
        fishSparkleLocation = GLES30.glGetUniformLocation(fishProgram, "uSparkle")
        createWaterGeometry()
        createFishGeometry()
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        Log.i(TAG, "weekdayIntro digit=$weekdayDigit phase=FORM")
    }

    fun resize(width: Int, height: Int) {
        this.width = width.coerceAtLeast(1)
        this.height = height.coerceAtLeast(1)
        GLES30.glViewport(0, 0, this.width, this.height)
    }

    fun touchDown(x: Float, y: Float) {
        val now = elapsedSeconds(System.nanoTime())
        // Typing always wins over decoration. The fish retain their current physical state and
        // immediately use the existing C-start path toward the finger.
        weekdayIntroPhase = WeekdayIntroPhase.DONE
        ripples[nextRipple].apply {
            this.x = x
            this.y = 1f - y
            start = now
        }
        nextRipple = (nextRipple + 1) % ripples.size
        updateTouchTarget(x, y, now)
        attractionX = x * 2f - 1f
        attractionY = 1f - y * 2f
        interactionVariant = (interactionVariant + 1) % FEED_VARIANT_COUNT
        touchSparkleFishIndex = if (activeFishCount > 0) random.nextInt(activeFishCount) else -1
        // Touch-time prize sparkle replaces, rather than stacks with, the idle random sparkle.
        // This preserves the one-fish-at-a-time visual rule.
        if (sparkleFishIndex >= 0) previousSparkleFishIndex = sparkleFishIndex
        sparkleFishIndex = -1
        sparkleUntil = -1f
        nextSparkleAt = now + SPARKLE_PAUSE_MIN_SECONDS
        attractionStartedAt = now
        attractionUntil = now + ATTRACTION_SECONDS
        touchHeld = true
        scatterUntil = -1f
        nextFeedReportAt = now
        // A tap is a school-wide feeding cue. Prime the turning fins and place the tail just
        // before a power-stroke crossing; the following update still derives both yaw and
        // forward travel from the visible articulated fins rather than moving the model directly.
        for (index in 0 until activeFishCount) {
            val f = fish[index]
            f.completedTailStrokes = 0
            val targetHeading = atan2(attractionY - f.y, attractionX - f.x)
            val headingError = wrapAngle(targetHeading - f.heading)
            val forwardAlignment = ((cos(headingError) + 1f) * 0.5f).coerceIn(0f, 1f)
            val turnKick = (abs(headingError) / PI.toFloat()).coerceIn(0f, 1f)
            // A feeding tap triggers a goldfish C-start: the caudal membrane is first coiled,
            // then crosses its centreline on the next rendered frame. Translation is still
            // produced by that visible power stroke; this only removes the arbitrary wait for
            // whichever idle tail phase the fish happened to have before the tap.
            val reactionLead = 0.050f + (index % 4) * 0.012f
            f.swimPhase = wrapPhase(-TAIL_PROPULSION_PHASE - reactionLead)
            f.tailDrive = max(f.tailDrive, 0.88f + (f.tailTempo - 1f) * 0.42f)
            f.turnDrive = if (abs(headingError) > 0.04f) {
                if (headingError > 0f) 1f else -1f
            } else {
                0f
            }
            f.fastTurnLatched = false
            if (headingError >= 0f) {
                f.leftFinDrive = max(f.leftFinDrive, 0.72f + turnKick * 0.28f)
                f.rightFinDrive = max(f.rightFinDrive, 0.28f)
            } else {
                f.rightFinDrive = max(f.rightFinDrive, 0.72f + turnKick * 0.28f)
                f.leftFinDrive = max(f.leftFinDrive, 0.28f)
            }
            f.brakeDrive = max(f.brakeDrive, (1f - forwardAlignment) * 0.94f)
        }
        Log.i(
            TAG,
            "touchFeed variant=$interactionVariant luckyFish=$touchSparkleFishIndex " +
                    "position=${"%.2f".format(attractionX)},${"%.2f".format(attractionY)}"
        )
    }

    fun touchMove(x: Float, y: Float) {
        if (!touchHeld) return
        val now = elapsedSeconds(System.nanoTime())
        updateTouchTarget(x, y, now)
    }

    fun touchUp() {
        if (!touchHeld) return
        val now = elapsedSeconds(System.nanoTime())
        touchHeld = false
        attractionUntil = now
        scatterUntil = now + SCATTER_SECONDS
        // Fish keep their momentum and physically swim into one of three rotating release
        // scenes instead of being teleported when the finger leaves the water.
        touchSparkleFishIndex = -1
        nextSparkleAt = now + SPARKLE_PAUSE_MIN_SECONDS
        for (index in 0 until activeFishCount) {
            val f = fish[index]
            val radialAngle = TWO_PI * index / activeFishCount.coerceAtLeast(1) +
                    f.seed * 0.31f + random.nextFloat() * 0.34f - 0.17f
            val (angle, radius) = when (interactionVariant) {
                0 -> radialAngle to (0.44f + random.nextFloat() * 0.42f)
                1 -> {
                    // The two schools peel away in opposite streams with individual spread.
                    val streamHeading = if (f.schoolId == 0) 0.18f else PI.toFloat() + 0.18f
                    (streamHeading + (index % 5 - 2) * 0.16f +
                            random.nextFloat() * 0.16f - 0.08f) to
                            (0.50f + (index % 3) * 0.10f)
                }
                else -> {
                    // A widening spiral gives every body a separate lane before normal social
                    // behaviour resumes.
                    (radialAngle + index * 0.28f) to
                            (0.34f + index * 0.052f)
                }
            }
            scatterTargetX[index] = (attractionX + cos(angle) * radius)
                .coerceIn(-0.88f, 0.88f)
            val releasedTargetY = (attractionY + sin(angle) * radius)
                .coerceIn(-0.94f, 0.84f)
            scatterTargetY[index] = if (f.routeCenterY < -0.60f) {
                min(releasedTargetY, -0.66f)
            } else {
                releasedTargetY
            }
            f.behaviorStep += 1 + (index + interactionVariant) % 3
            // Guarantee a mixture of independent routes, leader following and pair play after
            // every release; rotating the offset makes the same fish change roles next time.
            f.behavior = behaviorForStep(index + interactionVariant)
            f.behaviorUntil = scatterUntil + 4f + random.nextFloat() * 6f
        }
    }

    private fun updateTouchTarget(x: Float, y: Float, now: Float) {
        attractionX = x * 2f - 1f
        attractionY = 1f - y * 2f
        // This timeout is only a safety net for a lost ACTION_UP. While held, updateFish keeps
        // attraction active and every move refreshes the target without creating extra ripples.
        attractionUntil = now + TOUCH_EVENT_TIMEOUT_SECONDS
    }

    fun draw(frameNanos: Long) {
        val time = elapsedSeconds(frameNanos)
        val dt = ((frameNanos - previousNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
        previousNanos = frameNanos
        updateFish(time, dt)
        updateSparkle(time)
        drawWater(time)
        drawFish(time)
        reportPerformance(frameNanos)
    }

    fun destroy() {
        if (waterVbo != 0) GLES30.glDeleteBuffers(1, intArrayOf(waterVbo), 0)
        if (fishVbo != 0) GLES30.glDeleteBuffers(1, intArrayOf(fishVbo), 0)
        if (waterVao != 0) GLES30.glDeleteVertexArrays(1, intArrayOf(waterVao), 0)
        if (fishVao != 0) GLES30.glDeleteVertexArrays(1, intArrayOf(fishVao), 0)
        if (waterProgram != 0) GLES30.glDeleteProgram(waterProgram)
        if (fishProgram != 0) GLES30.glDeleteProgram(fishProgram)
    }

    private fun createFish(index: Int): Fish {
        val initialHeading = if (index % 2 == 0) 0f else PI.toFloat()
        val palette = FISH_PALETTES[index % FISH_PALETTES.size]
        val speedPersonality = FISH_SPEED_PERSONALITIES[index % FISH_SPEED_PERSONALITIES.size]
        val cruiseSpeed = 0.175f * speedPersonality
        val initialY = if (index % 3 == 0) {
            // Keep several fish visibly roaming under the bottom keyboard rows.
            -0.92f + random.nextFloat() * 0.62f
        } else {
            random.nextFloat() * 1.78f - 0.92f
        }
        val schoolId = index % 2
        val lowerRoute = index % 3 == 0
        val initialBehavior = behaviorForStep(index)
        return Fish(
            x = random.nextFloat() * 1.7f - 0.85f,
            y = initialY,
            heading = initialHeading + random.nextFloat() * 0.34f - 0.17f,
            forwardSpeed = cruiseSpeed * (0.72f + random.nextFloat() * 0.10f),
            angularSpeed = 0f,
            swimPhase = random.nextFloat() * (2f * PI.toFloat()),
            finPhase = random.nextFloat() * (2f * PI.toFloat()),
            tailDrive = 0.27f + speedPersonality * 0.12f + random.nextFloat() * 0.055f,
            leftFinDrive = 0.20f + random.nextFloat() * 0.08f,
            rightFinDrive = 0.20f + random.nextFloat() * 0.08f,
            turnDrive = 0f,
            fastTurnLatched = false,
            completedTailStrokes = 0,
            brakeDrive = 0f,
            bank = 0f,
            depth = random.nextFloat(),
            scale = FISH_SCALES[index % FISH_SCALES.size],
            cruiseSpeed = cruiseSpeed,
            tailTempo = 0.82f + speedPersonality * 0.24f,
            thrustScale = 0.73f + speedPersonality * 0.31f,
            maxForwardSpeed = 0.72f + speedPersonality * 0.38f,
            phase = random.nextFloat() * (2f * PI.toFloat()),
            seed = random.nextFloat() * 12f,
            baseColor = palette.base,
            patchColor = palette.patch,
            accentColor = palette.accent,
            pattern = (index % 4).toFloat(),
            schoolId = schoolId,
            routeCenterX = (if (schoolId == 0) -0.10f else 0.10f) +
                    random.nextFloat() * 0.16f - 0.08f,
            routeCenterY = if (lowerRoute) {
                -0.75f + random.nextFloat() * 0.10f
            } else {
                random.nextFloat() * 0.22f - 0.11f
            },
            routeRadiusX = 0.48f + random.nextFloat() * 0.22f,
            routeRadiusY = if (lowerRoute) {
                0.12f + random.nextFloat() * 0.10f
            } else {
                0.38f + random.nextFloat() * 0.22f
            },
            routeDirection = if ((index / 2) % 2 == 0) 1f else -1f,
            routeShape = random.nextFloat() * TWO_PI,
            routeProgress = random.nextFloat() * TWO_PI,
            behavior = initialBehavior,
            behaviorStep = index,
            behaviorUntil = 5f + random.nextFloat() * 6f
        )
    }

    private fun updateFish(time: Float, dt: Float) {
        val feeding = touchHeld || time < attractionUntil
        val scattering = !feeding && time < scatterUntil
        val rapidFeedReaction = feeding && time - attractionStartedAt < FEED_REACTION_SECONDS
        val weekdayIntroForming = weekdayIntroPhase == WeekdayIntroPhase.FORM ||
                weekdayIntroPhase == WeekdayIntroPhase.HOLD
        val weekdayIntroDeparting = weekdayIntroPhase == WeekdayIntroPhase.DEPART
        val weekdayIntroActive = weekdayIntroForming || weekdayIntroDeparting
        var weekdayDigitMaxDistance = 0f
        for (index in 0 until activeFishCount) {
            val f = fish[index]
            if (!feeding && !weekdayIntroActive && time >= f.behaviorUntil) {
                f.behaviorStep++
                f.behavior = behaviorForStep(f.behaviorStep)
                f.behaviorUntil = time + 6.5f + random.nextFloat() * 6.5f
            }

            // A route advances only with distance actually swum; it may request a direction,
            // but can never drag the fish by changing its world position.
            val meanRouteRadius = (f.routeRadiusX + f.routeRadiusY) * 0.5f
            f.routeProgress = wrapAngle(
                f.routeProgress + f.routeDirection * f.forwardSpeed * dt /
                        meanRouteRadius.coerceAtLeast(0.2f) * 0.72f
            )
            val lookAheadAngle = f.routeProgress + f.routeDirection * 0.48f
            var targetX = f.routeCenterX +
                    cos(lookAheadAngle) * f.routeRadiusX +
                    cos(lookAheadAngle * 2f + f.routeShape) * 0.065f
            var targetY = f.routeCenterY +
                    sin(lookAheadAngle) * f.routeRadiusY +
                    sin(lookAheadAngle * 3f - f.routeShape) * 0.045f
            var desiredSpeed = f.cruiseSpeed

            if (feeding) {
                // Each touch rotates the cast. Sprinters claim a tight slot, clockwise and
                // counter-clockwise fish circle the grass, and cautious fish first hold an outer
                // lane before joining. All roles still target the same touch neighbourhood and
                // can only travel through the tail/pectoral force integration below.
                val feedAge = (time - attractionStartedAt).coerceAtLeast(0f)
                val feedRole = (index + interactionVariant) % 4
                var offsetAngle = index * FEED_GOLDEN_ANGLE + f.seed * 0.37f
                val offsetRadius: Float
                val speedMultiplier: Float
                when (feedRole) {
                    0 -> {
                        offsetRadius = 0.105f + (index % 3) * 0.022f
                        speedMultiplier = 5.35f
                    }
                    1 -> {
                        offsetAngle += feedAge * (1.08f + (f.seed % 1f) * 0.28f)
                        offsetRadius = 0.155f + (index % 3) * 0.030f
                        speedMultiplier = 4.65f
                    }
                    2 -> {
                        offsetAngle -= feedAge * (0.88f + (f.seed % 1f) * 0.24f)
                        offsetRadius = 0.175f + (index % 2) * 0.035f
                        speedMultiplier = 4.20f
                    }
                    else -> {
                        val approach = smoothStep01(feedAge / 0.90f)
                        offsetRadius = 0.285f - approach * 0.115f +
                                (f.seed % 1f) * 0.025f
                        speedMultiplier = 3.75f
                    }
                }
                var offsetX = cos(offsetAngle) * offsetRadius
                var offsetY = sin(offsetAngle) * offsetRadius * 0.76f
                if (attractionX < -0.70f) offsetX = abs(offsetX)
                if (attractionX > 0.70f) offsetX = -abs(offsetX)
                if (attractionY < -0.68f) offsetY = abs(offsetY)
                if (attractionY > 0.66f) offsetY = -abs(offsetY)
                targetX = attractionX + offsetX
                targetY = attractionY + offsetY
                desiredSpeed = (f.cruiseSpeed *
                        (speedMultiplier + (f.seed % 1f) * 0.42f))
                    .coerceAtMost(f.maxForwardSpeed)
            } else if (weekdayIntroForming) {
                val digitTarget = weekdayDigitTargets[weekdayDigitTargetForFish[index]]
                targetX = digitTarget[0]
                targetY = digitTarget[1]
                desiredSpeed = if (weekdayIntroPhase == WeekdayIntroPhase.FORM) {
                    (f.cruiseSpeed * (3.05f + (f.seed % 1f) * 0.34f))
                        .coerceAtMost(f.maxForwardSpeed * 0.76f)
                } else {
                    f.cruiseSpeed * 0.42f
                }
            } else if (scattering) {
                targetX = scatterTargetX[index]
                targetY = scatterTargetY[index]
                desiredSpeed = (f.cruiseSpeed * (1.85f + (f.seed % 1f) * 0.42f))
                    .coerceAtMost(f.maxForwardSpeed * 0.68f)
            } else if (weekdayIntroDeparting) {
                // Route look-ahead already supplies a personal outward path. Keep it deliberately
                // slow so the digit dissolves as swimming fish instead of exploding apart.
                desiredSpeed = f.cruiseSpeed * (0.64f + (f.seed % 1f) * 0.16f)
            } else {
                when (f.behavior) {
                    FishBehavior.ROUTE -> Unit
                    FishBehavior.FOLLOW -> {
                        val leader = fish[schoolMateIndex(index, ahead = false)]
                        val spacing = 0.15f + f.scale * 0.65f
                        val sideOffset = sin(time * 0.54f + f.phase) * 0.035f
                        targetX = leader.x - cos(leader.heading) * spacing -
                                sin(leader.heading) * sideOffset
                        targetY = leader.y - sin(leader.heading) * spacing +
                                cos(leader.heading) * sideOffset
                        desiredSpeed = (leader.forwardSpeed * 0.82f + f.cruiseSpeed * 0.42f)
                            .coerceIn(f.cruiseSpeed * 0.74f, f.maxForwardSpeed * 0.58f)
                    }
                    FishBehavior.PLAY -> {
                        val partner = fish[schoolMateIndex(index, ahead = true)]
                        val fromPartnerX = f.x - partner.x
                        val fromPartnerY = f.y - partner.y
                        val partnerDistance = sqrt(
                            fromPartnerX * fromPartnerX + fromPartnerY * fromPartnerY
                        ).coerceAtLeast(0.03f)
                        val orbitSide = if (index % 2 == 0) 1f else -1f
                        val safePairSpacing = collisionRadius(f) + collisionRadius(partner) + 0.025f
                        // A PLAY interval is not one endless orbit. The pair alternates between
                        // circling, a tail-chase, and side-by-side weaving. All three produce only
                        // navigation targets; fins and tail strokes still create the actual pose.
                        when (((time / PLAY_STYLE_SECONDS).toInt() + index / 2) % 3) {
                            0 -> {
                                val orbitRadius = max(
                                    safePairSpacing,
                                    0.14f + (f.seed % 1f) * 0.055f
                                )
                                targetX = partner.x - fromPartnerY / partnerDistance *
                                        orbitRadius * orbitSide + cos(partner.heading) * 0.068f
                                targetY = partner.y + fromPartnerX / partnerDistance *
                                        orbitRadius * orbitSide + sin(partner.heading) * 0.068f
                                desiredSpeed = f.cruiseSpeed *
                                        (1.22f + (f.seed % 1f) * 0.30f)
                            }
                            1 -> {
                                val chaseSpacing = safePairSpacing + 0.035f
                                val tease = sin(time * 1.36f + f.phase) * 0.038f
                                targetX = partner.x - cos(partner.heading) * chaseSpacing -
                                        sin(partner.heading) * tease
                                targetY = partner.y - sin(partner.heading) * chaseSpacing +
                                        cos(partner.heading) * tease
                                desiredSpeed = partner.forwardSpeed * 0.74f +
                                        f.cruiseSpeed * (0.82f + (f.seed % 1f) * 0.24f)
                            }
                            else -> {
                                val weaveSide = orbitSide * (safePairSpacing + 0.018f) *
                                        (0.82f + sin(time * 1.18f + f.phase) * 0.18f)
                                val lead = 0.055f + cos(time * 0.82f + f.phase) * 0.030f
                                targetX = partner.x + cos(partner.heading) * lead -
                                        sin(partner.heading) * weaveSide
                                targetY = partner.y + sin(partner.heading) * lead +
                                        cos(partner.heading) * weaveSide
                                desiredSpeed = partner.forwardSpeed * 0.62f +
                                        f.cruiseSpeed * 0.72f
                            }
                        }
                        desiredSpeed = max(f.cruiseSpeed * 0.90f, desiredSpeed)
                            .coerceAtMost(f.maxForwardSpeed * 0.58f)
                    }
                }
            }

            // Four fish keep a genuine lower-pond territory even while following or playing.
            // They remain social but do not all migrate above the operation-water strip.
            if (!feeding && !scattering && !weekdayIntroActive && f.routeCenterY < -0.60f) {
                targetY = min(targetY, -0.67f)
            }

            targetX = targetX.coerceIn(-0.90f, 0.90f)
            targetY = targetY.coerceIn(-0.95f, 0.86f)
            val targetDx = targetX - f.x
            val targetDy = targetY - f.y
            val targetDistance = sqrt(targetDx * targetDx + targetDy * targetDy)
                .coerceAtLeast(0.001f)
            if (weekdayIntroForming) {
                weekdayDigitMaxDistance = max(weekdayDigitMaxDistance, targetDistance)
            }
            var intentX = targetDx / targetDistance
            var intentY = targetDy / targetDistance

            var separationX = 0f
            var separationY = 0f
            var alignmentX = 0f
            var alignmentY = 0f
            var cohesionX = 0f
            var cohesionY = 0f
            var schoolNeighbours = 0
            var crowdingBrake = 0f
            val forwardX = cos(f.heading)
            val forwardY = sin(f.heading)
            val selfCollisionRadius = collisionRadius(f)
            for (otherIndex in 0 until activeFishCount) {
                if (otherIndex == index) continue
                val other = fish[otherIndex]
                val awayX = f.x - other.x
                val awayY = f.y - other.y
                val distance2 = awayX * awayX + awayY * awayY
                if (distance2 <= 0.0001f) continue
                val distanceToOther = sqrt(distance2)
                val collisionDistance = selfCollisionRadius + collisionRadius(other)
                if (distanceToOther < collisionDistance) {
                    val separationWeight = (collisionDistance - distanceToOther) /
                            collisionDistance.coerceAtLeast(0.001f)
                    separationX += awayX / distanceToOther * separationWeight
                    separationY += awayY / distanceToOther * separationWeight
                    val aheadDot = (-awayX * forwardX - awayY * forwardY) / distanceToOther
                    if (aheadDot > 0.58f) {
                        crowdingBrake = max(crowdingBrake, separationWeight * aheadDot)
                    }
                }
                if (other.schoolId == f.schoolId && distanceToOther < 0.46f) {
                    val viewDot = (-awayX * forwardX - awayY * forwardY) / distanceToOther
                    if (viewDot > -0.52f) {
                        alignmentX += cos(other.heading)
                        alignmentY += sin(other.heading)
                        cohesionX += other.x
                        cohesionY += other.y
                        schoolNeighbours++
                    }
                }
            }
            val separationGain = when {
                feeding -> 0.52f
                weekdayIntroForming -> 0.86f
                scattering -> 1.05f
                else -> 1.55f
            }
            intentX += separationX * separationGain
            intentY += separationY * separationGain
            if (!feeding && !scattering && !weekdayIntroActive && schoolNeighbours > 0) {
                val inverseCount = 1f / schoolNeighbours
                intentX += (cohesionX * inverseCount - f.x) * 0.42f +
                        alignmentX * inverseCount * 0.22f
                intentY += (cohesionY * inverseCount - f.y) * 0.42f +
                        alignmentY * inverseCount * 0.22f
            }

            // Look ahead and request a turn before a wall. No wall may rotate or reverse a fish.
            val lookAheadDistance = 0.20f + f.forwardSpeed * 0.58f
            val projectedX = f.x + forwardX * lookAheadDistance
            val projectedY = f.y + forwardY * lookAheadDistance
            var edgeBrake = 0f
            if (projectedX < -0.86f) {
                val penetration = -0.86f - projectedX
                intentX += 0.55f + penetration * 8f
                edgeBrake = max(edgeBrake, penetration * 3.8f)
            } else if (projectedX > 0.86f) {
                val penetration = projectedX - 0.86f
                intentX -= 0.55f + penetration * 8f
                edgeBrake = max(edgeBrake, penetration * 3.8f)
            }
            if (projectedY < -0.95f) {
                val penetration = -0.95f - projectedY
                intentY += 0.55f + penetration * 8f
                edgeBrake = max(edgeBrake, penetration * 3.8f)
            } else if (projectedY > 0.86f) {
                val penetration = projectedY - 0.86f
                intentY -= 0.55f + penetration * 8f
                edgeBrake = max(edgeBrake, penetration * 3.8f)
            }
            if (feeding) {
                val scramble = sin(time * (7.4f + (f.seed % 1f) * 1.8f) + f.phase)
                intentX += -targetDy / targetDistance * scramble * 0.045f
                intentY += targetDx / targetDistance * scramble * 0.045f
            }

            val desiredHeading = atan2(intentY, intentX)
            val headingError = wrapAngle(desiredHeading - f.heading)
            val turnDemand = (headingError / 1.15f).coerceIn(-1f, 1f)
            val forwardAlignment = ((cos(headingError) + 1f) * 0.5f).coerceIn(0f, 1f)
            val arrivalRadius = when {
                feeding -> 0.20f + f.forwardSpeed * 0.34f
                weekdayIntroForming -> 0.12f
                else -> 0.14f
            }
            val arrivalBrake = if (targetDistance < arrivalRadius) {
                (1f - targetDistance / arrivalRadius).coerceIn(0f, 1f)
            } else {
                0f
            }
            val feedingTurnBrake = if (feeding) {
                smoothStep01((abs(headingError) - 0.52f) / 1.18f) * 0.94f
            } else {
                0f
            }
            val brakeDemand = max(
                max(max(crowdingBrake, edgeBrake), arrivalBrake),
                feedingTurnBrake
            )
                .coerceIn(0f, 1f)
            desiredSpeed *= when {
                feeding -> (targetDistance / arrivalRadius).coerceIn(0.08f, 1f)
                weekdayIntroForming ->
                    (targetDistance / arrivalRadius).coerceIn(0.025f, 1f)
                else -> 1f - arrivalBrake * 0.55f
            }
            if (feeding) {
                // Turning fish show energetic strokes without sliding rapidly sideways or
                // backwards. Large displacement is unlocked only after the body points toward
                // the food, so visible tail frequency and forward travel stay coupled.
                desiredSpeed *= 0.24f + forwardAlignment * 0.76f
            }

            // Navigation stops here. It may set muscle targets but cannot alter the fish pose.
            val speedError = desiredSpeed - f.forwardSpeed
            val feedingTailBoost = if (feeding) {
                (0.08f + smoothStep01(forwardAlignment) * 0.68f) *
                        (1f - arrivalBrake * 0.78f) +
                        if (rapidFeedReaction) 0.20f else 0f
            } else {
                0f
            }
            val tailDriveTarget = (
                    0.16f + max(speedError, 0f) * 1.72f +
                            abs(turnDemand) * 0.12f +
                            feedingTailBoost - brakeDemand * 0.20f
                    ).coerceIn(0.12f, 1f)
            val finBaseTarget = (
                    0.17f + max(speedError, 0f) * 0.42f +
                            abs(turnDemand) * 0.14f + brakeDemand * 0.60f +
                            (if (feeding) 0.10f else 0f)
                    ).coerceIn(0.12f, 0.86f)
            val leftFinTarget = (finBaseTarget + max(turnDemand, 0f) * 0.76f)
                .coerceIn(0.12f, 1f)
            val rightFinTarget = (finBaseTarget + max(-turnDemand, 0f) * 0.76f)
                .coerceIn(0.12f, 1f)
            val muscleResponse = (dt * when {
                rapidFeedReaction -> 11.5f
                feeding -> 7.2f
                weekdayIntroForming && weekdayIntroPhase == WeekdayIntroPhase.FORM -> 6.2f
                else -> 4.5f
            }).coerceIn(0f, 1f)
            f.tailDrive += (tailDriveTarget - f.tailDrive) * muscleResponse
            f.leftFinDrive += (leftFinTarget - f.leftFinDrive) * muscleResponse
            f.rightFinDrive += (rightFinTarget - f.rightFinDrive) * muscleResponse
            f.turnDrive += (turnDemand - f.turnDrive) *
                    (dt * when {
                        feeding -> 32f
                        weekdayIntroForming -> 13f
                        else -> 9f
                    }).coerceIn(0f, 1f)
            f.brakeDrive += (brakeDemand - f.brakeDrive) * muscleResponse

            val reactionTempo = if (rapidFeedReaction) 1.22f else 1f
            val tailBeatHz = tailBeatHzForDrive(f.tailDrive, f.tailTempo * reactionTempo)
            val finBeatHz = finBeatHzForDrive(
                max(f.leftFinDrive, f.rightFinDrive),
                f.tailTempo * reactionTempo
            )
            // Sample the articulated middle/outer caudal membrane. The shader delays this area
            // by the same local-X phase, so a CPU thrust pulse coincides with the tail that the
            // user can actually see crossing the centreline.
            val previousTailSweep = sin(f.swimPhase + TAIL_PROPULSION_PHASE)
            f.swimPhase = wrapPhase(f.swimPhase + TWO_PI * tailBeatHz * dt)
            f.finPhase = wrapPhase(f.finPhase + TWO_PI * finBeatHz * dt)
            val currentTailSweep = sin(f.swimPhase + TAIL_PROPULSION_PHASE)
            // A caudal stroke completes when the visible tail crosses the body centreline. Only
            // that completed power stroke may add forward momentum. Between strokes the fish
            // merely coasts against water drag, so a navigation target can never accelerate the
            // model directly and the speed visibly pulses in step with the tail.
            val completedPowerStroke = previousTailSweep * currentTailSweep <= 0f &&
                    abs(previousTailSweep - currentTailSweep) > 0.015f
            val tailAmplitude = 0.130f + (0.400f - 0.130f) * f.tailDrive
            val tailStrokeImpulse = if (completedPowerStroke) {
                f.completedTailStrokes++
                tailAmplitude * (0.115f + tailBeatHz * 0.020f) * f.thrustScale
            } else {
                0f
            }
            val finExtension = 0.34f + abs(sin(f.finPhase)) * 0.66f

            // The pectoral fins steer and brake; they do not secretly translate the body. A
            // fish facing away from its target first sheds speed and turns, then its tail pulses
            // propel it head-first along the new heading. forwardSpeed remains non-negative.
            val redirectedTailImpulse = tailStrokeImpulse *
                    (1f - f.brakeDrive * 0.96f).coerceIn(0.02f, 1f)
            f.forwardSpeed = (f.forwardSpeed + redirectedTailImpulse)
                .coerceIn(0f, f.maxForwardSpeed)
            val waterDrag = f.forwardSpeed * 1.18f +
                    f.forwardSpeed * f.forwardSpeed * 0.82f
            val pectoralBrake = f.brakeDrive * finExtension *
                    (0.34f + f.forwardSpeed * 1.20f)
            f.forwardSpeed = (f.forwardSpeed - (waterDrag + pectoralBrake) * dt)
                .coerceIn(0f, f.maxForwardSpeed)

            // Tail sweep and differential pectoral-fin drag create yaw torque. Heading is
            // integrated from angular velocity; routes, neighbours and walls cannot rotate it.
            val tailSteering = f.tailDrive *
                    (0.18f + tailBeatHz * 0.11f + tailStrokeImpulse * 3.2f)
            val pairedFinSteering = (f.leftFinDrive + f.rightFinDrive) *
                    finExtension * 0.55f
            val differentialFinTorque = (f.leftFinDrive - f.rightFinDrive) *
                    finExtension * 1.65f
            val feedingTurnGain = when {
                rapidFeedReaction -> 3.35f
                feeding -> 2.55f
                else -> 1f
            }
            val yawTorque = turnDemand *
                    (tailSteering * 2.35f + pairedFinSteering) * feedingTurnGain +
                    differentialFinTorque
            // Do not wait as long as half an idle beat before a newly requested C-turn becomes
            // visible. The one-shot preparatory curl rotates the body as the tail coils; the
            // latch prevents this onset impulse from becoming direct per-frame steering.
            val initialTurnAmount = smoothStep01((abs(turnDemand) - 0.52f) / 0.38f)
            if (feeding && initialTurnAmount > 0f && !f.fastTurnLatched) {
                f.fastTurnLatched = true
                f.turnDrive = turnDemand
                f.forwardSpeed *= 0.42f
                f.angularSpeed += turnDemand * initialTurnAmount *
                        (5.80f + f.tailDrive * 3.40f) * f.tailTempo
            } else if (abs(turnDemand) < 0.24f || !feeding) {
                f.fastTurnLatched = false
            }
            // A tight C-turn is released by the first completed caudal power stroke. This is an
            // angular impulse from the same visible tail event, not a direct heading assignment.
            val fastTurnAmount = smoothStep01((abs(turnDemand) - 0.38f) / 0.57f)
            if (completedPowerStroke && fastTurnAmount > 0f) {
                f.angularSpeed += turnDemand * fastTurnAmount * f.tailDrive *
                        when {
                            rapidFeedReaction -> 4.80f
                            feeding -> 3.30f
                            else -> 0.72f
                        }
            }
            val turnStop = 1f - smoothStep01(abs(turnDemand) / 0.34f)
            val angularAcceleration = yawTorque -
                    f.angularSpeed *
                    (2.15f + f.brakeDrive * 0.65f + turnStop * 6.8f)
            f.angularSpeed = (f.angularSpeed + angularAcceleration * dt)
                .coerceIn(
                    when {
                        rapidFeedReaction -> -14.50f
                        feeding -> -11.20f
                        else -> -3.20f
                    },
                    when {
                        rapidFeedReaction -> 14.50f
                        feeding -> 11.20f
                        else -> 3.20f
                    }
                )
            f.heading = wrapAngle(f.heading + f.angularSpeed * dt)
            val targetBank = (f.angularSpeed / 2.35f).coerceIn(-1f, 1f) * 0.25f
            f.bank += (targetBank - f.bank) * (dt * 4f).coerceIn(0f, 1f)

            // Semi-implicit, forward-only integration from the fin-generated forces above.
            val distance = f.forwardSpeed * dt
            f.x += cos(f.heading) * distance
            f.y += sin(f.heading) * distance
            if (f.x < -1.07f || f.x > 1.07f) {
                f.x = f.x.coerceIn(-1.07f, 1.07f)
                f.forwardSpeed *= 0.12f
            }
            if (f.y < -1.02f || f.y > 0.94f) {
                f.y = f.y.coerceIn(-1.02f, 0.94f)
                f.forwardSpeed *= 0.12f
            }
        }
        when (weekdayIntroPhase) {
            WeekdayIntroPhase.FORM -> {
                val settled = time >= INTRO_MIN_FORM_SECONDS &&
                        weekdayDigitMaxDistance <= INTRO_SETTLED_DISTANCE
                if (settled || time >= INTRO_FORM_TIMEOUT_SECONDS) {
                    weekdayIntroPhase = WeekdayIntroPhase.HOLD
                    weekdayIntroPhaseStartedAt = time
                    Log.i(
                        TAG,
                        "weekdayIntro digit=$weekdayDigit phase=HOLD " +
                                "maxDistance=${"%.3f".format(weekdayDigitMaxDistance)}"
                    )
                }
            }
            WeekdayIntroPhase.HOLD -> {
                if (time - weekdayIntroPhaseStartedAt >= INTRO_HOLD_SECONDS) {
                    weekdayIntroPhase = WeekdayIntroPhase.DEPART
                    weekdayIntroPhaseStartedAt = time
                    Log.i(TAG, "weekdayIntro digit=$weekdayDigit phase=DEPART")
                }
            }
            WeekdayIntroPhase.DEPART -> {
                if (time - weekdayIntroPhaseStartedAt >= INTRO_DEPART_SECONDS) {
                    weekdayIntroPhase = WeekdayIntroPhase.DONE
                    nextSparkleAt = time + SPARKLE_PAUSE_MIN_SECONDS
                    Log.i(TAG, "weekdayIntro digit=$weekdayDigit phase=DONE")
                }
            }
            WeekdayIntroPhase.DONE -> Unit
        }
        if (feeding && time >= nextFeedReportAt) {
            var distanceSum = 0f
            var distanceMax = 0f
            var speedSum = 0f
            var tailDriveSum = 0f
            var finDriveSum = 0f
            var tailBeatHzSum = 0f
            var finBeatHzSum = 0f
            var completedTailStrokesSum = 0
            var speedMin = Float.MAX_VALUE
            var speedMax = 0f
            for (index in 0 until activeFishCount) {
                val f = fish[index]
                val dx = attractionX - f.x
                val dy = attractionY - f.y
                val distance = sqrt(dx * dx + dy * dy)
                distanceSum += distance
                distanceMax = max(distanceMax, distance)
                speedSum += f.forwardSpeed
                speedMin = min(speedMin, f.forwardSpeed)
                speedMax = max(speedMax, f.forwardSpeed)
                tailDriveSum += f.tailDrive
                finDriveSum += (f.leftFinDrive + f.rightFinDrive) * 0.5f
                val reportReactionTempo = if (rapidFeedReaction) 1.22f else 1f
                tailBeatHzSum += tailBeatHzForDrive(
                    f.tailDrive,
                    f.tailTempo * reportReactionTempo
                )
                finBeatHzSum += finBeatHzForDrive(
                    max(f.leftFinDrive, f.rightFinDrive),
                    f.tailTempo * reportReactionTempo
                )
                completedTailStrokesSum += f.completedTailStrokes
            }
            val divisor = activeFishCount.coerceAtLeast(1).toFloat()
            Log.i(
                TAG,
                "feed age=${"%.2f".format(time - attractionStartedAt)} " +
                        "avgDistance=${"%.3f".format(distanceSum / divisor)} " +
                        "maxDistance=${"%.3f".format(distanceMax)} " +
                        "avgSpeed=${"%.3f".format(speedSum / divisor)} " +
                        "speedRange=${"%.3f".format(speedMin)}..${"%.3f".format(speedMax)} " +
                        "tail=${"%.2f".format(tailDriveSum / divisor)} " +
                        "fin=${"%.2f".format(finDriveSum / divisor)} " +
                        "tailHz=${"%.2f".format(tailBeatHzSum / divisor)} " +
                        "finHz=${"%.2f".format(finBeatHzSum / divisor)} " +
                        "avgStrokes=${"%.1f".format(completedTailStrokesSum / divisor)}"
            )
            nextFeedReportAt = time + FEED_REPORT_SECONDS
        }
    }

    private fun updateSparkle(time: Float) {
        if (touchHeld || weekdayIntroPhase != WeekdayIntroPhase.DONE) return
        if (sparkleFishIndex >= activeFishCount) {
            previousSparkleFishIndex = sparkleFishIndex
            sparkleFishIndex = -1
            sparkleUntil = -1f
            nextSparkleAt = time + 0.7f
        }
        if (sparkleFishIndex >= 0) {
            if (time >= sparkleUntil) {
                previousSparkleFishIndex = sparkleFishIndex
                sparkleFishIndex = -1
                sparkleUntil = -1f
                nextSparkleAt = time + SPARKLE_PAUSE_MIN_SECONDS +
                        random.nextFloat() * SPARKLE_PAUSE_RANGE_SECONDS
            }
            return
        }
        if (time < nextSparkleAt || activeFishCount <= 0) return
        var selected = random.nextInt(activeFishCount)
        if (activeFishCount > 1 && selected == previousSparkleFishIndex) {
            selected = (selected + 1 + random.nextInt(activeFishCount - 1)) % activeFishCount
        }
        sparkleFishIndex = selected
        sparkleStartedAt = time
        sparkleUntil = time + SPARKLE_DURATION_MIN_SECONDS +
                random.nextFloat() * SPARKLE_DURATION_RANGE_SECONDS
        Log.i(
            TAG,
            "sparkle fish=$sparkleFishIndex duration=${"%.2f".format(sparkleUntil - time)}"
        )
    }

    private fun assignWeekdayDigitTargets() {
        val assigned = BooleanArray(weekdayDigitTargets.size)
        for (index in fish.indices) {
            val f = fish[index]
            var nearest = -1
            var nearestDistance2 = Float.MAX_VALUE
            for (targetIndex in weekdayDigitTargets.indices) {
                if (assigned[targetIndex]) continue
                val target = weekdayDigitTargets[targetIndex]
                val dx = target[0] - f.x
                val dy = target[1] - f.y
                val distance2 = dx * dx + dy * dy
                if (distance2 < nearestDistance2) {
                    nearest = targetIndex
                    nearestDistance2 = distance2
                }
            }
            weekdayDigitTargetForFish[index] = nearest.coerceAtLeast(0)
            assigned[nearest.coerceAtLeast(0)] = true
        }
    }

    private fun currentWeekdayDigit(): Int {
        val systemDay = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        // Calendar uses Sunday=1. Product wording uses Monday=1 ... Sunday=7.
        return ((systemDay + 5) % 7) + 1
    }

    private fun createWeekdayDigitTargets(digit: Int): Array<FloatArray> {
        // Seven-segment strokes are inset at their ends. Sampling segment interiors prevents two
        // fish from sharing a corner while keeping the number readable through translucent keys.
        val top = DigitStroke(-0.34f, 0.72f, 0.34f, 0.72f)
        val upperRight = DigitStroke(0.34f, 0.65f, 0.34f, 0.07f)
        val lowerRight = DigitStroke(0.34f, -0.07f, 0.34f, -0.65f)
        val bottom = DigitStroke(0.34f, -0.72f, -0.34f, -0.72f)
        val lowerLeft = DigitStroke(-0.34f, -0.65f, -0.34f, -0.07f)
        val upperLeft = DigitStroke(-0.34f, 0.07f, -0.34f, 0.65f)
        val middle = DigitStroke(-0.34f, 0f, 0.34f, 0f)
        val strokes = when (digit) {
            1 -> listOf(upperRight, lowerRight)
            2 -> listOf(top, upperRight, middle, lowerLeft, bottom)
            3 -> listOf(top, upperRight, middle, lowerRight, bottom)
            4 -> listOf(upperLeft, middle, upperRight, lowerRight)
            5 -> listOf(top, upperLeft, middle, lowerRight, bottom)
            6 -> listOf(top, upperLeft, middle, lowerLeft, lowerRight, bottom)
            else -> listOf(top, upperRight, lowerRight)
        }
        val targets = ArrayList<FloatArray>(MAX_FISH)
        val baseCount = MAX_FISH / strokes.size
        val remainder = MAX_FISH % strokes.size
        strokes.forEachIndexed { strokeIndex, stroke ->
            val count = baseCount + if (strokeIndex < remainder) 1 else 0
            val dx = stroke.endX - stroke.startX
            val dy = stroke.endY - stroke.startY
            val length = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
            repeat(count) { sampleIndex ->
                val t = (sampleIndex + 0.5f) / count
                // Alternating thickness gives the largest fish enough room on the dense "1"
                // while preserving the seven-segment silhouette for every weekday.
                val thickness = if (sampleIndex % 2 == 0) -0.032f else 0.032f
                targets += floatArrayOf(
                    stroke.startX + dx * t - dy / length * thickness,
                    stroke.startY + dy * t + dx / length * thickness
                )
            }
        }
        return Array(MAX_FISH) { targets[it] }
    }

    private fun behaviorForStep(step: Int): FishBehavior = when ((step % 3 + 3) % 3) {
        0 -> FishBehavior.ROUTE
        1 -> FishBehavior.FOLLOW
        else -> FishBehavior.PLAY
    }

    private fun collisionRadius(f: Fish): Float {
        val renderedScale = f.scale * (0.75f + f.depth * 0.35f)
        // The torso is shorter than the veil, but a small tail allowance prevents two large fish
        // from visually clipping while turning. This keeps small fish from reserving the same
        // oversized fixed circle as the largest fish.
        return 0.025f + renderedScale * 0.88f
    }

    private fun tailBeatHzForDrive(drive: Float, tempo: Float): Float {
        val burst = smoothStep01((drive - 0.20f) / 0.80f)
        return (1.60f + burst * 3.80f) * tempo
    }

    private fun finBeatHzForDrive(drive: Float, tempo: Float): Float {
        val burst = smoothStep01((drive - 0.20f) / 0.80f)
        return (0.70f + burst * 1.55f) * (0.88f + tempo * 0.12f)
    }

    private fun smoothStep01(value: Float): Float {
        val x = value.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    private fun schoolMateIndex(index: Int, ahead: Boolean): Int {
        if (activeFishCount <= 1) return index
        val direction = if (ahead) 1 else -1
        for (offset in 1 until activeFishCount) {
            val candidate = (index + direction * offset + activeFishCount) % activeFishCount
            if (fish[candidate].schoolId == fish[index].schoolId) return candidate
        }
        return index
    }

    private fun wrapAngle(angle: Float): Float = atan2(sin(angle), cos(angle))

    private fun wrapPhase(phase: Float): Float {
        val wrapped = phase % TWO_PI
        return if (wrapped < 0f) wrapped + TWO_PI else wrapped
    }

    private fun drawWater(time: Float) {
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glUseProgram(waterProgram)
        GLES30.glUniform1f(waterTimeLocation, time)
        GLES30.glUniform2f(
            waterResolutionLocation,
            width.toFloat(),
            height.toFloat()
        )
        ripples.forEachIndexed { index, ripple ->
            rippleUniforms[index * 4] = ripple.x
            rippleUniforms[index * 4 + 1] = ripple.y
            rippleUniforms[index * 4 + 2] = ripple.start
            rippleUniforms[index * 4 + 3] = 1f
        }
        GLES30.glUniform4fv(
            waterRipplesLocation,
            MAX_RIPPLES,
            rippleUniforms,
            0
        )
        GLES30.glBindVertexArray(waterVao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)
    }

    private fun drawFish(time: Float) {
        GLES30.glUseProgram(fishProgram)
        GLES30.glUniform1f(fishTimeLocation, time)
        GLES30.glUniform1f(
            fishAspectLocation,
            width.toFloat() / height.coerceAtLeast(1)
        )
        GLES30.glBindVertexArray(fishVao)
        for (index in 0 until activeFishCount) {
            val f = fish[index]
            GLES30.glUniform2f(fishPositionLocation, f.x, f.y)
            GLES30.glUniform1f(fishHeadingLocation, f.heading)
            GLES30.glUniform1f(fishScaleLocation, f.scale * (0.75f + f.depth * 0.35f))
            GLES30.glUniform1f(fishSwimPhaseLocation, f.swimPhase)
            GLES30.glUniform1f(fishFinPhaseLocation, f.finPhase)
            GLES30.glUniform1f(fishTailDriveLocation, f.tailDrive)
            GLES30.glUniform1f(fishLeftFinDriveLocation, f.leftFinDrive)
            GLES30.glUniform1f(fishRightFinDriveLocation, f.rightFinDrive)
            GLES30.glUniform1f(fishTurnDriveLocation, f.turnDrive)
            GLES30.glUniform1f(fishBrakeDriveLocation, f.brakeDrive)
            GLES30.glUniform1f(fishSeedLocation, f.seed)
            GLES30.glUniform1f(fishAlphaLocation, 0.72f + f.depth * 0.24f)
            GLES30.glUniform1f(
                fishSpeedLocation,
                f.forwardSpeed
            )
            GLES30.glUniform1f(fishBankLocation, f.bank)
            GLES30.glUniform3f(
                fishBaseColorLocation,
                f.baseColor[0], f.baseColor[1], f.baseColor[2]
            )
            GLES30.glUniform3f(
                fishPatchColorLocation,
                f.patchColor[0], f.patchColor[1], f.patchColor[2]
            )
            GLES30.glUniform3f(
                fishAccentColorLocation,
                f.accentColor[0], f.accentColor[1], f.accentColor[2]
            )
            GLES30.glUniform1f(fishPatternLocation, f.pattern)
            val sparkleFade = if (index == sparkleFishIndex) {
                val fadeIn = smoothStep01((time - sparkleStartedAt) / 0.20f)
                val fadeOut = smoothStep01((sparkleUntil - time) / 0.30f)
                fadeIn * fadeOut
            } else {
                0f
            }
            val touchPrizeSparkle = if (touchHeld && index == touchSparkleFishIndex) {
                val dx = f.x - attractionX
                val dy = f.y - attractionY
                val proximity = 1f - smoothStep01(
                    (sqrt(dx * dx + dy * dy) - 0.10f) / 0.24f
                )
                val glint = 0.58f + sin(time * 12.6f + f.phase) * 0.42f
                proximity * glint.coerceIn(0f, 1f)
            } else {
                0f
            }
            GLES30.glUniform1f(fishSparkleLocation, max(sparkleFade, touchPrizeSparkle))
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, fishVertexCount)
        }
        GLES30.glBindVertexArray(0)
    }

    private fun reportPerformance(now: Long) {
        reportFrames++
        val elapsed = now - reportStartNanos
        if (elapsed < PERFORMANCE_REPORT_NS) return
        val fps = reportFrames * 1_000_000_000f / elapsed
        when {
            fps < 21f && activeFishCount > MIN_FISH -> {
                activeFishCount = MIN_FISH
                healthyReports = 0
            }
            fps < 26f && activeFishCount > MEDIUM_FISH -> {
                activeFishCount = MEDIUM_FISH
                healthyReports = 0
            }
            fps > 28.5f -> {
                healthyReports++
                if (healthyReports >= 2 && activeFishCount < MAX_FISH) {
                    activeFishCount = min(MAX_FISH, activeFishCount + 1)
                    healthyReports = 0
                }
            }
            else -> healthyReports = 0
        }
        Log.i(TAG, "fps=${"%.1f".format(fps)} fish=$activeFishCount surface=${width}x$height")
        reportFrames = 0
        reportStartNanos = now
    }

    private fun createWaterGeometry() {
        val vertices = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        val ids = IntArray(1)
        GLES30.glGenVertexArrays(1, ids, 0)
        waterVao = ids[0]
        GLES30.glGenBuffers(1, ids, 0)
        waterVbo = ids[0]
        GLES30.glBindVertexArray(waterVao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, waterVbo)
        val buffer = floatBuffer(vertices)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertices.size * 4, buffer, GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 2 * 4, 0)
        GLES30.glBindVertexArray(0)
    }

    private fun createFishGeometry() {
        val vertices = mutableListOf<Float>()
        fun vertex(x: Float, y: Float, z: Float, kind: Float) {
            vertices += x
            vertices += y
            vertices += z
            vertices += x
            vertices += y
            vertices += kind
        }
        fun triangle(
            x0: Float, y0: Float,
            x1: Float, y1: Float,
            x2: Float, y2: Float,
            z: Float,
            kind: Float
        ) {
            vertex(x0, y0, z, kind)
            vertex(x1, y1, z, kind)
            vertex(x2, y2, z, kind)
        }
        fun fan(
            centerX: Float,
            centerY: Float,
            z: Float,
            kind: Float,
            edge: Array<Pair<Float, Float>>
        ) {
            for (i in edge.indices) {
                val next = edge[(i + 1) % edge.size]
                triangle(
                    centerX, centerY,
                    edge[i].first, edge[i].second,
                    next.first, next.second,
                    z, kind
                )
            }
        }
        fun tailMembrane() {
            val columnsX = floatArrayOf(-0.52f, -0.72f, -0.94f, -1.16f, -1.36f, -1.48f)
            val halfSpans = floatArrayOf(0.075f, 0.145f, 0.275f, 0.425f, 0.490f, 0.405f)
            val lateralRows = floatArrayOf(-1f, -0.5f, 0f, 0.5f, 1f)
            fun tailVertex(column: Int, row: Int) {
                val lateral = lateralRows[row]
                // A shallow central notch preserves a goldfish tail silhouette without
                // splitting the membrane into two independently flapping wing shapes.
                val trailingNotch = if (column == columnsX.lastIndex) {
                    (1f - abs(lateral)) * 0.10f
                } else {
                    0f
                }
                vertex(
                    columnsX[column] + trailingNotch,
                    halfSpans[column] * lateral,
                    0.008f * (1f - abs(lateral)),
                    1f
                )
            }
            for (column in 0 until columnsX.lastIndex) {
                for (row in 0 until lateralRows.lastIndex) {
                    tailVertex(column, row)
                    tailVertex(column, row + 1)
                    tailVertex(column + 1, row)
                    tailVertex(column + 1, row)
                    tailVertex(column, row + 1)
                    tailVertex(column + 1, row + 1)
                }
            }
        }
        val segments = 32
        for (i in 0 until segments) {
            val a0 = 2.0 * PI * i / segments
            val a1 = 2.0 * PI * (i + 1) / segments
            vertex(0.09f, 0f, 0.15f, 0f)
            vertex(0.09f + cos(a0).toFloat() * 0.68f, sin(a0).toFloat() * 0.24f, 0.025f, 0f)
            vertex(0.09f + cos(a1).toFloat() * 0.68f, sin(a1).toFloat() * 0.24f, 0.025f, 0f)
        }

        // One continuous veil-like tail, subdivided both lengthwise and laterally. Every vertex
        // participates in the same bend field; there are no left/right lobes that can flap as
        // a symmetric pair of wings.
        tailMembrane()

        // One flowing pectoral-fin pair with a rounded trailing edge.
        fan(
            -0.02f, 0.22f, -0.035f, 2f,
            arrayOf(
                0.38f to 0.14f,
                0.13f to 0.24f,
                -0.15f to 0.38f,
                -0.38f to 0.49f,
                -0.49f to 0.42f,
                -0.34f to 0.28f,
                -0.08f to 0.17f
            )
        )
        fan(
            -0.02f, -0.22f, -0.035f, 2f,
            arrayOf(
                0.38f to -0.14f,
                0.13f to -0.24f,
                -0.15f to -0.38f,
                -0.38f to -0.49f,
                -0.49f to -0.42f,
                -0.34f to -0.28f,
                -0.08f to -0.17f
            )
        )
        fishVertexCount = vertices.size / 6
        val data = vertices.toFloatArray()
        val ids = IntArray(1)
        GLES30.glGenVertexArrays(1, ids, 0)
        fishVao = ids[0]
        GLES30.glGenBuffers(1, ids, 0)
        fishVbo = ids[0]
        GLES30.glBindVertexArray(fishVao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, fishVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.size * 4, floatBuffer(data), GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 6 * 4, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 6 * 4, 3 * 4)
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(2, 1, GLES30.GL_FLOAT, false, 6 * 4, 5 * 4)
        GLES30.glBindVertexArray(0)
    }

    private fun elapsedSeconds(now: Long): Float = (now - startNanos) / 1_000_000_000f

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertex = compileShader(GLES30.GL_VERTEX_SHADER, vertexSource)
        val fragment = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vertex)
        GLES30.glAttachShader(program, fragment)
        GLES30.glLinkProgram(program)
        val status = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0)
        check(status[0] == GLES30.GL_TRUE) { GLES30.glGetProgramInfoLog(program) }
        GLES30.glDeleteShader(vertex)
        GLES30.glDeleteShader(fragment)
        return program
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        check(status[0] == GLES30.GL_TRUE) { GLES30.glGetShaderInfoLog(shader) }
        return shader
    }

    private fun floatBuffer(values: FloatArray): FloatBuffer = ByteBuffer
        .allocateDirect(values.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply { put(values).position(0) }

    private companion object {
        const val TAG = "KBoardAquarium"
        const val MAX_FISH = 10
        const val MEDIUM_FISH = 7
        const val MIN_FISH = 5
        const val MAX_RIPPLES = 4
        const val ATTRACTION_SECONDS = 4.6f
        const val TOUCH_EVENT_TIMEOUT_SECONDS = 1.0f
        const val FEED_GOLDEN_ANGLE = 2.3999632f
        const val FEED_VARIANT_COUNT = 3
        const val SCATTER_SECONDS = 3.4f
        const val PLAY_STYLE_SECONDS = 3.8f
        const val FEED_REPORT_SECONDS = 0.75f
        const val FEED_REACTION_SECONDS = 0.46f
        const val INTRO_MIN_FORM_SECONDS = 1.25f
        const val INTRO_FORM_TIMEOUT_SECONDS = 5.20f
        const val INTRO_SETTLED_DISTANCE = 0.145f
        const val INTRO_HOLD_SECONDS = 1.45f
        const val INTRO_DEPART_SECONDS = 3.20f
        const val SPARKLE_DURATION_MIN_SECONDS = 1.15f
        const val SPARKLE_DURATION_RANGE_SECONDS = 0.95f
        const val SPARKLE_PAUSE_MIN_SECONDS = 1.40f
        const val SPARKLE_PAUSE_RANGE_SECONDS = 2.60f
        const val PERFORMANCE_REPORT_NS = 5_000_000_000L
        const val TWO_PI = 6.2831855f
        // Representative phase of the visible caudal membrane around 72% of its length.
        // The vertex shader applies a posterior delay of 1.34 rad from root to tip.
        const val TAIL_PROPULSION_PHASE = -0.96f
        val FISH_SCALES = floatArrayOf(
            0.045f, 0.064f, 0.053f, 0.079f, 0.042f,
            0.070f, 0.057f, 0.075f, 0.048f, 0.061f
        )
        // Deliberately non-monotonic and unique: neighbours in the list do not form a mechanical
        // slow-to-fast parade, while every fish still keeps a recognisable cruising personality.
        val FISH_SPEED_PERSONALITIES = floatArrayOf(
            0.72f, 1.18f, 0.88f, 1.36f, 0.79f,
            1.06f, 1.43f, 0.95f, 1.27f, 0.84f
        )

        val FISH_PALETTES = arrayOf(
            FishPalette(floatArrayOf(0.96f, 0.97f, 0.94f), floatArrayOf(0.96f, 0.18f, 0.07f), floatArrayOf(0.08f, 0.09f, 0.12f)),
            FishPalette(floatArrayOf(0.98f, 0.72f, 0.10f), floatArrayOf(0.94f, 0.30f, 0.04f), floatArrayOf(0.99f, 0.94f, 0.64f)),
            FishPalette(floatArrayOf(0.90f, 0.94f, 0.98f), floatArrayOf(0.10f, 0.42f, 0.82f), floatArrayOf(0.04f, 0.12f, 0.24f)),
            FishPalette(floatArrayOf(0.96f, 0.78f, 0.58f), floatArrayOf(0.76f, 0.08f, 0.11f), floatArrayOf(0.20f, 0.04f, 0.06f)),
            FishPalette(floatArrayOf(0.82f, 0.90f, 0.72f), floatArrayOf(0.24f, 0.60f, 0.18f), floatArrayOf(0.94f, 0.82f, 0.16f)),
            FishPalette(floatArrayOf(0.96f, 0.90f, 0.82f), floatArrayOf(0.46f, 0.24f, 0.12f), floatArrayOf(0.10f, 0.08f, 0.07f)),
            FishPalette(floatArrayOf(0.92f, 0.84f, 0.98f), floatArrayOf(0.48f, 0.18f, 0.72f), floatArrayOf(0.18f, 0.06f, 0.28f)),
            FishPalette(floatArrayOf(0.86f, 0.96f, 0.96f), floatArrayOf(0.04f, 0.62f, 0.64f), floatArrayOf(0.98f, 0.54f, 0.12f)),
            FishPalette(floatArrayOf(0.98f, 0.82f, 0.88f), floatArrayOf(0.88f, 0.18f, 0.42f), floatArrayOf(0.46f, 0.04f, 0.16f)),
            FishPalette(floatArrayOf(0.88f, 0.90f, 0.94f), floatArrayOf(0.16f, 0.20f, 0.28f), floatArrayOf(0.92f, 0.34f, 0.08f))
        )

        const val WATER_VERTEX_SHADER = """#version 300 es
            layout(location = 0) in vec2 aPosition;
            out vec2 vUv;
            void main() {
                vUv = aPosition * 0.5 + 0.5;
                gl_Position = vec4(aPosition, 0.0, 1.0);
            }
        """

        const val WATER_FRAGMENT_SHADER = """#version 300 es
            precision mediump float;
            in vec2 vUv;
            out vec4 fragColor;
            uniform float uTime;
            uniform vec2 uResolution;
            uniform vec4 uRipples[4];

            void main() {
                vec2 uv = vUv;
                float aspect = uResolution.x / max(uResolution.y, 1.0);
                vec2 waveSlope = vec2(0.0);
                float waveHeight = 0.0;
                float waveEnergy = 0.0;
                float rippleCrest = 0.0;
                float rippleShadow = 0.0;
                float contactDimple = 0.0;
                float contactGlint = 0.0;
                for (int i = 0; i < 4; ++i) {
                    float age = uTime - uRipples[i].z;
                    vec2 delta = uv - uRipples[i].xy;
                    delta.x *= aspect;
                    // A touch makes a shallow, drifting surface disturbance. Keep the outline
                    // slightly irregular so it feels like pond water, but avoid bright rings or
                    // a large lens-like distortion over the keyboard.
                    vec2 flowDrift = vec2(
                        sin(uRipples[i].x * 17.0 + uRipples[i].y * 5.0),
                        cos(uRipples[i].y * 13.0 - uRipples[i].x * 4.0)
                    ) * age * 0.006;
                    delta -= flowDrift;
                    float currentAngle = uRipples[i].x * 7.3 + uRipples[i].y * 11.1;
                    vec2 currentAxis = vec2(cos(currentAngle), sin(currentAngle));
                    vec2 currentNormal = vec2(-currentAxis.y, currentAxis.x);
                    float alongCurrent = dot(delta, currentAxis);
                    float acrossCurrent = dot(delta, currentNormal);
                    vec2 currentSpace = vec2(alongCurrent * 0.95, acrossCurrent * 1.05);
                    float rawDistance = max(length(currentSpace), 0.001);
                    float angle = atan(currentSpace.y, currentSpace.x);
                    float directionalStretch = 1.0 +
                        sin(angle * 2.0 + uRipples[i].x * 6.0) * 0.045 +
                        sin(angle * 3.0 - uRipples[i].y * 7.0 + age * 0.34) * 0.022;
                    float edgeVariation = sin(angle * 5.0 + uRipples[i].x * 8.0 + age * 0.42) * 0.004;
                    float distanceFromTouch = rawDistance * directionalStretch + edgeVariation;
                    float waveFront = age * 0.19;
                    float wake = waveFront - distanceFromTouch;
                    float normalizedWake = wake / 0.074;
                    float frontBand = exp(-normalizedWake * normalizedWake);
                    float innerWake = smoothstep(0.0, 0.055, wake) *
                                      exp(-max(wake, 0.0) * 5.4);
                    float lifetime = step(0.0, age) *
                                     (1.0 - smoothstep(1.55, 2.35, age));
                    float envelope = (frontBand * 0.64 + innerWake * 0.36) *
                                     lifetime * exp(-age * 0.82);
                    float phase = wake * 34.0;
                    float impact = exp(-rawDistance * rawDistance * 380.0) *
                                   exp(-age * 5.2) * sin(age * 11.0);
                    float height = (sin(phase) * 0.76 + sin(phase * 0.58 + 0.8) * 0.24) *
                                   envelope * 0.40 + impact * lifetime * 0.16;
                    vec2 radialInCurrent = currentSpace / rawDistance;
                    vec2 radial = currentAxis * radialInCurrent.x * 0.95 +
                                  currentNormal * radialInCurrent.y * 1.05;
                    // The travelling wave is almost flat on its first frame. Add the small
                    // asymmetric depression and offset reflection that are visible the instant
                    // a fingertip breaks the surface, then fade them before the wake takes over.
                    // Current-space scaling plus angular perturbation keeps this from becoming
                    // a synthetic circular ring.
                    float contactLifetime = step(0.0, age) *
                                            (1.0 - smoothstep(0.16, 0.30, age));
                    vec2 contactSpace = vec2(
                        currentSpace.x * 1.16 + currentSpace.y * 0.10,
                        currentSpace.y * 0.84
                    );
                    float contactDistance = max(length(contactSpace), 0.001);
                    float contactAngle = atan(contactSpace.y, contactSpace.x);
                    float irregularContactDistance = contactDistance * (
                        1.0 + sin(contactAngle * 3.0 + currentAngle) * 0.075 +
                        sin(contactAngle * 5.0 - currentAngle) * 0.035
                    );
                    float dimple = exp(-irregularContactDistance * irregularContactDistance * 190.0) *
                                   contactLifetime;
                    vec2 glintOffset = contactSpace - vec2(-0.021, 0.015);
                    float glint = exp(-dot(glintOffset, glintOffset) * 560.0) *
                                  contactLifetime;
                    float contactFront = 0.013 + age * 0.12;
                    float contactBandDistance =
                        (irregularContactDistance - contactFront) / 0.020;
                    float contactBand = exp(-contactBandDistance * contactBandDistance) *
                                        contactLifetime;
                    waveHeight += height;
                    waveEnergy += abs(height);
                    waveSlope += radial * (cos(phase) * envelope * 0.54 - dimple * 0.32);
                    contactDimple += dimple;
                    contactGlint += glint * 0.92 + contactBand * 0.52;
                    // A broad highlight and its offset shadow expose the surface displacement
                    // through translucent keys. Both inherit the current-stretched, irregular
                    // distance field above, so the result is a soft pond ripple rather than a
                    // geometrically perfect circle.
                    rippleCrest += (0.5 + 0.5 * sin(phase)) * envelope * 0.78 +
                                   (0.5 + 0.5 * sin(phase * 0.58 + 0.8)) *
                                   innerWake * lifetime * exp(-age * 0.92) * 0.22;
                    rippleShadow += (0.5 + 0.5 * sin(phase + 2.30)) * envelope;
                }

                vec2 refractedUv = clamp(uv + waveSlope * vec2(0.0025, 0.0034), 0.0, 1.0);
                float flowA = sin((refractedUv.x * 8.0 + refractedUv.y * 5.0) + uTime * 0.52);
                float flowB = sin((refractedUv.x * -11.0 + refractedUv.y * 7.0) + uTime * 0.39);
                float caustic = smoothstep(0.58, 0.98, 0.5 + 0.25 * flowA + 0.25 * flowB);
                vec3 deep = vec3(0.012, 0.075, 0.14);
                vec3 shallow = vec3(0.018, 0.22, 0.30);
                vec3 color = mix(deep, shallow, refractedUv.y * 0.72 + caustic * 0.12);
                vec3 waterNormal = normalize(vec3(-waveSlope.x * 1.08, -waveSlope.y * 1.08, 1.0));
                vec3 lightDirection = normalize(vec3(-0.38, 0.46, 0.80));
                float waveHighlight = pow(max(dot(waterNormal, lightDirection), 0.0), 18.0);
                float crest = max(waveHeight, 0.0);
                float trough = max(-waveHeight, 0.0);
                color += vec3(0.30, 0.78, 0.92) *
                         (waveHighlight * waveEnergy * 0.44 + crest * 0.072);
                color += vec3(0.12, 0.48, 0.66) * min(rippleCrest, 1.3) * 0.095;
                color -= vec3(0.02, 0.10, 0.15) *
                         (trough * 0.070 + min(rippleShadow, 1.2) * 0.030);
                // Keep the contact cue legible below the translucent keycaps. Its highlight is
                // deliberately offset from the shallow blue depression like a real water dimple.
                color -= vec3(0.04, 0.14, 0.20) * min(contactDimple, 1.0) * 0.34;
                color += vec3(0.34, 0.78, 0.94) * min(contactGlint, 1.25) * 0.24;
                float vignette = 1.0 - smoothstep(0.20, 1.18, length((uv - 0.5) * vec2(1.0, 0.74)));
                color *= 0.72 + vignette * 0.28;
                fragColor = vec4(color, 1.0);
            }
        """

        const val FISH_VERTEX_SHADER = """#version 300 es
            layout(location = 0) in vec3 aPosition;
            layout(location = 1) in vec2 aUv;
            layout(location = 2) in float aKind;
            uniform vec2 uPosition;
            uniform float uHeading;
            uniform float uScale;
            uniform float uSwimPhase;
            uniform float uFinPhase;
            uniform float uTailDrive;
            uniform float uLeftFinDrive;
            uniform float uRightFinDrive;
            uniform float uTurnDrive;
            uniform float uBrakeDrive;
            uniform float uAspect;
            uniform float uSpeed;
            uniform float uBank;
            out vec2 vLocal;
            out float vHighlight;
            out float vMembrane;
            out float vBody;
            void main() {
                vec3 local = aPosition;
                float tailWeight = 1.0 - step(0.5, abs(aKind - 1.0));
                float finWeight = 1.0 - step(0.5, abs(aKind - 2.0));
                float bodyWeight = 1.0 - clamp(tailWeight + finWeight, 0.0, 1.0);
                float tailProgress = clamp((-local.x - 0.52) / 0.96, 0.0, 1.0);
                float tailMotionWeight = tailWeight *
                    smoothstep(0.0, 1.0, tailProgress);
                float tailDrive = clamp(uTailDrive, 0.0, 1.0);
                // The caudal peduncle initiates the bend and the compliant membrane follows.
                // Increasing tailProgress subtracts phase, giving the tip a visible delay. The
                // previous expression added phase toward the tip and made both lobes snap ahead
                // of the body like insect wings.
                float beatPhase = uSwimPhase - tailProgress * 1.34;
                float travellingWave = sin(beatPhase);
                // Keep the torso and head rigid. Locomotion is readable at the articulated
                // caudal and pectoral fins; the fish body translates and steers as one solid
                // mass instead of visibly wobbling with the tail phase.
                float tailAmplitude = mix(0.130, 0.400, tailDrive);
                float rootBend = sin(uSwimPhase) *
                                 mix(0.018, 0.052, tailDrive);
                float tailLateral = aPosition.y + tailWeight * (
                    rootBend * tailProgress +
                    travellingWave * tailMotionWeight * tailAmplitude
                );
                // A large heading error coils the continuous caudal membrane into a C-turn.
                // Curvature grows from the fixed peduncle to the tip and may approach one full
                // turn. The membrane narrows while cupped so a broad veil does not become two
                // crossing wing panels.
                float curlActivation = smoothstep(0.38, 0.94, abs(uTurnDrive));
                float maxCurlAngle = -sign(uTurnDrive) * curlActivation *
                                     mix(3.20, 5.20, uBrakeDrive);
                if (tailWeight > 0.5 && abs(maxCurlAngle) > 0.025) {
                    float curvature = maxCurlAngle / 0.96;
                    float arcAngle = maxCurlAngle * tailProgress;
                    float centreX = -0.52 - sin(arcAngle) / curvature;
                    float centreY = (1.0 - cos(arcAngle)) / curvature;
                    float cup = abs(maxCurlAngle) / 5.20;
                    float lateral = tailLateral * mix(1.0, 0.58, cup);
                    local.x = centreX + sin(arcAngle) * lateral;
                    local.y = centreY + cos(arcAngle) * lateral;
                } else {
                    local.y = tailLateral;
                    local.x -= (1.0 - cos(beatPhase)) * tailMotionWeight *
                               mix(0.006, 0.020, tailDrive);
                }
                // Static membrane camber plus a very small water-loaded flex. There is no
                // alternating per-lobe Z flap, eliminating the dragonfly-wing silhouette.
                float membraneCrown = 1.0 - smoothstep(0.0, 0.50, abs(aPosition.y));
                local.z += tailWeight * tailProgress * membraneCrown *
                           (0.016 + abs(travellingWave) * 0.010);

                // Positive local Y is the left pectoral fin. Each side uses the exact muscle
                // drive that contributed to CPU thrust, braking and yaw torque this frame.
                float finSide = sign(local.y);
                float finDrive = mix(uRightFinDrive, uLeftFinDrive,
                                     step(0.0, local.y));
                vec2 finRoot = vec2(-0.02, finSide * 0.22);
                float finLever = smoothstep(0.035, 0.43,
                                            length(aPosition.xy - finRoot));
                float finFlutter = sin(uFinPhase + finSide * 0.34 + finLever * 0.58);
                float finSpan = smoothstep(0.10, 0.46, abs(aPosition.y));
                float finOpening = clamp(finDrive + uBrakeDrive * 0.38, 0.0, 1.0);
                local.y += finFlutter * finWeight * finLever * finSide *
                           mix(0.026, 0.082, finOpening);
                local.x -= finWeight * finSpan * uBrakeDrive * 0.052;
                local.x += cos(uFinPhase + finSide * 0.24) * finWeight * finLever *
                           mix(0.012, 0.038, finDrive);
                local.z += finFlutter * finWeight * finLever * finSide *
                           mix(0.024, 0.082, finOpening);
                local.z += local.y * uBank * 0.42;
                float c = cos(uHeading);
                float s = sin(uHeading);
                // GLSL matrices are column-major. These columns rotate local +X by +heading,
                // exactly matching CPU motion (cos(heading), sin(heading)); the old ordering
                // mirrored Y and made diagonal fish visibly travel tail-first.
                vec2 rotated = mat2(c, s, -s, c) * local.xy;
                rotated.y = (rotated.y + local.z * 0.24) * uAspect * 0.70;
                vec2 world = uPosition + rotated * uScale;
                gl_Position = vec4(world, local.z * 0.12, 1.0);
                vLocal = vec2(aPosition.x, aPosition.y);
                vec3 surfaceNormal = normalize(vec3(
                    aPosition.x * 0.52,
                    aPosition.y * 1.85,
                    1.15 + aPosition.z * 2.4
                ));
                vec3 fishLight = normalize(vec3(-0.38, 0.52, 0.76));
                float diffuse = max(dot(surfaceNormal, fishLight), 0.0);
                float movingSpecular = pow(max(dot(surfaceNormal,
                    normalize(vec3(-0.18 + finFlutter * 0.08, 0.32, 0.93))), 0.0), 14.0);
                vHighlight = 0.58 + diffuse * 0.42 + movingSpecular * 0.34;
                vMembrane = clamp(max(finWeight, tailWeight * 0.82), 0.0, 1.0);
                vBody = bodyWeight;
            }
        """

        const val FISH_FRAGMENT_SHADER = """#version 300 es
            precision mediump float;
            in vec2 vLocal;
            in float vHighlight;
            in float vMembrane;
            in float vBody;
            out vec4 fragColor;
            uniform float uSeed;
            uniform float uAlpha;
            uniform vec3 uBaseColor;
            uniform vec3 uPatchColor;
            uniform vec3 uAccentColor;
            uniform float uPattern;
            uniform float uTime;
            uniform float uSparkle;

            float starGlint(vec2 point, vec2 centre, float phase) {
                vec2 delta = abs(point - centre);
                float core = exp(-dot(delta, delta) * 310.0);
                float horizontal = exp(-delta.x * 43.0 - delta.y * 10.0);
                float vertical = exp(-delta.y * 58.0 - delta.x * 9.0);
                float pulse = pow(max(sin(phase), 0.0), 3.0);
                return (core * 1.25 + (horizontal + vertical) * 0.30) * pulse;
            }
            void main() {
                float organic = sin(vLocal.x * 13.0 + uSeed) +
                                sin(vLocal.y * 18.0 - uSeed * 1.7) * 0.72;
                float stripes = sin(vLocal.x * 24.0 + vLocal.y * 5.0 + uSeed) * 1.18;
                float speckles = sin(vLocal.x * 31.0 + uSeed) *
                                  sin(vLocal.y * 29.0 - uSeed) * 1.55;
                float saddle = cos((vLocal.x + 0.15) * 8.5 + uSeed) -
                               abs(vLocal.y) * 1.25;
                float pattern1 = 1.0 - smoothstep(0.38, 0.62, abs(uPattern - 1.0));
                float pattern2 = 1.0 - smoothstep(0.38, 0.62, abs(uPattern - 2.0));
                float pattern3 = 1.0 - smoothstep(0.38, 0.62, abs(uPattern - 3.0));
                float motif = mix(organic, stripes, pattern1);
                motif = mix(motif, speckles, pattern2);
                motif = mix(motif, saddle, pattern3);
                float colorPatch = smoothstep(0.16, 0.78, motif);
                vec3 color = mix(uBaseColor, uPatchColor, colorPatch);
                float accentPatch = smoothstep(1.12, 1.74,
                    sin(vLocal.x * 8.0 - uSeed * 2.3) + sin(vLocal.y * 11.0));
                color = mix(color, uAccentColor, accentPatch * 0.68);
                color *= clamp(vHighlight, 0.62, 1.18);
                float membraneRibs = 0.5 + 0.5 * sin(
                    vLocal.x * 21.0 + abs(vLocal.y) * 31.0 + uSeed
                );
                color = mix(color, uBaseColor + vec3(0.16, 0.20, 0.22),
                            vMembrane * (0.16 + membraneRibs * 0.16));
                color += vec3(0.22, 0.34, 0.40) *
                         vMembrane * membraneRibs * max(vHighlight - 0.72, 0.0);
                float eyeDistance = length(vec2(
                    (vLocal.x - 0.50) * 3.8,
                    (abs(vLocal.y) - 0.125) * 8.5
                ));
                float eye = (1.0 - smoothstep(0.12, 0.25, eyeDistance)) * vBody;
                color = mix(color, vec3(0.018, 0.025, 0.032), eye * 0.94);
                float headGloss = exp(-pow(vLocal.x - 0.37, 2.0) * 21.0 -
                                      pow(vLocal.y + 0.05, 2.0) * 42.0) * vBody;
                color += vec3(0.30, 0.42, 0.46) * headGloss * 0.34;
                // Only one fish receives a non-zero uSparkle at a time. Three independent glints
                // travel over its head, back and veil so the highlight feels alive rather than
                // like a permanent emissive texture.
                vec2 sparkleA = vec2(0.34 + sin(uSeed * 1.73) * 0.10,
                                     0.05 + sin(uSeed * 2.31) * 0.08);
                vec2 sparkleB = vec2(-0.10 + sin(uSeed * 0.91) * 0.18,
                                     -0.08 + cos(uSeed * 1.47) * 0.10);
                vec2 sparkleC = vec2(-0.70 + sin(uSeed * 1.19) * 0.13,
                                     cos(uSeed * 2.07) * 0.17);
                float sparkle = starGlint(vLocal, sparkleA, uTime * 8.6 + uSeed) +
                                 starGlint(vLocal, sparkleB, uTime * 7.1 + uSeed * 1.8) +
                                 starGlint(vLocal, sparkleC, uTime * 9.4 + uSeed * 0.7);
                sparkle *= uSparkle;
                color += vec3(1.00, 0.90, 0.56) * min(sparkle, 1.45) * 0.92;
                color += vec3(0.66, 0.90, 1.00) * min(sparkle, 1.15) * 0.34;
                float alpha = mix(uAlpha, uAlpha * (0.48 + membraneRibs * 0.16), vMembrane);
                alpha = max(alpha, min(1.0, uAlpha + sparkle * 0.10));
                fragColor = vec4(color, alpha);
            }
        """
    }
}
