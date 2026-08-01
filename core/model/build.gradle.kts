plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.redmiklab.model"
    compileSdk = 37

    defaultConfig { minSdk = 26 }
}

dependencies { testImplementation(libs.junit) }
