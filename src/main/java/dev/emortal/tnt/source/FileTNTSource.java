package dev.emortal.tnt.source;

import dev.emortal.tnt.TNT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileTNTSource implements TNTSource {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileTNTSource.class);

    private final Path path;

    public FileTNTSource(Path path) {
        this.path = path;
    }

    @Override
    public InputStream load() {
        if (!Files.exists(path)) { // No world folder
            String worldName = getNameWithoutExtension(path.getFileName().toString());

            if (Files.isDirectory(path.getParent().resolve(worldName))) {
                LOGGER.info("Path is an anvil world. Converting! (This might take a bit)");

                try {
                    TNT.convertAnvilToTNT(path.getParent().resolve(worldName), new FileTNTSource(path));
                } catch (IOException e) {
                    LOGGER.error("Failed to convert world {} to TNT: {}", worldName, e);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                LOGGER.info("Converted world {} to TNT", worldName);
            } else {
                LOGGER.error("No TNT or Anvil world found at path: " + this.path);
            }
        }

        try {
            return Files.newInputStream(path);
        } catch (IOException e) {
            LOGGER.error("Failed to load TNT file {}: {}", path, e);
            return InputStream.nullInputStream();
        }
    }

    @Override
    public void save(byte[] bytes) {
        try {
            Files.write(path, bytes);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String getNameWithoutExtension(String path) {
        int dotIndex = path.lastIndexOf('.');
        return (dotIndex == -1) ? path : path.substring(0, dotIndex);
    }

}