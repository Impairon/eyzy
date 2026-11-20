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
    implementation("androidx.biometric:biometric:1.1.0")
    testImplementation("junit:junit:4.13.2")
}

// Keep the brand artwork in the repository root as the single source of truth.
// These generated Android resources are copied from the two supplied logo variants.
val copySwizyBrandAssets by tasks.registering(Sync::class) {
    from(rootProject.file("katakito_no_background.png")) {
        into("drawable-nodpi")
        rename { "swizy_logo.png" }
    }
    from(rootProject.file("katakito.png")) {
        into("drawable-nodpi")
        rename { "swizy_logo_with_bg.png" }
    }
    from(rootProject.file("katib/app/src/main/res/font/plex_arabic_regular.ttf")) {
        into("font")
        rename { "ibm_plex_arabic_regular.ttf" }
    }
    from(rootProject.file("katib/app/src/main/res/font/plex_arabic_medium.ttf")) {
        into("font")
        rename { "ibm_plex_arabic_medium.ttf" }
    }
    from(rootProject.file("katib/app/src/main/res/font/amiri_bold.ttf")) {
        into("font")
        rename { "amiri_bold.ttf" }
    }
    into(layout.buildDirectory.dir("generated/res/swizyBrand"))
}

android.sourceSets.getByName("main").res.srcDir(
    layout.buildDirectory.dir("generated/res/swizyBrand")
)
tasks.named("preBuild").configure {
    dependsOn(copySwizyBrandAssets)
}
