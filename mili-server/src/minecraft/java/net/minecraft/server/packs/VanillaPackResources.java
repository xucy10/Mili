package net.minecraft.server.packs;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.util.FileUtil;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class VanillaPackResources implements PackResources {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final PackLocationInfo location;
    private final BuiltInMetadata metadata;
    private final Set<String> namespaces;
    private final List<Path> rootPaths;
    private final Map<PackType, List<Path>> pathsForType;

    VanillaPackResources(
        PackLocationInfo location, BuiltInMetadata metadata, Set<String> namespaces, List<Path> rootPaths, Map<PackType, List<Path>> pathsForType
    ) {
        this.location = location;
        this.metadata = metadata;
        this.namespaces = namespaces;
        this.rootPaths = rootPaths;
        this.pathsForType = pathsForType;
    }

    @Override
    public @Nullable IoSupplier<InputStream> getRootResource(String... elements) {
        FileUtil.validatePath(elements);
        List<String> list = List.of(elements);

        for (Path path : this.rootPaths) {
            Path path1 = FileUtil.resolvePath(path, list);
            if (Files.exists(path1) && PathPackResources.validatePath(path1)) {
                return IoSupplier.create(path1);
            }
        }

        return null;
    }

    public void listRawPaths(PackType packType, Identifier packLocation, Consumer<Path> output) {
        FileUtil.decomposePath(packLocation.getPath()).ifSuccess(elements -> {
            String namespace = packLocation.getNamespace();

            for (Path path : this.pathsForType.get(packType)) {
                Path path1 = path.resolve(namespace);
                output.accept(FileUtil.resolvePath(path1, (List<String>)elements));
            }
        }).ifError(error -> LOGGER.error("Invalid path {}: {}", packLocation, error.message()));
    }

    @Override
    public void listResources(PackType packType, String namespace, String path, PackResources.ResourceOutput resourceOutput) {
        FileUtil.decomposePath(path).ifSuccess(paths -> {
            List<Path> list = this.pathsForType.get(packType);
            int size = list.size();
            if (size == 1) {
                getResources(resourceOutput, namespace, list.get(0), (List<String>)paths);
            } else if (size > 1) {
                Map<Identifier, IoSupplier<InputStream>> map = new HashMap<>();

                for (int i = 0; i < size - 1; i++) {
                    getResources(map::putIfAbsent, namespace, list.get(i), (List<String>)paths);
                }

                Path path1 = list.get(size - 1);
                if (map.isEmpty()) {
                    getResources(resourceOutput, namespace, path1, (List<String>)paths);
                } else {
                    getResources(map::putIfAbsent, namespace, path1, (List<String>)paths);
                    map.forEach(resourceOutput);
                }
            }
        }).ifError(error -> LOGGER.error("Invalid path {}: {}", path, error.message()));
    }

    private static void getResources(PackResources.ResourceOutput resourceOutput, String namespace, Path root, List<String> paths) {
        Path path = root.resolve(namespace);
        PathPackResources.listPath(namespace, path, paths, resourceOutput);
    }

    @Override
    public @Nullable IoSupplier<InputStream> getResource(PackType packType, Identifier location) {
        return FileUtil.decomposePath(location.getPath()).mapOrElse(paths -> {
            String namespace = location.getNamespace();

            for (Path path : this.pathsForType.get(packType)) {
                Path path1 = FileUtil.resolvePath(path.resolve(namespace), (List<String>)paths);
                if (Files.exists(path1) && PathPackResources.validatePath(path1)) {
                    return IoSupplier.create(path1);
                }
            }

            return null;
        }, error -> {
            LOGGER.error("Invalid path {}: {}", location, error.message());
            return null;
        });
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return this.namespaces;
    }

    @Override
    public <T> @Nullable T getMetadataSection(MetadataSectionType<T> type) {
        IoSupplier<InputStream> rootResource = this.getRootResource("pack.mcmeta");
        if (rootResource != null) {
            try (InputStream inputStream = rootResource.get()) {
                T metadataFromStream = AbstractPackResources.getMetadataFromStream(type, inputStream, this.location);
                if (metadataFromStream != null) {
                    return metadataFromStream;
                }

                return this.metadata.get(type);
            } catch (IOException var8) {
            }
        }

        return this.metadata.get(type);
    }

    @Override
    public PackLocationInfo location() {
        return this.location;
    }

    @Override
    public void close() {
    }

    public ResourceProvider asProvider() {
        return location -> Optional.ofNullable(this.getResource(PackType.CLIENT_RESOURCES, location))
            .map(ioSupplier -> new Resource(this, (IoSupplier<InputStream>)ioSupplier));
    }
}
