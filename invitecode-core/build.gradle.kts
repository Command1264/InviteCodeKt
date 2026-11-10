description = "Platform-independent InviteCode domain model."

plugins {
    `java-test-fixtures`
}

dependencies {
    api(project(":invitecode-api"))
    testFixturesImplementation(platform("org.junit:junit-bom:5.13.4"))
    testFixturesImplementation("org.junit.jupiter:junit-jupiter")
}
