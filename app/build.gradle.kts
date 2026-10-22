plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.nikoapps.dialer"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.nikoapps.dialer"
        minSdk = 26
        targetSdk = 37
        versionCode = providers.gradleProperty("VERSION_CODE").get().toInt()
        versionName = providers.gradleProperty("VERSION_NAME").get()
    }

    // Release signing only applies when the keystore is provided, so F-Droid
    // and local builds still produce an unsigned release APK.
    val keystoreFile = providers.environmentVariable("NIKO_KEYSTORE_FILE").orNull
    if (keystoreFile != null) {
        signingConfigs {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = providers.environmentVariable("NIKO_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("NIKO_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("NIKO_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystoreFile != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // Lets people pick Niko Dialer's language on its own in Android 13+ settings.
    androidResources {
        generateLocaleConfig = true
    }

    lint {
        // Accessibility issues fail the build.
        error += listOf("ContentDescription", "ClickableViewAccessibility", "KeyboardInaccessibleWidget", "LabelFor")
    }

    // F-Droid: don't embed Google's dependency metadata blob in the APK.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

// Reproducible builds: baseline profiles change with the build machine's CPU count.
tasks.configureEach {
    if (name.contains("ArtProfile")) enabled = false
}

dependencies {
    implementation(project(":commons"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
