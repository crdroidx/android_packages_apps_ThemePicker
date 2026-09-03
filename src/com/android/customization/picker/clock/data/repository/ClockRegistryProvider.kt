/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.customization.picker.clock.data.repository

import android.content.Context
import android.view.LayoutInflater
import com.android.systemui.plugins.PluginManager
import com.android.systemui.shared.clocks.ClockRegistry
import com.android.systemui.shared.clocks.DefaultClockProvider
import com.android.systemui.shared.plugins.PluginEnabler
import com.android.systemui.shared.plugins.PluginManagerImpl
import com.android.systemui.shared.system.UncaughtExceptionPreHandlerManager_Factory
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

/**
 * Provide the [ClockRegistry] singleton. Note that we need to make sure that the [PluginManager]
 * needs to be connected before [ClockRegistry] is ready to use.
 */
class ClockRegistryProvider(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val mainDispatcher: CoroutineDispatcher,
    private val backgroundDispatcher: CoroutineDispatcher,
) {
    private val clockRegistry: ClockRegistry by lazy {
        ClockRegistry(
            context,
            createPluginManager(context),
            coroutineScope,
            mainDispatcher,
            backgroundDispatcher,
            handleAllUsers = false,
            DefaultClockProvider(
                layoutInflater = LayoutInflater.from(context),
                resources = context.resources,
                vibrator = null,
            ),
            keepAllLoaded = true,
            subTag = "Picker",
        )
    }

    init {
        // Listeners in ClockRegistry get cleaned up when app ended
        clockRegistry.registerListeners()
    }

    fun get() = clockRegistry

    private fun createPluginManager(context: Context): PluginManager {
        return PluginManagerImpl.create(
            context,
            // Single source of truth: read the allowlist from SystemUI's
            // config_pluginAllowlist instead of a duplicated hardcoded list,
            // so a clock only needs allowlisting in one place.
            loadClockPluginAllowlist(context),
            PluginEnabler.AlwaysEnabled(),
            Executors.newSingleThreadExecutor(),
            UncaughtExceptionPreHandlerManager_Factory.create().get(),
        )
    }

    /**
     * Read the clock plugin allowlist from SystemUI's config_pluginAllowlist so the picker
     * and SystemUI share one definition. Falls back to a built-in list (including DerpFest
     * clocks) if the SystemUI resource cannot be read.
     */
    private fun loadClockPluginAllowlist(context: Context): List<String> {
        try {
            val sysui = context.createPackageContext(SYSTEMUI_PACKAGE, 0)
            val resId =
                sysui.resources.getIdentifier("config_pluginAllowlist", "array", SYSTEMUI_PACKAGE)
            if (resId != 0) {
                val list = sysui.resources.getStringArray(resId).filterNotNull()
                if (list.isNotEmpty()) return list
            }
        } catch (e: Exception) {
            // fall through to the built-in list below
        }
        return FALLBACK_ALLOWLIST
    }

    companion object {
        private const val SYSTEMUI_PACKAGE = "com.android.systemui"
        private val FALLBACK_ALLOWLIST =
            listOf(
                "com.android.systemui.clocks.bignum",
                "com.android.systemui.clocks.calligraphy",
                "com.android.systemui.clocks.growth",
                "com.android.systemui.clocks.handwritten",
                "com.android.systemui.clocks.inflate",
                "com.android.systemui.clocks.metro",
                "com.android.systemui.clocks.numoverlap",
                "com.android.systemui.clocks.weather",
            )
    }
}
