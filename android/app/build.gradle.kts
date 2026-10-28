import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// The app always carries CapsuleOS's own version (panalume/config.yaml), so the
// tablet, the TV and the NUC show the same number in the bottom-left corner.
val capsuleVersion: String = rootProject.file("../panalume/config.yaml").readLines()
    .firstNotNullOfOrNull { Regex("""^version:\s*"?([^"\s]+)"?\s*$""").find(it)?.groupValues?.get(1) }
    ?: error("No version in panalume/config.yaml")
val capsuleVersionCode: Int = capsuleVersion.split(".").map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    .let { (it.getOrElse(0) { 0 } * 10000) + (it.getOrElse(1) { 0 } * 100) + it.getOrElse(2) { 0 } }
    .coerceAtLeast(1)

// Release signing comes from the repository's secrets (see android/README.md).
// Without them the release APK is signed with the debug key, which works but
// can't update an APK signed by another machine.
val keystoreB64: String? = System.getenv("ANDROID_KEYSTORE_BASE64")?.takeIf { it.isNotBlank() }

android {
    namespace = "com.capsulecorp.kiosk"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.capsulecorp.kiosk"
        minSdk = 26
        targetSdk = 35
        versionName = capsuleVersion
        versionCode = capsuleVersionCode
    }

    signingConfigs {
        if (keystoreB64 != null) {
            create("release") {
                val file = layout.buildDirectory.file("release.jks").get().asFile
                file.parentFile.mkdirs()
                file.writeBytes(Base64.getDecoder().decode(keystoreB64))
                storeFile = file
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
