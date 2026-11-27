dependencies {
    implementation("org.spongepowered:configurate-yaml:4.2.0-GeyserMC-SNAPSHOT")
    shadow("org.spongepowered:configurate-yaml:4.2.0-GeyserMC-SNAPSHOT")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.17.0")
    implementation("com.fasterxml.jackson.core:jackson-core:2.17.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    shadow("com.fasterxml.jackson.core:jackson-annotations:2.17.0")
    shadow("com.fasterxml.jackson.core:jackson-core:2.17.0")
    shadow("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    compileOnly("com.github.retrooper:packetevents-api:2.10.0")
    compileOnly("org.geysermc.geyser:core:${property("geyser_version")}")
    compileOnly("org.geysermc.floodgate:core:2.2.5-SNAPSHOT")
    compileOnly("org.projectlombok:lombok:1.18.34")
    annotationProcessor("org.projectlombok:lombok:1.18.34")
    testCompileOnly("org.projectlombok:lombok:1.18.34")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.34")

    testImplementation("org.geysermc.geyser:core:${property("geyser_version")}")
    testImplementation("org.geysermc.floodgate:core:2.2.5-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}


tasks {
    test {
        useJUnitPlatform()
    }

    shadowJar {
        archiveClassifier.set("")
        archiveFileName.set("GeyserPlus.jar")
        relocate("org.spongepowered", "dev.letsgoaway.relocate.org.spongepowered")
        relocate("io.leangen.geantyref", "dev.letsgoaway.io.leangen.geantyref")
        relocate("com.fasterxml", "dev.letsgoaway.relocate.com.fasterxml")

    }
}

