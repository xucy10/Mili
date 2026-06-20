package net.minecraft.commands.arguments.coordinates;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public record LocalCoordinates(double left, double up, double forwards) implements Coordinates {
    public static final char PREFIX_LOCAL_COORDINATE = '^';

    @Override
    public Vec3 getPosition(CommandSourceStack source) {
        Vec3 vec3 = source.getAnchor().apply(source);
        return Vec3.applyLocalCoordinatesToRotation(source.getRotation(), new Vec3(this.left, this.up, this.forwards)).add(vec3.x, vec3.y, vec3.z);
    }

    @Override
    public Vec2 getRotation(CommandSourceStack source) {
        return Vec2.ZERO;
    }

    @Override
    public boolean isXRelative() {
        return true;
    }

    @Override
    public boolean isYRelative() {
        return true;
    }

    @Override
    public boolean isZRelative() {
        return true;
    }

    public static LocalCoordinates parse(StringReader reader) throws CommandSyntaxException {
        int cursor = reader.getCursor();
        double _double = readDouble(reader, cursor);
        if (reader.canRead() && reader.peek() == ' ') {
            reader.skip();
            double _double1 = readDouble(reader, cursor);
            if (reader.canRead() && reader.peek() == ' ') {
                reader.skip();
                double _double2 = readDouble(reader, cursor);
                return new LocalCoordinates(_double, _double1, _double2);
            } else {
                reader.setCursor(cursor);
                throw Vec3Argument.ERROR_NOT_COMPLETE.createWithContext(reader);
            }
        } else {
            reader.setCursor(cursor);
            throw Vec3Argument.ERROR_NOT_COMPLETE.createWithContext(reader);
        }
    }

    private static double readDouble(StringReader reader, int start) throws CommandSyntaxException {
        if (!reader.canRead()) {
            throw WorldCoordinate.ERROR_EXPECTED_DOUBLE.createWithContext(reader);
        } else if (reader.peek() != '^') {
            reader.setCursor(start);
            throw Vec3Argument.ERROR_MIXED_TYPE.createWithContext(reader);
        } else {
            reader.skip();
            return reader.canRead() && reader.peek() != ' ' ? reader.readDouble() : 0.0;
        }
    }
}
