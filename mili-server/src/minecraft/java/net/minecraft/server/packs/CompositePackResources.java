package net.minecraft.server.packs;

import com.google.common.collect.Lists;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jspecify.annotations.Nullable;

public class CompositePackResources implements PackResources {
    private final PackResources primaryPackResources;
    private final List<PackResources> packResourcesStack;

    public CompositePackResources(PackResources primaryPackResources, List<PackResources> packResourcesStack) {
        this.primaryPackResources = primaryPackResources;
        List<PackResources> list = new ArrayList<>(packResourcesStack.size() + 1);
        list.addAll(Lists.reverse(packResourcesStack));
        list.add(primaryPackResources);
        this.packResourcesStack = List.copyOf(list);
    }

    @Override
    public @Nullable IoSupplier<InputStream> getRootResource(String... elements) {
        return this.primaryPackResources.getRootResource(elements);
    }

    @Override
    public @Nullable IoSupplier<InputStream> getResource(PackType packType, Identifier location) {
        for (PackResources packResources : this.packResourcesStack) {
            IoSupplier<InputStream> resource = packResources.getResource(packType, location);
            if (resource != null) {
                return resource;
            }
        }

        return null;
    }

    @Override
    public void listResources(PackType packType, String namespace, String path, PackResources.ResourceOutput resourceOutput) {
        Map<Identifier, IoSupplier<InputStream>> map = new HashMap<>();

        for (PackResources packResources : this.packResourcesStack) {
            packResources.listResources(packType, namespace, path, map::putIfAbsent);
        }

        map.forEach(resourceOutput);
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        Set<String> set = new HashSet<>();

        for (PackResources packResources : this.packResourcesStack) {
            set.addAll(packResources.getNamespaces(type));
        }

        return set;
    }

    @Override
    public <T> @Nullable T getMetadataSection(MetadataSectionType<T> type) throws IOException {
        return this.primaryPackResources.getMetadataSection(type);
    }

    @Override
    public PackLocationInfo location() {
        return this.primaryPackResources.location();
    }

    @Override
    public void close() {
        this.packResourcesStack.forEach(PackResources::close);
    }
}
