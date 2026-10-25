/*
 * Emyth
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package dev.emyth.app.plugin.di

import dev.emyth.app.data.datastore.SettingsStore
import dev.emyth.app.data.service.MemoryBankService
import dev.emyth.app.plugin.loader.PluginLoader
import dev.emyth.app.plugin.manager.PluginManager
import dev.emyth.app.plugin.provider.PluginToolProvider
import dev.emyth.app.plugin.repository.PluginRepository
import dev.emyth.app.plugin.scanner.PluginScanner
import dev.emyth.app.plugin.ui.PluginViewModel
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * 插件模块依赖注入
 */
val pluginModule = module {
    // Scanner
    single { PluginScanner(androidContext(), get()) }

    // Repository
    single { PluginRepository(androidContext()) }

    // Loader - 需要OkHttpClient、MemoryBankService和SettingsStore
    single { PluginLoader(androidContext(), get<OkHttpClient>(), get<MemoryBankService>(), get<SettingsStore>()) }

    // Manager
    single { PluginManager(androidContext(), get(), get(), get(), get()) }

    // Provider - 需要PluginManager来确保插件已初始化
    single { PluginToolProvider(get(), get()) }

    // ViewModel
    viewModel { PluginViewModel(get()) }
}
