import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// CloudStream's plugin API (vendored from recloudstream/cloudstream, GPL-3.0), Android only.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.kotlinxSerialization)
}

kotlin {
    applyDefaultHierarchyTemplate()

    android {
        namespace = "com.lagradost.api"
        compileSdk {
            version = release(libs.versions.android.compileSdk.get().toInt()) {
                minorApiLevel = libs.versions.android.compileSdkMinor.get().toInt()
            }
        }
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        all {
            languageSettings {
                optIn("com.lagradost.cloudstream3.InternalAPI")
                optIn("com.lagradost.cloudstream3.Prerelease")
            }
        }
        commonMain.dependencies {
            api("androidx.annotation:annotation:1.10.0")
            api("com.fasterxml.jackson.module:jackson-module-kotlin") {
                version { strictly("2.13.1") }
            }
            api("org.jsoup:jsoup:1.22.1")
            api(libs.kotlinx.atomicfu)
            api(libs.kotlinx.coroutines.core)
            api("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")
            api("org.jetbrains.kotlinx:kotlinx-io-core:0.9.1")
            api(libs.kotlinx.serialization.json)
            api("com.fleeksoft.ksoup:ksoup:0.2.6")
            api("io.ktor:ktor-http:3.4.1")
            api("com.github.Blatzar:NiceHttp:0.4.18")
            api("org.mozilla:rhino") {
                version { strictly("1.8.1") }
            }
            api("dev.whyoleg.cryptography:cryptography-core:0.6.0")
            api("dev.whyoleg.cryptography:cryptography-provider-optimal:0.6.0")
        }
        val jvmCommonMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                api("org.jetbrains.kotlin:kotlin-reflect:${libs.versions.kotlin.get()}")
                api("com.github.teamnewpipe:NewPipeExtractor:v0.26.3")
            }
        }
        androidMain { dependsOn(jvmCommonMain) }
    }
}
