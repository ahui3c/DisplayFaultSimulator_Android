import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

val signingPropertiesFile = rootProject.file("keystore.properties")
val signingProperties = Properties().apply {
    if (signingPropertiesFile.isFile) signingPropertiesFile.inputStream().use(::load)
}
val hasConsistentSigning = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    .all { signingProperties.getProperty(it).isNullOrBlank().not() }

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
        versionCode = 10701
        versionName = "1.7.1"
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
