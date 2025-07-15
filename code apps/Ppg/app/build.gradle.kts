plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services") // 🔥 Plugin Firebase
}

android {
    namespace = "com.example.ppgekg"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.ppgekg"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    lint {
        abortOnError = false
        baseline = file("lint-baseline.xml")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // 🔥 Bluetooth Serial Classic (SPP)
    implementation("com.github.douglasjunior:AndroidBluetoothLibrary:0.3.5") {
        exclude(group = "com.android.support")
        exclude(module = "support-compat")
    }

    // 🔥 MPAndroidChart untuk grafik
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // 🔥 GraphView untuk menampilkan gelombang PPG
    implementation("com.jjoe64:graphview:4.2.2")

    // 🔥 Firebase (Menggunakan BOM agar versi selalu kompatibel)
    implementation(platform("com.google.firebase:firebase-bom:32.7.3"))
    implementation("com.google.firebase:firebase-firestore-ktx") // Firestore Database
    implementation("com.google.firebase:firebase-auth-ktx") // Firebase Authentication
    implementation("com.jjoe64:graphview:4.2.2")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)


}

// 🔹 Hindari konflik dengan library lama
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "androidx.core" && requested.name == "core") {
            useVersion("1.15.0") // 🔥 Paksa AndroidX Core versi terbaru
        }
    }
}
