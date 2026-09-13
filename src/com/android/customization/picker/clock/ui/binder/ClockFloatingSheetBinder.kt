/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.customization.picker.clock.ui.binder

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.customization.picker.clock.model.ClockStyleModel
import com.android.customization.picker.clock.shared.ClockSize
import com.android.customization.picker.clock.shared.model.ClockMetadataModel
import com.android.customization.picker.clock.ui.viewmodel.ClockFloatingSheetHeightsViewModel
import com.android.customization.picker.clock.ui.viewmodel.ClockPickerViewModel.Tab
import com.android.customization.picker.color.ui.binder.ColorOptionIconBinder
import com.android.customization.picker.color.ui.view.ColorOptionIconView
import com.android.customization.picker.color.ui.viewmodel.ColorOptionIconViewModel
import com.android.customization.picker.common.ui.view.SingleRowListItemSpacing
import com.android.themepicker.R
import com.android.wallpaper.config.BaseFlags
import com.android.wallpaper.customization.ui.binder.FloatingSheetHeightAnimationBinder
import com.android.wallpaper.customization.ui.binder.SliderColorBinder
import com.android.wallpaper.customization.ui.binder.SwitchColorBinder
import com.android.wallpaper.customization.ui.util.ThemePickerCustomizationOptionUtil.ThemePickerLockCustomizationOption.CLOCK
import com.android.wallpaper.customization.ui.viewmodel.ThemePickerCustomizationOptionsViewModel
import com.android.wallpaper.picker.category.ui.binder.SectionsBinder.removeItemDecorations
import com.android.wallpaper.picker.customization.ui.binder.ColorUpdateBinder
import com.android.wallpaper.picker.customization.ui.view.FloatingToolbar
import com.android.wallpaper.picker.customization.ui.view.adapter.FloatingToolbarTabAdapter
import com.android.wallpaper.picker.customization.ui.viewmodel.ColorUpdateViewModel
import com.android.wallpaper.picker.option.ui.adapter.OptionItemAdapter2
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.LabelFormatter
import com.google.android.material.slider.Slider
import com.google.android.material.slider.Slider.OnSliderTouchListener
import java.lang.ref.WeakReference
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

object ClockFloatingSheetBinder {
    private const val SLIDER_ENABLED_ALPHA = 1f
    private const val SLIDER_DISABLED_ALPHA = .3f

    private val _clockFloatingSheetHeights: MutableStateFlow<ClockFloatingSheetHeightsViewModel> =
        MutableStateFlow(ClockFloatingSheetHeightsViewModel())
    private val clockFloatingSheetHeights: Flow<ClockFloatingSheetHeightsViewModel> =
        _clockFloatingSheetHeights.asStateFlow().filterNotNull()

    fun bind(
        view: View,
        optionsViewModel: ThemePickerCustomizationOptionsViewModel,
        colorUpdateViewModel: ColorUpdateViewModel,
        lifecycleOwner: LifecycleOwner,
    ) {
        val viewModel = optionsViewModel.clockPickerViewModel
        val appContext = view.context.applicationContext
        val isFloatingSheetActive = { optionsViewModel.selectedOption.value == CLOCK }

        val tabs: FloatingToolbar = view.requireViewById(R.id.floating_toolbar)
        val tabContainer =
            tabs.findViewById<ViewGroup>(com.android.wallpaper.R.id.floating_toolbar_tab_container)
        val isDesktopUi: Boolean = BaseFlags.get(appContext).shouldShowDesktopUi(view.context)
        ColorUpdateBinder.bind(
            setColor = { color ->
                DrawableCompat.setTint(DrawableCompat.wrap(tabContainer.background), color)
            },
            color = colorUpdateViewModel.floatingToolbarBackground,
            shouldAnimate = isFloatingSheetActive,
            lifecycleOwner = lifecycleOwner,
        )
        val tabAdapter =
            FloatingToolbarTabAdapter(
                    colorUpdateViewModel = WeakReference(colorUpdateViewModel),
                    shouldAnimateColor = isFloatingSheetActive,
                )
                .also { tabs.setAdapter(it) }

        val floatingSheetContainer: ViewGroup =
            view.requireViewById(R.id.floating_sheet_content_container)
        ColorUpdateBinder.bind(
            setColor = { color ->
                DrawableCompat.setTint(
                    DrawableCompat.wrap(floatingSheetContainer.background),
                    color,
                )
            },
            color = colorUpdateViewModel.colorSurfaceBright,
            shouldAnimate = isFloatingSheetActive,
            lifecycleOwner = lifecycleOwner,
        )

        // Clock style
        val clockStyleContent: View = view.requireViewById(R.id.clock_floating_sheet_style_content)
        val isClockStyleActive = {
            isFloatingSheetActive() && viewModel.selectedTab.value == Tab.STYLE
        }
        val clockStyleAdapter =
            createClockStyleOptionItemAdapter(
                colorUpdateViewModel = colorUpdateViewModel,
                shouldAnimateColor = isClockStyleActive,
                lifecycleOwner = lifecycleOwner,
            )
        val clockStyleList: RecyclerView = view.requireViewById(R.id.clock_style_list)
        clockStyleList.initStyleList(appContext, clockStyleAdapter)
        val clockStyleListContainer: View = view.requireViewById(R.id.clock_style_list_container)

        val axisPresetSliderContainer: View =
            clockStyleContent.requireViewById(R.id.clock_face_width_container)
        val axisPresetSlider =
            clockStyleContent.requireViewById<Slider>(R.id.clock_axis_preset_slider).also {
                SliderColorBinder.bind(
                    slider = it,
                    colorUpdateViewModel = colorUpdateViewModel,
                    shouldAnimateColor = isFloatingSheetActive,
                    lifecycleOwner = lifecycleOwner,
                )
            }

        val clockPresetGroupContainer: View =
            clockStyleContent.requireViewById(R.id.clock_preset_group_container)
        val clockPresetGroupToggle: MaterialButtonToggleGroup =
            clockStyleContent.requireViewById(R.id.clock_preset_group_toggle)
        val clockPresetGroupButtons =
            listOf<MaterialButton>(
                clockStyleContent.requireViewById(R.id.clock_preset_group_button_0),
                clockStyleContent.requireViewById(R.id.clock_preset_group_button_1),
            )

        clockPresetGroupButtons.forEach { button ->
            button.text =
                button.text.toString().replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                }
        }

        val sliderLabel = appContext.getString(R.string.clock_face_width)
        axisPresetSlider.contentDescription = sliderLabel

        axisPresetSlider.setLabelFormatter { value ->
            val mappedValueInt = Math.round(value + 1.0f)
            mappedValueInt.toString()
        }

        ColorUpdateBinder.bind(
            setColor = { color ->
                axisPresetSliderContainer
                    .requireViewById<TextView>(R.id.clock_face_width_label)
                    .setTextColor(color)
                clockPresetGroupContainer
                    .requireViewById<TextView>(R.id.clock_preset_group_label)
                    .setTextColor(color)
            },
            color = colorUpdateViewModel.colorOnSurfaceVariant,
            shouldAnimate = isClockStyleActive,
            lifecycleOwner = lifecycleOwner,
        )

        // Clock color
        val clockColorContent: View = view.requireViewById(R.id.clock_floating_sheet_color_content)
        val clockColorAdapter =
            createClockColorOptionItemAdapter(
                uiMode = view.resources.configuration.uiMode,
                colorUpdateViewModel = colorUpdateViewModel,
                shouldAnimateColor = isFloatingSheetActive,
                lifecycleOwner = lifecycleOwner,
            )
        val clockColorList: RecyclerView = view.requireViewById(R.id.clock_color_list)
        clockColorList.adapter = clockColorAdapter
        clockColorList.layoutManager =
            if (isDesktopUi) FlexboxLayoutManager(appContext, FlexDirection.ROW, FlexWrap.WRAP)
            else LinearLayoutManager(appContext, LinearLayoutManager.HORIZONTAL, false)

        val clockColorSlider =
            view.requireViewById<Slider>(R.id.clock_color_slider).also {
                SliderColorBinder.bind(
                    slider = it,
                    colorUpdateViewModel = colorUpdateViewModel,
                    shouldAnimateColor = isFloatingSheetActive,
                    lifecycleOwner = lifecycleOwner,
                )
            }

        clockColorSlider.apply {
            valueFrom = ClockMetadataModel.MIN_COLOR_TONE_PROGRESS.toFloat()
            valueTo = ClockMetadataModel.MAX_COLOR_TONE_PROGRESS.toFloat()
            labelBehavior = LabelFormatter.LABEL_GONE
            addOnChangeListener { slider, value, fromUser ->
                if (fromUser) {
                    viewModel.onSliderProgressChanged(value.roundToInt())
                }
                updateAccessibilityStateDescription(
                    slider = slider,
                    appContext = slider.context,
                    isEnabled = slider.isEnabled,
                    hasValueOffset = false,
                )
            }
            addOnSliderTouchListener(
                object : OnSliderTouchListener {
                    override fun onStartTrackingTouch(slider: Slider) {
                        // Do nothing intended
                    }

                    override fun onStopTrackingTouch(slider: Slider) {
                        viewModel.onSliderTouchUpProgressChanged(slider.value.roundToInt())
                    }
                }
            )
        }
        val isClockColorActive = {
            isFloatingSheetActive() && viewModel.selectedTab.value == Tab.COLOR
        }
        ColorUpdateBinder.bind(
            setColor = { color ->
                clockColorContent
                    .requireViewById<TextView>(R.id.clock_color_title)
                    .setTextColor(color)
            },
            color = colorUpdateViewModel.colorOnSurface,
            shouldAnimate = isClockColorActive,
            lifecycleOwner = lifecycleOwner,
        )
        ColorUpdateBinder.bind(
            setColor = { color ->
                clockColorContent
                    .requireViewById<TextView>(R.id.clock_color_description)
                    .setTextColor(color)
            },
            color = colorUpdateViewModel.colorOnSurfaceVariant,
            shouldAnimate = isClockColorActive,
            lifecycleOwner = lifecycleOwner,
        )

        // Clock size
        val clockSizeContent: View = view.requireViewById(R.id.clock_floating_sheet_size_content)
        val clockSizeDescription: TextView =
            clockSizeContent.requireViewById(R.id.clock_style_clock_size_description)
        if (isDesktopUi) {
            clockSizeDescription.isVisible = false
        }
        val clockSizeSwitch: MaterialSwitch =
            clockSizeContent.requireViewById(R.id.clock_style_clock_size_switch)
        ColorUpdateBinder.bind(
            setColor = { color ->
                clockSizeContent
                    .requireViewById<TextView>(R.id.clock_style_clock_size_title)
                    .setTextColor(color)
            },
            color = colorUpdateViewModel.colorOnSurface,
            shouldAnimate = isClockStyleActive,
            lifecycleOwner = lifecycleOwner,
        )

        ColorUpdateBinder.bind(
            setColor = { color ->
                clockSizeContent
                    .requireViewById<TextView>(R.id.clock_style_clock_size_description)
                    .setTextColor(color)
            },
            color = colorUpdateViewModel.colorOnSurfaceVariant,
            shouldAnimate = isClockStyleActive,
            lifecycleOwner = lifecycleOwner,
        )

        clockStyleContent.viewTreeObserver.addOnGlobalLayoutListener(
            object : OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (
                        clockStyleListContainer.height != 0 &&
                            axisPresetSliderContainer.height != 0 &&
                            (_clockFloatingSheetHeights.value.clockStyleContentHeight !=
                                clockStyleListContainer.height ||
                                _clockFloatingSheetHeights.value.axisPresetSliderHeight !=
                                    axisPresetSliderContainer.height)
                    ) {
                        _clockFloatingSheetHeights.value =
                            _clockFloatingSheetHeights.value.copy(
                                clockStyleContentHeight = clockStyleListContainer.height,
                                axisPresetSliderHeight = axisPresetSliderContainer.height,
                            )
                    }
                    clockStyleContent.viewTreeObserver.removeOnGlobalLayoutListener(this)
                }
            }
        )

        clockColorContent.viewTreeObserver.addOnGlobalLayoutListener(
            object : OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (
                        clockColorContent.height != 0 &&
                            _clockFloatingSheetHeights.value.clockColorContentHeight !=
                                clockColorContent.height
                    ) {
                        _clockFloatingSheetHeights.value =
                            _clockFloatingSheetHeights.value.copy(
                                clockColorContentHeight = clockColorContent.height
                            )
                        clockColorContent.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    }
                }
            }
        )

        clockSizeContent.viewTreeObserver.addOnGlobalLayoutListener(
            object : OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (
                        clockSizeContent.height != 0 &&
                            _clockFloatingSheetHeights.value.clockSizeContentHeight !=
                                clockSizeContent.height
                    ) {
                        _clockFloatingSheetHeights.value =
                            _clockFloatingSheetHeights.value.copy(
                                clockSizeContentHeight = clockSizeContent.height
                            )
                    }
                    clockSizeContent.viewTreeObserver.removeOnGlobalLayoutListener(this)
                }
            }
        )

        val clockStyleContentVerticalPadding =
            view.resources.getDimensionPixelSize(R.dimen.floating_sheet_content_vertical_padding)
        val clockStyleContentSliderMargin =
            view.resources.getDimensionPixelSize(
                R.dimen.clock_axis_control_slider_row_margin_vertical
            )
        lifecycleOwner.lifecycleScope.launch {
            var currentTab: Tab? = null
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.tabs.collect { tabAdapter.submitList(it) } }

                launch {
                    combine(
                            clockFloatingSheetHeights,
                            viewModel.selectedTab,
                            viewModel.shouldShowPresetSlider,
                            ::Triple,
                        )
                        .collect { (heights, selectedTab, shouldShowPresetSlider) ->
                            val (
                                clockStyleContentHeight,
                                clockColorContentHeight,
                                clockSizeContentHeight,
                                axisPresetSliderHeight) =
                                heights
                            clockStyleContentHeight ?: return@collect
                            clockColorContentHeight ?: return@collect
                            clockSizeContentHeight ?: return@collect
                            axisPresetSliderHeight ?: return@collect

                            clockStyleContent.isVisible = currentTab == Tab.STYLE
                            clockColorContent.isVisible = currentTab == Tab.COLOR
                            clockSizeContent.isVisible = currentTab == Tab.SIZE

                            val fromHeight = floatingSheetContainer.height
                            val toHeight =
                                when (selectedTab) {
                                    Tab.STYLE ->
                                        if (shouldShowPresetSlider)
                                            clockStyleContentHeight +
                                                axisPresetSliderHeight +
                                                2 * clockStyleContentVerticalPadding +
                                                clockStyleContentSliderMargin
                                        else
                                            clockStyleContentHeight +
                                                2 * clockStyleContentVerticalPadding
                                    Tab.COLOR -> clockColorContentHeight
                                    Tab.SIZE -> clockSizeContentHeight
                                }
                            val currentContent: View? =
                                when (currentTab) {
                                    Tab.STYLE -> clockStyleContent
                                    Tab.COLOR -> clockColorContent
                                    Tab.SIZE -> clockSizeContent
                                    else -> null
                                }
                            val selectedContent: View =
                                when (selectedTab) {
                                    Tab.STYLE -> clockStyleContent
                                    Tab.COLOR -> clockColorContent
                                    Tab.SIZE -> clockSizeContent
                                }
                            val fromContent =
                                if (currentTab != selectedTab) currentContent else null
                            val toContent = if (currentTab != selectedTab) selectedContent else null
                            FloatingSheetHeightAnimationBinder.bind(
                                floatingSheetContainer,
                                fromHeight,
                                toHeight,
                                fromContent,
                                toContent,
                            )
                            currentTab = selectedTab
                        }
                }

                launch {
                    viewModel.shouldShowPresetSlider.collect {
                        axisPresetSliderContainer.isVisible = it
                    }
                }

                launch {
                    colorUpdateViewModel.systemColorsUpdated.collect {
                        clockPresetGroupButtons.forEach {
                            it.backgroundTintList =
                                appContext.getColorStateList(
                                    R.color.clock_style_button_background
                                )
                            it.setTextColor(
                                appContext.getColorStateList(R.color.clock_style_button_label)
                            )
                        }
                    }
                }

                launch {
                    combine(
                            viewModel.clockPresetGroupCount,
                            viewModel.previewingClockPresetGroupIndex,
                            viewModel.onClockFaceClicked,
                            ::Triple,
                        )
                        .collect { (groupCount, groupIndex, onClockFaceClicked) ->
                            // The toggle cycles, so every group needs a button of its own.
                            clockPresetGroupContainer.isVisible =
                                groupCount == clockPresetGroupButtons.size
                            if (!clockPresetGroupContainer.isVisible) return@collect

                            clockPresetGroupButtons.forEachIndexed { index, button ->
                                button.setOnClickListener {
                                    if (index != groupIndex) onClockFaceClicked?.invoke()
                                }
                            }
                            clockPresetGroupToggle.check(clockPresetGroupButtons[groupIndex].id)
                        }
                }

                launch {
                    viewModel.clockStyleOptions.collect { styleOptions ->
                        clockStyleAdapter.setItems(styleOptions) {
                            val dividerIndex =
                                styleOptions.indexOfLast { it.payload?.hasPresets ?: false }
                            var indexToFocus = styleOptions.indexOfFirst { it.isSelected.value }
                            indexToFocus = if (indexToFocus < 0) 0 else indexToFocus
                            (clockStyleList.layoutManager as LinearLayoutManager)
                                .scrollToPositionWithOffset(indexToFocus, 0)
                            clockStyleList.removeItemDecorations()
                            clockStyleList.addItemDecoration(
                                SingleRowListItemSpacing(
                                    view.context.resources.getDimensionPixelSize(
                                        R.dimen.floating_sheet_content_horizontal_padding
                                    ),
                                    view.context.resources.getDimensionPixelSize(
                                        R.dimen.floating_sheet_list_item_horizontal_space
                                    ),
                                    dividerIndex = dividerIndex,
                                    dividerDrawable =
                                        ContextCompat.getDrawable(
                                            view.context,
                                            R.drawable.option_list_grouping_divider,
                                        ),
                                )
                            )
                        }
                    }
                }

                launch {
                    viewModel.clockColorOptions.collect { colorOptions ->
                        clockColorAdapter.setItems(colorOptions) {
                            var indexToFocus = colorOptions.indexOfFirst { it.isSelected.value }
                            indexToFocus = if (indexToFocus < 0) 0 else indexToFocus
                            if (isDesktopUi) {
                                (clockColorList.layoutManager as FlexboxLayoutManager)
                                    .scrollToPosition(indexToFocus)
                            } else {
                                (clockColorList.layoutManager as LinearLayoutManager)
                                    .scrollToPositionWithOffset(indexToFocus, 0)
                            }
                        }
                    }
                }

                launch {
                    viewModel.previewingColorSliderProgress.collect { progress ->
                        clockColorSlider.value = progress.toFloat()
                    }
                }

                launch {
                    viewModel.isSliderEnabled.collect { isEnabled ->
                        clockColorSlider.isEnabled = isEnabled
                        clockColorSlider.alpha =
                            if (isEnabled) SLIDER_ENABLED_ALPHA else SLIDER_DISABLED_ALPHA
                        updateAccessibilityStateDescription(
                            slider = clockColorSlider,
                            appContext = clockColorSlider.context,
                            isEnabled = isEnabled,
                            hasValueOffset = false,
                        )
                    }
                }

                launch {
                    viewModel.previewingClockColorOptionIndex.collect { indexToFocus ->
                        clockColorList.post {
                            val layoutManager =
                                if (isDesktopUi)
                                    clockColorList.layoutManager as? FlexboxLayoutManager
                                        ?: return@post
                                else
                                    clockColorList.layoutManager as? LinearLayoutManager
                                        ?: return@post
                            val itemView = layoutManager.findViewByPosition(indexToFocus)

                            if (itemView != null) {
                                val parentCenter = clockColorList.width / 2
                                val itemCenter = itemView.left + itemView.width / 2 + itemView.width
                                val scrollBy = itemCenter - parentCenter
                                clockColorList.smoothScrollBy(scrollBy, 0)
                            }
                        }
                    }
                }

                launch {
                    viewModel.previewingClockStyleOptionIndex.collect { indexToFocus ->
                        clockStyleList.post {
                            val layoutManager =
                                clockStyleList.layoutManager as? LinearLayoutManager ?: return@post
                            val itemView = layoutManager.findViewByPosition(indexToFocus)

                            if (itemView != null) {
                                val parentCenter = clockStyleList.width / 2
                                val itemCenter = itemView.left + itemView.width / 2
                                val scrollBy = itemCenter - parentCenter
                                clockStyleList.smoothScrollBy(scrollBy, 0)
                            }
                        }
                    }
                }

                launch {
                    var binding: SwitchColorBinder.Binding? = null
                    viewModel.previewingClockSize.collect { size ->
                        when (size) {
                            ClockSize.DYNAMIC -> clockSizeSwitch.isChecked = true
                            ClockSize.SMALL -> clockSizeSwitch.isChecked = false
                        }
                        binding?.destroy()
                        binding =
                            SwitchColorBinder.bind(
                                switch = clockSizeSwitch,
                                isChecked =
                                    when (size) {
                                        ClockSize.DYNAMIC -> true
                                        ClockSize.SMALL -> false
                                    },
                                colorUpdateViewModel = colorUpdateViewModel,
                                shouldAnimateColor = isClockStyleActive,
                                lifecycleOwner = lifecycleOwner,
                            )
                    }
                }

                launch {
                    viewModel.onClockSizeSwitchCheckedChange.collect { onCheckedChange ->
                        clockSizeSwitch.setOnCheckedChangeListener { _, _ ->
                            onCheckedChange.invoke()
                        }
                    }
                }

                launch {
                    viewModel.axisPresetsSliderViewModel.collect {
                        val axisPresetsSliderViewModel = it ?: return@collect
                        axisPresetSlider.valueFrom = axisPresetsSliderViewModel.valueFrom
                        axisPresetSlider.valueTo = axisPresetsSliderViewModel.valueTo
                        axisPresetSlider.stepSize = axisPresetsSliderViewModel.stepSize
                        axisPresetSlider.clearOnSliderTouchListeners()
                        updateAccessibilityStateDescription(
                            slider = axisPresetSlider,
                            appContext = axisPresetSlider.context,
                            isEnabled = true,
                            hasValueOffset = true,
                        )
                        axisPresetSlider.addOnSliderTouchListener(
                            object : OnSliderTouchListener {
                                override fun onStartTrackingTouch(slider: Slider) {}

                                override fun onStopTrackingTouch(slider: Slider) {
                                    if (!optionsViewModel.isAccessibilityEnabled(slider.context)) {
                                        axisPresetsSliderViewModel.onSliderStopTrackingTouch(
                                            slider.value
                                        )
                                    }
                                }
                            }
                        )
                        axisPresetSlider.clearOnChangeListeners()
                        axisPresetSlider.addOnChangeListener { slider, value, fromUser ->
                            updateAccessibilityStateDescription(
                                slider = slider,
                                appContext = slider.context,
                                isEnabled = true,
                                hasValueOffset = true,
                            )

                            if (optionsViewModel.isAccessibilityEnabled(slider.context)) {
                                axisPresetsSliderViewModel.onSliderStopTrackingTouch(value)
                            }
                            updateAccessibilityStateDescription(
                                slider = axisPresetSlider,
                                appContext = slider.context,
                                isEnabled = true,
                                hasValueOffset = true,
                            )
                        }
                    }
                }

                launch {
                    viewModel.axisPresetsSliderSelectedValue.collect { axisPresetSlider.value = it }
                }
            }
        }
    }

    private fun updateAccessibilityStateDescription(
        slider: Slider,
        appContext: Context,
        isEnabled: Boolean,
        hasValueOffset: Boolean,
    ) {
        val offset = if (hasValueOffset) 1f else 0f
        val currentValueInt = Math.round(slider.value + offset)
        val minInt = (slider.valueFrom + offset).toInt()
        val maxInt = (slider.valueTo + offset).toInt()

        val stateDescription =
            if (isEnabled) {
                R.string.enabled_slider_state_description_template
            } else {
                R.string.disabled_slider_state_description_template
            }

        slider.stateDescription =
            appContext.getString(stateDescription, currentValueInt, minInt, maxInt)
    }

    private fun createClockStyleOptionItemAdapter(
        colorUpdateViewModel: ColorUpdateViewModel,
        shouldAnimateColor: () -> Boolean,
        lifecycleOwner: LifecycleOwner,
    ): OptionItemAdapter2<ClockStyleModel> =
        OptionItemAdapter2(
            layoutResourceId = R.layout.clock_style_option,
            lifecycleOwner = lifecycleOwner,
            bindPayload = { view: View, styleModel: ClockStyleModel ->
                view
                    .findViewById<ImageView>(R.id.foreground)
                    ?.setImageDrawable(styleModel.thumbnail)
                return@OptionItemAdapter2 null
            },
            colorUpdateViewModel = WeakReference(colorUpdateViewModel),
            shouldAnimateColor = shouldAnimateColor,
        )

    private fun RecyclerView.initStyleList(
        context: Context,
        adapter: OptionItemAdapter2<ClockStyleModel>,
    ) {
        this.adapter = adapter
        layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
    }

    private fun createClockColorOptionItemAdapter(
        uiMode: Int,
        colorUpdateViewModel: ColorUpdateViewModel,
        shouldAnimateColor: () -> Boolean,
        lifecycleOwner: LifecycleOwner,
    ): OptionItemAdapter2<ColorOptionIconViewModel> =
        OptionItemAdapter2(
            layoutResourceId = R.layout.color_option,
            lifecycleOwner = lifecycleOwner,
            bindPayload = { itemView: View, colorIcon: ColorOptionIconViewModel ->
                val colorOptionIconView: ColorOptionIconView =
                    itemView.requireViewById(com.android.wallpaper.R.id.background)
                val binding =
                    ColorOptionIconBinder.bind(
                        view = colorOptionIconView,
                        viewModel = colorIcon,
                        colorUpdateViewModel = colorUpdateViewModel,
                        shouldAnimateColor = shouldAnimateColor,
                        lifecycleOwner = lifecycleOwner,
                    )
                return@OptionItemAdapter2 DisposableHandle { binding.destroy() }
            },
            colorUpdateViewModel = WeakReference(colorUpdateViewModel),
            shouldAnimateColor = shouldAnimateColor,
        )
}
