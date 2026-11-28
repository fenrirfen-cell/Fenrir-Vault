import java.util.Properties

fun readXcconfigValue(file: File, key: String): String? {
    if (!file.exists()) return null
    return file.readLines()
        .asSequence()
        .map(String::trim)
        .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains('=') }
        .map { line ->
            val separatorIndex = line.indexOf('=')
            line.substring(0, separatorIndex).trim() to line.substring(separatorIndex + 1).trim()
        }
        .firstOrNull { (entryKey, _) -> entryKey == key }
        ?.second
}

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.androidxBaselineProfile)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.sentry.android.gradle)
}

val localProps = Properties().apply {
    val propsFile = rootProject.file("local.properties")
    if (propsFile.exists()) propsFile.inputStream().use { load(it) }
}
val releaseStoreFile = localProps.getProperty("NUVIO_RELEASE_STORE_FILE")?.takeIf { it.isNotBlank() }
val releaseStorePassword = localProps.getProperty("NUVIO_RELEASE_STORE_PASSWORD")?.takeIf { it.isNotBlank() }
val releaseKeyAlias = localProps.getProperty("NUVIO_RELEASE_KEY_ALIAS")?.takeIf { it.isNotBlank() }
val releaseKeyPassword = localProps.getProperty("NUVIO_RELEASE_KEY_PASSWORD")?.takeIf { it.isNotBlank() }
val releaseKeystore = releaseStoreFile?.let(rootProject::file)
fun envOrLocalProperty(key: String): String? =
    providers.environmentVariable(key).orNull?.trim()?.takeIf { it.isNotBlank() }
        ?: localProps.getProperty(key)?.trim()?.takeIf { it.isNotBlank() }

val sentryAuthToken = envOrLocalProperty("SENTRY_AUTH_TOKEN")
val sentryOrg = envOrLocalProperty("SENTRY_ORG")
val sentryProject = envOrLocalProperty("SENTRY_PROJECT")
val sentryMappingUploadEnabled = sentryAuthToken != null && sentryOrg != null && sentryProject != null
val appVersionConfigFile = rootProject.file("iosApp/Configuration/Version.xcconfig")
val releaseAppVersionName = providers.gradleProperty("nuvio.app.versionName").orNull
    ?: readXcconfigValue(appVersionConfigFile, "MARKETING_VERSION")
    ?: error("MARKETING_VERSION is missing from ${appVersionConfigFile.path}")
val releaseAppVersionCode = readXcconfigValue(appVersionConfigFile, "CURRENT_PROJECT_VERSION")
    ?.toIntOrNull()
    ?: error("CURRENT_PROJECT_VERSION is missing or invalid in ${appVersionConfigFile.path}")
val requestedTaskNames = gradle.startParameter.taskNames.map { it.substringAfterLast(':') }
val buildsReleaseApks = requestedTaskNames.any {
    it.startsWith("assemble", ignoreCase = true) && it.endsWith("Release", ignoreCase = true)
}

android {
    namespace = "com.nuvio.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    compileSdkMinor = libs.versions.android.compileSdkMinor.get().toInt()

    signingConfigs {
        create("release") {
            if (releaseKeystore != null && releaseStorePassword != null && releaseKeyAlias != null && releaseKeyPassword != null) {
                storeFile = releaseKeystore
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    defaultConfig {
        applicationId = "com.nuvio.app"
        // Read mode (Tsundoku) needs Android 8.0+.
        minSdk = maxOf(libs.versions.android.minSdk.get().toInt(), 26)
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        // Fenrir Vault: every published build gets its own, higher version code (Nuvio's code x 1000
        // + build number). Phones cache an app's icon per version, so a code that never changed kept
        // showing an icon saved from an old install.
        val fenrirBuildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
        versionCode = if (fenrirBuildNumber != null) releaseAppVersionCode * 1000 + fenrirBuildNumber else releaseAppVersionCode
        versionName = if (fenrirBuildNumber != null) "$releaseAppVersionName-r$fenrirBuildNumber" else releaseAppVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("full") {
            dimension = "distribution"
        }
        create("playstore") {
            dimension = "distribution"
        }
    }

    sourceSets.getByName("full") {
        manifest.srcFile("src/full/AndroidManifest.xml")
        jniLibs.directories.add("../composeApp/src/full/jniLibs")
    }

    sourceSets.getByName("androidTest").assets.srcDir("../testFixtures")
    sourceSets.getByName("main").baselineProfiles.directories.add("src/main/generated/baselineProfiles")

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // From Tsundoku's packaging rules (Read mode).
            excludes += setOf(
                "kotlin-tooling-metadata.json",
                "LICENSE.txt",
                "META-INF/**/LICENSE.txt",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/NOTICE",
                "META-INF/README.md",
            )
            // CloudStream library dependencies (Jackson, Rhino, NewPipe) ship overlapping metadata.
            excludes += setOf(
                "META-INF/LICENSE.md",
                "META-INF/NOTICE.md",
                "META-INF/NOTICE.txt",
                "META-INF/LICENSE-notice.md",
                "META-INF/INDEX.LIST",
                "META-INF/versions/9/module-info.class",
            )
        }
        jniLibs {
            useLegacyPackaging = true
            pickFirsts += listOf(
                "lib/*/libc++_shared.so",
                "lib/*/libavcodec.so",
                "lib/*/libavutil.so",
                "lib/*/libswscale.so",
                "lib/*/libswresample.so"
            )
        }
    }

    androidResources {
        noCompress += "cvr"
    }

    splits {
        abi {
            isEnable = buildsReleaseApks
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = false
        }
    }

    buildTypes {
        getByName("release") {
            val minifyRelease = providers.gradleProperty("releaseMinifyEnabled")
                .map(String::toBooleanStrict)
                .getOrElse(true)
            isMinifyEnabled = minifyRelease
            isShrinkResources = minifyRelease
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "../composeApp/proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
        create("benchmark") {
            initWith(buildTypes.getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            matchingFallbacks += "release"
        }
        create("fast") {
            // Fenrir Vault everyday build: not debuggable (fast) but no code shrinking (safe).
            initWith(buildTypes.getByName("benchmark"))
            isMinifyEnabled = false
            isShrinkResources = false
            matchingFallbacks += listOf("benchmark", "release")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        // 17 to match Tsundoku (Read mode), which is compiled for Java 17.
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        variant.applicationId.set("com.nuviodebug.com")
    }
    onVariants(selector().withBuildType("benchmark")) { variant ->
        variant.applicationId.set("com.nuviodebug.com")
    }
    onVariants(selector().withBuildType("fast")) { variant ->
        // Fenrir Vault's own app ID. Phones remember an app's icon by its ID (even across
        // reinstalls), and com.nuviodebug.com was already tied to Nuvio's icon.
        variant.applicationId.set("com.fenrirread.app")
    }
    onVariants { variant ->
        val buildType = variant.buildType.orEmpty()
        if (buildType.startsWith("nonMinified") || buildType.startsWith("benchmark") && buildType != "benchmark") {
            variant.applicationId.set("com.nuviodebug.com")
            variant.signingConfig.setConfig(android.signingConfigs.getByName("debug"))
        }
    }
}

baselineProfile {
    automaticGenerationDuringBuild = false
    saveInSrc = true
    mergeIntoMain = true
    baselineProfileOutputDir = "generated/baselineProfiles"
}

sentry {
    includeProguardMapping.set(true)
    autoUploadProguardMapping.set(sentryMappingUploadEnabled)
    uploadNativeSymbols.set(false)
    autoUploadNativeSymbols.set(false)
    includeNativeSources.set(false)
    includeSourceContext.set(false)
    autoUploadSourceContext.set(false)
    includeDependenciesReport.set(false)
    telemetry.set(false)
    sentryAuthToken?.let(authToken::set)
    sentryOrg?.let(org::set)
    sentryProject?.let(projectName::set)
    ignoredBuildTypes.set(setOf("debug", "benchmark", "fast"))
    autoInstallation {
        enabled.set(false)
    }
    tracingInstrumentation {
        enabled.set(false)
    }
}

// Read mode: the "full" build already packages Nuvio's own quickjs-kt
// (composeApp/libs/quickjs-kt-android-1.0.5-nuvio.aar), so drop the copy Tsundoku brings.
// Only one can be in the app; Tsundoku uses Nuvio's at runtime.
configurations.configureEach {
    if (name.startsWith("full")) {
        exclude(group = "io.github.dokar3", module = "quickjs-kt")
        exclude(group = "io.github.dokar3", module = "quickjs-kt-android")
    }
}

dependencies {
    implementation(project(":composeApp"))
    // Read mode: Tsundoku, from the ./tsundoku included build (see settings.gradle.kts).
    implementation("app.tsundoku:embedded")
    // CloudStream plugin library (vendored, GPL-3.0) for CloudStream plugins.
    implementation(project(":cloudstreamLibrary"))
    // Tsundoku brings some Coil 3 modules at an older version; keep every Coil module on Nuvio's.
    implementation(platform("io.coil-kt.coil3:coil-bom:${libs.versions.coil.get()}"))
    implementation(libs.androidx.appcompat)
    implementation(libs.compose.runtime)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    baselineProfile(project(":baselineprofile"))
    debugImplementation(libs.compose.uiTooling)
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation(libs.androidx.testExt.junit)
    androidTestImplementation(libs.androidx.activity.compose)
    androidTestImplementation(libs.compose.foundation)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:${libs.versions.composeMultiplatform.get()}")
    debugImplementation("androidx.compose.ui:ui-test-manifest:${libs.versions.composeMultiplatform.get()}")
}
