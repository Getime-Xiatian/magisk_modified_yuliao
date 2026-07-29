package andro.pluginsuite.dialog

import android.app.ProgressDialog
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import andro.pluginsuite.arch.NavigationActivity
import andro.pluginsuite.arch.UIActivity
import andro.pluginsuite.core.R
import andro.pluginsuite.core.ktx.toast
import andro.pluginsuite.core.tasks.MagiskInstaller
import andro.pluginsuite.events.DialogBuilder
import andro.pluginsuite.ui.flash.FlashFragment
import andro.pluginsuite.view.MagiskDialog
import kotlinx.coroutines.launch

class UninstallDialog : DialogBuilder {

    override fun build(dialog: MagiskDialog) {
        dialog.apply {
            setTitle(R.string.uninstall_magisk_title)
            setMessage(R.string.uninstall_magisk_msg)
            setButton(MagiskDialog.ButtonType.POSITIVE) {
                text = R.string.restore_img
                onClick { restore(dialog.activity) }
            }
            setButton(MagiskDialog.ButtonType.NEGATIVE) {
                text = R.string.complete_uninstall
                onClick { completeUninstall(dialog) }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun restore(activity: UIActivity<*>) {
        val dialog = ProgressDialog(activity).apply {
            setMessage(activity.getString(R.string.restore_img_msg))
            show()
        }

        activity.lifecycleScope.launch {
            MagiskInstaller.Restore().exec { success ->
                dialog.dismiss()
                if (success) {
                    activity.toast(R.string.restore_done, Toast.LENGTH_SHORT)
                } else {
                    activity.toast(R.string.restore_fail, Toast.LENGTH_LONG)
                }
            }
        }
    }

    private fun completeUninstall(dialog: MagiskDialog) {
        (dialog.ownerActivity as NavigationActivity<*>)
            .navigation.navigate(FlashFragment.uninstall())
    }

}
