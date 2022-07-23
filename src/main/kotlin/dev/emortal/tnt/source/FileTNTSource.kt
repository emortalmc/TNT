package dev.emortal.tnt.source

import dev.emortal.tnt.LOGGER
import dev.emortal.tnt.TNT
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.nameWithoutExtension

class FileTNTSource(val path: Path) : TNTSource {
    override fun load(): InputStream {
        if (!Files.exists(path)) {
            // No world folder

            if (Files.isDirectory(path.parent.resolve(path.nameWithoutExtension))) {
                LOGGER.info("Path is an anvil world. Converting! (This might take a bit)")

                TNT.convertAnvilToTNT(path.parent.resolve(path.nameWithoutExtension), FileTNTSource(path))
                LOGGER.info("Converted!")
            } else {
                LOGGER.error("Path doesn't exist!")
            }
        }

        return Files.newInputStream(path)
    }

    override fun save(bytes: ByteArray) {
        Files.write(path, bytes)
    }
}