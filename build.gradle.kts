plugins {
    id("java")
    id("com.gradleup.shadow") version "8.3.1"
    id("maven-publish")
}

group = "com.aurelian2842.jarflow"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("org.json:json:20250517")
    implementation("net.bytebuddy:byte-buddy-agent:1.12.1")
    compileOnly("org.jetbrains:annotations:24.0.1")
    annotationProcessor("org.jetbrains:annotations:24.0.1")
    implementation("me.lucko:jar-relocator:1.7")
    compileOnly("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok:1.18.42")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()

            from(components["java"])
        }
    }
}

tasks.publish {
    dependsOn(tasks.named("publishMavenPublicationToMavenLocal"))
}