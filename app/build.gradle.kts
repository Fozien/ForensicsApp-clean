import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

// Читаем с fallback чтобы не крашить сборку если ключа нет
val yandexFolderId = localProperties["YANDEX_FOLDER_ID"]?.toString() ?: ""
val yandexApiKey   = localProperties["YANDEX_API_KEY"]?.toString()   ?: ""

android {
    namespace = "com.example.fixator"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.fixator"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField("String", "YANDEX_FOLDER_ID", "\"$yandexFolderId\"")
            buildConfigField("String", "YANDEX_API_KEY",   "\"$yandexApiKey\"")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "YANDEX_FOLDER_ID", "\"$yandexFolderId\"")
            buildConfigField("String", "YANDEX_API_KEY",   "\"$yandexApiKey\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.vision.common)
    implementation(libs.face.mesh.detection)
    implementation(libs.androidx.camera.view)
    implementation(libs.play.services.mlkit.face.detection)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.exifinterface)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.logging)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.coroutines.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}