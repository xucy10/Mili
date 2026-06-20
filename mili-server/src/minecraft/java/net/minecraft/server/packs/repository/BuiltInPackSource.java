package net.minecraft.server.packs.repository;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.VanillaPackResources;
import net.minecraft.world.level.validation.DirectoryValidator;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public abstract class BuiltInPackSource implements RepositorySource {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String VANILLA_ID = "vanilla";
    public static final String TESTS_ID = "tests";
    public static final KnownPack CORE_PACK_INFO = KnownPack.vanilla("core");
    private final PackType packType;
    private final VanillaPackResources vanillaPack;
    private final Identifier packDir;
    private final DirectoryValidator validator;

    public BuiltInPackSource(PackType packType, VanillaPackResources vanillaPack, Identifier packDir, DirectoryValidator validator) {
        this.packType = packType;
        this.vanillaPack = vanillaPack;
        this.packDir = packDir;
        this.validator = validator;
    }

    @Override
    public void loadPacks(Consumer<Pack> onLoad) {
        Pack pack = this.createVanillaPack(this.vanillaPack);
        if (pack != null) {
            onLoad.accept(pack);
        }

        this.listBundledPacks(onLoad);
    }

    protected abstract @Nullable Pack createVanillaPack(PackResources resources);

    protected abstract Component getPackTitle(String id);

    public VanillaPackResources getVanillaPack() {
        return this.vanillaPack;
    }

    private void listBundledPacks(Consumer<Pack> packConsumer) {
        Map<String, Function<String, Pack>> map = new HashMap<>();
        this.populatePackList(map::put);
        map.forEach((string, packGetter) -> {
            Pack pack = packGetter.apply(string);
            if (pack != null) {
                packConsumer.accept(pack);
            }
        });
    }

    protected void populatePackList(BiConsumer<String, Function<String, Pack>> populator) {
        this.vanillaPack.listRawPaths(this.packType, this.packDir, path -> this.discoverPacksInPath(path, populator));
    }

    protected void discoverPacksInPath(@Nullable Path directoryPath, BiConsumer<String, Function<String, @Nullable Pack>> packGetter) {
        if (directoryPath != null && Files.isDirectory(directoryPath)) {
            try {
                FolderRepositorySource.discoverPacks(
                    directoryPath,
                    this.validator,
                    (path, resources) -> packGetter.accept(pathToId(path), string -> this.createBuiltinPack(string, resources, this.getPackTitle(string)))
                );
            } catch (IOException var4) {
                LOGGER.warn("Failed to discover packs in {}", directoryPath, var4);
            }
        }
    }

    private static String pathToId(Path path) {
        return StringUtils.removeEnd(path.getFileName().toString(), ".zip");
    }

    protected abstract @Nullable Pack createBuiltinPack(String id, Pack.ResourcesSupplier resources, Component title);

    protected static Pack.ResourcesSupplier fixedResources(final PackResources resources) {
        return new Pack.ResourcesSupplier() {
            @Override
            public PackResources openPrimary(PackLocationInfo location) {
                return resources;
            }

            @Override
            public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
                return resources;
            }
        };
    }
}
