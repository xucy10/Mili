package net.minecraft.server;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Stream;
import net.minecraft.commands.Commands;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.level.WorldDataConfiguration;
import org.slf4j.Logger;

public class WorldLoader {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static <D, R> CompletableFuture<R> load(
        WorldLoader.InitConfig initConfig,
        WorldLoader.WorldDataSupplier<D> worldDataSupplier,
        WorldLoader.ResultFactory<D, R> resultFactory,
        Executor backgroundExecutor,
        Executor gameExecutor
    ) {
        try {
            Pair<WorldDataConfiguration, CloseableResourceManager> pair = initConfig.packConfig.createResourceManager();
            CloseableResourceManager closeableResourceManager = pair.getSecond();
            LayeredRegistryAccess<RegistryLayer> layeredRegistryAccess = RegistryLayer.createRegistryAccess();
            List<Registry.PendingTags<?>> list = TagLoader.loadTagsForExistingRegistries(
                closeableResourceManager, layeredRegistryAccess.getLayer(RegistryLayer.STATIC), io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent.Cause.INITIAL // Paper - tag lifecycle - add cause
            );
            RegistryAccess.Frozen accessForLoading = layeredRegistryAccess.getAccessForLoading(RegistryLayer.WORLDGEN);
            List<HolderLookup.RegistryLookup<?>> list1 = TagLoader.buildUpdatedLookups(accessForLoading, list);
            RegistryAccess.Frozen frozen = RegistryDataLoader.load(closeableResourceManager, list1, RegistryDataLoader.WORLDGEN_REGISTRIES);
            List<HolderLookup.RegistryLookup<?>> list2 = Stream.concat(list1.stream(), frozen.listRegistries()).toList();
            RegistryAccess.Frozen frozen1 = RegistryDataLoader.load(closeableResourceManager, list2, RegistryDataLoader.DIMENSION_REGISTRIES);
            WorldDataConfiguration worldDataConfiguration = pair.getFirst();
            HolderLookup.Provider provider = HolderLookup.Provider.create(list2.stream());
            WorldLoader.DataLoadOutput<D> dataLoadOutput = worldDataSupplier.get(
                new WorldLoader.DataLoadContext(closeableResourceManager, worldDataConfiguration, provider, frozen1)
            );
            LayeredRegistryAccess<RegistryLayer> layeredRegistryAccess1 = layeredRegistryAccess.replaceFrom(
                RegistryLayer.WORLDGEN, frozen, dataLoadOutput.finalDimensions
            );
            return ReloadableServerResources.loadResources(
                    closeableResourceManager,
                    layeredRegistryAccess1,
                    list,
                    worldDataConfiguration.enabledFeatures(),
                    initConfig.commandSelection(),
                    initConfig.functionCompilationPermissions(),
                    backgroundExecutor,
                    gameExecutor
                )
                .whenComplete((result, exception1) -> {
                    if (exception1 != null) {
                        closeableResourceManager.close();
                    }
                })
                .thenApplyAsync(reloadableServerResources -> {
                    reloadableServerResources.updateStaticRegistryTags();
                    return resultFactory.create(closeableResourceManager, reloadableServerResources, layeredRegistryAccess1, dataLoadOutput.cookie);
                }, gameExecutor);
        } catch (Exception var18) {
            return CompletableFuture.failedFuture(var18);
        }
    }

    public record DataLoadContext(
        ResourceManager resources, WorldDataConfiguration dataConfiguration, HolderLookup.Provider datapackWorldgen, RegistryAccess.Frozen datapackDimensions
    ) {
    }

    public record DataLoadOutput<D>(D cookie, RegistryAccess.Frozen finalDimensions) {
    }

    public record InitConfig(WorldLoader.PackConfig packConfig, Commands.CommandSelection commandSelection, PermissionSet functionCompilationPermissions) {
    }

    public record PackConfig(PackRepository packRepository, WorldDataConfiguration initialDataConfig, boolean safeMode, boolean initMode) {
        public Pair<WorldDataConfiguration, CloseableResourceManager> createResourceManager() {
            WorldDataConfiguration worldDataConfiguration = MinecraftServer.configurePackRepository(
                this.packRepository, this.initialDataConfig, this.initMode, this.safeMode
            );
            List<PackResources> list = this.packRepository.openAllSelected();
            CloseableResourceManager closeableResourceManager = new MultiPackResourceManager(PackType.SERVER_DATA, list);
            return Pair.of(worldDataConfiguration, closeableResourceManager);
        }
    }

    @FunctionalInterface
    public interface ResultFactory<D, R> {
        R create(CloseableResourceManager manager, ReloadableServerResources resources, LayeredRegistryAccess<RegistryLayer> registryAccess, D cookie);
    }

    @FunctionalInterface
    public interface WorldDataSupplier<D> {
        WorldLoader.DataLoadOutput<D> get(WorldLoader.DataLoadContext context);
    }
}
