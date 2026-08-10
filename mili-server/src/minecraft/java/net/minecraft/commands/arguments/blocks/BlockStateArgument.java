package net.minecraft.commands.arguments.blocks;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;

public class BlockStateArgument implements ArgumentType<BlockInput> {
    private static final Collection<String> EXAMPLES = Arrays.asList("stone", "minecraft:stone", "stone[foo=bar]", "foo{bar=baz}");
    private final HolderLookup<Block> blocks;

    public BlockStateArgument(CommandBuildContext buildContext) {
        this.blocks = buildContext.lookupOrThrow(Registries.BLOCK);
    }

    public static BlockStateArgument block(CommandBuildContext buildContext) {
        return new BlockStateArgument(buildContext);
    }

    @Override
    public BlockInput parse(StringReader reader) throws CommandSyntaxException {
        BlockStateParser.BlockResult blockResult = BlockStateParser.parseForBlock(this.blocks, reader, true);
        return new BlockInput(blockResult.blockState(), blockResult.properties().keySet(), blockResult.nbt());
    }

    public static BlockInput getBlock(CommandContext<CommandSourceStack> context, String name) {
        return context.getArgument(name, BlockInput.class);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return BlockStateParser.fillSuggestions(this.blocks, builder, false, true);
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
