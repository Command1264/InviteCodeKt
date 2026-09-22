description = "Isolated capability adapters for modern Paper and Spigot APIs."

dependencies {
    implementation(project(":invitecode-platform-bukkit"))
    implementation(project(":invitecode-core"))
    compileOnly("org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT")
    testImplementation("org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT")
}
