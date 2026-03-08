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
        mavenLocal {
            content {
                includeGroup("io.github.libxposed")
            }
        }
        maven("https://jitpack.io")
    }
    versionCatalogs {
        create("libs")
    }
}

rootProject.name = "QSBoundlessTiles"

include(":app")
