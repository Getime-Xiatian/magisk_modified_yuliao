package andro.pluginsuite.ui.install

import andro.pluginsuite.R
import andro.pluginsuite.arch.BaseFragment
import andro.pluginsuite.arch.viewModel
import andro.pluginsuite.databinding.FragmentInstallMd2Binding
import andro.pluginsuite.core.R as CoreR

class InstallFragment : BaseFragment<FragmentInstallMd2Binding>() {

    override val layoutRes = R.layout.fragment_install_md2
    override val viewModel by viewModel<InstallViewModel>()

    override fun onStart() {
        super.onStart()
        requireActivity().setTitle(CoreR.string.install)
    }
}
