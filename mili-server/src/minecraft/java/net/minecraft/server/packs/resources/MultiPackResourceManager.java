package net.minecraft.server.packs.resources;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class MultiPackResourceManager implements CloseableResourceManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Map<String, FallbackResourceManager> namespacedManagers;
    private final List<PackResources> packs;

    public MultiPackResourceManager(PackType type, List<PackResources> packs) {
        this.packs = List.copyOf(packs);
        Map<String, FallbackResourceManager> map = new HashMap<>();
        List<String> list = packs.stream().flatMap(resources -> resources.getNamespaces(type).stream()).distinct().toList();

        for (PackResources packResources : packs) {
            ResourceFilterSection packFilterSection = this.getPackFilterSection(packResources);
            Set<String> namespaces = packResources.getNamespaces(type);
            Predicate<Identifier> predicate = packFilterSection != null ? location -> packFilterSection.isPathFiltered(location.getPath()) : null;

            for (String string : list) {
                boolean flag = namespaces.contains(string);
                boolean flag1 = packFilterSection != null && packFilterSection.isNamespaceFiltered(string);
                if (flag || flag1) {
                    FallbackResourceManager fallbackResourceManager = map.get(string);
                    if (fallbackResourceManager == null) {
                        fallbackResourceManager = new FallbackResourceManager(type, string);
                        map.put(string, fallbackResourceManager);
                    }

                    if (flag && flag1) {
                        fallbackResourceManager.push(packResources, predicate);
                    } else if (flag) {
                        fallbackResourceManager.push(packResources);
                    } else {
                        fallbackResourceManager.pushFilterOnly(packResources.packId(), predicate);
                    }
                }
            }
        }

        this.namespacedManagers = map;
    }

    private @Nullable ResourceFilterSection getPackFilterSection(PackResources packResources) {
        try {
            return packResources.getMetadataSection(ResourceFilterSection.TYPE);
        } catch (IOException var3) {
            LOGGER.error("Failed to get filter section from pack {}", packResources.packId());
            return null;
        }
    }

    @Override
    public Set<String> getNamespaces() {
        return this.namespacedManagers.keySet();
    }

    @Override
    public Optional<Resource> getResource(Identifier location) {
        ResourceManager resourceManager = this.namespacedManagers.get(location.getNamespace());
        return resourceManager != null ? resourceManager.getResource(location) : Optional.empty();
    }

    @Override
    public List<Resource> getResourceStack(Identifier location) {
        ResourceManager resourceManager = this.namespacedManagers.get(location.getNamespace());
        return resourceManager != null ? resourceManager.getResourceStack(location) : List.of();
    }

    @Override
    public Map<Identifier, Resource> listResources(String path, Predicate<Identifier> filter) {
        checkTrailingDirectoryPath(path);
        Map<Identifier, Resource> map = new TreeMap<>();

        for (FallbackResourceManager fallbackResourceManager : this.namespacedManagers.values()) {
            map.putAll(fallbackResourceManager.listResources(path, filter));
        }

        return map;
    }

    @Override
    public Map<Identifier, List<Resource>> listResourceStacks(String path, Predicate<Identifier> filter) {
        checkTrailingDirectoryPath(path);
        Map<Identifier, List<Resource>> map = new TreeMap<>();

        for (FallbackResourceManager fallbackResourceManager : this.namespacedManagers.values()) {
            map.putAll(fallbackResourceManager.listResourceStacks(path, filter));
        }

        return map;
    }

    private static void checkTrailingDirectoryPath(String path) {
        if (path.endsWith("/")) {
            throw new IllegalArgumentException("Trailing slash in path " + path);
        }
    }

    @Override
    public Stream<PackResources> listPacks() {
        return this.packs.stream();
    }

    @Override
    public void close() {
        this.packs.forEach(PackResources::close);
    }
}
