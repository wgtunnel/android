pluginManagement {
	repositories {
		mavenLocal()
		google()
		mavenCentral()
		gradlePluginPortal()
	}
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
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

include(":app")
include(":logcatter")
include(":networkmonitor")
