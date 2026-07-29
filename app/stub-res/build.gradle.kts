plugins {
    alias(libs.plugins.android.application)
}

setupCommon()

android {
    namespace = "andro.pluginsuite"
    enableKotlin = false

    buildTypes {
        release {
            isShrinkResources = false
        }
    }
}
