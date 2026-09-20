pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven("https://jitpack.io") {
            name = "jitpack"
        }
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") {
            name = "spigot-snapshots"
        }
        maven("https://repo.papermc.io/repository/maven-public/") {
            name = "papermc"
        }
    }
}

rootProject.name = "InviteCodeKt"

include(
    "invitecode-api",
    "invitecode-core",
    "invitecode-storage-json",
    "invitecode-storage-sql",
    "invitecode-platform-bukkit",
    "invitecode-platform-modern",
    "invitecode-plugin",
    "invitecode-testkit",
)
