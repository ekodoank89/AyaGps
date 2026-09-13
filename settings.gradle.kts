pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// Menamai project utama kita
rootProject.name = "HOTSPOT"

// Mendaftarkan folder 'app' sebagai modul Android executable
include(":app")
