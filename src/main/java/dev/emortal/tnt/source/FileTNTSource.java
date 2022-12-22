package dev.emortal.tnt.source;

import dev.emortal.tnt.TNT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileTNTSource implements TNTSource {

    private static Logger LOGGER = LoggerFactory.getLogger("FileTNTSource");

    private final Path path;

    public FileTNTSource(Path path) {
        this.path = path;
    }

    @Override
    public InputStream load() {
        if (!Files.exists(path)) { // No world folder
            if (Files.isDirectory(path.getParent().resolve(getNameWithoutExtension(path.getFileName().toString())))) {
                LOGGER.info("Path is an anvil world. Converting! (This might take a bit)");

                try {
                    TNT.convertAnvilToTNT(path.getParent().resolve(getNameWithoutExtension(path.getFileName().toString())), new FileTNTSource(path));
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                LOGGER.info("Converted!");
            } else {
                LOGGER.error("Path doesn't exist!");
            }
        }

        try {
            return Files.newInputStream(path);
        } catch (IOException e) {
            e.printStackTrace();
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