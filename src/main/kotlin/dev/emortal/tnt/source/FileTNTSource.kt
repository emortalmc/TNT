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
            LOGGER.error("Path doesn't exist")

            if (Files.isDirectory(path.parent.resolve(path.nameWithoutExtension))) {
                LOGGER.info("Path is an anvil world. Converting!")

                TNT.convertAnvilToTNT(path)
                LOGGER.info("Converted!")
            }
        }

        return Files.newInputStream(path)
    }
}