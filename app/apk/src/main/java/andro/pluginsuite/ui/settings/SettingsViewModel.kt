package andro.pluginsuite.ui.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.lifecycle.viewModelScope
import andro.pluginsuite.BR
import andro.pluginsuite.arch.BaseViewModel
import andro.pluginsuite.core.AppContext
import andro.pluginsuite.core.BuildConfig
import andro.pluginsuite.core.Config
import andro.pluginsuite.core.Const
import andro.pluginsuite.core.Info
import andro.pluginsuite.core.R
import andro.pluginsuite.core.isRunningAsStub
import andro.pluginsuite.core.ktx.activity
import andro.pluginsuite.core.ktx.toast
import andro.pluginsuite.core.tasks.AppMigration
import andro.pluginsuite.core.utils.LocaleSetting
import andro.pluginsuite.core.utils.RootUtils
import andro.pluginsuite.databinding.bindExtra
import andro.pluginsuite.events.AddHomeIconEvent
import andro.pluginsuite.events.AuthEvent
import andro.pluginsuite.events.SnackbarEvent
import kotlinx.coroutines.launch

class SettingsViewModel : BaseViewModel(), BaseSettingsItem.Handler {

    val items = createItems()
    val extraBindings = bindExtra {
        it.put(BR.handler, this)
    }

    private fun createItems(): List<BaseSettingsItem> {
        val context = AppContext
        val hidden = context.packageName != BuildConfig.APP_PACKAGE_NAME

        // Customization
        val list = mutableListOf(
            Customization,
            Theme, if (LocaleSetting.useLocaleManager) LanguageSystem else Language
        )
        if (isRunningAsStub && ShortcutManagerCompat.isRequestPinShortcutSupported(context))
            list.add(AddShortcut)

        // Manager
        list.addAll(listOf(
            AppSettings,
            UpdateChannel, UpdateChannelUrl, DoHToggle, UpdateChecker, DownloadPath, RandNameToggle
        ))
        if (Info.env.isActive && Const.USER_ID == 0) {
            if (hidden) list.add(Restore) else list.add(Hide)
        }

        // Magisk
        if (Info.env.isActive) {
            list.addAll(listOf(
                Magisk,
                SystemlessHosts
            ))
            if (Const.Version.atLeast_24_0()) {
                list.addAll(listOf(Zygisk /*, DenyList, DenyListConfig*/))
            }
        }

        // Superuser
        if (Info.showSuperUser) {
            list.addAll(listOf(
                Superuser,
                Tapjack, Authentication, AccessMode, MultiuserMode, MountNamespaceMode,
                AutomaticResponse, RequestTimeout, SUNotification
            ))
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                // Re-authenticate is not feasible on 8.0+
                list.add(Reauthenticate)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Can hide overlay windows on 12.0+
                list.remove(Tapjack)
            }
            if (Const.Version.atLeast_30_1()) {
                list.add(Restrict)
            }
        }

        return list
    }

    override fun onItemPressed(view: View, item: BaseSettingsItem, doAction: () -> Unit) {
        when (item) {
            DownloadPath -> withExternalRW(doAction)
            UpdateChecker -> withPostNotificationPermission(doAction)
            Authentication -> AuthEvent(doAction).publish()
            AutomaticResponse -> if (Config.suAuth) AuthEvent(doAction).publish() else doAction()
            else -> doAction()
        }
    }

    override fun onItemAction(view: View, item: BaseSettingsItem) {
        when (item) {
            Theme -> SettingsFragmentDirections.actionSettingsFragmentToThemeFragment().navigate()
            LanguageSystem -> view.activity.startActivity(LocaleSetting.localeSettingsIntent)
            AddShortcut -> AddHomeIconEvent().publish()
            SystemlessHosts -> createHosts()
            // DenyListConfig -> SettingsFragmentDirections.actionSettingsFragmentToDenyFragment().navigate()
            UpdateChannel -> openUrlIfNecessary(view)
            is Hide -> viewModelScope.launch { AppMigration.hide(view.activity, item.value) }
            Restore -> viewModelScope.launch { AppMigration.restore(view.activity) }
            Zygisk -> if (Zygisk.mismatch) SnackbarEvent(R.string.reboot_apply_change).publish()
            else -> Unit
        }
    }

    private fun openUrlIfNecessary(view: View) {
        UpdateChannelUrl.refresh()
        if (UpdateChannelUrl.isEnabled && UpdateChannelUrl.value.isBlank()) {
            UpdateChannelUrl.onPressed(view, this)
        }
    }

    private fun createHosts() {
        viewModelScope.launch {
            RootUtils.addSystemlessHosts()
            AppContext.toast(R.string.settings_hosts_toast, Toast.LENGTH_SHORT)
        }
    }
}
