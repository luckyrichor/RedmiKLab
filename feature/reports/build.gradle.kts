plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.redmiklab.reports"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    testOptions {
        targetSdk = 35
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:storage"))
    testImplementation(libs.junit)
    testImplementation(libs.room.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
