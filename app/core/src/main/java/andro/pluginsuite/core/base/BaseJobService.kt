package andro.pluginsuite.core.base

import android.app.job.JobService
import android.content.Context
import andro.pluginsuite.core.patch

abstract class BaseJobService : JobService() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base.patch())
    }
}
