plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.lecturasgas.beta"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lecturasgas.beta"
        minSdk = 26
        targetSdk = 35
        versionCode = 58
        versionName = "0.7.9"
    }

    buildTypes {
        release {
            // Versión liviana y rápida para el celular de trabajo (Build Variants ▸ release).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Se firma con la misma clave de depuración para poder instalarla directo, sin crear llaves.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Mapa (misma versión que ya funcionaba)
    implementation("org.maplibre.gl:android-sdk:11.11.0")

    // Ubicación de Google (la de Google Maps/Waze). Opcional en el celular: si no la tiene,
    // la app usa el GPS normal del sistema.
    implementation("com.google.android.gms:play-services-location:21.3.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
