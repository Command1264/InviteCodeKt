description = "Bukkit compatibility layer shared by legacy and modern servers."

dependencies {
    implementation(project(":invitecode-core"))
    compileOnly("org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT")
}
