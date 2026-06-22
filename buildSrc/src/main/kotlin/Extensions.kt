import org.gradle.api.Project
import org.gradle.api.provider.Provider

fun Project.languageListProvider(): Provider<List<String>> {
    return providers.provider {
        fileTree("../app/src/main/res") { include("**/strings.xml") }
            .asSequence()
            .map { stringFile -> stringFile.parentFile.name }
            .map { valuesFolderName -> valuesFolderName.replace("values-", "") }
            .filter { valuesFolderName -> valuesFolderName != "values" }
            .map { languageCode -> languageCode.replace("-r", "_") }
            .distinct()
            .sorted()
            .toList() + "en"
    }
}

fun allowedLicenses(): List<String> {
    return listOf("MIT", "Apache-2.0", "BSD-3-Clause")
}

fun allowedLicenseUrls(): List<String> {
    return listOf(
        "https://jsoup.org/license",
        "http://opensource.org/licenses/bsd-license.php",
        "https://github.com/RikkaApps/Shizuku-API/blob/master/LICENSE",
        "https://github.com/rafi0101/Android-Room-Database-Backup/blob/master/LICENSE",
        "https://opensource.org/license/mit",
        "https://www.bouncycastle.org/licence.html",
    )
}

fun buildLanguagesArray(languages: List<String>): String {
    return languages.joinToString(separator = ", ") { "\"$it\"" }
}

fun bumpToNextPatchVersion(version: String): String {
    val parts = version.split(".")
    return if (parts.size == 3) {
        val patch = parts[2].toIntOrNull() ?: 0
        "${parts[0]}.${parts[1]}.${patch + 1}"
    } else {
        "$version-next"
    }
}

fun Project.getGitCommitHash(): String {
    return providers
        .provider {
            val ciSha =
                System.getenv("GITHUB_SHA")
                    ?: System.getenv("CI_COMMIT_SHA")
                    ?: System.getenv("GIT_COMMIT")

            if (ciSha != null) {
                return@provider ciSha.take(7)
            }

            // Local only
            runGitCommand(listOf("rev-parse", "--short", "HEAD"))
        }
        .get()
}

private fun Project.runGitCommand(args: List<String>): String {
    return providers
        .exec {
            commandLine("git", *args.toTypedArray())
            workingDir = projectDir
            isIgnoreExitValue = true
        }
        .standardOutput
        .asText
        .get()
        .trim()
}

fun Project.getCommitCountSinceLastCommit(): Int {
    return providers
        .provider {
            val output = runGitCommand(listOf("rev-list", "--count", "HEAD"))
            output.toIntOrNull() ?: 0
        }
        .get()
}

// Get versionCode increment for nightly
fun Project.getVersionCodeIncrement(): Int {
    val isNightlyBuild = gradle.startParameter.taskNames.any { it.lowercase().contains("nightly") }
    if (!isNightlyBuild) return 0

    return System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
        ?: System.getenv("CI_BUILD_NUMBER")?.toIntOrNull()
        ?: getCommitCountSinceLastCommit()
}

fun Project.isNightlyBuild(): Boolean {
    return gradle.startParameter.taskNames.any { it.lowercase().contains(Constants.NIGHTLY) }
}
