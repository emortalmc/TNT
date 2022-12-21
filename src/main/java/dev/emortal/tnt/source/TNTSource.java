package dev.emortal.tnt.source;

import java.io.InputStream;

public interface TNTSource {
    InputStream load();

    void save(byte[] bytes);
}
