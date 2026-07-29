package andro.pluginsuite.ui.theme

import andro.pluginsuite.arch.BaseViewModel
import andro.pluginsuite.core.Config
import andro.pluginsuite.dialog.DarkThemeDialog
import andro.pluginsuite.events.RecreateEvent
import andro.pluginsuite.view.TappableHeadlineItem

class ThemeViewModel : BaseViewModel(), TappableHeadlineItem.Listener {

    val themeHeadline = TappableHeadlineItem.ThemeMode

    override fun onItemPressed(item: TappableHeadlineItem) = when (item) {
        is TappableHeadlineItem.ThemeMode -> DarkThemeDialog().show()
    }

    fun saveTheme(theme: Theme) {
        if (!theme.isSelected) {
            Config.themeOrdinal = theme.ordinal
            RecreateEvent().publish()
        }
    }
}
