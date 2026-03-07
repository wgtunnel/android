plugins {
	alias(libs.plugins.android.application) apply false
	alias(libs.plugins.kotlin.android) apply false
	alias(libs.plugins.kotlinxSerialization) apply false
	alias(libs.plugins.ksp) apply false
	alias(libs.plugins.androidLibrary) apply false
	alias(libs.plugins.compose.compiler) apply false
	alias(libs.plugins.ktfmt)
	alias(libs.plugins.licensee) apply false
}

subprojects {
	apply {
		plugin(rootProject.libs.plugins.ktfmt.get().pluginId)
	}

	ktfmt {
		kotlinLangStyle()
	}
}
