import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

val signingPropertiesFile = rootProject.file("keystore.properties")
val signingProperties = Properties().apply {
    if (signingPropertiesFile.isFile) signingPropertiesFile.inputStream().use(::load)
}
val hasConsistentSigning = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    .all { signingProperties.getProperty(it).isNullOrBlank().not() }
val playSigningPropertiesFile = rootProject.file("play-upload.properties")
val playSigningProperties = Properties().apply {
    if (playSigningPropertiesFile.isFile) playSigningPropertiesFile.inputStream().use(::load)
}
val hasPlayUploadSigning = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    .all { playSigningProperties.getProperty(it).isNullOrBlank().not() }

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "tw.chehu.displayfaultsimulator"
    compileSdk = 36

    defaultConfig {
        applicationId = "tw.chehu.displayfaultsimulator"
        minSdk = 26
        targetSdk = 36
        versionCode = 10900
        versionName = "1.9.0"
    }

    signingConfigs {
        if (hasConsistentSigning) {
            create("consistentDevelopment") {
                storeFile = file(signingProperties.getProperty("storeFile"))
                storePassword = signingProperties.getProperty("storePassword")
                keyAlias = signingProperties.getProperty("keyAlias")
                keyPassword = signingProperties.getProperty("keyPassword")
            }
        }
        if (hasPlayUploadSigning) {
            create("playUpload") {
                storeFile = file(playSigningProperties.getProperty("storeFile"))
                storePassword = playSigningProperties.getProperty("storePassword")
                keyAlias = playSigningProperties.getProperty("keyAlias")
                keyPassword = playSigningProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            if (hasConsistentSigning) signingConfig = signingConfigs.getByName("consistentDevelopment")
        }
        release {
            if (hasConsistentSigning) signingConfig = signingConfigs.getByName("consistentDevelopment")
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("play") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            if (hasPlayUploadSigning) signingConfig = signingConfigs.getByName("playUpload")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
