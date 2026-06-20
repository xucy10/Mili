package net.minecraft.server;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.Commands;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleReloadInstance;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.util.Unit;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.crafting.RecipeManager;
import org.slf4j.Logger;

public class ReloadableServerResources {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final CompletableFuture<Unit> DATA_RELOAD_INITIAL_TASK = CompletableFuture.completedFuture(Unit.INSTANCE);
    private final ReloadableServerRegistries.Holder fullRegistryHolder;
    public Commands commands;
    private final RecipeManager recipes;
    private final ServerAdvancementManager advancements;
    private final ServerFunctionLibrary functionLibrary;
    private final List<Registry.PendingTags<?>> postponedTags;

    private ReloadableServerResources(
        LayeredRegistryAccess<RegistryLayer> registryAccess,
        HolderLookup.Provider registries,
        FeatureFlagSet enabledFeatures,
        Commands.CommandSelection commandSelection,
        List<Registry.PendingTags<?>> postponedTags,
        PermissionSet permissions
    ) {
        this.fullRegistryHolder = new ReloadableServerRegistries.Holder(registryAccess.compositeAccess());
        this.postponedTags = postponedTags;
        this.recipes = new RecipeManager(registries);
        this.commands = new Commands(commandSelection, CommandBuildContext.simple(registries, enabledFeatures), true); // Paper - Brigadier Command API - use modern alias registration
        io.papermc.paper.command.brigadier.PaperCommands.INSTANCE.setDispatcher(this.commands, CommandBuildContext.simple(registries, enabledFeatures)); // Paper - Brigadier Command API
        io.papermc.paper.command.PaperCommands.registerCommands(); // Paper
        this.advancements = new ServerAdvancementManager(registries);
        this.functionLibrary = new ServerFunctionLibrary(permissions, this.commands.getDispatcher());
    }

    public ServerFunctionLibrary getFunctionLibrary() {
        return this.functionLibrary;
    }

    public ReloadableServerRegistries.Holder fullRegistries() {
        return this.fullRegistryHolder;
    }

    public RecipeManager getRecipeManager() {
        return this.recipes;
    }

    public Commands getCommands() {
        return this.commands;
    }

    public ServerAdvancementManager getAdvancements() {
        return this.advancements;
    }

    public List<PreparableReloadListener> listeners() {
        return List.of(this.recipes, this.functionLibrary, this.advancements);
    }

    public static CompletableFuture<ReloadableServerResources> loadResources(
        ResourceManager resourceManager,
        LayeredRegistryAccess<RegistryLayer> registryAccess,
        List<Registry.PendingTags<?>> postponedTags,
        FeatureFlagSet enabledFeatures,
        Commands.CommandSelection commandSelection,
        PermissionSet permissions,
        Executor backgroundExecutor,
        Executor gameExecutor
    ) {
        return ReloadableServerRegistries.reload(registryAccess, postponedTags, resourceManager, backgroundExecutor)
            .thenCompose(
                loadResult -> {
                    ReloadableServerResources reloadableServerResources = new ReloadableServerResources(
                        loadResult.layers(), loadResult.lookupWithUpdatedTags(), enabledFeatures, commandSelection, postponedTags, permissions
                    );
                    // Paper start - call commands event for bootstraps
                    //noinspection ConstantValue
                    io.papermc.paper.plugin.lifecycle.event.LifecycleEventRunner.INSTANCE.callReloadableRegistrarEvent(
                        io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents.COMMANDS,
                        io.papermc.paper.command.brigadier.PaperCommands.INSTANCE,
                        io.papermc.paper.plugin.bootstrap.BootstrapContext.class,
                        MinecraftServer.getServer() == null ? io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent.Cause.INITIAL : io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent.Cause.RELOAD);
                    // Paper end - call commands event
                    return SimpleReloadInstance.create(
                            resourceManager,
                            reloadableServerResources.listeners(),
                            backgroundExecutor,
                            gameExecutor,
                            DATA_RELOAD_INITIAL_TASK,
                            LOGGER.isDebugEnabled()
                        )
                        .done()
                        .thenApply(object -> reloadableServerResources);
                }
            );
    }

    public void updateStaticRegistryTags() {
        this.postponedTags.forEach(Registry.PendingTags::apply);
    }
}
