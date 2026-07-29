package andro.pluginsuite.ui.flash

import andro.pluginsuite.R
import andro.pluginsuite.databinding.DiffItem
import andro.pluginsuite.databinding.ItemWrapper
import andro.pluginsuite.databinding.RvItem

class ConsoleItem(
    override val item: String
) : RvItem(), DiffItem<ConsoleItem>, ItemWrapper<String> {
    override val layoutRes = R.layout.item_console_md2
}
