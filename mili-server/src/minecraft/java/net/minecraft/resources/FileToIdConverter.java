package net.minecraft.resources;

import java.util.List;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

public class FileToIdConverter {
    private final String prefix;
    private final String extension;

    public FileToIdConverter(String prefix, String extension) {
        this.prefix = prefix;
        this.extension = extension;
    }

    public static FileToIdConverter json(String name) {
        return new FileToIdConverter(name, ".json");
    }

    public static FileToIdConverter registry(ResourceKey<? extends Registry<?>> registryKey) {
        return json(Registries.elementsDirPath(registryKey));
    }

    public Identifier idToFile(Identifier id) {
        return id.withPath(this.prefix + "/" + id.getPath() + this.extension);
    }

    public Identifier fileToId(Identifier file) {
        String path = file.getPath();
        return file.withPath(path.substring(this.prefix.length() + 1, path.length() - this.extension.length()));
    }

    public Map<Identifier, Resource> listMatchingResources(ResourceManager resourceManager) {
        return resourceManager.listResources(this.prefix, location -> location.getPath().endsWith(this.extension));
    }

    public Map<Identifier, List<Resource>> listMatchingResourceStacks(ResourceManager resourceManager) {
        return resourceManager.listResourceStacks(this.prefix, location -> location.getPath().endsWith(this.extension));
    }
}
