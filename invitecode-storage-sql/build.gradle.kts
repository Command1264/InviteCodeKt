description = "Shared SQLite and MySQL storage contracts and migrations."

dependencies {
    implementation(project(":invitecode-core"))
    implementation("org.xerial:sqlite-jdbc:3.53.4.0")
}
