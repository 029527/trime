/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.switches

import android.app.Dialog
import android.view.View
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.R
import com.osfans.trime.core.RimeApi
import com.osfans.trime.core.RimeConfig
import com.osfans.trime.core.RimeMessage
import com.osfans.trime.core.SchemaItem
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.ime.broadcast.InputBroadcastReceiver
import com.osfans.trime.ime.compose.clipboard.PanelBarButton
import com.osfans.trime.ime.compose.clipboard.PanelMenuAction
import com.osfans.trime.ime.compose.imeComposeView
import com.osfans.trime.ime.compose.switches.SwitchOptionGrid
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.dialog.EnabledSchemaPickerDialog
import com.osfans.trime.ime.window.BoardWindow
import com.osfans.trime.util.AppUtils
import kotlinx.coroutines.launch
import org.kodein.di.instance

class SwitchOptionWindow :
    BoardWindow.BarBoardWindow(),
    InputBroadcastReceiver {
    private val service: TrimeInputMethodService by di.instance()
    private val rime: RimeSession by di.instance()

    private val staticEntries by lazy {
        listOf(
            SwitchOptionEntry.Static(
                context.getString(R.string.theme),
                R.drawable.ic_baseline_color_lens_24,
                SwitchOptionEntry.Static.Type.ThemeList,
            ),
            SwitchOptionEntry.Static(
                context.getString(R.string.schemata),
                R.drawable.ic_round_view_list_24,
                SwitchOptionEntry.Static.Type.SchemaList,
            ),
            SwitchOptionEntry.Static(
                context.getString(R.string.update_config),
                R.drawable.ic_baseline_sync_24,
                SwitchOptionEntry.Static.Type.UpdateConfig,
            ),
            SwitchOptionEntry.Static(
                context.getString(R.string.virtual_keyboard),
                R.drawable.ic_baseline_keyboard_24,
                SwitchOptionEntry.Static.Type.Keyboard,
            ),
        )
    }

    private var entries by mutableStateOf(emptyList<SwitchOptionEntry>())

    private val saveOptions by lazy {
        RimeConfig.openConfig("default").use {
            it.getList("switcher/save_options", RimeConfig::getString).toSet()
        }
    }

    private suspend fun RimeApi.applyOption(option: String, value: Boolean) {
        setRuntimeOption(option, value)
        if (option in saveOptions) {
            RimeConfig.openUserConfig("user").use {
                it.setBool("var/option/$option", value)
            }
        }
    }

    private fun showDialog(builder: suspend (RimeApi) -> Dialog) {
        rime.launchOnReady { api ->
            service.lifecycleScope.launch {
                service.showDialog(builder(api))
            }
        }
    }

    private fun onEntryClick(entry: SwitchOptionEntry) {
        when (entry) {
            is SwitchOptionEntry.Static -> when (entry.type) {
                SwitchOptionEntry.Static.Type.SchemaList -> showDialog { r ->
                    EnabledSchemaPickerDialog.build(r, service.lifecycleScope, context) {
                        setNegativeButton(R.string.enable_schemata) { _, _ ->
                            AppUtils.launchMainToSchemaList(context)
                        }
                    }
                }
                SwitchOptionEntry.Static.Type.UpdateConfig -> rime.launchOnReady { r ->
                    r.updateConfig()
                    service.lifecycleScope.launch {
                        Toast.makeText(service, R.string.done, Toast.LENGTH_SHORT).show()
                    }
                }
                SwitchOptionEntry.Static.Type.Keyboard -> AppUtils.launchMainToKeyboard(context)
                // 主题已内置，没有主题可选；这里改为打开键盘样式设置页（深浅色、配色微调）
                SwitchOptionEntry.Static.Type.ThemeList -> AppUtils.launchMainToTheme(context)
            }
            // a switch with a list of options opens its menu instead, see menuFor
            is SwitchOptionEntry.Custom -> rime.launchOnReady {
                val oldValue = it.getRuntimeOption(entry.switch.name)
                it.applyOption(entry.switch.name, !oldValue)
            }
        }
    }

    private fun menuFor(entry: SwitchOptionEntry.Custom): List<PanelMenuAction> {
        val options = entry.switch.options
        return entry.switch.states.mapIndexed { i, state ->
            PanelMenuAction(state) {
                rime.launchOnReady {
                    options.forEachIndexed { j, option ->
                        it.applyOption(option, i == j)
                    }
                }
            }
        }
    }

    /** Rebuild the list from the cached schema; callable from any thread. */
    private fun updateSchemaOptionEntries() {
        val switches = rime.run { schemaCached }.switches
        val list = staticEntries + switches.mapNotNull { SwitchOptionEntry.fromSwitch(rime, it) }
        service.lifecycleScope.launch { entries = list }
    }

    override fun onRimeSchemaUpdated(schema: SchemaItem) {
        updateSchemaOptionEntries()
    }

    override fun onRimeOptionUpdated(value: RimeMessage.OptionMessage.Data) {
        updateSchemaOptionEntries()
    }

    override fun onCreateView(): View = context.imeComposeView {
        SwitchOptionGrid(entries, onClick = ::onEntryClick, menuFor = ::menuFor)
    }

    override fun onCreateBarView(): View = context.imeComposeView {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.End) {
            PanelBarButton(R.drawable.ic_baseline_settings_24, onClick = { AppUtils.launchMainActivity(context) })
        }
    }

    override fun onAttached() {
        rime.launchOnReady { api ->
            val data = api.currentSchema().switches
            val list = staticEntries + data.mapNotNull { SwitchOptionEntry.fromSwitch(rime, it) }
            service.lifecycleScope.launch { entries = list }
        }
    }

    // the option menu is a popup of the grid's composition and closes with the view
    override fun onDetached() {}
}
