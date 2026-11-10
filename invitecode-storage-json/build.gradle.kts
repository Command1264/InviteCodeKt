description = "Versioned JSON storage for small installations and interchange."

dependencies {
    implementation(project(":invitecode-core"))
    implementation(project(":invitecode-storage-sql"))
    implementation("com.google.code.gson:gson:2.14.0")
    testImplementation(testFixtures(project(":invitecode-core")))
}
