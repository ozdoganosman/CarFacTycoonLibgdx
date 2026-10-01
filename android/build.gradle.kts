// Android launcher. Built only where an Android SDK is installed (see settings.gradle.kts).
//   ./gradlew :android:assembleDebug -> android/build/outputs/apk/debug/
// Kotlin comes from the Android plugin's built-in Kotlin support (no kotlin-android plugin).
plugins {
    id("com.android.application")
}

android {
    namespace = "com.toyquaise.toothfort"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.toyquaise.toothfort"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

val abis = listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
val natives = configurations.create("natives")

dependencies {
    implementation(project(":core"))
    implementation(libs.gdx.backend.android)
    for (abi in abis) {
        natives(variantOf(libs.gdx.platform) { classifier("natives-$abi") })
        natives(variantOf(libs.gdx.freetype.platform) { classifier("natives-$abi") })
    }
}

/** Unpacks libGDX's native libraries (libgdx.so, libgdx-freetype.so) into one folder per ABI. */
abstract class ExtractGdxNatives : DefaultTask() {
    @get:InputFiles
    abstract val nativeJars: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Inject
    abstract val fs: FileSystemOperations

    @get:Inject
    abstract val archives: ArchiveOperations

    @TaskAction
    fun extract() {
        fs.delete { delete(outputDir) }
        for (jar in nativeJars.files) {
            val abi = jar.name.substringAfter("-natives-").removeSuffix(".jar")
            fs.copy {
                from(archives.zipTree(jar))
                include("*.so")
                into(outputDir.dir(abi))
            }
        }
    }
}

val extractGdxNatives = tasks.register<ExtractGdxNatives>("extractGdxNatives") {
    nativeJars.from(natives)
    outputDir.set(layout.buildDirectory.dir("gdx-natives"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.jniLibs?.addGeneratedSourceDirectory(extractGdxNatives, ExtractGdxNatives::outputDir)
        variant.sources.assets?.addStaticSourceDirectory(rootProject.file("assets").path)
    }
}
