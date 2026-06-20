package net.minecraft.data.info;

import com.mojang.brigadier.CommandDispatcher;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.synchronization.ArgumentUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

public class CommandsReport implements DataProvider {
    private final PackOutput output;
    private final CompletableFuture<HolderLookup.Provider> registries;

    public CommandsReport(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        this.output = output;
        this.registries = registries;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        Path path = this.output.getOutputFolder(PackOutput.Target.REPORTS).resolve("commands.json");
        return this.registries
            .thenCompose(
                lookupProvider -> {
                    CommandDispatcher<CommandSourceStack> dispatcher = new Commands(
                            Commands.CommandSelection.ALL, Commands.createValidationContext(lookupProvider)
                        )
                        .getDispatcher();
                    return DataProvider.saveStable(output, ArgumentUtils.serializeNodeToJson(dispatcher, dispatcher.getRoot()), path);
                }
            );
    }

    @Override
    public final String getName() {
        return "Command Syntax";
    }
}
