package andro.pluginsuite.arch

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import andro.pluginsuite.core.Info
import andro.pluginsuite.core.di.ServiceLocator
import andro.pluginsuite.ui.home.HomeViewModel
import andro.pluginsuite.ui.install.InstallViewModel
import andro.pluginsuite.ui.log.LogViewModel
import andro.pluginsuite.ui.superuser.SuperuserViewModel
import andro.pluginsuite.ui.surequest.SuRequestViewModel

interface ViewModelHolder : LifecycleOwner, ViewModelStoreOwner {

    val viewModel: BaseViewModel

    fun startObserveLiveData() {
        viewModel.viewEvents.observe(this, this::onEventDispatched)
        Info.isConnected.observe(this, viewModel::onNetworkChanged)
    }

    /**
     * Called for all [ViewEvent]s published by associated viewModel.
     */
    fun onEventDispatched(event: ViewEvent) {}
}

object VMFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when (modelClass) {
            HomeViewModel::class.java -> HomeViewModel(ServiceLocator.networkService)
            LogViewModel::class.java -> LogViewModel(ServiceLocator.logRepo)
            SuperuserViewModel::class.java -> SuperuserViewModel(ServiceLocator.policyDB)
            InstallViewModel::class.java ->
                InstallViewModel(ServiceLocator.networkService, ServiceLocator.markwon)
            SuRequestViewModel::class.java ->
                SuRequestViewModel(ServiceLocator.policyDB, ServiceLocator.timeoutPrefs)
            else -> modelClass.newInstance()
        } as T
    }
}

inline fun <reified VM : ViewModel> ViewModelHolder.viewModel() =
    lazy(LazyThreadSafetyMode.NONE) {
        ViewModelProvider(this, VMFactory)[VM::class.java]
    }
