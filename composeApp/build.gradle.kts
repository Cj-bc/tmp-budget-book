@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenEnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenPlugin
import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsPlugin
import org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnPlugin
import org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnRootEnvSpec

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

// Use the node/yarn executables supplied by the Nix development shell.
// Node setup (:kotlinWasmNodeJsSetup) lives in the root project while webpack tasks use this
// project's spec, so configure both.
listOf(project, rootProject).forEach { p ->
    p.plugins.withType<WasmNodeJsPlugin> {
        p.the<WasmNodeJsEnvSpec>().download.set(false)
    }
}

rootProject.plugins.withType<WasmYarnPlugin> {
    rootProject.the<WasmYarnRootEnvSpec>().download.set(false)
}

plugins.withType<BinaryenPlugin> {
    the<BinaryenEnvSpec>().download.set(false)
}

kotlin {
    android {
        namespace = "jp.cjbc.kakeibo"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        // Run commonTest on the JVM. wasmJsNodeTest cannot run it: Skiko's wasm fails to load
        // under Node.
        withHostTest {}
    }

    wasmJs {
        outputModuleName.set("kakeibo")
        browser()
        nodejs()
        binaries.executable()
        compilerOptions {
            sourceMap.set(true)
        }
    }

    js {
        useEsModules()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            implementation("androidx.activity:activity-compose:1.10.0")
        }
    }
}

compose.resources {
    packageOfResClass = "jp.cjbc.kakeibo.resources"
}
