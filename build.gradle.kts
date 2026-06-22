import com.ncorti.ktfmt.gradle.tasks.KtfmtFormatTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	alias(libs.plugins.android.application) apply false
	alias(libs.plugins.kotlinxSerialization) apply false
	alias(libs.plugins.ksp) apply false
	alias(libs.plugins.androidLibrary) apply false
	alias(libs.plugins.compose.compiler) apply false
	alias(libs.plugins.ktfmt)
	alias(libs.plugins.licensee) apply false
    alias(libs.plugins.jetbrains.kotlin.jvm) apply false
}

subprojects {
	apply {
		plugin(rootProject.libs.plugins.ktfmt.get().pluginId)
	}

	plugins.withId("org.jetbrains.kotlin.android") {
		extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension> {
			jvmToolchain(21)
		}
	}

	plugins.withId("org.jetbrains.kotlin.jvm") {
		extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension> {
			jvmToolchain(21)
		}
	}

	tasks.register<KtfmtFormatTask>("format") {
		description = "Format Kotlin code style deviations."
        source = project.fileTree(rootDir)
		include("**/*.kt")
		exclude("**/build/**", ".*generated.*", "**/amneziawg-tools/**", "**/.gradle/**")
	}

	ktfmt {
		kotlinLangStyle()
	}
}
