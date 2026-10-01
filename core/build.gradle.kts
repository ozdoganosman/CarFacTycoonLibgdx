// Rendering, input and screens, shared by the desktop and Android launchers.
plugins {
    kotlin("jvm")
    `java-library`
}

dependencies {
    api(project(":logic"))
    api(libs.gdx)
    api(libs.gdx.freetype)
}
