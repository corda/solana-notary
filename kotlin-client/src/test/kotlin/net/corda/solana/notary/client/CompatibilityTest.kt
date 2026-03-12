package net.corda.solana.notary.client

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.lang.classfile.ClassFile
import java.lang.classfile.ClassFile.JAVA_17_VERSION
import java.nio.file.FileSystems
import java.nio.file.Files
import kotlin.io.path.Path

class CompatibilityTest {
    private val jarFs = FileSystems.newFileSystem(Path(System.getProperty("gradle.test.jar")))

    @AfterEach
    fun close() {
        jarFs.close()
    }

    @Test
    fun `all classes target Java 17`() {
        val classes = Files.walk(jarFs.getPath("/"))
            .filter { it.toString().endsWith(".class") }
            .toList()
        assertThat(classes)
            .isNotEmpty
            .allSatisfy {
                val classModel = ClassFile.of().parse(it)
                assertThat(classModel.majorVersion()).isEqualTo(JAVA_17_VERSION)
            }
    }
}
