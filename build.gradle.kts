import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// ---------------------------------------------------------------------------------------------
// Version
// ---------------------------------------------------------------------------------------------
// versionName is what people see. Change it by hand when you release something new.
val appVersionName = "2.0.0"

// versionCode must grow with every build, otherwise Android refuses to install the new APK over
// the old one. Minutes since 1970 always grows (also if the repository is ever re-created) and fits
// into an Int for thousands of years. Pass -PversionCode=NUMBER to override it.
val appVersionCode: Int =
    project.findProperty("versionCode")?.toString()?.toIntOrNull()
        ?: (System.currentTimeMillis() / 60_000L).toInt()

// ---------------------------------------------------------------------------------------------
// Signing key
// ---------------------------------------------------------------------------------------------
// Android only installs an update over an installed app when both are signed with the SAME key.
// The key that ships in keystore/ is used by default. To use your own private key instead, set the
// KEYSTORE_FILE, KEYSTORE_PASSWORD, KEY_ALIAS and KEY_PASSWORD environment variables (the GitHub
// workflow does this when the repository secrets described in the README exist).
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore/keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(envName: String, propName: String): String? =
    System.getenv(envName)?.takeIf { it.isNotBlank() } ?: keystoreProps.getProperty(propName)

val signingStoreFile: File =
    System.getenv("KEYSTORE_FILE")?.takeIf { it.isNotBlank() }?.let { File(it) }
        ?: rootProject.file("keystore/" + (keystoreProps.getProperty("storeFile") ?: "battery-float.p12"))

android {
    namespace = "com.example.battery_float"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.battery_float"
        minSdk = 21
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        create("release") {
            storeFile = signingStoreFile
            storePassword = signingValue("KEYSTORE_PASSWORD", "storePassword")
            keyAlias = signingValue("KEY_ALIAS", "keyAlias")
            keyPassword = signingValue("KEY_PASSWORD", "keyPassword")
            // PKCS12 also opens older JKS key files on JDK 9 and newer.
            storeType = "pkcs12"
        }
    }

    buildTypes {
        release {
            // Shrinks code and resources: the APK ends up a few hundred KB.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            // Same key as release, so even a debug build can update an installed copy.
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        // Lint is not part of the APK build; it must never turn a good build red.
        checkReleaseBuilds = false
        abortOnError = false
    }

    // Do not embed the dependency list in the APK: smaller file, nothing to leak.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        resources {
            // Kotlin metadata that is only needed at compile time.
            excludes += setOf(
                "/META-INF/*.version",
                "/META-INF/*.kotlin_module",
                "/kotlin/**",
                "/kotlin-tooling-metadata.json",
                "/DebugProbesKt.bin"
            )
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
