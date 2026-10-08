package ly.mahd.app.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * A picture the mother can send on WhatsApp: the child's name, age and the skills they have reached.
 * Drawn with Android's own Canvas (no library). Text goes through [StaticLayout], which shapes Arabic
 * correctly and lays right-to-left text out from the right.
 */
object ShareCard {
    private const val WIDTH = 1080
    private const val HEIGHT = 1350
    private const val MARGIN = 72
    private const val BRAND = 0xFFA23B5B.toInt()
    private const val INK = 0xFF22191B.toInt()
    private const val SOFT_INK = 0xFF524346.toInt()

    class Content(
        val appName: String,
        val tagline: String,
        /** e.g. "إنجازات Omar" */
        val title: String,
        /** e.g. "10 أشهر · مهارات عمر 9 أشهر" */
        val subtitle: String,
        val achievements: List<String>,
        /** e.g. "ظهرت 5 من أصل 13" */
        val countLine: String,
        val footer: String,
    )

    fun render(content: Content): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val inner = WIDTH - MARGIN * 2

        // Soft rose background
        val background = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, HEIGHT.toFloat(), 0xFFFFF4F5.toInt(), 0xFFFFD3DC.toInt(), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), background)

        // Brand mark: three moons, crescent to full (the "stages")
        drawMoons(canvas, centerY = 150f)

        // Lay everything out first, so the whole block can be centred between the moons and the footer.
        val lines = content.achievements.take(5).map { "✓  $it" }
        val textWidth = inner - 80
        val layouts = lines.map { layout(it, 38f, INK, false, textWidth, Layout.Alignment.ALIGN_NORMAL, maxLines = 2) }
        val countLayout = layout(content.countLine, 36f, BRAND, true, textWidth, Layout.Alignment.ALIGN_CENTER, maxLines = 1)
        val gap = 26f
        val panelHeight = 48f + layouts.sumOf { it.height.toDouble() }.toFloat() + gap * layouts.size + countLayout.height + 40f

        val nameH = layout(content.appName, 84f, BRAND, true, inner, Layout.Alignment.ALIGN_CENTER, 3).height
        val tagH = layout(content.tagline, 32f, SOFT_INK, false, inner, Layout.Alignment.ALIGN_CENTER, 3).height
        val titleH = layout(content.title, 68f, INK, true, inner, Layout.Alignment.ALIGN_CENTER, 1).height
        val subH = layout(content.subtitle, 38f, BRAND, true, inner, Layout.Alignment.ALIGN_CENTER, 2).height
        val block = nameH + 6f + tagH + 52f + titleH + 10f + subH + 40f + panelHeight
        val areaTop = 230f
        val areaBottom = HEIGHT - 150f
        var y = areaTop + ((areaBottom - areaTop - block) / 2f).coerceAtLeast(0f)

        y += drawText(canvas, content.appName, size = 84f, color = BRAND, bold = true, width = inner, top = y) + 6f
        y += drawText(canvas, content.tagline, size = 32f, color = SOFT_INK, bold = false, width = inner, top = y) + 52f
        y += drawText(canvas, content.title, size = 68f, color = INK, bold = true, width = inner, top = y, maxLines = 1) + 10f
        y += drawText(canvas, content.subtitle, size = 38f, color = BRAND, bold = true, width = inner, top = y, maxLines = 2) + 40f

        // White panel with the achievements
        val panelTop = y
        val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
        canvas.drawRoundRect(RectF(MARGIN.toFloat(), panelTop, (WIDTH - MARGIN).toFloat(), panelTop + panelHeight), 44f, 44f, panelPaint)
        var py = panelTop + 48f
        layouts.forEach { l ->
            canvas.save()
            canvas.translate((MARGIN + 40).toFloat(), py)
            l.draw(canvas)
            canvas.restore()
            py += l.height + gap
        }
        canvas.save()
        canvas.translate((MARGIN + 40).toFloat(), py)
        countLayout.draw(canvas)
        canvas.restore()

        // Footer
        drawText(canvas, content.footer, size = 32f, color = SOFT_INK, bold = false, width = inner, top = HEIGHT - 110f)
        return bitmap
    }

    /** Saves the picture (off the main thread, so the screen does not stutter) and opens Android's share sheet. */
    suspend fun share(context: Context, bitmap: Bitmap, chooserTitle: String, fileName: String = "atwar-achievements.png") {
        val file = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            File(dir, fileName).also { file ->
                FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, chooserTitle))
    }

    private fun drawMoons(canvas: Canvas, centerY: Float) {
        val radius = 34f
        val centers = floatArrayOf(WIDTH / 2f - 110f, WIDTH / 2f, WIDTH / 2f + 110f)
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BRAND; alpha = 70 }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BRAND }
        centers.forEach { cx -> canvas.drawCircle(cx, centerY, radius, outline) }

        // crescent = disc minus a shifted disc
        val crescent = Path().apply {
            addCircle(centers[0], centerY, radius, Path.Direction.CW)
            val cut = Path().apply { addCircle(centers[0] - radius * 0.55f, centerY, radius * 0.95f, Path.Direction.CW) }
            op(cut, Path.Op.DIFFERENCE)
        }
        canvas.drawPath(crescent, fill)

        // half = right half of the disc
        val half = Path().apply {
            addCircle(centers[1], centerY, radius, Path.Direction.CW)
            val rightHalf = Path().apply { addRect(centers[1], centerY - radius, centers[1] + radius, centerY + radius, Path.Direction.CW) }
            op(rightHalf, Path.Op.INTERSECT)
        }
        canvas.drawPath(half, fill)

        canvas.drawCircle(centers[2], centerY, radius, fill)
    }

    private fun layout(
        text: String,
        size: Float,
        color: Int,
        bold: Boolean,
        width: Int,
        alignment: Layout.Alignment,
        maxLines: Int = Int.MAX_VALUE,
    ): StaticLayout {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(alignment)
            .setLineSpacing(0f, 1.15f)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
    }

    /** Draws centred text starting at [top] (in a column starting at [left]); returns its height. */
    private fun drawText(
        canvas: Canvas,
        text: String,
        size: Float,
        color: Int,
        bold: Boolean,
        width: Int,
        top: Float,
        maxLines: Int = 3,
        left: Float = MARGIN.toFloat(),
    ): Float {
        val l = layout(text, size, color, bold, width, Layout.Alignment.ALIGN_CENTER, maxLines)
        canvas.save()
        canvas.translate(left, top)
        l.draw(canvas)
        canvas.restore()
        return l.height.toFloat()
    }

    /** Two photos of a child side by side, each with its age (and date, weight) underneath. */
    class Comparison(
        /** e.g. "سارة: من 4 أشهر إلى سنة" */
        val title: String,
        val older: Bitmap,
        /** e.g. "4 أشهر\n1 أكتوبر 2025\n6.4 كغ" */
        val olderLabel: String,
        val newer: Bitmap,
        val newerLabel: String,
        val footer: String,
    )

    fun renderComparison(content: Comparison): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val background = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, HEIGHT.toFloat(), 0xFFFFF4F5.toInt(), 0xFFFFD3DC.toInt(), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), background)
        drawMoons(canvas, centerY = 110f)

        var y = 190f
        y += drawText(canvas, content.title, size = 56f, color = INK, bold = true, width = WIDTH - MARGIN * 2, top = y, maxLines = 2) + 40f

        // Arabic reads from the right, so the older photo is on the right and the newer one on the left.
        val gap = 36f
        val panelWidth = (WIDTH - MARGIN * 2 - gap) / 2f
        val panelHeight = panelWidth * 4f / 3f
        val rightLeft = WIDTH - MARGIN - panelWidth
        drawPhoto(canvas, content.older, RectF(rightLeft, y, rightLeft + panelWidth, y + panelHeight))
        drawPhoto(canvas, content.newer, RectF(MARGIN.toFloat(), y, MARGIN + panelWidth, y + panelHeight))

        val labelTop = y + panelHeight + 24f
        drawText(canvas, content.olderLabel, 36f, BRAND, bold = true, width = panelWidth.toInt(), top = labelTop, maxLines = 3, left = rightLeft)
        drawText(canvas, content.newerLabel, 36f, BRAND, bold = true, width = panelWidth.toInt(), top = labelTop, maxLines = 3)

        drawText(canvas, content.footer, size = 32f, color = SOFT_INK, bold = false, width = WIDTH - MARGIN * 2, top = HEIGHT - 110f)
        return bitmap
    }

    /** Draws [photo] filling [dst] with rounded corners, cropped from the middle like the album does. */
    private fun drawPhoto(canvas: Canvas, photo: Bitmap, dst: RectF) {
        val dstRatio = dst.width() / dst.height()
        val srcRatio = photo.width.toFloat() / photo.height
        val src = if (srcRatio > dstRatio) {
            val w = (photo.height * dstRatio).toInt()
            val x = (photo.width - w) / 2
            Rect(x, 0, x + w, photo.height)
        } else {
            val h = (photo.width / dstRatio).toInt()
            val top = (photo.height - h) / 2
            Rect(0, top, photo.width, top + h)
        }
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(dst, 36f, 36f, Path.Direction.CW) })
        canvas.drawBitmap(photo, src, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        canvas.restore()
    }
}
