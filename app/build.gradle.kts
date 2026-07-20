import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.lina"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.lina"
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // Claude API Key aus local.properties (nicht im Git); leer = Ebene 2 aus
        val localProps = Properties().apply {
            val f = rootProject.file("local.properties")
            if (f.exists()) f.inputStream().use { load(it) }
        }
        buildConfigField(
            "String",
            "CLAUDE_API_KEY",
            "\"${localProps.getProperty("CLAUDE_API_KEY") ?: ""}\"",
        )

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            // sherpa-onnx-AAR liefert unter x86 eine überflüssige libonnxruntime.so
            // mit, die mit dem Microsoft-ONNX-Runtime-AAR kollidiert. Wir shippen
            // ohnehin nur ARM (abiFilters).
            excludes += "lib/x86/**"
            excludes += "lib/x86_64/**"
        }
        resources {
            // Anthropic-SDK zieht Apache HttpClient5 mit, dessen Jars gleichnamige
            // META-INF-Dateien mitbringen
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE*"
            excludes += "META-INF/NOTICE*"
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Wake Word – OpenWakeWord (ONNX Runtime)
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.17.0")

    // TTS (Piper) + STT (Whisper) – sherpa-onnx
    // AAR via scripts/download-models.sh (statisch gelinktes ONNX Runtime,
    // damit keine libonnxruntime.so-Kollision mit dem Microsoft-AAR entsteht)
    implementation(files("libs/sherpa-onnx-static-link-onnxruntime-1.13.3.aar"))

    // STT – Vosk (offline, Deutsch)
    implementation("com.alphacephei:vosk-android:0.3.47")

    // Claude API – freie Konversation (Ebene 2, siehe CLAUDE.md Vision)
    implementation("com.anthropic:anthropic-java:2.34.0")

    // WorkManager (Background Sync)
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // ExoPlayer (Hörbuch-Wiedergabe)
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")

    // Unit-Tests (reine JVM – Parser und Intent-Erkennung, kein Gerät nötig)
    testImplementation("junit:junit:4.13.2")

    // CameraX (Dokument-Foto für Vorlesen per Vision)
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
}
