package net.minecraft.commands.arguments.coordinates;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

public class SwizzleArgument implements ArgumentType<EnumSet<Direction.Axis>> {
    private static final Collection<String> EXAMPLES = Arrays.asList("xyz", "x");
    private static final SimpleCommandExceptionType ERROR_INVALID = new SimpleCommandExceptionType(Component.translatable("arguments.swizzle.invalid"));

    public static SwizzleArgument swizzle() {
        return new SwizzleArgument();
    }

    public static EnumSet<Direction.Axis> getSwizzle(CommandContext<CommandSourceStack> context, String name) {
        return context.getArgument(name, EnumSet.class);
    }

    @Override
    public EnumSet<Direction.Axis> parse(StringReader reader) throws CommandSyntaxException {
        EnumSet<Direction.Axis> set = EnumSet.noneOf(Direction.Axis.class);

        while (reader.canRead() && reader.peek() != ' ') {
            char c = reader.read();

            Direction.Axis axis = switch (c) {
                case 'x' -> Direction.Axis.X;
                case 'y' -> Direction.Axis.Y;
                case 'z' -> Direction.Axis.Z;
                default -> throw ERROR_INVALID.createWithContext(reader);
            };
            if (set.contains(axis)) {
                throw ERROR_INVALID.createWithContext(reader);
            }

            set.add(axis);
        }

        return set;
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
