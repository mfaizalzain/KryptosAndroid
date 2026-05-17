import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Load upload-keystore credentials from either:
//   1. KRYPTOS_KEYSTORE_PROPERTIES env var (path to props file), or
//   2. ~/keystores/kryptos-upload.properties (default), or
//   3. keystore.properties in project root (legacy)
val keystoreProps = Properties().apply {
    val candidates = listOfNotNull(
        System.getenv("KRYPTOS_KEYSTORE_PROPERTIES")?.let { file(it) },
        file("${System.getProperty("user.home")}/keystores/kryptos-upload.properties"),
        rootProject.file("keystore.properties"),
    )
    candidates.firstOrNull { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.fmz.kryptos"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fmz.kryptos"
        resValue("string", "google_web_client_id",
            "706867595241-e3ck7u69mnp2dtgf38vpu22k4ic5pcv9.apps.googleusercontent.com")
        minSdk = 26
        targetSdk = 35
        versionCode = 11
        versionName = "1.1.0"
    }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }



    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }
    buildTypes {
        create("signedDebug") {
            initWith(getByName("debug"))
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/DEPENDENCIES",
            "META-INF/INDEX.LIST",
            "META-INF/LICENSE*",
            "META-INF/NOTICE*",
            "META-INF/*.SF",
            "META-INF/*.DSA",
            "META-INF/*.RSA",
            "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
        )
    }
}


kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.sqlite.ktx)
    implementation(libs.sqlcipher)

    implementation(libs.androidx.biometric)
    implementation(libs.androidx.security.crypto)

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.google.id)

    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.mlkit.document.scanner)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.mlkit.vision)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // ePassport (ICAO 9303) NFC reading.
    // JMRTD's transitive scuba-smartcards is desktop-only; exclude in favor of scuba-sc-android.
    implementation(libs.jmrtd) {
        exclude(group = "net.sf.scuba", module = "scuba-smartcards")
    }
    implementation(libs.scuba.sc.android)

    implementation(libs.coil.compose)
    implementation(libs.play.services.auth)
    implementation(libs.billing.ktx)
    implementation(libs.zxing.core)
    implementation(libs.androidx.work.runtime.ktx)

    debugImplementation(libs.androidx.ui.tooling)
}
