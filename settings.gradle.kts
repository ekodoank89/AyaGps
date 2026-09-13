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
        // Repository resmi Xposed API (de.robv.android.xposed:api)
        maven { url = uri("https://api.xposed.info/") }
    }
}

rootProject.name = "AyaGps"
include(":app")
