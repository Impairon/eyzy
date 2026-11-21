import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

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

// The two cleaned root images are the only source artwork. Generate Android-sized
// resources from them at build time so the app never decodes the oversized masters.
val brandResDir = layout.buildDirectory.dir("generated/res/swizyBrand")
val copySwizyBrandAssets by tasks.registering(Sync::class) {
    from(rootProject.file("katakito_no_background.png")) {
        into("drawable-nodpi")
        rename { "swizy_logo.png" }
    }
    from(rootProject.file("katakito.png")) {
        into("drawable-nodpi")
        rename { "swizy_logo_with_bg.png" }
    }
    into(brandResDir)

    doLast {
        fun writeOptimizedPng(source: File, destination: File, maxSide: Int) {
            val original = requireNotNull(ImageIO.read(source)) {
                "Could not read Swizy artwork: ${source.absolutePath}"
            }
            val scale = minOf(1.0, maxSide.toDouble() / maxOf(original.width, original.height))
            val width = (original.width * scale).toInt().coerceAtLeast(1)
            val height = (original.height * scale).toInt().coerceAtLeast(1)
            val resized = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            val graphics = resized.createGraphics()
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.drawImage(original, 0, 0, width, height, null)
            graphics.dispose()
            destination.parentFile.mkdirs()
            check(ImageIO.write(resized, "png", destination)) {
                "Could not write optimized Swizy artwork: ${destination.absolutePath}"
            }
            original.flush()
            resized.flush()
        }

        val drawableDir = brandResDir.get().dir("drawable-nodpi").get().asFile
        writeOptimizedPng(rootProject.file("katakito_no_background.png"), drawableDir.resolve("swizy_logo.png"), 512)
        writeOptimizedPng(rootProject.file("katakito_no_background.png"), drawableDir.resolve("swizy_logo_small.png"), 384)
        writeOptimizedPng(rootProject.file("katakito.png"), drawableDir.resolve("swizy_logo_with_bg.png"), 512)
    }
}

android.sourceSets.getByName("main").res.srcDir(
    layout.buildDirectory.dir("generated/res/swizyBrand")
)
tasks.named("preBuild").configure {
    dependsOn(copySwizyBrandAssets)
}
