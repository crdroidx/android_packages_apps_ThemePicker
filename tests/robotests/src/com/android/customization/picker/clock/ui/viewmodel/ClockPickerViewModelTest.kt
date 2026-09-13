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

package com.android.customization.picker.clock.ui.viewmodel

import android.content.Context
import android.stats.style.StyleEnums
import androidx.core.graphics.ColorUtils
import androidx.test.filters.SmallTest
import com.android.customization.module.logging.TestThemesUserEventLogger
import com.android.customization.picker.clock.ai.DefaultClockStyleViewUtil
import com.android.customization.picker.clock.data.repository.FakeClockPickerRepository
import com.android.customization.picker.clock.domain.interactor.ClockPickerInteractor
import com.android.customization.picker.clock.shared.ClockSize
import com.android.customization.picker.clock.shared.model.ClockMetadataModel
import com.android.customization.picker.clock.ui.viewmodel.ClockPickerViewModel.Tab
import com.android.customization.picker.color.data.repository.FakeColorPickerRepository
import com.android.customization.picker.color.domain.interactor.ColorPickerInteractor
import com.android.systemui.plugins.keyguard.ui.clocks.AxisPresetConfig.IndexedStyle
import com.android.systemui.shared.customization.data.content.FakeCustomizationProviderClient
import com.android.themepicker.R
import com.android.wallpaper.config.BaseFlags
import com.android.wallpaper.picker.common.icon.ui.viewmodel.Icon
import com.android.wallpaper.picker.common.text.ui.viewmodel.Text
import com.android.wallpaper.picker.customization.data.repository.CustomizationRuntimeValuesRepository
import com.android.wallpaper.picker.customization.ui.viewmodel.FloatingToolbarTabViewModel
import com.android.wallpaper.testing.collectLastValue
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@HiltAndroidTest
@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(RobolectricTestRunner::class)
class ClockPickerViewModelTest {

    private val logger = TestThemesUserEventLogger()

    @get:Rule var hiltRule = HiltAndroidRule(this)
    @Inject @ApplicationContext lateinit var context: Context
    @Inject lateinit var testDispatcher: TestDispatcher
    @Inject lateinit var testScope: TestScope

    private lateinit var colorMap: Map<String, ClockColorViewModel>
    private lateinit var underTest: ClockPickerViewModel

    @Before
    fun setUp() {
        hiltRule.inject()
        Dispatchers.setMain(testDispatcher)
        val repository = FakeClockPickerRepository()
        val customizationProviderClient = FakeCustomizationProviderClient()
        val clockPickerInteractor =
            ClockPickerInteractor(
                repository = repository,
                CustomizationRuntimeValuesRepository(customizationProviderClient),
            )
        val colorPickerRepository = FakeColorPickerRepository(baseFlags = BaseFlags.get(context))
        val colorPickerInteractor = ColorPickerInteractor(repository = colorPickerRepository)
        val clockStyleViewUtil = DefaultClockStyleViewUtil()
        colorMap = ClockColorViewModel.getPresetColorMap(context.resources)
        underTest =
            ClockPickerViewModel(
                context = context,
                resources = context.resources,
                clockPickerInteractor = clockPickerInteractor,
                colorPickerInteractor = colorPickerInteractor,
                logger = logger,
                backgroundDispatcher = testDispatcher,
                viewModelScope = testScope,
                clockStyleViewUtil = clockStyleViewUtil,
            )

        testScope.launch {
            clockPickerInteractor.setSelectedClock(FakeClockPickerRepository.CLOCK_ID_0)
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    //// Tabs
    @Test
    fun tabs_whenInitialState() = runTest {
        val tabs = collectLastValue(underTest.tabs)

        val resultTabs = checkNotNull(tabs())
        assertThat(resultTabs).hasSize(3)
        resultTabs.forEachIndexed { index, floatingToolbarTabViewModel ->
            when (index) {
                0 -> {
                    assertFloatingToolbarTabViewModel(
                        viewModel = floatingToolbarTabViewModel,
                        icon =
                            Icon.Resource(
                                res = R.drawable.ic_clock_filled_24px,
                                contentDescription = Text.Resource(R.string.clock_style),
                            ),
                        text = context.getString(R.string.clock_style),
                        isSelected = true,
                        isOnClickNull = true,
                    )
                }
                1 -> {
                    assertFloatingToolbarTabViewModel(
                        viewModel = floatingToolbarTabViewModel,
                        icon =
                            Icon.Resource(
                                res = R.drawable.ic_palette_filled_24px,
                                contentDescription = Text.Resource(R.string.clock_color),
                            ),
                        text = context.getString(R.string.clock_color),
                        isSelected = false,
                        isOnClickNull = false,
                    )
                }
                2 -> {
                    assertFloatingToolbarTabViewModel(
                        viewModel = floatingToolbarTabViewModel,
                        icon =
                            Icon.Resource(
                                res = R.drawable.ic_font_size_filled_24px,
                                contentDescription = Text.Resource(R.string.clock_size),
                            ),
                        text = context.getString(R.string.clock_size),
                        isSelected = false,
                        isOnClickNull = false,
                    )
                }
            }
        }
    }

    private fun assertFloatingToolbarTabViewModel(
        viewModel: FloatingToolbarTabViewModel,
        icon: Icon,
        text: String,
        isSelected: Boolean,
        isOnClickNull: Boolean,
    ) {
        assertThat(viewModel.icon).isEqualTo(icon)
        assertThat(viewModel.text).isEqualTo(text)
        assertThat(viewModel.isSelected).isEqualTo(isSelected)
        assertThat(viewModel.icon).isEqualTo(icon)
        if (isOnClickNull) {
            assertThat(viewModel.onClick).isNull()
        } else {
            assertThat(viewModel.onClick).isNotNull()
        }
    }

    @Test
    fun selectedTab_whenClickOnTabStyle() = runTest {
        val tabs = collectLastValue(underTest.tabs)
        val selectedTab = collectLastValue(underTest.selectedTab)

        tabs()?.get(0)?.onClick?.invoke()

        assertThat(selectedTab()).isEqualTo(Tab.STYLE)
    }

    @Test
    fun selectedTab_whenClickOnTabColor() = runTest {
        val tabs = collectLastValue(underTest.tabs)
        val selectedTab = collectLastValue(underTest.selectedTab)

        tabs()?.get(1)?.onClick?.invoke()

        assertThat(selectedTab()).isEqualTo(Tab.COLOR)
    }

    @Test
    fun selectedTab_whenClickOnTabSize() = runTest {
        val tabs = collectLastValue(underTest.tabs)
        val selectedTab = collectLastValue(underTest.selectedTab)

        tabs()?.get(2)?.onClick?.invoke()

        assertThat(selectedTab()).isEqualTo(Tab.SIZE)
    }

    //// Clock style
    @Test
    fun selectedClock_whenClickOnStyleOptions() = runTest {
        val selectedClock = collectLastValue(underTest.selectedClock)

        assertThat(selectedClock()?.clockId).isEqualTo(FakeClockPickerRepository.CLOCK_ID_0)
    }

    @Test
    fun previewingClock_whenClickOnStyleOptions() = runTest {
        val previewingClock = collectLastValue(underTest.previewingClock)
        val clockStyleOptions = collectLastValue(underTest.clockStyleOptions)
        // Advance CLOCKS_EVENT_UPDATE_DELAY_MILLIS since there is a delay from clockStyleOptions
        advanceTimeBy(ClockPickerViewModel.CLOCKS_EVENT_UPDATE_DELAY_MILLIS)

        assertThat(previewingClock()?.clockId).isEqualTo(FakeClockPickerRepository.CLOCK_ID_0)

        val option1OnClicked = collectLastValue(clockStyleOptions()!![1].onClicked)
        option1OnClicked()?.invoke()
        // Advance CLOCKS_EVENT_UPDATE_DELAY_MILLIS since there is a delay from clockColorOptions
        advanceTimeBy(ClockPickerViewModel.CLOCKS_EVENT_UPDATE_DELAY_MILLIS)

        assertThat(previewingClock()?.clockId).isEqualTo(FakeClockPickerRepository.CLOCK_ID_1)
    }

    @Test
    fun clockStyleOptions_whenClickOnStyleOptions() = runTest {
        val clockStyleOptions = collectLastValue(underTest.clockStyleOptions)
        // Advance CLOCKS_EVENT_UPDATE_DELAY_MILLIS since there is a delay from clockStyleOptions
        advanceTimeBy(ClockPickerViewModel.CLOCKS_EVENT_UPDATE_DELAY_MILLIS)
        val option0IsSelected = collectLastValue(clockStyleOptions()!![0].isSelected)
        val option0OnClicked = collectLastValue(clockStyleOptions()!![0].onClicked)
        val option1IsSelected = collectLastValue(clockStyleOptions()!![1].isSelected)
        val option1OnClicked = collectLastValue(clockStyleOptions()!![1].onClicked)

        assertThat(option0IsSelected()).isTrue()
        assertThat(option0OnClicked()).isNull()

        option1OnClicked()?.invoke()
        // Advance CLOCKS_EVENT_UPDATE_DELAY_MILLIS since there is a delay from clockColorOptions
        advanceTimeBy(ClockPickerViewModel.CLOCKS_EVENT_UPDATE_DELAY_MILLIS)

        assertThat(option0IsSelected()).isFalse()
        assertThat(option1IsSelected()).isTrue()
        assertThat(option1OnClicked()).isNull()
    }

    @Test
    fun showPickerClockControllerView_false_by_default() = runTest {
        val showPickerClockControllerView =
            collectLastValue(underTest.showPickerClockControllerView)

        assertThat(showPickerClockControllerView()).isFalse()
    }

    @Test
    fun showKeyguardPreviewRendererSmartspace_true_by_default() = runTest {
        val showKeyguardPreviewRendererSmartspace =
            collectLastValue(underTest.showKeyguardPreviewRendererSmartspace)

        assertThat(showKeyguardPreviewRendererSmartspace()).isTrue()
    }

    //// Clock font
    @Test
    fun previewingClockPresetIndexedStyle_whenInitialState() = runTest {
        val previewingClockPresetIndexedStyle =
            collectLastValue(underTest.previewingClockPresetIndexedStyle)

        assertThat(previewingClockPresetIndexedStyle())
            .isEqualTo(FakeClockPickerRepository.fakeClocks[0].axisPresetConfig?.current)
    }

    @Test
    fun previewingClockPresetIndexedStyle_whenClickOnSliderStep() = runTest {
        val previewingClockPresetIndexedStyle =
            collectLastValue(underTest.previewingClockPresetIndexedStyle)
        val axisPresetsSliderViewModel = collectLastValue(underTest.axisPresetsSliderViewModel)
        val onSlierStopTrackingTouch = axisPresetsSliderViewModel()?.onSliderStopTrackingTouch
        checkNotNull(onSlierStopTrackingTouch)

        onSlierStopTrackingTouch.invoke(1F)

        assertThat(previewingClockPresetIndexedStyle())
            .isEqualTo(
                IndexedStyle(
                    groupIndex = 0,
                    presetIndex = 1,
                    style = FakeClockPickerRepository.fakeClockAxisStyle1,
                )
            )
    }

    @Test
    fun shouldShowPresetSlider_true_whenDefault() = runTest {
        val shouldShowPresetSlider = collectLastValue(underTest.shouldShowPresetSlider)

        assertThat(shouldShowPresetSlider()).isTrue()
    }

    @Test
    fun shouldShowPresetSlider_false_whenSelectClockWithNullAxisPresetConfig() = runTest {
        val shouldShowPresetSlider = collectLastValue(underTest.shouldShowPresetSlider)
        val clockStyleOptions = collectLastValue(underTest.clockStyleOptions)
        // Advance CLOCKS_EVENT_UPDATE_DELAY_MILLIS since there is a delay from clockStyleOptions
        advanceTimeBy(ClockPickerViewModel.CLOCKS_EVENT_UPDATE_DELAY_MILLIS)
        val onClockOption1Clicked =
            clockStyleOptions()?.get(1)?.onClicked?.let { collectLastValue(it) }
        checkNotNull(onClockOption1Clicked)

        onClockOption1Clicked()?.invoke()

        assertThat(shouldShowPresetSlider()).isFalse()
    }

    @Test
    fun axisPresetsSliderViewModel_initialState() = runTest {
        val axisPresetsSliderViewModel = collectLastValue(underTest.axisPresetsSliderViewModel)

        val expectedPresetSize =
            FakeClockPickerRepository.fakeClocks[0].axisPresetConfig?.groups?.get(0)?.presets?.size
        checkNotNull(expectedPresetSize)
        val resultAxisPresetsSliderViewModel = axisPresetsSliderViewModel()
        assertThat(resultAxisPresetsSliderViewModel?.valueFrom).isEqualTo(0F)
        assertThat(resultAxisPresetsSliderViewModel?.valueTo)
            .isEqualTo((expectedPresetSize - 1).toFloat())
        assertThat(resultAxisPresetsSliderViewModel?.stepSize).isEqualTo(1F)
    }

    @Test
    fun axisPresetsSliderSelectedValue_update_whenClickOnSliderStep() = runTest {
        val axisPresetsSliderSelectedValue =
            collectLastValue(underTest.axisPresetsSliderSelectedValue)
        val axisPresetsSliderViewModel = collectLastValue(underTest.axisPresetsSliderViewModel)
        val onSlierStopTrackingTouch = axisPresetsSliderViewModel()?.onSliderStopTrackingTouch
        checkNotNull(onSlierStopTrackingTouch)

        onSlierStopTrackingTouch.invoke(1F)

        val expectedResult =
            FakeClockPickerRepository.fakeClocks[0]
                .axisPresetConfig
                ?.groups
                ?.get(0)
                ?.presets
                ?.get(1)
        checkNotNull(expectedResult)
        assertThat(axisPresetsSliderSelectedValue()).isEqualTo(1F)
    }

    @Test
    fun previewingClockPresetIndexedStyle_whenOnClockFaceClicked() = runTest {
        val previewingClockPresetIndexedStyle =
            collectLastValue(underTest.previewingClockPresetIndexedStyle)
        val onClockFaceClicked = collectLastValue(underTest.onClockFaceClicked)

        onClockFaceClicked()?.invoke()

        val expectedResult =
            FakeClockPickerRepository.fakeClocks[0]
                .axisPresetConfig
                ?.groups
                ?.get(1)
                ?.presets
                ?.get(0)
        checkNotNull(expectedResult)
        assertThat(previewingClockPresetIndexedStyle())
            .isEqualTo(IndexedStyle(groupIndex = 1, presetIndex = 0, style = expectedResult))
    }

    @Test
    fun onClockFaceClicked_isNull_whenOnlyOnePresetGroup() = runTest {
        val clockStyleOptions = collectLastValue(underTest.clockStyleOptions)
        // Advance CLOCKS_EVENT_UPDATE_DELAY_MILLIS since there is a delay from clockStyleOptions
        advanceTimeBy(ClockPickerViewModel.CLOCKS_EVENT_UPDATE_DELAY_MILLIS)
        val onClockOption1Clicked =
            clockStyleOptions()?.get(1)?.onClicked?.let { collectLastValue(it) }
        checkNotNull(onClockOption1Clicked)

        // Select a clock with only one preset group
        onClockOption1Clicked()?.invoke()
        // Advance CLOCKS_EVENT_UPDATE_DELAY_MILLIS since there is a delay from clockStyleOptions
        advanceTimeBy(ClockPickerViewModel.CLOCKS_EVENT_UPDATE_DELAY_MILLIS)

        val onClockFaceClicked = collectLastValue(underTest.onClockFaceClicked)
        assertThat(onClockFaceClicked()).isNull()
    }

    @Test
    fun onClockFaceClicked_isNotNull_whenMultiplePresetGroups() = runTest {
        // Default clock has multiple preset groups
        val onClockFaceClicked = collectLastValue(underTest.onClockFaceClicked)
        assertThat(onClockFaceClicked()).isNotNull()
    }

    //// Clock size
    @Test
    fun previewingClockSize_whenCallingOnClockSizeSwitchChecked() = runTest {
        val previewingClockSize = collectLastValue(underTest.previewingClockSize)

        assertThat(previewingClockSize()).isEqualTo(ClockSize.DYNAMIC)

        val onClockSizeSwitchCheckedChange =
            collectLastValue(underTest.onClockSizeSwitchCheckedChange)
        onClockSizeSwitchCheckedChange()?.invoke()

        assertThat(previewingClockSize()).isEqualTo(ClockSize.SMALL)
    }

    //// Clock color
    @Test
    fun sliderProgress_whenOnSliderProgressChanged() = runTest {
        val sliderProgress = collectLastValue(underTest.previewingColorSliderProgress)

        assertThat(sliderProgress()).isEqualTo(ClockMetadataModel.DEFAULT_COLOR_TONE_PROGRESS)

        underTest.onSliderProgressChanged(87)

        assertThat(sliderProgress()).isEqualTo(87)
    }

    @Test
    fun sliderTouchUpProgress_whenOnSliderProgressChanged() = runTest {
        val sliderProgress = collectLastValue(underTest.previewingColorSliderProgress)

        assertThat(sliderProgress()).isEqualTo(ClockMetadataModel.DEFAULT_COLOR_TONE_PROGRESS)

        underTest.onSliderTouchUpProgressChanged(87)

        assertThat(sliderProgress()).isEqualTo(87)
    }

    @Test
    fun isSliderEnabledShouldBeTrue_whenTheClockIsReactiveToToneAndSolidColor() = runTest {
        val clockStyleOptions = collectLastValue(underTest.clockStyleOptions)
        // Advance CLOCKS_EVENT_UPDATE_DELAY_MILLIS since there is a delay from clockStyleOptions
        advanceTimeBy(ClockPickerViewModel.CLOCKS_EVENT_UPDATE_DELAY_MILLIS)
        val styleOption0OnClicked = collectLastValue(clockStyleOptions()!![0].onClicked)
        val clockColorOptions = collectLastValue(underTest.clockColorOptions)
        // Advance COLOR_OPTIONS_EVENT_UPDATE_DELAY_MILLIS since there is a delay from
        // clockColorOptions
        advanceTimeBy(ClockPickerViewModel.COLOR_OPTIONS_EVENT_UPDATE_DELAY_MILLIS)
        val colorOption1OnClicked = collectLastValue(clockColorOptions()!![1].onClicked)
        val isSliderEnabled = collectLastValue(underTest.isSliderEnabled)

        styleOption0OnClicked()?.invoke()
        colorOption1OnClicked()?.invoke()
        // Advance CLOCKS_EVENT_UPDATE_DELAY_MILLIS since there is a delay from clockStyleOptions
        advanceTimeBy(ClockPickerViewModel.CLOCKS_EVENT_UPDATE_DELAY_MILLIS)

        assertThat(isSliderEnabled()).isTrue()
    }

    @Test
    fun isSliderEnabledShouldBeFalse_whenTheClockIsReactiveToToneAndDefaultColor() = runTest {
        val clockStyleOptions = collectLastValue(underTest.clockStyleOptions)
        // Advance CLOCKS_EVENT_UPDATE_DELAY_MILLIS since there is a delay from clockStyleOptions
        advanceTimeBy(ClockPickerViewModel.CLOCKS_EVENT_UPDATE_DELAY_MILLIS)
        val styleOption0OnClicked = collectLastValue(clockStyleOptions()!![0].onClicked)
        val clockColorOptions = collectLastValue(underTest.clockColorOptions)
        // Advance COLOR_OPTIONS_EVENT_UPDATE_DELAY_MILLIS since there is a delay from
        // clockColorOptions
        advanceTimeBy(ClockPickerViewModel.COLOR_OPTIONS_EVENT_UPDATE_DELAY_MILLIS)
        val colorOption0OnClicked = collectLastValue(clockColorOptions()!![0].onClicked)
        val isSliderEnabled = collectLastValue(underTest.isSliderEnabled)

        styleOption0OnClicked()?.invoke()
        colorOption0OnClicked()?.invoke()
        // Advance CLOCKS_EVENT_UPDATE_DELAY_MILLIS since there is a delay from clockStyleOptions
        advanceTimeBy(ClockPickerViewModel.CLOCKS_EVENT_UPDATE_DELAY_MILLIS)

        assertThat(isSliderEnabled()).isFalse()
    }

    @Test
    fun isSliderEnabledShouldBeFalse_whenTheClockIsNotReactiveToTone() = runTest {
        val clockStyleOptions = collectLastValue(underTest.clockStyleOptions)
        // Advance CLOCKS_EVENT_UPDATE_DELAY_MILLIS since there is a delay from clockStyleOptions
        advanceTimeBy(ClockPickerViewModel.CLOCKS_EVENT_UPDATE_DELAY_MILLIS)
        val styleOption3OnClicked = collectLastValue(clockStyleOptions()!![3].onClicked)
        val isSliderEnabled = collectLastValue(underTest.isSliderEnabled)

        styleOption3OnClicked()?.invoke()
        // Advance CLOCKS_EVENT_UPDATE_DELAY_MILLIS since there is a delay from clockStyleOptions
        advanceTimeBy(ClockPickerViewModel.CLOCKS_EVENT_UPDATE_DELAY_MILLIS)

        assertThat(isSliderEnabled()).isFalse()
    }

    @Test
    fun previewingSeedColor_whenChangeColorOptionAndToneProgress() = runTest {
        val previewingSeedColor = collectLastValue(underTest.previewingSeedColor)
        val clockColorOptions = collectLastValue(underTest.clockColorOptions)
        // Advance COLOR_OPTIONS_EVENT_UPDATE_DELAY_MILLIS since there is a delay from
        // clockColorOptions
        advanceTimeBy(ClockPickerViewModel.COLOR_OPTIONS_EVENT_UPDATE_DELAY_MILLIS)
        val option1OnClicked = collectLastValue(clockColorOptions()!![1].onClicked)

        option1OnClicked()?.invoke()
        // Advance COLOR_OPTIONS_EVENT_UPDATE_DELAY_MILLIS since there is a delay from
        // clockColorOptions
        advanceTimeBy(ClockPickerViewModel.COLOR_OPTIONS_EVENT_UPDATE_DELAY_MILLIS)
        val targetProgress = 55
        underTest.onSliderProgressChanged(targetProgress)

        val expectedSelectedColorModel = colorMap.values.first() // RED
        assertThat(previewingSeedColor())
            .isEqualTo(
                blendColorWithTone(
                    expectedSelectedColorModel.color,
                    expectedSelectedColorModel.getColorTone(targetProgress),
                )
            )
    }

    @Test
    fun previewingSeedColor_nullWhenDefaultIsSelected() = runTest {
        val previewingSeedColor = collectLastValue(underTest.previewingSeedColor)
        val clockColorOptions = collectLastValue(underTest.clockColorOptions)
        val onApply = collectLastValue(underTest.onApply)
        // Advance COLOR_OPTIONS_EVENT_UPDATE_DELAY_MILLIS since there is a delay from
        // clockColorOptions
        advanceTimeBy(ClockPickerViewModel.COLOR_OPTIONS_EVENT_UPDATE_DELAY_MILLIS)
        val option1OnClicked = collectLastValue(clockColorOptions()!![1].onClicked)
        option1OnClicked()?.invoke()
        // Advance COLOR_OPTIONS_EVENT_UPDATE_DELAY_MILLIS since there is a delay from
        // clockColorOptions
        advanceTimeBy(ClockPickerViewModel.COLOR_OPTIONS_EVENT_UPDATE_DELAY_MILLIS)
        onApply()?.invoke()
        // Advance COLOR_OPTIONS_EVENT_UPDATE_DELAY_MILLIS since there is a delay from
        // clockColorOptions
        advanceTimeBy(ClockPickerViewModel.COLOR_OPTIONS_EVENT_UPDATE_DELAY_MILLIS)
        val option0OnClicked = collectLastValue(clockColorOptions()!![0].onClicked)
        option0OnClicked()?.invoke()
        // Advance COLOR_OPTIONS_EVENT_UPDATE_DELAY_MILLIS since there is a delay from
        // clockColorOptions
        advanceTimeBy(ClockPickerViewModel.COLOR_OPTIONS_EVENT_UPDATE_DELAY_MILLIS)

        assertThat(previewingSeedColor()).isNull()
    }

    @Test
    fun clockColorOptions_whenClickOnColorOptions() = runTest {
        val clockColorOptions = collectLastValue(underTest.clockColorOptions)
        // Advance COLOR_OPTIONS_EVENT_UPDATE_DELAY_MILLIS since there is a delay from
        // clockColorOptions
        advanceTimeBy(ClockPickerViewModel.COLOR_OPTIONS_EVENT_UPDATE_DELAY_MILLIS)
        val option0IsSelected = collectLastValue(clockColorOptions()!![0].isSelected)
        val option0OnClicked = collectLastValue(clockColorOptions()!![0].onClicked)
        val option1IsSelected = collectLastValue(clockColorOptions()!![1].isSelected)
        val option1OnClicked = collectLastValue(clockColorOptions()!![1].onClicked)

        assertThat(option0IsSelected()).isTrue()
        assertThat(option0OnClicked()).isNull()

        option1OnClicked()?.invoke()
        // Advance COLOR_OPTIONS_EVENT_UPDATE_DELAY_MILLIS since there is a delay from
        // clockColorOptions
        advanceTimeBy(ClockPickerViewModel.COLOR_OPTIONS_EVENT_UPDATE_DELAY_MILLIS)

        assertThat(option0IsSelected()).isFalse()
        assertThat(option1IsSelected()).isTrue()
        assertThat(option1OnClicked()).isNull()
    }

    @Test
    fun apply_nullWhenInitialState() = runTest {
        val onApply = collectLastValue(underTest.onApply)

        assertThat(onApply()).isNull()
    }

    @Test
    fun apply_notNullWhenClockStyleChanged() = runTest {
        val onApply = collectLastValue(underTest.onApply)
        val clockStyleOptions = collectLastValue(underTest.clockStyleOptions)
        // Advance CLOCKS_EVENT_UPDATE_DELAY_MILLIS since there is a delay from clockStyleOptions
        advanceTimeBy(ClockPickerViewModel.CLOCKS_EVENT_UPDATE_DELAY_MILLIS)

        val option1OnClicked = collectLastValue(clockStyleOptions()!![1].onClicked)
        option1OnClicked()?.invoke()

        assertThat(onApply()).isNotNull()
    }

    @Test
    fun apply_nullAfterApplyingClockStyle() = runTest {
        val onApply = collectLastValue(underTest.onApply)
        val clockStyleOptions = collectLastValue(underTest.clockStyleOptions)
        // Advance CLOCKS_EVENT_UPDATE_DELAY_MILLIS since there is a delay from clockStyleOptions
        advanceTimeBy(ClockPickerViewModel.CLOCKS_EVENT_UPDATE_DELAY_MILLIS)

        val option1OnClicked = collectLastValue(clockStyleOptions()!![1].onClicked)
        option1OnClicked()?.invoke()
        onApply()?.invoke()

        assertThat(onApply()).isNull()
    }

    @Test
    fun apply_notNullWhenClockSizeChanged() = runTest {
        val onApply = collectLastValue(underTest.onApply)
        val onClockSizeSwitchCheckedChange =
            collectLastValue(underTest.onClockSizeSwitchCheckedChange)

        onClockSizeSwitchCheckedChange()?.invoke()

        assertThat(onApply()).isNotNull()
    }

    @Test
    fun apply_nullAfterApplyingClockSize() = runTest {
        val onApply = collectLastValue(underTest.onApply)
        val onClockSizeSwitchCheckedChange =
            collectLastValue(underTest.onClockSizeSwitchCheckedChange)

        onClockSizeSwitchCheckedChange()?.invoke()
        onApply()?.invoke()

        assertThat(onApply()).isNull()
    }

    @Test
    fun apply_notNullWhenColorChanged() = runTest {
        val onApply = collectLastValue(underTest.onApply)
        val clockColorOptions = collectLastValue(underTest.clockColorOptions)
        // Advance COLOR_OPTIONS_EVENT_UPDATE_DELAY_MILLIS since there is a delay from
        // clockColorOptions
        advanceTimeBy(ClockPickerViewModel.COLOR_OPTIONS_EVENT_UPDATE_DELAY_MILLIS)
        val option1OnClicked = collectLastValue(clockColorOptions()!![1].onClicked)

        option1OnClicked()?.invoke()

        assertThat(onApply()).isNotNull()
    }

    @Test
    fun apply_nullAfterApplyingColor() = runTest {
        val onApply = collectLastValue(underTest.onApply)
        val clockColorOptions = collectLastValue(underTest.clockColorOptions)
        // Advance COLOR_OPTIONS_EVENT_UPDATE_DELAY_MILLIS since there is a delay from
        // clockColorOptions
        advanceTimeBy(ClockPickerViewModel.COLOR_OPTIONS_EVENT_UPDATE_DELAY_MILLIS)
        val option1OnClicked = collectLastValue(clockColorOptions()!![1].onClicked)

        option1OnClicked()?.invoke()
        onApply()?.invoke()

        assertThat(onApply()).isNull()
    }

    @Test
    fun onApply_notNullWhenSliderProgressChanged() = runTest {
        val onApply = collectLastValue(underTest.onApply)

        underTest.onSliderTouchUpProgressChanged(87)

        assertThat(onApply()).isNotNull()
    }

    @Test
    fun onApply_nullAfterApplyingSliderProgress() = runTest {
        val onApply = collectLastValue(underTest.onApply)

        underTest.onSliderTouchUpProgressChanged(87)
        onApply()?.invoke()

        assertThat(onApply()).isNull()
    }

    @Test
    fun onApply_logsClockApplied() = runTest {
        val onApply = collectLastValue(underTest.onApply)
        val clockStyleOptions = collectLastValue(underTest.clockStyleOptions)
        // Advance CLOCKS_EVENT_UPDATE_DELAY_MILLIS since there is a delay from clockStyleOptions
        advanceTimeBy(ClockPickerViewModel.CLOCKS_EVENT_UPDATE_DELAY_MILLIS)

        val option1OnClicked = collectLastValue(clockStyleOptions()!![1].onClicked)
        option1OnClicked()?.invoke()
        onApply()?.invoke()

        assertThat(logger.clockId).isEqualTo(FakeClockPickerRepository.CLOCK_ID_1)
    }

    @Test
    fun onApply_logsClockSizeApplied() = runTest {
        val onApply = collectLastValue(underTest.onApply)
        val onClockSizeSwitchCheckedChange =
            collectLastValue(underTest.onClockSizeSwitchCheckedChange)

        onClockSizeSwitchCheckedChange()?.invoke()
        onApply()?.invoke()

        assertThat(logger.clockSize).isEqualTo(StyleEnums.CLOCK_SIZE_SMALL)
    }

    @Test
    fun onApply_logsClockColorApplied() = runTest {
        val onApply = collectLastValue(underTest.onApply)
        val clockColorOptions = collectLastValue(underTest.clockColorOptions)
        // Advance COLOR_OPTIONS_EVENT_UPDATE_DELAY_MILLIS since there is a delay from
        // clockColorOptions
        advanceTimeBy(ClockPickerViewModel.COLOR_OPTIONS_EVENT_UPDATE_DELAY_MILLIS)
        val option1OnClicked = collectLastValue(clockColorOptions()!![1].onClicked)

        option1OnClicked()?.invoke()
        onApply()?.invoke()

        assertThat(logger.clockSeedColor).isEqualTo(colorMap.values.first().color)
    }

    @Test
    fun resetPreview_resetsStateFlows() = runTest {
        val previewingClock = collectLastValue(underTest.previewingClock)
        val previewingClockPresetIndexedStyle =
            collectLastValue(underTest.previewingClockPresetIndexedStyle)
        val previewingSeedColor = collectLastValue(underTest.previewingSeedColor)
        val previewingColorSliderProgress =
            collectLastValue(underTest.previewingColorSliderProgress)
        val previewingClockSize = collectLastValue(underTest.previewingClockSize)

        // Modify some values
        val clockStyleOptions = collectLastValue(underTest.clockStyleOptions)
        advanceTimeBy(ClockPickerViewModel.CLOCKS_EVENT_UPDATE_DELAY_MILLIS)
        val option1OnClicked = collectLastValue(clockStyleOptions()!![1].onClicked)
        option1OnClicked()?.invoke()
        underTest.onSliderTouchUpProgressChanged(87)
        val onClockSizeSwitchCheckedChange =
            collectLastValue(underTest.onClockSizeSwitchCheckedChange)
        onClockSizeSwitchCheckedChange()?.invoke()
        val axisPresetsSliderViewModel = collectLastValue(underTest.axisPresetsSliderViewModel)
        axisPresetsSliderViewModel()?.onSliderStopTrackingTouch?.invoke(1F)

        // Reset
        underTest.resetPreview()
        advanceTimeBy(ClockPickerViewModel.CLOCKS_EVENT_UPDATE_DELAY_MILLIS)

        // Assert values are reset to initial state
        assertThat(previewingClock()?.clockId).isEqualTo(FakeClockPickerRepository.CLOCK_ID_0)
        assertThat(previewingClockPresetIndexedStyle())
            .isEqualTo(FakeClockPickerRepository.fakeClocks[0].axisPresetConfig?.current)
        assertThat(previewingSeedColor()).isEqualTo(null)
        assertThat(previewingColorSliderProgress())
            .isEqualTo(ClockMetadataModel.DEFAULT_COLOR_TONE_PROGRESS)
        assertThat(previewingClockSize()).isEqualTo(ClockSize.DYNAMIC)
    }

    @Test
    fun resetPreview_resetsSelectedTab() = runTest {
        val selectedTab = collectLastValue(underTest.selectedTab)

        // Change tab
        val tabs = collectLastValue(underTest.tabs)
        tabs()?.get(1)?.onClick?.invoke() // Color tab
        assertThat(selectedTab()).isEqualTo(Tab.COLOR)

        // Reset
        underTest.resetPreview()

        assertThat(selectedTab()).isEqualTo(Tab.STYLE)
    }

    private fun blendColorWithTone(color: Int, colorTone: Double): Int {
        val helperColorLab = DoubleArray(3)
        ColorUtils.colorToLAB(color, helperColorLab)
        return ColorUtils.LABToColor(colorTone, helperColorLab[1], helperColorLab[2])
    }
}
