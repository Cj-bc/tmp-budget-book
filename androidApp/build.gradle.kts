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
        // The release workflow passes these from the tag and the run number.
        versionCode = providers.gradleProperty("kakeibo.versionCode").map(String::toInt).getOrElse(1)
        versionName = providers.gradleProperty("kakeibo.versionName").getOrElse("1.0")
    }

    // The workflows decode the environment's keystore and pass it in these variables. Without them
    // (local builds), debug uses Android's default debug key and release stays unsigned.
    val keystoreFile = providers.environmentVariable("KAKEIBO_KEYSTORE_FILE")
    val keystoreSigning = if (keystoreFile.isPresent) {
        signingConfigs.create("keystore") {
            storeFile = file(keystoreFile.get())
            storePassword = providers.environmentVariable("KAKEIBO_KEYSTORE_PASSWORD").get()
            keyAlias = providers.environmentVariable("KAKEIBO_KEY_ALIAS").get()
            keyPassword = providers.environmentVariable("KAKEIBO_KEY_PASSWORD").get()
        }
    } else {
        null
    }

    buildTypes {
        debug {
            // So that the debug build installs next to the release one.
            applicationIdSuffix = ".debug"
            if (keystoreSigning != null) signingConfig = keystoreSigning
        }
        release {
            // R8 is off until its effect on JGit is checked.
            isMinifyEnabled = false
            if (keystoreSigning != null) signingConfig = keystoreSigning
        }
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
