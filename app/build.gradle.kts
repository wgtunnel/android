import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.grgit)
    alias(libs.plugins.licensee)
}

android {
    namespace = Constants.APP_ID
    compileSdk = Constants.TARGET_SDK

    androidResources { generateLocaleConfig = true }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    ksp { arg("room.schemaLocation", "$projectDir/schemas") }

    // fix okhttp proguard issue
    packaging { resources { pickFirsts.add("okhttp3/internal/publicsuffix/publicsuffixes.gz") } }

    splits {
        abi {
            isEnable = !project.hasProperty("noSplits")
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = !project.hasProperty("noSplits")
        }
    }

    defaultConfig {
        applicationId = Constants.APP_ID
        minSdk = Constants.MIN_SDK
        targetSdk = Constants.TARGET_SDK
        versionCode = computeVersionCode()
        versionName = computeVersionName()

        sourceSets { getByName("debug").assets.srcDirs(files("$projectDir/schemas")) }

        val languagesArray = buildLanguagesArray(languageList())
        buildConfigField("String[]", "LANGUAGES", "new String[]{ $languagesArray }")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create(Constants.RELEASE) {
            storeFile = file(System.getenv("KEY_STORE_PATH") ?: "keystore/android_keystore.jks")
            storePassword =
                LocalProperties.get("SIGNING_STORE_PASSWORD")
                    ?: System.getenv("SIGNING_STORE_PASSWORD")
            keyAlias =
                LocalProperties.get("SIGNING_KEY_ALIAS") ?: System.getenv("SIGNING_KEY_ALIAS")
            keyPassword =
                LocalProperties.get("SIGNING_KEY_PASSWORD") ?: System.getenv("SIGNING_KEY_PASSWORD")
        }
    }

    buildTypes {
        packaging.jniLibs.keepDebugSymbols.addAll(
            listOf("libwg-go.so", "libwg-quick.so", "libwg.so")
        )

        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName(Constants.RELEASE)
            resValue("string", "provider", "\"${Constants.APP_NAME}.provider\"")
        }

        debug {
            applicationIdSuffix = ".debug"
            resValue("string", "app_name", "WG Tunnel Debug")
            isDebuggable = true
            resValue("string", "provider", "\"${Constants.APP_NAME}.provider.debug\"")
        }

        create(Constants.NIGHTLY) {
            initWith(buildTypes.getByName(Constants.RELEASE))
            applicationIdSuffix = ".nightly"
            resValue("string", "app_name", "WG Tunnel Nightly")
            resValue("string", "provider", "\"${Constants.APP_NAME}.provider.nightly\"")
        }
    }

    flavorDimensions.add("type")
    productFlavors {
        create("fdroid") {
            dimension = "type"
            buildConfigField("String", "FLAVOR", "\"fdroid\"")
        }
        create("google") {
            dimension = "type"
            buildConfigField("String", "FLAVOR", "\"google\"")
        }
        create("standalone") {
            dimension = "type"
            buildConfigField("String", "FLAVOR", "\"standalone\"")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
            freeCompilerArgs = listOf("-XXLanguage:+PropertyParamAnnotationDefaultTargetMode")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }

    licensee {
        allowedLicenses().forEach { allow(it) }
        allowedLicenseUrls().forEach { allowUrl(it) }
        // foss, but missing license
        ignoreDependencies("com.github.T8RIN.QuickieExtended")
    }

    android.applicationVariants.all {
        val variant = this

        val abiNameMap =
            mapOf(
                "armeabi-v7a" to "armv7",
                "arm64-v8a" to "arm64",
                "x86" to "x86",
                "x86_64" to "x64",
            )

        variant.outputs.all {
            val output = this as BaseVariantOutputImpl
            val abi = output.getFilter("ABI")

            val baseFileName = "${Constants.APP_NAME}-${variant.flavorName}-v${variant.versionName}"

            val outputFileName =
                if (!abi.isNullOrEmpty()) {
                    val shortAbiName = abiNameMap.getOrDefault(abi, abi)
                    "${baseFileName}-${shortAbiName}.apk"
                } else {
                    "${baseFileName}.apk"
                }

            output.outputFileName = outputFileName
        }
    }
}

dependencies {
    implementation(project(":logcatter"))
    implementation(project(":networkmonitor"))

    // Core foundations
    implementation(libs.bundles.androidx.core.full)
    implementation(libs.bundles.androidx.lifecycle.core)
    implementation(libs.bundles.androidx.appcompat)
    implementation(libs.bundles.androidx.storage)

    // Compose setup
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.androidx.compose.ui)
    implementation(libs.bundles.androidx.compose.material)
    implementation(libs.androidx.activity.compose)

    // Navigation
    implementation(libs.bundles.androidx.navigation3)
    implementation(libs.bundles.navigation.lifecycle)

    // Material and icons
    implementation(libs.bundles.google.material)
    implementation(libs.bundles.material.icons)

    // Database
    implementation(libs.bundles.androidx.room)
    implementation(libs.bundles.androidx.datastore)
    ksp(libs.androidx.room.compiler)

    implementation(libs.bundles.androidx.work)

    // Networking and serialization
    implementation(libs.bundles.ktor.client)
    implementation(libs.bundles.kotlinx.serialization)
    implementation(libs.ipaddress)

    // State management
    implementation(libs.bundles.orbit.mvi)

    // Tunnel
    implementation(libs.bundles.wireguard.tunnel)

    // Shizuku
    implementation(libs.bundles.shizuku)

    // UI utilities
    implementation(libs.bundles.ui.utilities)

    // Misc utilities
    implementation(libs.bundles.misc.utilities)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Accompanist
    implementation(libs.bundles.accompanist)

    // Lifecycle Compose
    implementation(libs.lifecycle.runtime.compose)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.manifest)

    debugImplementation(libs.leakcanary.android)

    // Room database backup
    implementation(libs.roomdatabasebackup) {
        exclude(group = "org.reactivestreams", module = "reactive-streams")
    }

    // DI
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.androidx.navigation)
    implementation(libs.koin.lazy)
    implementation(libs.koin.worker)
}

tasks.register<Copy>("copyLicenseeJsonToAssets") {
    dependsOn("licensee")
    val outputAssets = layout.projectDirectory.dir("src/main/assets")
    from(layout.buildDirectory.file("reports/licensee/androidFdroidRelease/artifacts.json")) {
        rename("artifacts.json", "licenses.json")
    }
    into(outputAssets)
}

tasks.named("preBuild") { dependsOn("copyLicenseeJsonToAssets") }

// https://gist.github.com/obfusk/61046e09cee352ae6dd109911534b12e#fix-proposed-by-linsui-disable-baseline-profiles
tasks.whenTaskAdded {
    if (name.contains("ArtProfile")) {
        enabled = false
    }
}
