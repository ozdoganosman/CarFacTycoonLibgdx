// Desktop launcher, used for development and for screenshots.
//   ./gradlew :lwjgl3:run
//   ./gradlew :lwjgl3:run --args="--screenshot build/shot.png"
plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation(project(":core"))
    implementation(libs.gdx.backend.lwjgl3)
    implementation(variantOf(libs.gdx.platform) { classifier("natives-desktop") })
    implementation(variantOf(libs.gdx.freetype.platform) { classifier("natives-desktop") })
}

application {
    mainClass.set("com.toyquaise.vektor.lwjgl3.Lwjgl3LauncherKt")
    applicationName = "vektorpilotu"
}

// The shared assets folder is both the working directory of `run` and part of the jar.
sourceSets.main {
    resources.srcDir(rootProject.file("assets"))
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.file("assets")
    if (System.getProperty("os.name").lowercase().contains("mac")) jvmArgs("-XstartOnFirstThread")
}

// A single runnable jar: ./gradlew :lwjgl3:dist -> lwjgl3/build/libs/vektorpilotu-<version>.jar
tasks.register<Jar>("dist") {
    group = "distribution"
    archiveBaseName.set("vektorpilotu")
    manifest { attributes["Main-Class"] = application.mainClass.get() }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({ configurations.runtimeClasspath.get().filter { it.name.endsWith(".jar") }.map { zipTree(it) } })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/INDEX.LIST")
}
