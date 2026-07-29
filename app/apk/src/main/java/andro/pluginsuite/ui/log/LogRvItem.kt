package andro.pluginsuite.ui.log

import andro.pluginsuite.R
import andro.pluginsuite.databinding.DiffItem
import andro.pluginsuite.databinding.ItemWrapper
import andro.pluginsuite.databinding.ObservableRvItem

class LogRvItem(
    override val item: String
) : ObservableRvItem(), DiffItem<LogRvItem>, ItemWrapper<String> {
    override val layoutRes = R.layout.item_log_textview
}
