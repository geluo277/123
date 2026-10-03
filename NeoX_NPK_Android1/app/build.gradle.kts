plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.neox.npkunpacker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.neox.npkunpacker"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
}

dependencies {
    implementation("androidx.documentfile:documentfile:1.0.1")
}
