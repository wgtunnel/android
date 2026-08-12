pluginManagement {
	repositories {
		mavenLocal()
		google()
		mavenCentral()
		gradlePluginPortal()
	}
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
//		// Match coordinates from core's mavenPublishing / project name
//		substitute(module("com.wgtunnel.tunnel:backend"))
//			.using(project(":backend"))
//		// if the app also needs the android native AAR wrapper:
//		substitute(module("com.wgtunnel.tunnel:backend-android-jni"))
//			.using(project(":backend-android-jni"))
//	}
//}

include(":app")
include(":logcatter")
include(":networkmonitor")
include(":pinger")
