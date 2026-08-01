plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.redmiklab.storage"
    compileSdk = 37
    defaultConfig {
        minSdk = 26
    }
    testOptions {
        targetSdk = 35
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":core:model"))
    api(libs.room.runtime)
    annotationProcessor(libs.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.room.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
