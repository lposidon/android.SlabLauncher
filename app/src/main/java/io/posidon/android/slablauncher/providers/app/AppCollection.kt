package io.posidon.android.slablauncher.providers.app

import android.content.Context
import android.graphics.*
import android.graphics.drawable.*
import android.os.UserHandle
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.alpha
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.toXfermode
import androidx.palette.graphics.Palette
import io.posidon.android.launcherutils.appLoading.AppLoader
import io.posidon.android.slablauncher.data.items.App
import io.posidon.android.slablauncher.util.drawable.FastBitmapDrawable
import io.posidon.android.slablauncher.util.drawable.FastColorDrawable
import io.posidon.android.slablauncher.util.storage.DoReshapeAdaptiveIconsSetting.doReshapeAdaptiveIcons
import io.posidon.android.slablauncher.util.storage.Settings
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap


class AppCollection(
    appCount: Int,
    val settings: Settings
) : AppLoader.AppCollection<AppCollection.ExtraIconData> {
    val list = ArrayList<App>(appCount)
    val byName = HashMap<String, MutableList<App>>()

    inline operator fun get(i: Int) = list[i]
    inline val size get() = list.size

    private val tmpHSL = FloatArray(3)

    override fun addApp(
        context: Context,
        packageName: String,
        name: String,
        profile: UserHandle,
        label: String,
        icon: Drawable,
        extra: AppLoader.ExtraAppInfo<ExtraIconData>,
    ) {
        val extraIconData = extra.extraIconData
        if (!extra.isUserRunning) {
            icon.convertToGrayscale()
            extraIconData.color = run {
                val a = extraIconData.color
                ColorUtils.colorToHSL(a, tmpHSL)
                tmpHSL[1] = 0f
                ColorUtils.HSLToColor(tmpHSL)
            }
            val b = extraIconData.background
            if (b is FastColorDrawable) {
                extraIconData.background = FastColorDrawable(extraIconData.color)
            } else b?.convertToGrayscale()
        }

        val app = createApp(
            packageName,
            name,
            profile,
            label,
            icon,
            extraIconData,
            settings
        )

        list.add(app)
        putInMap(app)
    }

    override fun modifyIcon(
        icon: Drawable,
        packageName: String,
        name: String,
        profile: UserHandle,
        expandableBackground: Drawable?
    ): Pair<Drawable, ExtraIconData> {
        var color = 0
        var icon = icon
        var background: Drawable? = null

        if (expandableBackground != null) {
            background = expandableBackground
            val palette = Palette.from(background.toBitmap(8, 8)).generate()
            val d = palette.dominantSwatch
            color = d?.rgb ?: color
        }
        else if (
            settings.doReshapeAdaptiveIcons &&
            icon is AdaptiveIconDrawable &&
            icon.background != null &&
            icon.foreground != null
        ) {
            val (i, b, c) = reshapeAdaptiveIcon(icon)
            icon = i
            background = b ?: background
            color = c
        }

        if (color == 0) {
            val palette = Palette.from(icon.toBitmap(32, 32)).generate()
            val d = palette.dominantSwatch
            color = run {
                val c = d?.rgb ?: return@run color
                if (d.hsl[1] < .1f) {
                    palette.getVibrantColor(c)
                } else c
            }
        }

        color = color and 0xffffff or 0xff000000.toInt()

        return icon.let {
            if (it is BitmapDrawable) FastBitmapDrawable(it.bitmap)
            else it
        } to ExtraIconData(
            background, color
        )
    }

    private fun putInMap(app: App) {
        val list = byName[app.packageName]
        if (list == null) {
            byName[app.packageName] = arrayListOf(app)
            return
        }
        val thisAppI = list.indexOfFirst {
            it.name == app.name && it.userHandle.hashCode() == app.userHandle.hashCode()
        }
        if (thisAppI == -1) {
            list.add(app)
            return
        }
        list[thisAppI] = app
    }

    override fun finalize(context: Context) {
        list.sortWith { o1, o2 ->
            o1.label.compareTo(o2.label, ignoreCase = true)
        }
    }

    companion object {

        fun Drawable.convertToGrayscale() {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
                setSaturation(0f)
            })
        }

        fun createApp(
            packageName: String,
            name: String,
            profile: UserHandle,
            label: String,
            icon: Drawable,
            extra: ExtraIconData,
            settings: Settings,
        ): App {

            return App(
                packageName,
                name,
                profile,
                label,
                icon,
                extra.background,
                extra.color
            )
        }

        private fun scale(fg: Drawable): Drawable {
            return InsetDrawable(
                fg,
                -1 / 3f
            )
        }

        /**
         * @return (icon, expandable background, color)
         */
        private fun reshapeAdaptiveIcon(icon: AdaptiveIconDrawable): Triple<Drawable, Drawable?, Int> {
            var color = 0
            val b = icon.background
            val isForegroundDangerous = run {
                val fg = icon.foreground.toBitmap(32, 32)
                val width = fg.width
                val height = fg.height
                val canvas = Canvas(fg)
                canvas.drawRect(6f, 6f, width - 6f, height - 6f, Paint().apply {
                    xfermode = PorterDuff.Mode.CLEAR.toXfermode()
                })
                val pixels = IntArray(width * height)
                fg.getPixels(pixels, 0, width, 0, 0, width, height)
                for (pixel in pixels) {
                    if (pixel.alpha != 0) {
                        return@run true
                    }
                }
                false
            }
            val (foreground, background) = when (b) {
                is ColorDrawable -> {
                    color = b.color
                    (if (isForegroundDangerous) icon else scale(icon.foreground)) to FastColorDrawable(color)
                }
                is ShapeDrawable -> {
                    color = b.paint.color
                    (if (isForegroundDangerous) icon else scale(icon.foreground)) to FastColorDrawable(color)
                }
                is GradientDrawable -> {
                    color = b.color?.defaultColor ?: Palette.from(b.toBitmap(8, 8)).generate().getDominantColor(0)
                    (if (isForegroundDangerous) icon else scale(icon.foreground)) to FastColorDrawable(color)
                }
                else -> if (b != null) {
                    val bitmap = b.toBitmap(32, 32)
                    val px = b.toBitmap(1, 1).getPixel(0, 0)
                    val width = bitmap.width
                    val height = bitmap.height
                    val pixels = IntArray(width * height)
                    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                    var isOneColor = true
                    for (pixel in pixels) {
                        if (pixel != px) {
                            isOneColor = false
                            break
                        }
                    }
                    if (isOneColor) {
                        color = px
                        (if (isForegroundDangerous) icon else scale(icon.foreground)) to FastColorDrawable(color)
                    } else {
                        val palette = Palette.from(bitmap).generate()
                        color = palette.vibrantSwatch?.rgb ?: palette.dominantSwatch?.rgb ?: 0
                        icon to null
                    }
                } else icon to null
            }

            return Triple(foreground, background, color)
        }
    }

    class ExtraIconData(
        var background: Drawable?,
        var color: Int,
    )
}