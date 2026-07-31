import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

val localProperties =
    Properties().apply {
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.isFile) {
            localPropertiesFile.inputStream().use(::load)
        }
    }
val googleWebClientId =
    providers
        .gradleProperty("worqorder.google.webClientId")
        .orNull
        ?: localProperties.getProperty("worqorder.google.webClientId")
        ?: throw GradleException(
            "Missing worqorder.google.webClientId. See docs/GOOGLE_SHEETS_SETUP.md.",
        )
require(
    googleWebClientId.matches(
        Regex("""[0-9]+-[A-Za-z0-9_-]+\.apps\.googleusercontent\.com"""),
    ),
) {
    "worqorder.google.webClientId is malformed. See docs/GOOGLE_SHEETS_SETUP.md."
}

val releaseSigningPropertiesFile = rootProject.file("keystore.properties")
val releaseSigningProperties =
    Properties().apply {
        if (releaseSigningPropertiesFile.isFile) {
            releaseSigningPropertiesFile.inputStream().use(::load)
        }
    }
val hasReleaseSigningProperties = releaseSigningPropertiesFile.isFile

fun requiredReleaseSigningProperty(name: String): String =
    releaseSigningProperties
        .getProperty(name)
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: throw GradleException(
            "Missing $name in ignored keystore.properties. " +
                "See docs/RELEASE_CHECKLIST.md.",
        )

val releaseKeystoreFile =
    if (hasReleaseSigningProperties) {
        rootProject.file(requiredReleaseSigningProperty("storeFile"))
    } else {
        rootProject.file(".release-keystore-not-configured")
    }

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
}

android {
    namespace = "worq.order"

    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "worq.order"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField(
            "String",
            "GOOGLE_WEB_CLIENT_ID",
            "\"$googleWebClientId\"",
        )
    }

    signingConfigs {
        create("release") {
            storeFile = releaseKeystoreFile
            storePassword =
                if (hasReleaseSigningProperties) {
                    requiredReleaseSigningProperty("storePassword")
                } else {
                    ""
                }
            keyAlias =
                if (hasReleaseSigningProperties) {
                    requiredReleaseSigningProperty("keyAlias")
                } else {
                    ""
                }
            keyPassword =
                if (hasReleaseSigningProperties) {
                    requiredReleaseSigningProperty("keyPassword")
                } else {
                    ""
                }
        }
    }

    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        getByName("androidTest").assets.directories.add(file("schemas").path)
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
}

ksp {
    arg("room.schemaLocation", file("schemas").path)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.google.identity.googleid)
    implementation(libs.google.play.services.auth)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
