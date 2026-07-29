package andro.pluginsuite.arch

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import andro.pluginsuite.core.AppContext
import andro.pluginsuite.core.ktx.toast
import andro.pluginsuite.ui.navigation.Route
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

abstract class BaseViewModel : ViewModel() {

    private val _navEvents = MutableSharedFlow<Route>(extraBufferCapacity = 1)
    val navEvents: SharedFlow<Route> = _navEvents

    fun showSnackbar(@StringRes resId: Int) {
        AppContext.toast(resId, Toast.LENGTH_SHORT)
    }

    fun showSnackbar(msg: String) {
        AppContext.toast(msg, Toast.LENGTH_SHORT)
    }

    fun navigateTo(route: Route) {
        _navEvents.tryEmit(route)
    }
}
