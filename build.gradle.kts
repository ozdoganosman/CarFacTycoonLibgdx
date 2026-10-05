// The Kotlin and Android plugins share the root classloader so the Android plugin's built-in
// Kotlin support sees the same Kotlin plugin as the JVM modules. The Android plugin is only put
// on the classpath when settings.gradle.kts included the :android module (an Android SDK exists).
buildscript {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.google.*")
            }
        }
    }
    dependencies {
        classpath(libs.kotlin.gradle)
        if (findProject(":android") != null) classpath(libs.android.gradle)
    }
}

subprojects {
    version = "0.1.0"
    group = "com.toyquaise.kaptan"

    // Android runs Java 17 bytecode; every JVM module targets it too.
    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
            compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}
