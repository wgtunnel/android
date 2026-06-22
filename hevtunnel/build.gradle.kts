plugins {
    alias(libs.plugins.androidLibrary)
}

android {
    namespace = "com.zaneschepke.hevtunnel"
    version= "1.0.1"

    compileSdk {
        version = release(Constants.TARGET_SDK)
    }

    ndkVersion = Constants.NDK_VERSION

    defaultConfig {
        minSdk = Constants.MIN_SDK

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        externalNativeBuild {
            ndkBuild {
                arguments.add("APP_CFLAGS+=-DPKGNAME=com/zaneschepke/hevtunnel -ffile-prefix-map=${rootDir}=.")
                arguments.add("APP_LDFLAGS+=-Wl,--build-id=none")
            }
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
        create(Constants.NIGHTLY) { initWith(getByName(Constants.RELEASE)) }
    }

    externalNativeBuild {
        ndkBuild {
            path = file("src/main/jni/Android.mk")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}