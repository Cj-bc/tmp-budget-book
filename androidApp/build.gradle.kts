plugins {
    alias(libs.plugins.android.application)
}

// The Android application that packages composeApp into an APK. composeApp uses the
// Kotlin Multiplatform Android library plugin, which can't build APKs itself.
android {
    namespace = "jp.cjbc.kakeibo.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "jp.cjbc.kakeibo"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
}

dependencies {
    implementation(project(":composeApp"))
}

// Compose Multiplatform 1.7 doesn't package composeApp's resources (strings and fonts) when it
// uses the Kotlin Multiplatform Android library plugin. Copy them into this app's assets, where
// Res reads them from: composeResources/<packageOfResClass>/.
abstract class CopyComposeResources : DefaultTask() {
    @get:InputFiles
    abstract val resources: ConfigurableFileCollection

    @get:Input
    abstract val packageOfResClass: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Inject
    abstract val fs: FileSystemOperations

    @TaskAction
    fun copy() {
        fs.sync {
            from(resources)
            into(outputDirectory.dir("composeResources/${packageOfResClass.get()}"))
        }
    }
}

val copyComposeResources by tasks.registering(CopyComposeResources::class) {
    resources.from(project(":composeApp").tasks.named("prepareComposeResourcesTaskForCommonMain"))
    packageOfResClass.set("jp.cjbc.kakeibo.resources")
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(copyComposeResources, CopyComposeResources::outputDirectory)
    }
}
