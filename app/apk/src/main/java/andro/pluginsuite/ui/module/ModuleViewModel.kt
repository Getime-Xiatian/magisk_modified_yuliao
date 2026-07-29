package andro.pluginsuite.ui.module

import android.net.Uri
import androidx.databinding.Bindable
import androidx.lifecycle.MutableLiveData
import andro.pluginsuite.BR
import andro.pluginsuite.MainDirections
import andro.pluginsuite.R
import andro.pluginsuite.arch.AsyncLoadViewModel
import andro.pluginsuite.core.Const
import andro.pluginsuite.core.Info
import andro.pluginsuite.core.base.ContentResultCallback
import andro.pluginsuite.core.model.module.LocalModule
import andro.pluginsuite.core.model.module.OnlineModule
import andro.pluginsuite.databinding.MergeObservableList
import andro.pluginsuite.databinding.RvItem
import andro.pluginsuite.databinding.bindExtra
import andro.pluginsuite.databinding.diffList
import andro.pluginsuite.databinding.set
import andro.pluginsuite.dialog.LocalModuleInstallDialog
import andro.pluginsuite.dialog.OnlineModuleInstallDialog
import andro.pluginsuite.events.GetContentEvent
import andro.pluginsuite.events.SnackbarEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import andro.pluginsuite.core.R as CoreR

class ModuleViewModel : AsyncLoadViewModel() {

    val bottomBarBarrierIds = intArrayOf(R.id.module_update, R.id.module_remove)

    private val itemsInstalled = diffList<LocalModuleRvItem>()

    val items = MergeObservableList<RvItem>()
    val extraBindings = bindExtra {
        it.put(BR.viewModel, this)
    }

    val data get() = uri

    @get:Bindable
    var loading = true
        private set(value) = set(value, field, { field = it }, BR.loading)

    override suspend fun doLoadWork() {
        loading = true
        val moduleLoaded = Info.env.isActive &&
                withContext(Dispatchers.IO) { LocalModule.loaded() }
        if (moduleLoaded) {
            loadInstalled()
            if (items.isEmpty()) {
                items.insertItem(InstallModule)
                    .insertList(itemsInstalled)
            }
        }
        loading = false
        loadUpdateInfo()
    }

    override fun onNetworkChanged(network: Boolean) = startLoading()

    private suspend fun loadInstalled() {
        withContext(Dispatchers.Default) {
            val installed = LocalModule.installed().map { LocalModuleRvItem(it) }
            itemsInstalled.update(installed)
        }
    }

    private suspend fun loadUpdateInfo() {
        withContext(Dispatchers.IO) {
            itemsInstalled.forEach {
                if (it.item.fetch())
                    it.fetchedUpdateInfo()
            }
        }
    }

    fun downloadPressed(item: OnlineModule?) =
        if (item != null && Info.isConnected.value == true) {
            withExternalRW { OnlineModuleInstallDialog(item).show() }
        } else {
            SnackbarEvent(CoreR.string.no_connection).publish()
        }

    fun installPressed() = withExternalRW {
        GetContentEvent("application/zip", UriCallback()).publish()
    }

    fun requestInstallLocalModule(uri: Uri, displayName: String) {
        LocalModuleInstallDialog(this, uri, displayName).show()
    }

    @Parcelize
    class UriCallback : ContentResultCallback {
        override fun onActivityResult(result: Uri) {
            uri.value = result
        }
    }

    fun runAction(id: String, name: String) {
        MainDirections.actionActionFragment(id, name).navigate()
    }

    companion object {
        private val uri = MutableLiveData<Uri?>()
    }
}
