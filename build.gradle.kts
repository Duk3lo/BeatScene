plugins {
    id("java")
    id("com.gradleup.shadow") version "9.2.0"
}

group = "org.astral.beatscene"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        name = "Hytale"
        url = uri("https://maven.hytale.com/release")
    }

}

dependencies {

    val lwjglVersion = "3.3.4"
    val lwjglNatives = "natives-linux" // Cambia a "natives-windows" si es necesario

    implementation(platform("org.lwjgl:lwjgl-bom:$lwjglVersion"))
    implementation("org.lwjgl:lwjgl")
    implementation("org.lwjgl:lwjgl-openal")
    implementation("org.lwjgl:lwjgl-stb")

    runtimeOnly("org.lwjgl:lwjgl::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-openal::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-stb::$lwjglNatives")

    compileOnly("com.hypixel.hytale:Server:+")
    compileOnly("org.jetbrains:annotations:24.1.0")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    mergeServiceFiles()
    archiveClassifier.set("")
    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}