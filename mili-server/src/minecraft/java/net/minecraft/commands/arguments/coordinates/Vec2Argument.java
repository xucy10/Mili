package net.minecraft.commands.arguments.coordinates;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public class Vec2Argument implements ArgumentType<Coordinates> {
    private static final Collection<String> EXAMPLES = Arrays.asList("0 0", "~ ~", "0.1 -0.5", "~1 ~-2");
    public static final SimpleCommandExceptionType ERROR_NOT_COMPLETE = new SimpleCommandExceptionType(Component.translatable("argument.pos2d.incomplete"));
    private final boolean centerCorrect;

    public Vec2Argument(boolean centerCorrect) {
        this.centerCorrect = centerCorrect;
    }

    public static Vec2Argument vec2() {
        return new Vec2Argument(true);
    }

    public static Vec2Argument vec2(boolean centerCorrect) {
        return new Vec2Argument(centerCorrect);
    }

    public static Vec2 getVec2(CommandContext<CommandSourceStack> context, String name) {
        Vec3 position = context.getArgument(name, Coordinates.class).getPosition(context.getSource());
        return new Vec2((float)position.x, (float)position.z);
    }

    @Override
    public Coordinates parse(StringReader reader) throws CommandSyntaxException {
        int cursor = reader.getCursor();
        if (!reader.canRead()) {
            throw ERROR_NOT_COMPLETE.createWithContext(reader);
        } else {
            WorldCoordinate worldCoordinate = WorldCoordinate.parseDouble(reader, this.centerCorrect);
            if (reader.canRead() && reader.peek() == ' ') {
                reader.skip();
                WorldCoordinate worldCoordinate1 = WorldCoordinate.parseDouble(reader, this.centerCorrect);
                return new WorldCoordinates(worldCoordinate, new WorldCoordinate(true, 0.0), worldCoordinate1);
            } else {
                reader.setCursor(cursor);
                throw ERROR_NOT_COMPLETE.createWithContext(reader);
            }
        }
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        if (!(context.getSource() instanceof SharedSuggestionProvider)) {
            return Suggestions.empty();
        } else {
            String remaining = builder.getRemaining();
            Collection<SharedSuggestionProvider.TextCoordinates> collection;
            if (!remaining.isEmpty() && remaining.charAt(0) == '^') {
                collection = Collections.singleton(SharedSuggestionProvider.TextCoordinates.DEFAULT_LOCAL);
            } else {
                collection = ((SharedSuggestionProvider)context.getSource()).getAbsoluteCoordinates();
            }

            return SharedSuggestionProvider.suggest2DCoordinates(remaining, collection, builder, Commands.createValidator(this::parse));
        }
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
