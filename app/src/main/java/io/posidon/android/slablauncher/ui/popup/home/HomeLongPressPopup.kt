package io.posidon.android.slablauncher.ui.popup.home

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.posidon.android.slablauncher.R
import io.posidon.android.slablauncher.providers.color.pallete.ColorPalette
import io.posidon.android.slablauncher.providers.color.theme.ColorTheme
import io.posidon.android.slablauncher.ui.home.main.acrylicBlur
import io.posidon.android.slablauncher.ui.popup.PopupUtils
import io.posidon.android.slablauncher.ui.popup.listPopup.ListPopupAdapter
import io.posidon.android.slablauncher.ui.popup.listPopup.ListPopupItem
import io.posidon.android.slablauncher.ui.settings.iconPackPicker.IconPackPickerActivity
import io.posidon.android.slablauncher.util.storage.ColorExtractorSetting.colorTheme
import io.posidon.android.slablauncher.util.storage.ColorThemeSetting.colorThemeDayNight
import io.posidon.android.slablauncher.util.storage.ColorThemeSetting.setColorThemeDayNight
import io.posidon.android.slablauncher.util.storage.DoBlurSetting.doBlur
import io.posidon.android.slablauncher.util.storage.DoShowKeyboardOnAllAppsScreenOpenedSetting.doAutoKeyboardInAllApps
import io.posidon.android.slablauncher.util.storage.DoSuggestionStripSetting.doSuggestionStrip
import io.posidon.android.slablauncher.util.storage.Settings
import io.posidon.android.slablauncher.ui.view.SeeThroughView
import io.posidon.android.slablauncher.util.storage.DoMonochromeIconsSetting.monochromatism
import io.posidon.android.slablauncher.util.storage.DockRowCount.dockRowCount
import io.posidon.android.conveniencelib.Device
import io.posidon.android.conveniencelib.units.dp
import io.posidon.android.conveniencelib.units.toPixels
import io.posidon.android.slablauncher.BuildConfig
import io.posidon.android.slablauncher.util.storage.ColumnCount.dockColumnCount
import io.posidon.android.slablauncher.util.storage.DoAlignMediaPlayerToTop.alignMediaPlayerToTop
import io.posidon.android.slablauncher.util.storage.GreetingSetting.getDefaultGreeting
import io.posidon.android.slablauncher.util.storage.GreetingSetting.setDefaultGreeting
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock
import kotlin.math.min

class HomeLongPressPopup(
    private val update: HomeLongPressPopup.() -> Unit
) {

    private inline fun update() = update(this)

    companion object {

        fun calculateHeight(context: Context) = min(
            Device.screenHeight(context) / 2,
            360.dp.toPixels(context)
        )

        fun show(
            parent: View,
            touchX: Float,
            touchY: Float,
            settings: Settings,
            reloadColorPalette: () -> Unit,
            updateColorTheme: (ColorPalette) -> Unit,
            reloadItemGraphics: () -> Unit,
            reloadBlur: (() -> Unit) -> Unit,
            updateLayout: () -> Unit,
            updateGreeting: () -> Unit,
            popupWidth: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
            popupHeight: Int = calculateHeight(parent.context),
        ) {
            val content = LayoutInflater.from(parent.context).inflate(R.layout.list_popup, null)
            val window = PopupWindow(
                content,
                popupWidth,
                popupHeight,
                true
            )
            PopupUtils.setCurrent(window)

            val blurBG = content.findViewById<SeeThroughView>(R.id.blur_bg)

            val cardView = content.findViewById<CardView>(R.id.card)
            val recycler = content.findViewById<RecyclerView>(R.id.recycler)
            val popupAdapter = ListPopupAdapter()
            val updateLock = ReentrantLock()

            val popup = HomeLongPressPopup {
                blurBG.drawable = acrylicBlur?.smoothBlurDrawable
                cardView.setCardBackgroundColor(ColorTheme.cardBG)
                recycler.backgroundTintList = ColorStateList.valueOf(ColorTheme.separator)
                popupAdapter.updateItems(
                    createMainAdapter(
                        parent.context, settings,
                        reloadColorPalette = {
                            thread(name = "Reloading color palette", isDaemon = true) {
                                updateLock.withLock {
                                    reloadColorPalette()
                                    cardView.post { update() }
                                }
                            }
                        },
                        updateColorTheme = {
                            updateColorTheme(ColorPalette.getCurrent())
                            cardView.post { update() }
                        },
                        reloadItemGraphics = reloadItemGraphics,
                        reloadBlur = {
                            reloadBlur {
                                cardView.post { update() }
                            }
                        },
                        updateLayout = {
                            parent.post(updateLayout)
                        },
                        updateGreeting = {
                            parent.post(updateGreeting)
                        },
                    )
                )
            }

            content.findViewById<RecyclerView>(R.id.recycler).apply {
                layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
                adapter = popupAdapter
            }

            popup.update()

            val gravity = Gravity.CENTER
            val x = touchX.toInt() - Device.screenWidth(parent.context) / 2
            val y = touchY.toInt() - Device.screenHeight(parent.context) / 2
            window.showAtLocation(parent, gravity, x, y)
        }

        fun updateCurrent() {
            current?.update()
        }

        private var current: HomeLongPressPopup? = null

        private fun createMainAdapter(
            context: Context,
            settings: Settings,
            reloadColorPalette: () -> Unit,
            updateColorTheme: () -> Unit,
            reloadItemGraphics: () -> Unit,
            reloadBlur: () -> Unit,
            updateLayout: () -> Unit,
            updateGreeting: () -> Unit,
        ): List<ListPopupItem<*>> {
            return listOf(
                ListPopupItem(
                    context.getString(R.string.app_name),
                    BuildConfig.VERSION_NAME,
                    icon = context.getDrawable(R.mipmap.ic_launcher),
                    isTitle = true,
                ),
                ListPopupItem(context.getString(R.string.general), isTitle = true),
                ListPopupItem(
                    context.getString(R.string.color_theme_gen),
                    description = context.resources.getStringArray(R.array.color_theme_gens)[settings.colorTheme],
                    icon = ContextCompat.getDrawable(context, R.drawable.ic_color_dropper),
                ) {
                    AlertDialog.Builder(context)
                        .setSingleChoiceItems(
                            context.resources.getStringArray(R.array.color_theme_gens).copyOf(context.resources.getInteger(R.integer.color_theme_gens_available)),
                            settings.colorTheme
                        ) { d, i ->
                            settings.edit(context) {
                                colorTheme =
                                    context.resources.getStringArray(R.array.color_theme_gens_data)[i].toInt()
                                reloadColorPalette()
                            }
                            d.dismiss()
                        }
                        .show()
                },
                ListPopupItem(
                    context.getString(R.string.color_theme_day_night),
                    description = context.resources.getStringArray(R.array.color_theme_day_night)[settings.colorThemeDayNight.ordinal],
                    icon = ContextCompat.getDrawable(context, R.drawable.ic_lightness),
                ) {
                    AlertDialog.Builder(context)
                        .setSingleChoiceItems(
                            R.array.color_theme_day_night,
                            settings.colorThemeDayNight.ordinal
                        ) { d, i ->
                            settings.edit(context) {
                                setColorThemeDayNight(context.resources.getStringArray(R.array.color_theme_day_night_data)[i].toInt())
                                updateColorTheme()
                            }
                            d.dismiss()
                        }
                        .show()
                },
                ListPopupItem(
                    context.getString(R.string.greeting),
                    icon = ContextCompat.getDrawable(context, R.drawable.ic_home),
                    value = settings.getDefaultGreeting(context),
                    onValueChange = { _, value ->
                        settings.edit(context) {
                            setDefaultGreeting(value)
                            updateGreeting()
                        }
                    }
                ),
                ListPopupItem(
                    context.getString(R.string.blur),
                    icon = ContextCompat.getDrawable(context, R.drawable.ic_shapes),
                    value = settings.doBlur,
                    states = 2,
                    onValueChange = { _, value ->
                        settings.edit(context) {
                            doBlur = value
                            reloadBlur()
                        }
                    }
                ),
                ListPopupItem(context.getString(R.string.layout), isTitle = true),
                ListPopupItem(
                    context.getString(R.string.columns),
                    icon = ContextCompat.getDrawable(context, R.drawable.ic_home),
                    value = settings.dockColumnCount - 2,
                    states = 5,
                    onValueChange = { _, value ->
                        settings.edit(context) {
                            dockColumnCount = value + 2
                            updateLayout()
                        }
                    }
                ),
                ListPopupItem(
                    context.getString(R.string.dock_row_count),
                    icon = ContextCompat.getDrawable(context, R.drawable.ic_home),
                    value = settings.dockRowCount,
                    states = 5,
                    onValueChange = { _, value ->
                        settings.edit(context) {
                            dockRowCount = value
                            updateLayout()
                        }
                    }
                ),
                ListPopupItem(
                    context.getString(R.string.show_app_suggestions),
                    icon = ContextCompat.getDrawable(context, R.drawable.ic_visible),
                    value = settings.doSuggestionStrip,
                    states = 2,
                    onValueChange = { _, value ->
                        settings.edit(context) {
                            doSuggestionStrip = value
                            updateLayout()
                        }
                    }
                ),
                ListPopupItem(
                    context.getString(R.string.align_media_player_top_top),
                    icon = ContextCompat.getDrawable(context, R.drawable.ic_play),
                    value = settings.alignMediaPlayerToTop,
                    states = 2,
                    onValueChange = { _, value ->
                        settings.edit(context) {
                            alignMediaPlayerToTop = value
                            updateLayout()
                        }
                    }
                ),
                ListPopupItem(context.getString(R.string.tiles), isTitle = true),
                ListPopupItem(
                    context.getString(R.string.icon_packs),
                    icon = ContextCompat.getDrawable(context, R.drawable.ic_shapes),
                ) {
                    context.startActivity(Intent(context, IconPackPickerActivity::class.java))
                },
                ListPopupItem(
                    context.getString(R.string.monochrome_icons),
                    icon = ContextCompat.getDrawable(context, R.drawable.ic_color_dropper),
                    value = settings.monochromatism,
                    states = 2,
                    onValueChange = { _, value ->
                        settings.edit(context) {
                            monochromatism = value
                            reloadItemGraphics()
                        }
                    }
                ),
                ListPopupItem(context.getString(R.string.all_apps), isTitle = true),
                ListPopupItem(
                    context.getString(R.string.auto_show_keyboard),
                    description = context.getString(R.string.auto_show_keyboard_explanation),
                    icon = ContextCompat.getDrawable(context, R.drawable.ic_keyboard),
                    value = settings.doAutoKeyboardInAllApps,
                    states = 2,
                    onValueChange = { _, value ->
                        settings.edit(context) {
                            doAutoKeyboardInAllApps = value
                        }
                    }
                ),
            )
        }
    }
}