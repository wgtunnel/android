plugins {
    alias(libs.plugins.androidLibrary)
}

android {
    namespace = "com.zaneschepke.wstunnel"
    version = "1.0.0"

    compileSdk {
        version = release(Constants.TARGET_SDK)
    }

    defaultConfig {
        minSdk = Constants.MIN_SDK

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        // wstunnel (github.com/erebe/wstunnel) only publishes a prebuilt Android binary for
        // arm64 as of v10.6.1 - no armeabi-v7a/x86/x86_64 releases exist upstream. On those
        // ABIs, WsTunnelService.isSupported() returns false and the feature should be hidden
        // in the UI rather than attempted.
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        create(Constants.NIGHTLY) { initWith(getByName(Constants.RELEASE)) }
    }

    // We are bundling a prebuilt Rust binary (renamed libwstunnel.so) rather than
    // compiling native code in this module, so no externalNativeBuild block here.
    // src/main/jniLibs/<abi>/libwstunnel.so is picked up automatically by AGP.
}

dependencies {
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
}
