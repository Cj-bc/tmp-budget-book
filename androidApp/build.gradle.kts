// The Android application. The UI and the domain logic live in :composeApp, which is a
// Kotlin Multiplatform library (AGP 9 doesn't allow com.android.application in a KMP module).
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "jp.cjbc.kakeibo"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "jp.cjbc.kakeibo"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":composeApp"))
    implementation(libs.androidx.activity.compose)
}

// Compose Multiplatform 1.7 doesn't package :composeApp's compose resources (fonts, strings)
// for the com.android.kotlin.multiplatform.library target. Ship them as assets of the app,
// under the path the generated Res accessors read them from.
val composeResourcesAssets = layout.buildDirectory.dir("generated/composeResourcesAssets")
val copyComposeResourcesToAssets = tasks.register<Sync>("copyComposeResourcesToAssets") {
    from(project(":composeApp").tasks.named("prepareComposeResourcesTaskForCommonMain"))
    into(composeResourcesAssets.map { it.dir("composeResources/jp.cjbc.kakeibo.resources") })
}
android.sourceSets.named("main") {
    assets.directories.add(composeResourcesAssets.get().asFile.path)
}
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
    dependsOn(copyComposeResourcesToAssets)
}
