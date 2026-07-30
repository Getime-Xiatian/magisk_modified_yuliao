package andro.pluginsuite.ui.home

import andro.pluginsuite.R
import andro.pluginsuite.core.Const
import andro.pluginsuite.databinding.RvItem
import andro.pluginsuite.core.R as CoreR

interface Dev {
    val name: String
}

private interface JohnImpl : Dev {
    override val name get() = "topjohnwu"
}

private interface GetimeImpl : Dev {
    override val name get() = "Getime-Xiatian"
}

sealed class DeveloperItem : Dev {

    abstract val items: List<IconLink>
    val handle get() = "@${name}"

    object John : DeveloperItem(), JohnImpl {
        override val items =
            listOf(
                IconLink.Github.Project
            )
    }

    object Getime : DeveloperItem(), GetimeImpl {
        override val items =
            listOf<IconLink>(
                object : IconLink.Github.User(), GetimeImpl {}
            )
    }
}

sealed class IconLink : RvItem() {

    abstract val icon: Int
    abstract val title: Int
    abstract val link: String

    override val layoutRes get() = R.layout.item_icon_link

    abstract class Github : IconLink() {
        override val icon get() = CoreR.drawable.ic_github
        override val title get() = CoreR.string.github

        abstract class User : Github(), Dev {
            override val link get() = "https://github.com/$name"
        }

        object Project : Github() {
            override val link get() = Const.Url.SOURCE_CODE_URL
        }
    }
}
