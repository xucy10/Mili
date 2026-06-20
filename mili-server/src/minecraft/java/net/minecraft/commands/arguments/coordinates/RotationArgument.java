package net.minecraft.commands.arguments.coordinates;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Arrays;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

public class RotationArgument implements ArgumentType<Coordinates> {
    private static final Collection<String> EXAMPLES = Arrays.asList("0 0", "~ ~", "~-5 ~5");
    public static final SimpleCommandExceptionType ERROR_NOT_COMPLETE = new SimpleCommandExceptionType(Component.translatable("argument.rotation.incomplete"));

    public static RotationArgument rotation() {
        return new RotationArgument();
    }

    public static Coordinates getRotation(CommandContext<CommandSourceStack> context, String name) {
        return context.getArgument(name, Coordinates.class);
    }

    @Override
    public Coordinates parse(StringReader reader) throws CommandSyntaxException {
        int cursor = reader.getCursor();
        if (!reader.canRead()) {
            throw ERROR_NOT_COMPLETE.createWithContext(reader);
        } else {
            WorldCoordinate worldCoordinate = WorldCoordinate.parseDouble(reader, false);
            if (reader.canRead() && reader.peek() == ' ') {
                reader.skip();
                WorldCoordinate worldCoordinate1 = WorldCoordinate.parseDouble(reader, false);
                return new WorldCoordinates(worldCoordinate1, worldCoordinate, new WorldCoordinate(true, 0.0));
            } else {
                reader.setCursor(cursor);
                throw ERROR_NOT_COMPLETE.createWithContext(reader);
            }
        }
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
