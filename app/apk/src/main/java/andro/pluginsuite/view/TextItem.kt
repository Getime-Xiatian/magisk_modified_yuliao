package andro.pluginsuite.view

import andro.pluginsuite.R
import andro.pluginsuite.databinding.DiffItem
import andro.pluginsuite.databinding.ItemWrapper
import andro.pluginsuite.databinding.RvItem

class TextItem(override val item: Int) : RvItem(), DiffItem<TextItem>, ItemWrapper<Int> {
    override val layoutRes = R.layout.item_text
}
