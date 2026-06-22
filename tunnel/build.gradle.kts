plugins {
    alias(libs.plugins.androidLibrary)
}

android {
    namespace = "com.zaneschepke.tunnel"
    compileSdk {
        version = release(Constants.TARGET_SDK)
    }

    ndkVersion = Constants.NDK_VERSION

    defaultConfig {
        minSdk = Constants.MIN_SDK

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    externalNativeBuild {
        cmake {
            path("tools/CMakeLists.txt")
        }
    }

    val basePackageName = namespace

    buildTypes {
        all {
            externalNativeBuild {
                cmake {
                    targets("libam-go.so", "libam.so", "libam-quick.so")
                    arguments("-DGRADLE_USER_HOME=${project.gradle.gradleUserHomeDir}")
                    arguments("-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON")
                }
            }
        }

        release {
            externalNativeBuild {
                cmake {
                    arguments("-DANDROID_PACKAGE_NAME=$basePackageName")
                }
            }
        }

        debug {
            externalNativeBuild {
                cmake {
                    arguments("-DANDROID_PACKAGE_NAME=$basePackageName.debug")
                }
            }
        }
        create(Constants.NIGHTLY) { initWith(getByName(Constants.RELEASE)) }
    }
}

dependencies {
    implementation(project(":hevtunnel"))
    api(project(":pinger"))
    implementation(project(":networkmonitor"))

    implementation(libs.androidx.lifecycle.service)

    implementation(libs.relinker)

    api(libs.amneziawg.parser)
    implementation(libs.libsu)

    implementation(libs.timber)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}