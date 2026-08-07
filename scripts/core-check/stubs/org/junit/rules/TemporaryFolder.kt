package org.junit.rules

import java.io.File
import java.nio.file.Files

class TemporaryFolder {
    val root: File by lazy { Files.createTempDirectory("unison-test-").toFile() }

    fun newFile(name: String): File = File(root, name).also { file ->
        file.parentFile?.mkdirs()
        if (!file.createNewFile()) error("Could not create ${file.absolutePath}")
    }

    fun newFolder(name: String): File = File(root, name).also { folder ->
        if (!folder.mkdirs() && !folder.isDirectory) error("Could not create ${folder.absolutePath}")
    }
}
