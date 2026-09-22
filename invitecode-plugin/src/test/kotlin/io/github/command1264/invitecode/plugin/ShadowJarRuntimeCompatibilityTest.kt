package io.github.command1264.invitecode.plugin

import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Path
import java.nio.file.Paths
import java.sql.Connection

class ShadowJarRuntimeCompatibilityTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `plugin uses its bundled SQLite driver when parent exposes the same package`() {
        val archive = Paths.get(requireNotNull(System.getProperty("invitecode.shadowJar")))
        val parent = ClassLoader.getSystemClassLoader()
        val parentDriverLoader = Class.forName("org.sqlite.JDBC", true, parent).classLoader

        PluginFirstClassLoader(archive.toUri().toURL(), parent).use { loader ->
            val connections = loader.loadClass(
                "io.github.command1264.invitecode.storage.sql.SqliteSchemaMigratorKt",
            )
            val connection = connections.getMethod("connect", Path::class.java)
                .invoke(null, temporaryDirectory.resolve("isolated.db")) as Connection

            connection.use {
                assertTrue(it.isValid(1))
                assertNotSame(parentDriverLoader, it.javaClass.classLoader)
            }
        }
    }

    private class PluginFirstClassLoader(
        archive: URL,
        parent: ClassLoader,
    ) : URLClassLoader(arrayOf(archive), parent) {
        override fun loadClass(name: String, resolve: Boolean): Class<*> {
            if (!name.startsWith("io.github.command1264.invitecode.")) {
                return super.loadClass(name, resolve)
            }
            synchronized(getClassLoadingLock(name)) {
                val loaded = findLoadedClass(name) ?: findClass(name)
                if (resolve) resolveClass(loaded)
                return loaded
            }
        }
    }
}
