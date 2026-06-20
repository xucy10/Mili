package net.minecraft.server;

import com.mojang.logging.LogUtils;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.minecraft.SharedConstants;
import net.minecraft.util.CommonLinks;
import org.slf4j.Logger;

public class Eula {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Path file;
    private final boolean agreed;

    public Eula(Path file) {
        this.file = file;
        this.agreed = SharedConstants.IS_RUNNING_IN_IDE || this.readFile();
    }

    private boolean readFile() {
        try {
            boolean var3;
            try (InputStream inputStream = Files.newInputStream(this.file)) {
                Properties map = new Properties();
                map.load(inputStream);
                var3 = Boolean.parseBoolean(map.getProperty("eula", "false"));
            }

            return var3;
        } catch (Exception var6) {
            LOGGER.warn("Failed to load {}", this.file);
            this.saveDefaults();
            return false;
        }
    }

    public boolean hasAgreedToEULA() {
        return this.agreed;
    }

    private void saveDefaults() {
        if (!SharedConstants.IS_RUNNING_IN_IDE) {
            try (OutputStream outputStream = Files.newOutputStream(this.file)) {
                Properties map = new Properties();
                map.setProperty("eula", "false");
                map.store(outputStream, "By changing the setting below to TRUE you are indicating your agreement to our EULA (" + CommonLinks.EULA + ").");
            } catch (Exception var6) {
                LOGGER.warn("Failed to save {}", this.file, var6);
            }
        }
    }
}
