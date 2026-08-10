package net.minecraft.server;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.ImmutableMap.Builder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.tags.TagLoader;
import org.slf4j.Logger;

public class ServerFunctionLibrary implements PreparableReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final ResourceKey<Registry<CommandFunction<CommandSourceStack>>> TYPE_KEY = ResourceKey.createRegistryKey(
        Identifier.withDefaultNamespace("function")
    );
    private static final FileToIdConverter LISTER = new FileToIdConverter(Registries.elementsDirPath(TYPE_KEY), ".mcfunction");
    private volatile Map<Identifier, CommandFunction<CommandSourceStack>> functions = ImmutableMap.of();
    private final TagLoader<CommandFunction<CommandSourceStack>> tagsLoader = new TagLoader<>(
        (id, required) -> this.getFunction(id), Registries.tagsDirPath(TYPE_KEY)
    );
    private volatile Map<Identifier, List<CommandFunction<CommandSourceStack>>> tags = Map.of();
    private final PermissionSet functionCompilationPermissions;
    private final CommandDispatcher<CommandSourceStack> dispatcher;

    public Optional<CommandFunction<CommandSourceStack>> getFunction(Identifier location) {
        return Optional.ofNullable(this.functions.get(location));
    }

    public Map<Identifier, CommandFunction<CommandSourceStack>> getFunctions() {
        return this.functions;
    }

    public List<CommandFunction<CommandSourceStack>> getTag(Identifier location) {
        return this.tags.getOrDefault(location, List.of());
    }

    public Iterable<Identifier> getAvailableTags() {
        return this.tags.keySet();
    }

    public ServerFunctionLibrary(PermissionSet functionCompilationPermissions, CommandDispatcher<CommandSourceStack> dispatcher) {
        this.functionCompilationPermissions = functionCompilationPermissions;
        this.dispatcher = dispatcher;
    }

    @Override
    public CompletableFuture<Void> reload(
        PreparableReloadListener.SharedState state, Executor backgroundExecutor, PreparableReloadListener.PreparationBarrier preparation, Executor gameExecutor
    ) {
        ResourceManager resourceManager = state.resourceManager();
        CompletableFuture<Map<Identifier, List<TagLoader.EntryWithSource>>> completableFuture = CompletableFuture.supplyAsync(
            () -> this.tagsLoader.load(resourceManager), backgroundExecutor
        );
        CompletableFuture<Map<Identifier, CompletableFuture<CommandFunction<CommandSourceStack>>>> completableFuture1 = CompletableFuture.<Map<Identifier, Resource>>supplyAsync(
                () -> LISTER.listMatchingResources(resourceManager), backgroundExecutor
            )
            .thenCompose(map -> {
                Map<Identifier, CompletableFuture<CommandFunction<CommandSourceStack>>> map1 = Maps.newHashMap();
                CommandSourceStack commandSourceStack = Commands.createCompilationContext(this.functionCompilationPermissions);

                for (Entry<Identifier, Resource> entry : map.entrySet()) {
                    Identifier identifier = entry.getKey();
                    Identifier identifier1 = LISTER.fileToId(identifier);
                    map1.put(identifier1, CompletableFuture.supplyAsync(() -> {
                        List<String> lines = readLines(entry.getValue());
                        return CommandFunction.fromLines(identifier1, this.dispatcher, commandSourceStack, lines);
                    }, backgroundExecutor));
                }

                CompletableFuture<?>[] completableFutures = map1.values().toArray(new CompletableFuture[0]);
                return CompletableFuture.allOf(completableFutures).handle((_void, throwable) -> map1);
            });
        return completableFuture.thenCombine(completableFuture1, Pair::of)
            .thenCompose(preparation::wait)
            .thenAcceptAsync(
                pair -> {
                    Map<Identifier, CompletableFuture<CommandFunction<CommandSourceStack>>> map = (Map<Identifier, CompletableFuture<CommandFunction<CommandSourceStack>>>)pair.getSecond();
                    Builder<Identifier, CommandFunction<CommandSourceStack>> builder = ImmutableMap.builder();
                    map.forEach((identifier, completableFuture2) -> completableFuture2.handle((commandFunction, throwable) -> {
                        if (throwable != null) {
                            LOGGER.error("Failed to load function {}", identifier, throwable);
                        } else {
                            builder.put(identifier, commandFunction);
                        }

                        return null;
                    }).join());
                    this.functions = builder.build();
                    this.tags = this.tagsLoader.build((Map<Identifier, List<TagLoader.EntryWithSource>>)pair.getFirst(), null); // Paper - command function tags are not implemented yet
                },
                gameExecutor
            );
    }

    private static List<String> readLines(Resource resource) {
        try {
            List var2;
            try (BufferedReader bufferedReader = resource.openAsReader()) {
                var2 = bufferedReader.lines().toList();
            }

            return var2;
        } catch (IOException var6) {
            throw new CompletionException(var6);
        }
    }
}
