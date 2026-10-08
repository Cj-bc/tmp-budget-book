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
        namespace = "jp.cjbc.kakeibo.composeapp"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        androidResources {
            enable = true
        }
    }

    wasmJs {
        outputModuleName.set("kakeibo")
        browser()
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
        }
    }
}

compose.resources {
    packageOfResClass = "jp.cjbc.kakeibo.resources"
}
