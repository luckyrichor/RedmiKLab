plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.redmiklab.diagnostics"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:storage"))
    testImplementation(libs.junit)
}
