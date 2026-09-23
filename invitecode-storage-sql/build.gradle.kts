description = "Shared SQLite and MySQL storage contracts and migrations."

dependencies {
    implementation(project(":invitecode-core"))
    testImplementation(testFixtures(project(":invitecode-core")))
    implementation("org.xerial:sqlite-jdbc:3.53.4.0")
    implementation("com.mysql:mysql-connector-j:26.7.0")
    implementation("com.zaxxer:HikariCP:4.0.3")
    implementation("org.slf4j:slf4j-jdk14:1.7.36")
}
