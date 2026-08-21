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
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.LockSupport
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private data class AquariumFeedCommand(val x: Float, val y: Float)

/**
 * A self-contained GLES 3 aquarium surface used only by the desktop keyboard.
 * Android key Views stay above this surface and keep full ownership of hit testing.
 */
class DesktopAquariumView(context: Context) : TextureView(context),
    TextureView.SurfaceTextureListener {

    private val feedCommands = ConcurrentLinkedQueue<AquariumFeedCommand>()
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
        feedCommands.clear()
    }

    fun feedAt(normalizedX: Float, normalizedY: Float) {
        if (!active) return
        while (feedCommands.size >= MAX_PENDING_FEEDS) feedCommands.poll()
        feedCommands.offer(
            AquariumFeedCommand(
                normalizedX.coerceIn(0f, 1f),
                normalizedY.coerceIn(0f, 1f)
            )
        )
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        if (active) startRenderer(surface, width, height)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        renderThread?.resize(width, height)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        stopRenderer()
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    private fun startRenderer(surface: SurfaceTexture, width: Int, height: Int) {
        if (renderThread?.isAlive == true || width <= 0 || height <= 0) return
        surface.setDefaultBufferSize(
            min(width, MAX_RENDER_WIDTH),
            (height * min(width, MAX_RENDER_WIDTH).toFloat() / width).toInt().coerceAtLeast(1)
        )
        renderThread = AquariumRenderThread(surface, width, height, feedCommands).also { it.start() }
    }

    private fun stopRenderer() {
        renderThread?.requestStop()
        renderThread = null
    }

    private companion object {
        const val MAX_PENDING_FEEDS = 6
        const val MAX_RENDER_WIDTH = 1440
    }
}

private class AquariumRenderThread(
    surfaceTexture: SurfaceTexture,
    initialWidth: Int,
    initialHeight: Int,
    private val commands: ConcurrentLinkedQueue<AquariumFeedCommand>
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
                drainFeedCommands(engine)
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

    private fun drainFeedCommands(engine: AquariumEngine) {
        while (true) {
            val command = commands.poll() ?: break
            engine.feed(command.x, command.y)
        }
    }

    private companion object {
        const val TAG = "KBoardAquarium"
        const val MAX_RENDER_WIDTH = 1440
        const val TARGET_FRAME_NS = 16_666_667L
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

    private data class Fish(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        val depth: Float,
        val scale: Float,
        val phase: Float,
        val seed: Float,
        val baseColor: FloatArray,
        val patchColor: FloatArray,
        val accentColor: FloatArray,
        val pattern: Float,
        var wanderX: Float,
        var wanderY: Float,
        var nextWanderTime: Float
    )

    private data class FishPalette(
        val base: FloatArray,
        val patch: FloatArray,
        val accent: FloatArray
    )

    private data class Ripple(var x: Float = 0f, var y: Float = 0f, var start: Float = -100f)

    private val random = Random(0x4B4F49)
    private val fish = MutableList(MAX_FISH) { index -> createFish(index) }
    private val ripples = Array(MAX_RIPPLES) { Ripple() }
    private var nextRipple = 0
    private var attractionX = 0f
    private var attractionY = 0f
    private var attractionUntil = -1f
    private var activeFishCount = MAX_FISH

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
    private var fishTimeLocation = -1
    private var fishAspectLocation = -1
    private var fishPositionLocation = -1
    private var fishHeadingLocation = -1
    private var fishScaleLocation = -1
    private var fishPhaseLocation = -1
    private var fishSeedLocation = -1
    private var fishAlphaLocation = -1
    private var fishBaseColorLocation = -1
    private var fishPatchColorLocation = -1
    private var fishAccentColorLocation = -1
    private var fishPatternLocation = -1
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
        waterProgram = createProgram(WATER_VERTEX_SHADER, WATER_FRAGMENT_SHADER)
        fishProgram = createProgram(FISH_VERTEX_SHADER, FISH_FRAGMENT_SHADER)
        waterTimeLocation = GLES30.glGetUniformLocation(waterProgram, "uTime")
        waterResolutionLocation = GLES30.glGetUniformLocation(waterProgram, "uResolution")
        waterRipplesLocation = GLES30.glGetUniformLocation(waterProgram, "uRipples[0]")
        fishTimeLocation = GLES30.glGetUniformLocation(fishProgram, "uTime")
        fishAspectLocation = GLES30.glGetUniformLocation(fishProgram, "uAspect")
        fishPositionLocation = GLES30.glGetUniformLocation(fishProgram, "uPosition")
        fishHeadingLocation = GLES30.glGetUniformLocation(fishProgram, "uHeading")
        fishScaleLocation = GLES30.glGetUniformLocation(fishProgram, "uScale")
        fishPhaseLocation = GLES30.glGetUniformLocation(fishProgram, "uPhase")
        fishSeedLocation = GLES30.glGetUniformLocation(fishProgram, "uSeed")
        fishAlphaLocation = GLES30.glGetUniformLocation(fishProgram, "uAlpha")
        fishBaseColorLocation = GLES30.glGetUniformLocation(fishProgram, "uBaseColor")
        fishPatchColorLocation = GLES30.glGetUniformLocation(fishProgram, "uPatchColor")
        fishAccentColorLocation = GLES30.glGetUniformLocation(fishProgram, "uAccentColor")
        fishPatternLocation = GLES30.glGetUniformLocation(fishProgram, "uPattern")
        createWaterGeometry()
        createFishGeometry()
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
    }

    fun resize(width: Int, height: Int) {
        this.width = width.coerceAtLeast(1)
        this.height = height.coerceAtLeast(1)
        GLES30.glViewport(0, 0, this.width, this.height)
    }

    fun feed(x: Float, y: Float) {
        val now = elapsedSeconds(System.nanoTime())
        ripples[nextRipple].apply {
            this.x = x
            this.y = 1f - y
            start = now
        }
        nextRipple = (nextRipple + 1) % ripples.size
        attractionX = x * 2f - 1f
        attractionY = 1f - y * 2f
        attractionUntil = now + ATTRACTION_SECONDS
    }

    fun draw(frameNanos: Long) {
        val time = elapsedSeconds(frameNanos)
        val dt = ((frameNanos - previousNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
        previousNanos = frameNanos
        updateFish(time, dt)
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
        val heading = if (index % 2 == 0) 1f else -1f
        val palette = FISH_PALETTES[index % FISH_PALETTES.size]
        return Fish(
            x = random.nextFloat() * 1.7f - 0.85f,
            y = random.nextFloat() * 1.55f - 0.78f,
            vx = heading * (0.07f + random.nextFloat() * 0.08f),
            vy = random.nextFloat() * 0.08f - 0.04f,
            depth = random.nextFloat(),
            scale = FISH_SCALES[index % FISH_SCALES.size],
            phase = random.nextFloat() * (2f * PI.toFloat()),
            seed = random.nextFloat() * 12f,
            baseColor = palette.base,
            patchColor = palette.patch,
            accentColor = palette.accent,
            pattern = (index % 4).toFloat(),
            wanderX = random.nextFloat() * 1.6f - 0.8f,
            wanderY = random.nextFloat() * 1.4f - 0.7f,
            nextWanderTime = 1f + random.nextFloat() * 3f
        )
    }

    private fun updateFish(time: Float, dt: Float) {
        val feeding = time < attractionUntil
        for (index in 0 until activeFishCount) {
            val f = fish[index]
            if (!feeding && time >= f.nextWanderTime) {
                f.wanderX = random.nextFloat() * 1.7f - 0.85f
                f.wanderY = random.nextFloat() * 1.5f - 0.75f
                f.nextWanderTime = time + 2.5f + random.nextFloat() * 4f
            }
            val offsetAngle = f.seed * 2.1f
            val targetX = if (feeding) attractionX + cos(offsetAngle) * 0.10f else f.wanderX
            val targetY = if (feeding) attractionY + sin(offsetAngle) * 0.08f else f.wanderY
            var ax = (targetX - f.x) * if (feeding) 1.9f else 0.32f
            var ay = (targetY - f.y) * if (feeding) 1.9f else 0.32f
            for (otherIndex in 0 until activeFishCount) {
                if (otherIndex == index) continue
                val other = fish[otherIndex]
                val dx = f.x - other.x
                val dy = f.y - other.y
                val distance2 = dx * dx + dy * dy
                if (distance2 in 0.0001f..0.025f) {
                    val separation = (0.025f - distance2) * 0.9f / distance2
                    ax += dx * separation
                    ay += dy * separation
                }
            }
            ax += sin(time * 0.73f + f.phase) * 0.025f
            ay += cos(time * 0.61f + f.phase) * 0.018f
            f.vx += ax * dt
            f.vy += ay * dt
            val maxSpeed = if (feeding) 0.52f else 0.24f
            val speed = sqrt(f.vx * f.vx + f.vy * f.vy).coerceAtLeast(0.0001f)
            if (speed > maxSpeed) {
                f.vx *= maxSpeed / speed
                f.vy *= maxSpeed / speed
            }
            val damping = exp((-if (feeding) 0.45f else 0.7f) * dt)
            f.vx *= damping
            f.vy *= damping
            f.x += f.vx * dt
            f.y += f.vy * dt
            if (f.x < -1.05f || f.x > 1.05f) {
                f.x = f.x.coerceIn(-1.05f, 1.05f)
                f.vx = -f.vx
            }
            if (f.y < -0.92f || f.y > 0.92f) {
                f.y = f.y.coerceIn(-0.92f, 0.92f)
                f.vy = -f.vy
            }
        }
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
            val heading = atan2(f.vy, f.vx)
            GLES30.glUniform2f(fishPositionLocation, f.x, f.y)
            GLES30.glUniform1f(fishHeadingLocation, heading)
            GLES30.glUniform1f(fishScaleLocation, f.scale * (0.75f + f.depth * 0.35f))
            GLES30.glUniform1f(fishPhaseLocation, f.phase)
            GLES30.glUniform1f(fishSeedLocation, f.seed)
            GLES30.glUniform1f(fishAlphaLocation, 0.72f + f.depth * 0.24f)
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
            fps < 32f && activeFishCount > MIN_FISH -> {
                activeFishCount = MIN_FISH
                healthyReports = 0
            }
            fps < 44f && activeFishCount > MEDIUM_FISH -> {
                activeFishCount = MEDIUM_FISH
                healthyReports = 0
            }
            fps > 55f -> {
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
        fun vertex(x: Float, y: Float, z: Float) {
            vertices += x
            vertices += y
            vertices += z
            vertices += (x + 1.1f) / 2.1f
            vertices += y + 0.5f
        }
        val segments = 18
        for (i in 0 until segments) {
            val a0 = 2.0 * PI * i / segments
            val a1 = 2.0 * PI * (i + 1) / segments
            vertex(0.05f, 0f, 0.13f)
            vertex(0.05f + cos(a0).toFloat() * 0.78f, sin(a0).toFloat() * 0.34f, 0.02f)
            vertex(0.05f + cos(a1).toFloat() * 0.78f, sin(a1).toFloat() * 0.34f, 0.02f)
        }
        vertex(-0.62f, 0f, 0.01f)
        vertex(-1.08f, 0.43f, -0.02f)
        vertex(-0.98f, 0f, 0f)
        vertex(-0.62f, 0f, 0.01f)
        vertex(-0.98f, 0f, 0f)
        vertex(-1.08f, -0.43f, -0.02f)
        vertex(-0.10f, 0.16f, 0.01f)
        vertex(-0.48f, 0.54f, -0.03f)
        vertex(0.25f, 0.23f, 0f)
        vertex(-0.10f, -0.16f, 0.01f)
        vertex(-0.48f, -0.54f, -0.03f)
        vertex(0.25f, -0.23f, 0f)
        fishVertexCount = vertices.size / 5
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
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 5 * 4, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 5 * 4, 3 * 4)
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
        const val ATTRACTION_SECONDS = 2.4f
        const val PERFORMANCE_REPORT_NS = 5_000_000_000L

        val FISH_SCALES = floatArrayOf(
            0.072f, 0.112f, 0.086f, 0.145f, 0.066f,
            0.124f, 0.094f, 0.136f, 0.078f, 0.104f
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
                float flowA = sin((uv.x * 8.0 + uv.y * 5.0) + uTime * 0.52);
                float flowB = sin((uv.x * -11.0 + uv.y * 7.0) + uTime * 0.39);
                float caustic = smoothstep(0.58, 0.98, 0.5 + 0.25 * flowA + 0.25 * flowB);
                vec3 deep = vec3(0.012, 0.075, 0.14);
                vec3 shallow = vec3(0.018, 0.22, 0.30);
                vec3 color = mix(deep, shallow, uv.y * 0.72 + caustic * 0.10);
                float rippleLight = 0.0;
                for (int i = 0; i < 4; ++i) {
                    float age = uTime - uRipples[i].z;
                    vec2 delta = uv - uRipples[i].xy;
                    delta.x *= aspect;
                    float distanceFromTouch = length(delta);
                    float radius = age * 0.44;
                    float ring = exp(-abs(distanceFromTouch - radius) * 58.0);
                    float echo = exp(-abs(distanceFromTouch - radius * 0.68) * 46.0) * 0.58;
                    float softRing = exp(-abs(distanceFromTouch - radius * 0.42) * 38.0) * 0.28;
                    float touchGlow = exp(-distanceFromTouch * 34.0) *
                                      (1.0 - smoothstep(0.0, 0.34, age));
                    float alive = step(0.0, age) * (1.0 - smoothstep(0.8, 1.75, age));
                    rippleLight += (ring + echo + softRing + touchGlow) * alive;
                }
                color += vec3(0.20, 0.74, 0.92) * rippleLight * 0.46;
                float vignette = 1.0 - smoothstep(0.20, 1.18, length((uv - 0.5) * vec2(1.0, 0.74)));
                color *= 0.72 + vignette * 0.28;
                fragColor = vec4(color, 1.0);
            }
        """

        const val FISH_VERTEX_SHADER = """#version 300 es
            layout(location = 0) in vec3 aPosition;
            layout(location = 1) in vec2 aUv;
            uniform vec2 uPosition;
            uniform float uHeading;
            uniform float uScale;
            uniform float uPhase;
            uniform float uTime;
            uniform float uAspect;
            out vec2 vLocal;
            out float vHighlight;
            void main() {
                vec3 local = aPosition;
                float tailWeight = 1.0 - smoothstep(-1.08, -0.38, local.x);
                local.y += sin(uTime * 7.0 + uPhase + local.x * 2.4) * tailWeight * 0.28;
                local.y += sin(uTime * 4.2 + uPhase + local.x * 3.0) * 0.025;
                float c = cos(uHeading);
                float s = sin(uHeading);
                vec2 rotated = mat2(c, -s, s, c) * local.xy;
                rotated.y *= uAspect * 0.70;
                vec2 world = uPosition + rotated * uScale;
                gl_Position = vec4(world, local.z * 0.12, 1.0);
                vLocal = vec2(aPosition.x, aPosition.y);
                vHighlight = 0.55 + aPosition.z * 2.2;
            }
        """

        const val FISH_FRAGMENT_SHADER = """#version 300 es
            precision mediump float;
            in vec2 vLocal;
            in float vHighlight;
            out vec4 fragColor;
            uniform float uSeed;
            uniform float uAlpha;
            uniform vec3 uBaseColor;
            uniform vec3 uPatchColor;
            uniform vec3 uAccentColor;
            uniform float uPattern;
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
                float fin = 1.0 - smoothstep(0.18, 0.35, abs(vLocal.y));
                float alpha = mix(uAlpha * 0.68, uAlpha, fin);
                fragColor = vec4(color, alpha);
            }
        """
    }
}
