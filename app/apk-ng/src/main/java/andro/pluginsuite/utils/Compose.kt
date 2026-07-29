package andro.pluginsuite.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import andro.pluginsuite.core.utils.TextHolder

@Composable
fun textHolder(holder: TextHolder) = holder.getText(LocalResources.current)
