package net.minecraft.server.packs;

import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.util.FileUtil;
import net.minecraft.util.Util;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class PathPackResources extends AbstractPackResources {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Joiner PATH_JOINER = Joiner.on("/");
    private final Path root;

    public PathPackResources(PackLocationInfo location, Path root) {
        super(location);
        this.root = root;
    }

    @Override
    public @Nullable IoSupplier<InputStream> getRootResource(String... elements) {
        FileUtil.validatePath(elements);
        Path path = FileUtil.resolvePath(this.root, List.of(elements));
        return Files.exists(path) ? IoSupplier.create(path) : null;
    }

    public static boolean validatePath(Path path) {
        if (!SharedConstants.DEBUG_VALIDATE_RESOURCE_PATH_CASE) {
            return true;
        } else if (path.getFileSystem() != FileSystems.getDefault()) {
            return true;
        } else {
            try {
                return path.toRealPath().endsWith(path);
            } catch (IOException var2) {
                LOGGER.warn("Failed to resolve real path for {}", path, var2);
                return false;
            }
        }
    }

    @Override
    public @Nullable IoSupplier<InputStream> getResource(PackType packType, Identifier location) {
        Path path = this.root.resolve(packType.getDirectory()).resolve(location.getNamespace());
        return getResource(location, path);
    }

    public static @Nullable IoSupplier<InputStream> getResource(Identifier location, Path path) {
        return FileUtil.decomposePath(location.getPath()).mapOrElse(elements -> {
            Path path1 = FileUtil.resolvePath(path, (List<String>)elements);
            return returnFileIfExists(path1);
        }, error -> {
            LOGGER.error("Invalid path {}: {}", location, error.message());
            return null;
        });
    }

    private static @Nullable IoSupplier<InputStream> returnFileIfExists(Path path) {
        return Files.exists(path) && validatePath(path) ? IoSupplier.create(path) : null;
    }

    @Override
    public void listResources(PackType packType, String namespace, String path, PackResources.ResourceOutput resourceOutput) {
        FileUtil.decomposePath(path).ifSuccess(tets -> {
            Path path1 = this.root.resolve(packType.getDirectory()).resolve(namespace);
            listPath(namespace, path1, (List<String>)tets, resourceOutput);
        }).ifError(error -> LOGGER.error("Invalid path {}: {}", path, error.message()));
    }

    public static void listPath(String namespace, Path namespacePath, List<String> decomposedPath, PackResources.ResourceOutput resourceOutput) {
        Path path = FileUtil.resolvePath(namespacePath, decomposedPath);

        try (Stream<Path> stream = Files.find(path, Integer.MAX_VALUE, PathPackResources::isRegularFile)) {
            stream.forEach(path1 -> {
                String string = PATH_JOINER.join(namespacePath.relativize(path1));
                Identifier identifier = Identifier.tryBuild(namespace, string);
                if (identifier == null) {
                    Util.logAndPauseIfInIde(String.format(Locale.ROOT, "Invalid path in pack: %s:%s, ignoring", namespace, string));
                } else {
                    resourceOutput.accept(identifier, IoSupplier.create(path1));
                }
            });
        } catch (NotDirectoryException | NoSuchFileException var10) {
        } catch (IOException var11) {
            LOGGER.error("Failed to list path {}", path, var11);
        }
    }

    private static boolean isRegularFile(Path path, BasicFileAttributes attributes) {
        return !SharedConstants.IS_RUNNING_IN_IDE
            ? attributes.isRegularFile()
            : attributes.isRegularFile() && !StringUtils.equalsIgnoreCase(path.getFileName().toString(), ".ds_store");
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        Set<String> set = Sets.newHashSet();
        Path path = this.root.resolve(type.getDirectory());

        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(path)) {
            for (Path path1 : directoryStream) {
                String string = path1.getFileName().toString();
                // Paper start - Improve logging and errors
                if (!Files.isDirectory(path1)) {
                    LOGGER.error("Invalid directory entry: {} in {}.", string, this.root, new java.nio.file.NotDirectoryException(string));
                    continue;
                }
                // Paper end - Improve logging and errors
                if (Identifier.isValidNamespace(string)) {
                    set.add(string);
                } else {
                    LOGGER.warn("Non [a-z0-9_.-] character in namespace {} in pack {}, ignoring", string, this.root);
                }
            }
        } catch (NotDirectoryException | NoSuchFileException var10) {
        } catch (IOException var11) {
            LOGGER.error("Failed to list path {}", path, var11);
        }

        return set;
    }

    @Override
    public void close() {
    }

    public static class PathResourcesSupplier implements Pack.ResourcesSupplier {
        private final Path content;

        public PathResourcesSupplier(Path content) {
            this.content = content;
        }

        @Override
        public PackResources openPrimary(PackLocationInfo location) {
            return new PathPackResources(location, this.content);
        }

        @Override
        public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
            PackResources packResources = this.openPrimary(location);
            List<String> list = metadata.overlays();
            if (list.isEmpty()) {
                return packResources;
            } else {
                List<PackResources> list1 = new ArrayList<>(list.size());

                for (String string : list) {
                    Path path = this.content.resolve(string);
                    list1.add(new PathPackResources(location, path));
                }

                return new CompositePackResources(packResources, list1);
            }
        }
    }
}
