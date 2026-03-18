pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()

        maven("https://jitpack.io")
    }
    versionCatalogs {
        create("libs")
    }
}

rootProject.name = "QSBoundlessTiles"

include(":app")
