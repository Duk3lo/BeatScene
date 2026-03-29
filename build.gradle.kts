plugins {
    id("java")
    id("com.gradleup.shadow") version "9.2.0"
    application
}

group = "org.astral.beatscene"
version = "1.0-SNAPSHOT"


val osName = System.getProperty("os.name").lowercase()
val osArch = System.getProperty("os.arch").lowercase()

val lwjglNatives = when {
    osName.contains("win") -> "natives-windows"
    osName.contains("linux") -> {
        if (osArch.contains("arm") || osArch.contains("aarch64")) "natives-linux-arm64" else "natives-linux"
    }
    osName.contains("mac") -> {
        if (osArch.contains("arm") || osArch.contains("aarch64")) "natives-macos-arm64" else "natives-macos"
    }
    else -> "natives-linux"
}

repositories {
    mavenCentral()
    maven {
        name = "Hytale"
        url = uri("https://maven.hytale.com/release")
    }
}

dependencies {
    val lwjglVersion = "3.3.4"
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

tasks.withType<JavaExec> {
    jvmArgs(
        "--add-opens", "java.base/java.nio=ALL-UNNAMED",
        "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED",
        "-Dorg.lwjgl.util.NoChecks=true"
    )
}

tasks.shadowJar {
    mergeServiceFiles()
    archiveClassifier.set("")
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}