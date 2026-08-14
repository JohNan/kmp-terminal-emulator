package com.antigravity.agy.android.di

import com.antigravity.agy.android.network.WorkspaceClient
import com.antigravity.agy.android.state.SettingsRepository
import com.antigravity.agy.android.state.TerminalViewModel
import com.antigravity.agy.android.ui.RealAgyViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { SettingsRepository(androidContext()) }
    single { WorkspaceClient.createDefaultHttpClient() }

    factory { TerminalViewModel(get(), get()) }
    viewModel { RealAgyViewModel(get(), get()) }
}
