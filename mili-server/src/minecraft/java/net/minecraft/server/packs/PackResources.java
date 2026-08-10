package net.minecraft.server.packs;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jspecify.annotations.Nullable;

public interface PackResources extends AutoCloseable {
    String METADATA_EXTENSION = ".mcmeta";
    String PACK_META = "pack.mcmeta";

    @Nullable IoSupplier<InputStream> getRootResource(String... elements);

    @Nullable IoSupplier<InputStream> getResource(PackType packType, Identifier location);

    void listResources(PackType packType, String namespace, String path, PackResources.ResourceOutput resourceOutput);

    Set<String> getNamespaces(PackType type);

    <T> @Nullable T getMetadataSection(MetadataSectionType<T> type) throws IOException;

    PackLocationInfo location();

    default String packId() {
        return this.location().id();
    }

    default Optional<KnownPack> knownPackInfo() {
        return this.location().knownPackInfo();
    }

    @Override
    void close();

    @FunctionalInterface
    public interface ResourceOutput extends BiConsumer<Identifier, IoSupplier<InputStream>> {
    }
}
