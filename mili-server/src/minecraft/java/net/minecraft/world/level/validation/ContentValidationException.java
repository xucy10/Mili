package net.minecraft.world.level.validation;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class ContentValidationException extends Exception {
    private final Path directory;
    private final List<ForbiddenSymlinkInfo> entries;

    public ContentValidationException(Path directory, List<ForbiddenSymlinkInfo> entries) {
        this.directory = directory;
        this.entries = entries;
    }

    @Override
    public String getMessage() {
        return getMessage(this.directory, this.entries);
    }

    public static String getMessage(Path directory, List<ForbiddenSymlinkInfo> entries) {
        return "Failed to validate '"
            + directory
            + "'. Found forbidden symlinks: "
            + entries.stream().map(symlinkInfo -> symlinkInfo.link() + "->" + symlinkInfo.target()).collect(Collectors.joining(", "));
    }
}
