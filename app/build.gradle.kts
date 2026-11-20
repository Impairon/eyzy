plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.abo.avatarblackout"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.abo.avatarblackout"
        minSdk = 24
        targetSdk = 34
        versionCode = 3
        versionName = "1.7.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
