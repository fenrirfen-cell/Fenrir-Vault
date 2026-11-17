rootProject.name = "Nuvio"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        // Needed by Tsundoku's dependencies (Read mode).
        maven(url = "https://www.jitpack.io")
    }
}

// Read mode: Tsundoku (git submodule in ./tsundoku) is built as an included build and its
// :embedded library module is linked into the Android app. Its own settings, version catalogs
// and build logic stay separate from Nuvio's.
includeBuild("tsundoku") {
    dependencySubstitution {
        substitute(module("app.tsundoku:embedded")).using(project(":embedded"))
    }
}

include(":composeApp")
include(":androidApp")
include(":baselineprofile")
