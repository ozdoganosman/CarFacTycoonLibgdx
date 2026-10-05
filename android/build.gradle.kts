// Android launcher with AdMob. Built only where an Android SDK is installed (see settings.gradle.kts).
//   ./gradlew :android:assembleDebug -> android/build/outputs/apk/debug/
// Kotlin comes from the Android plugin's built-in Kotlin support (no kotlin-android plugin).
//
// AdMob ids: debug builds always use Google's test ids. Release builds take the real ones from
// Gradle properties (~/.gradle/gradle.properties or -P): admob.appId, admob.interstitial,
// admob.rewarded; without them they fall back to the test ids too.
plugins {
    id("com.android.application")
}

/** Google's public test ids: safe to click, never pay. */
object TestAds {
    const val APP = "ca-app-pub-3940256099942544~3347511713"
    const val INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
    const val REWARDED = "ca-app-pub-3940256099942544/5224354917"
}

fun admob(name: String, test: String): String = (findProperty("admob.$name") as String?)?.takeIf { it.isNotBlank() } ?: test

android {
    namespace = "com.toyquaise.kaptan"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.toyquaise.hamurkaptan"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        debug {
            manifestPlaceholders["admobAppId"] = TestAds.APP
            buildConfigField("String", "ADMOB_INTERSTITIAL", "\"${TestAds.INTERSTITIAL}\"")
            buildConfigField("String", "ADMOB_REWARDED", "\"${TestAds.REWARDED}\"")
        }
        release {
            isMinifyEnabled = false
            manifestPlaceholders["admobAppId"] = admob("appId", TestAds.APP)
            buildConfigField("String", "ADMOB_INTERSTITIAL", "\"${admob("interstitial", TestAds.INTERSTITIAL)}\"")
            buildConfigField("String", "ADMOB_REWARDED", "\"${admob("rewarded", TestAds.REWARDED)}\"")
        }
    }
}

val abis = listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
val natives = configurations.create("natives")

dependencies {
    implementation(project(":core"))
    implementation(libs.gdx.backend.android)
    implementation(libs.admob)
    implementation(libs.ump)
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
