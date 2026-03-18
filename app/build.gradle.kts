plugins {
    alias(libs.plugins.agp.app)
    alias(libs.plugins.kotlin.compose)
}

val xposedScopePackage: Provider<String> =
    providers
        .fileContents(layout.projectDirectory.file("src/main/resources/META-INF/xposed/scope.list"))
        .asText
        .map { content ->
            content
                .lineSequence()
                .first { it.isNotBlank() }
                .trim()
        }

android {
    namespace = "eu.hxreborn.qsboundlesstiles"
    compileSdk = 36

    defaultConfig {
        applicationId = "eu.hxreborn.qsboundlesstiles"
        minSdk = 33
        targetSdk = 36
        versionCode = 300
        versionName = "3.0.0"
        buildConfigField("String", "SYSTEMUI_PACKAGE", "\"${xposedScopePackage.get()}\"")
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
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources {
            pickFirsts += "META-INF/xposed/*"
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
        disable.addAll(listOf("PrivateApi", "DiscouragedPrivateApi", "QueryAllPackagesPermission"))
        ignoreTestSources = true
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)
    implementation(libs.material)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.core.ktx)
    implementation(libs.libsu.core)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    debugImplementation(libs.compose.ui.tooling)
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
