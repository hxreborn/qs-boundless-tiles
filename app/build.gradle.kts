plugins {
    alias(libs.plugins.agp.app)
}

android {
    namespace = "eu.hxreborn.qsboundlesstiles"
    compileSdk = 37

    defaultConfig {
        applicationId = "eu.hxreborn.qsboundlesstiles"
        minSdk = 33
        targetSdk = 37
        versionCode = 311
        versionName = "3.1.1"
    }

    signingConfigs {
        create("release") {
            fun secret(name: String): String? =
                providers
                    .gradleProperty(name)
                    .orElse(providers.environmentVariable(name))
                    .orNull

            val storeFilePath = secret("RELEASE_STORE_FILE")
            if (!storeFilePath.isNullOrBlank()) {
                storeFile = file(storeFilePath)
                storePassword = secret("RELEASE_STORE_PASSWORD")
                keyAlias = secret("RELEASE_KEY_ALIAS")
                keyPassword = secret("RELEASE_KEY_PASSWORD")
                storeType = secret("RELEASE_STORE_TYPE") ?: "PKCS12"
            } else {
                logger.warn("RELEASE_STORE_FILE not found. Release signing is disabled.")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig =
                signingConfigs
                    .getByName("release")
                    .takeIf { it.storeFile != null }
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources {
            merges += "META-INF/xposed/*"
            excludes += "**"
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
        disable.addAll(listOf("PrivateApi", "DiscouragedPrivateApi"))
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    compileOnly(libs.libxposed.api)
}

val ktlintCheck by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Check Kotlin code style"
    mainClass.set("com.pinterest.ktlint.Main")
    classpath = configurations.detachedConfiguration(
        dependencies.create("com.pinterest.ktlint:ktlint-cli:1.8.0"),
    )
    args("src/**/*.kt")
}

val ktlintFormat by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Auto-fix Kotlin code style"
    mainClass.set("com.pinterest.ktlint.Main")
    classpath = configurations.detachedConfiguration(
        dependencies.create("com.pinterest.ktlint:ktlint-cli:1.8.0"),
    )
    args("-F", "src/**/*.kt")
}
