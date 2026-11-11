pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "UTEZStudentWallet"
include(":shared", ":server")
// Allows running the backend on computers without the Android SDK.
if (!providers.gradleProperty("backendOnly").isPresent) include(":app")
