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
    implementation("com.googlecode.soundlibs:vorbisspi:1.0.3.3")
    implementation("com.googlecode.soundlibs:jorbis:0.0.17.4")
    implementation("com.googlecode.soundlibs:tritonus-share:0.3.7.4")
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