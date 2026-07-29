package andro.pluginsuite.ui.module

import android.view.MenuItem
import androidx.databinding.ObservableArrayList
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import andro.pluginsuite.R
import andro.pluginsuite.arch.BaseViewModel
import andro.pluginsuite.core.ktx.synchronized
import andro.pluginsuite.core.ktx.timeFormatStandard
import andro.pluginsuite.core.ktx.toTime
import andro.pluginsuite.core.utils.MediaStoreUtils
import andro.pluginsuite.core.utils.MediaStoreUtils.outputStream
import andro.pluginsuite.events.SnackbarEvent
import andro.pluginsuite.ui.flash.ConsoleItem
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException

class ActionViewModel : BaseViewModel() {

    enum class State {
        RUNNING, SUCCESS, FAILED
    }

    private val _state = MutableLiveData(State.RUNNING)
    val state: LiveData<State> get() = _state

    val items = ObservableArrayList<ConsoleItem>()
    lateinit var args: ActionFragmentArgs

    private val logItems = mutableListOf<String>().synchronized()
    private val outItems = object : CallbackList<String>() {
        override fun onAddElement(e: String?) {
            e ?: return
            items.add(ConsoleItem(e))
            logItems.add(e)
        }
    }

    fun startRunAction() = viewModelScope.launch {
        onResult(withContext(Dispatchers.IO) {
            try {
                Shell.cmd("run_action \'${args.id}\'")
                    .to(outItems, logItems)
                    .exec().isSuccess
            } catch (e: IOException) {
                Timber.e(e)
                false
            }
        })
    }

    private fun onResult(success: Boolean) {
        _state.value = if (success) State.SUCCESS else State.FAILED
    }

    fun onMenuItemClicked(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_save -> savePressed()
        }
        return true
    }

    private fun savePressed() = withExternalRW {
        viewModelScope.launch(Dispatchers.IO) {
            val name = "%s_action_log_%s.log".format(
                args.name,
                System.currentTimeMillis().toTime(timeFormatStandard)
            )
            val file = MediaStoreUtils.getFile(name)
            file.uri.outputStream().bufferedWriter().use { writer ->
                synchronized(logItems) {
                    logItems.forEach {
                        writer.write(it)
                        writer.newLine()
                    }
                }
            }
            SnackbarEvent(file.toString()).publish()
        }
    }
}
