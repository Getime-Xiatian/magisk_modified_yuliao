package andro.pluginsuite.arch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import andro.pluginsuite.core.di.ServiceLocator
import andro.pluginsuite.ui.home.HomeViewModel
import andro.pluginsuite.ui.install.InstallViewModel
import andro.pluginsuite.ui.log.LogViewModel
import andro.pluginsuite.ui.superuser.SuperuserViewModel
import andro.pluginsuite.ui.surequest.SuRequestViewModel

object VMFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when (modelClass) {
            HomeViewModel::class.java -> HomeViewModel(ServiceLocator.networkService)
            LogViewModel::class.java -> LogViewModel(ServiceLocator.logRepo)
            SuperuserViewModel::class.java -> SuperuserViewModel(ServiceLocator.policyDB)
            InstallViewModel::class.java ->
                InstallViewModel(ServiceLocator.networkService)
            SuRequestViewModel::class.java ->
                SuRequestViewModel(ServiceLocator.policyDB, ServiceLocator.timeoutPrefs)
            else -> modelClass.newInstance()
        } as T
    }
}
