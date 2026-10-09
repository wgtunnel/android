import com.ncorti.ktfmt.gradle.tasks.KtfmtBaseTask
import com.ncorti.ktfmt.gradle.tasks.KtfmtCheckTask
import com.ncorti.ktfmt.gradle.tasks.KtfmtFormatTask

plugins {
	alias(libs.plugins.android.application) apply false
	alias(libs.plugins.kotlinxSerialization) apply false
	alias(libs.plugins.ksp) apply false
	alias(libs.plugins.androidLibrary) apply false
	alias(libs.plugins.compose.compiler) apply false
	alias(libs.plugins.ktfmt)
	alias(libs.plugins.licensee) apply false
    alias(libs.plugins.jetbrains.kotlin.jvm) apply false
	alias(libs.plugins.aboutlibraries) apply false
}

ktfmt { kotlinLangStyle() }

fun KtfmtBaseTask.useRepoKotlinSources() {
	source = fileTree(rootDir)
	include("**/*.kt")
	exclude("**/build/**", ".*generated.*", "**/amneziawg-tools/**", "**/.gradle/**")
}

tasks.register<KtfmtFormatTask>("format") {
	description = "Format Kotlin sources with the same scope as formatCheck."
	useRepoKotlinSources()
}

tasks.register<KtfmtCheckTask>("formatCheck") {
	description = "Fail if Kotlin sources would change under ./gradlew format."
	useRepoKotlinSources()
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

	ktfmt {
		kotlinLangStyle()
	}
}
