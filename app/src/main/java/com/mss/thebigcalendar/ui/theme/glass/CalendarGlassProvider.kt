// SPDX-License-Identifier: GPL-3.0-or-later
package com.mss.thebigcalendar.ui.theme.glass

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.util.DisplayMetrics
import android.view.View
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Offset
import androidx.core.graphics.ColorUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Manages procedural background textures and renders refractive liquid/frosted glass
 * mapped directly to the screen coordinate system for Big Calendar.
 * Does NOT access system home wallpaper, preserving app privacy and custom aesthetics.
 */
object CalendarGlassProvider {

    private const val SHARP_WIDTH = 720
    private const val FROSTED_SAMPLE_WIDTH = 180

    private val initialized = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _wallpaperTick = MutableStateFlow(0)
    val wallpaperTick: StateFlow<Int> = _wallpaperTick.asStateFlow()

    @Volatile
    private var cachedSharp: Bitmap? = null

    @Volatile
    private var cachedBlurred: Bitmap? = null

    @Volatile
    private var lastDark: Boolean? = null

    @Volatile
    private var lastPrimaryHex: String? = null

    private var liquidShaderInstance: Any? = null

    @Volatile
    private var appContextRef: Context? = null

    private val emptyMaskShader: BitmapShader by lazy {
        val bmp = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    }

    private val reusablePath = Path()
    private val reusableRadii = FloatArray(8)
    private val reusableMatrix = Matrix()
    private val reusablePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val reusableTintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val reusableBasePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    @Volatile
    private var cachedActiveBitmapShader: BitmapShader? = null
    @Volatile
    private var lastBitmapForShader: Bitmap? = null

    private fun getOrCreateBitmapShader(bitmap: Bitmap): BitmapShader {
        var bs = cachedActiveBitmapShader
        if (bs == null || lastBitmapForShader !== bitmap) {
            bs = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            cachedActiveBitmapShader = bs
            lastBitmapForShader = bitmap
        }
        return bs
    }

    fun init(context: Context, isDark: Boolean = false, primaryColorHex: String? = null) {
        val appContext = context.applicationContext
        appContextRef = appContext
        if (initialized.compareAndSet(false, true) || lastDark != isDark || lastPrimaryHex != primaryColorHex) {
            refreshBackground(appContext, isDark, primaryColorHex)
        }
    }

    fun refreshBackground(context: Context, isDark: Boolean, primaryColorHex: String? = null) {
        val appContext = context.applicationContext
        appContextRef = appContext
        lastDark = isDark
        lastPrimaryHex = primaryColorHex
        scope.launch {
            val (w, h) = getRealWindowSize(appContext)
            val base = createProceduralBackground(
                context = appContext,
                width = max(1, w.roundToInt()),
                height = max(1, h.roundToInt()),
                isDark = isDark,
                primaryColorHex = primaryColorHex
            )
            processBackground(base)
            withContext(Dispatchers.Main) {
                _wallpaperTick.value += 1
            }
        }
    }

    private fun processBackground(source: Bitmap) {
        val aspect = source.width.toFloat() / source.height.coerceAtLeast(1)
        val sharpW = min(SHARP_WIDTH, max(1, source.width))
        val sharpH = max(1, (sharpW / aspect).roundToInt())
        val sharp = if (source.width == sharpW && source.height == sharpH) {
            source
        } else {
            Bitmap.createScaledBitmap(source, sharpW, sharpH, true)
        }
        cachedSharp = sharp

        val blurW = min(FROSTED_SAMPLE_WIDTH, sharpW)
        val blurred = GlassShader.sampleBlur(sharp, blurW, 4)
        cachedBlurred = blurred
    }

    private fun createProceduralBackground(
        context: Context,
        width: Int,
        height: Int,
        isDark: Boolean,
        primaryColorHex: String?
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val primaryInt = if (!primaryColorHex.isNullOrBlank() && primaryColorHex != "AUTO") {
            try {
                Color.parseColor(primaryColorHex)
            } catch (e: Exception) {
                if (isDark) 0xFF7A68E3.toInt() else 0xFF6750A4.toInt()
            }
        } else {
            if (isDark) 0xFF7A68E3.toInt() else 0xFF6750A4.toInt()
        }

        val baseColor1 = if (isDark) 0xFF0D111A.toInt() else 0xFFE9EEF5.toInt()
        val baseColor2 = if (isDark) 0xFF171E2D.toInt() else 0xFFD3DFEC.toInt()

        val spotColor1 = ColorUtils.setAlphaComponent(primaryInt, if (isDark) 0x36 else 0x2A)
        val accentSecondary = if (isDark) 0xFF1976D2.toInt() else 0xFF42A5F5.toInt()
        val spotColor2 = ColorUtils.setAlphaComponent(accentSecondary, if (isDark) 0x2D else 0x24)
        val spotColor3 = if (isDark) 0x1FFFFFFF else 0x24FFFFFF

        val linearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f,
                width.toFloat(), height.toFloat(),
                baseColor1, baseColor2,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawPaint(linearPaint)

        val spotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                width * 0.32f, height * 0.28f, width * 0.65f,
                spotColor1, 0, Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(width * 0.32f, height * 0.28f, width * 0.65f, spotPaint)

        spotPaint.shader = RadialGradient(
            width * 0.78f, height * 0.72f, width * 0.60f,
            spotColor2, 0, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(width * 0.78f, height * 0.72f, width * 0.60f, spotPaint)

        spotPaint.shader = RadialGradient(
            width * 0.50f, height * 0.12f, width * 0.45f,
            spotColor3, 0, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(width * 0.50f, height * 0.12f, width * 0.45f, spotPaint)

        return bitmap
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun getOrCreateLiquidShader(): RuntimeShader {
        var s = liquidShaderInstance as? RuntimeShader
        if (s == null) {
            s = RuntimeShader(GlassShader.LIQUID)
            liquidShaderInstance = s
        }
        return s
    }

    fun getRealWindowSize(context: Context, view: View? = null): Pair<Float, Float> {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (wm != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val metrics = wm.currentWindowMetrics
                val bounds = metrics.bounds
                return bounds.width().toFloat() to bounds.height().toFloat()
            } else {
                @Suppress("DEPRECATION")
                val dm = DisplayMetrics()
                @Suppress("DEPRECATION")
                wm.defaultDisplay.getRealMetrics(dm)
                return dm.widthPixels.toFloat() to dm.heightPixels.toFloat()
            }
        }
        val dm = context.resources.displayMetrics
        return dm.widthPixels.toFloat() to dm.heightPixels.toFloat()
    }

    fun drawBackgroundCanvas(
        canvas: Canvas,
        width: Float,
        height: Float,
        night: Boolean
    ) {
        val base = cachedSharp
        if (base != null && !base.isRecycled) {
            val scale = max(width / base.width.toFloat(), height / base.height.toFloat())
            val scaledW = base.width.toFloat() * scale
            val scaledH = base.height.toFloat() * scale
            val dx = (width - scaledW) / 2f
            val dy = (height - scaledH) / 2f
            val matrix = reusableMatrix.apply {
                reset()
                setScale(scale, scale)
                postTranslate(dx, dy)
            }
            val paint = reusablePaint.apply {
                reset()
                isAntiAlias = true
                shader = getOrCreateBitmapShader(base).apply {
                    setLocalMatrix(matrix)
                }
            }
            canvas.drawRect(0f, 0f, width, height, paint)
        } else {
            val paint = reusableBasePaint.apply {
                reset()
                isAntiAlias = true
                color = if (night) 0xFF0E131C.toInt() else 0xFFEAF0F6.toInt()
            }
            canvas.drawRect(0f, 0f, width, height, paint)
        }
    }

    fun drawGlassElement(
        canvas: Canvas,
        bounds: RectF,
        positionInWindow: Offset,
        screenWidth: Float,
        screenHeight: Float,
        topCorner: Float,
        bottomCorner: Float,
        style: GlassStyle = GlassStyle.DEFAULT,
        surfaceColor: Int = Color.WHITE,
        tintOpacity: Int = 20,
        density: Float = 1f,
        night: Boolean = true,
    ) {
        if (bounds.isEmpty) return

        val path = reusablePath.apply {
            reset()
            if (topCorner == bottomCorner) {
                addRoundRect(bounds, topCorner, topCorner, Path.Direction.CW)
            } else {
                reusableRadii[0] = topCorner
                reusableRadii[1] = topCorner
                reusableRadii[2] = topCorner
                reusableRadii[3] = topCorner
                reusableRadii[4] = bottomCorner
                reusableRadii[5] = bottomCorner
                reusableRadii[6] = bottomCorner
                reusableRadii[7] = bottomCorner
                addRoundRect(bounds, reusableRadii, Path.Direction.CW)
            }
        }

        GlassShader.drawShadow(canvas, path, bounds, style.shadow, density)

        val base = cachedSharp
        var targetWidth = screenWidth
        var targetHeight = screenHeight
        if (targetWidth <= 0f || targetHeight <= 0f) {
            appContextRef?.let { ctx ->
                val (w, h) = getRealWindowSize(ctx)
                targetWidth = w
                targetHeight = h
            }
        }

        if (base != null && targetWidth > 0f && targetHeight > 0f) {
            val isTiramisu = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            val activeBitmap = cachedBlurred ?: base
            val scale = targetWidth / activeBitmap.width.toFloat().coerceAtLeast(1f)
            val scaledWidth = targetWidth
            val scaledHeight = activeBitmap.height.toFloat() * scale
            val dx = 0f
            val dy = (targetHeight - scaledHeight) / 2f

            val shaderMatrix = reusableMatrix.apply {
                reset()
                setScale(scale, scale)
                postTranslate(dx, dy)
                postTranslate(-positionInWindow.x, -positionInWindow.y)
            }

            val imageShader = getOrCreateBitmapShader(activeBitmap).apply {
                setLocalMatrix(shaderMatrix)
            }

            val dimColor = if (style.dimFactor > 0f) {
                if (night) {
                    val alpha = (85 * style.dimFactor).roundToInt().coerceIn(0, 255)
                    Color.argb(alpha, 14, 18, 26)
                } else {
                    val alpha = (95 * style.dimFactor).roundToInt().coerceIn(0, 255)
                    Color.argb(alpha, 245, 247, 252)
                }
            } else {
                Color.TRANSPARENT
            }

            if (isTiramisu) {
                val shader = getOrCreateLiquidShader()
                shader.setInputShader("image", imageShader)
                shader.setFloatUniform("origin", bounds.left, bounds.top)
                shader.setFloatUniform("size", bounds.width(), bounds.height())
                GlassShader.setRadii(shader, topCorner, bottomCorner)
                GlassShader.applyStyle(shader, bounds.width(), bounds.height(), style)
                GlassShader.setDim(shader, dimColor)
                shader.setInputShader("mask", emptyMaskShader)
                shader.setFloatUniform("useMask", 0f)
                shader.setFloatUniform("maskOrigin", 0f, 0f)
                shader.setFloatUniform("maskScale", 1f)
                shader.setFloatUniform("maskRange", 1f)

                val paint = reusablePaint.apply {
                    reset()
                    isAntiAlias = true
                    this.shader = shader
                }
                canvas.drawPath(path, paint)
            } else {
                val paint = reusablePaint.apply {
                    reset()
                    isAntiAlias = true
                    this.shader = imageShader
                    colorFilter = GlassShader.backdropFilter(dimColor, style.saturation)
                }
                canvas.drawPath(path, paint)
            }
        } else {
            val basePaint = reusableBasePaint.apply {
                reset()
                isAntiAlias = true
                color = if (night) 0x33FFFFFF else 0x55FFFFFF
            }
            canvas.drawPath(path, basePaint)
        }

        if (tintOpacity > 0) {
            val tintAlpha = (tintOpacity.coerceIn(0, 100) * 255 / 100)
            val tintPaint = reusableTintPaint.apply {
                reset()
                isAntiAlias = true
                color = ColorUtils.setAlphaComponent(surfaceColor, tintAlpha)
            }
            canvas.drawPath(path, tintPaint)
        }

        GlassShader.drawOutline(canvas, bounds, topCorner, bottomCorner, style, 1f, density)
    }
}
