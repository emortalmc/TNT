package dev.emortal.tnt;

import dev.emortal.tnt.source.FileTNTSource;
import net.minestom.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static dev.emortal.tnt.source.FileTNTSource.getNameWithoutExtension;

public class StandaloneConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger("StandaloneConverter");

    public static void main(String[] args) throws IOException {
        MinecraftServer.init(); // We need the instance manager

        Path path = Path.of("./worlds/");
        Files.list(path).forEach((st) -> {
            String worldName = getNameWithoutExtension(st.getFileName().toString());
            Path actualPath = st.getParent().resolve(worldName + ".tnt");

            LOGGER.info("Beginning conversion for map " + worldName);

            try {
                TNT.convertAnvilToTNT(actualPath.getParent().resolve(worldName), new FileTNTSource(actualPath));
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }

            LOGGER.info("Conversion finished for map " + worldName);
        });
    }

}
