package dev.emortal.tnt;

import dev.emortal.tnt.source.FileTNTSource;
import net.minestom.server.Git;
import net.minestom.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class StandaloneConverter {
    private static final Logger LOGGER = LoggerFactory.getLogger(StandaloneConverter.class);

    private static final String SERVER_BRAND = MinecraftServer.getBrandName();
    private static final String SERVER_VERSION = MinecraftServer.VERSION_NAME;

    private static final Path WORLDS_PATH = Path.of("worlds");

    public static void main(String[] args) throws IOException {
        MinecraftServer.init(); // We need the instance manager

        LOGGER.info("Beginning conversion for all maps on {} {} ({})", SERVER_BRAND, SERVER_VERSION, Git.commit());
        try (Stream<Path> pathStream = Files.list(WORLDS_PATH)) {
            pathStream.forEach(st -> {
                String worldName = FileTNTSource.getNameWithoutExtension(st.getFileName().toString());
                Path actualPath = st.getParent().resolve(worldName + ".tnt");

                LOGGER.info("Beginning conversion for map {}", worldName);

                try {
                    TNT.convertAnvilToTNT(actualPath.getParent().resolve(worldName), new FileTNTSource(actualPath));
                } catch (IOException | InterruptedException e) {
                    throw new RuntimeException(e);
                }

                LOGGER.info("Conversion finished for map {}", worldName);
            });
        }

        System.exit(0);
    }
}
