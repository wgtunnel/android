plugins {
    alias(libs.plugins.androidLibrary)
}

android {
    namespace = "com.zaneschepke.networkmonitor"
    compileSdk = Constants.TARGET_SDK

    defaultConfig {
        minSdk = Constants.MIN_SDK

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }

        create(Constants.NIGHTLY) { initWith(getByName(Constants.RELEASE)) }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // shizuku
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    implementation(libs.timber)
}
