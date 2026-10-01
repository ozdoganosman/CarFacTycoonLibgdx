rootProject.name = "toothfort"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.google.*")
            }
        }
    }
}

// logic: the game rules and the circuit solver, plain Kotlin (no libGDX), tested on the JVM.
// core: rendering, input and screens (libGDX). lwjgl3: desktop launcher. android: Android launcher.
include(":logic", ":core", ":lwjgl3")

// The Android module needs the Android SDK. Without it (a plain JDK machine, some CI jobs) the
// desktop game and the tests still build; Android Studio and the Android CI job have the SDK.
if (androidSdkDir(settingsDir) != null) {
    include(":android")
} else {
    logger.lifecycle("Android SDK bulunamadı: :android modülü atlandı (ANDROID_HOME ya da local.properties içinde sdk.dir).")
}

fun androidSdkDir(root: File): File? {
    val fromProps = File(root, "local.properties").takeIf { it.isFile }?.readLines()
        ?.firstOrNull { it.trim().startsWith("sdk.dir") }
        ?.substringAfter("=")?.trim()?.replace("\\:", ":")?.replace("\\\\", "\\")
    return listOfNotNull(fromProps, System.getenv("ANDROID_HOME"), System.getenv("ANDROID_SDK_ROOT"))
        .map(::File).firstOrNull { it.isDirectory }
}
