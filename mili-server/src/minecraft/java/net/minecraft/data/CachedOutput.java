package net.minecraft.data;

import com.google.common.hash.HashCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.util.FileUtil;

public interface CachedOutput {
    CachedOutput NO_CACHE = (filePath, data, hashCode) -> {
        FileUtil.createDirectoriesSafe(filePath.getParent());
        Files.write(filePath, data);
    };

    void writeIfNeeded(Path filePath, byte[] data, HashCode hashCode) throws IOException;
}
