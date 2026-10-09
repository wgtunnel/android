pluginManagement {
	repositories {
		mavenLocal()
		google()
		mavenCentral()
		gradlePluginPortal()
	}
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
	repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
	repositories {
		mavenLocal()
		google()
		mavenCentral()
		maven { url = uri("https://jitpack.io") }
	}
}

rootProject.name = "WG Tunnel"

// Local dev
//includeBuild("../core") {
//	dependencySubstitution {
//		substitute(module("com.wgtunnel:backend"))
//			.using(project(":backend"))
//		substitute(module("com.wgtunnel:backend-android"))
//			.using(project(":backend"))
//		substitute(module("com.wgtunnel:backend-android-jni"))
//			.using(project(":backend-android-jni"))
//		substitute(module("com.wgtunnel:hevtunnel"))
//			.using(project(":hevtunnel"))
//		substitute(module("com.wgtunnel:parser"))
//			.using(project(":parser"))
//	}
//}

include(":app")
include(":logcatter")
include(":networkmonitor")
include(":pinger")
