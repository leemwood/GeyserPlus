dependencies {
    implementation(project(":core"))

    compileOnly("org.geysermc.geyser:core:2.11.1-SNAPSHOT")
    compileOnly("net.md-5:bungeecord-api:1.21-R0.1")
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        archiveFileName.set("GeyserPlus-Extension.jar")
        relocate("org.spongepowered", "dev.letsgoaway.relocate.org.spongepowered")
        relocate("io.leangen.geantyref", "dev.letsgoaway.io.leangen.geantyref")
        relocate("com.fasterxml", "dev.letsgoaway.relocate.com.fasterxml")
    }
}
