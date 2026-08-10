package net.minecraft.commands.arguments.coordinates;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public record WorldCoordinates(WorldCoordinate x, WorldCoordinate y, WorldCoordinate z) implements Coordinates {
    public static final WorldCoordinates ZERO_ROTATION = absolute(new Vec2(0.0F, 0.0F));

    @Override
    public Vec3 getPosition(CommandSourceStack source) {
        Vec3 position = source.getPosition();
        return new Vec3(this.x.get(position.x), this.y.get(position.y), this.z.get(position.z));
    }

    @Override
    public Vec2 getRotation(CommandSourceStack source) {
        Vec2 rotation = source.getRotation();
        return new Vec2((float)this.x.get(rotation.x), (float)this.y.get(rotation.y));
    }

    @Override
    public boolean isXRelative() {
        return this.x.isRelative();
    }

    @Override
    public boolean isYRelative() {
        return this.y.isRelative();
    }

    @Override
    public boolean isZRelative() {
        return this.z.isRelative();
    }

    public static WorldCoordinates parseInt(StringReader reader) throws CommandSyntaxException {
        int cursor = reader.getCursor();
        WorldCoordinate worldCoordinate = WorldCoordinate.parseInt(reader);
        if (reader.canRead() && reader.peek() == ' ') {
            reader.skip();
            WorldCoordinate worldCoordinate1 = WorldCoordinate.parseInt(reader);
            if (reader.canRead() && reader.peek() == ' ') {
                reader.skip();
                WorldCoordinate worldCoordinate2 = WorldCoordinate.parseInt(reader);
                return new WorldCoordinates(worldCoordinate, worldCoordinate1, worldCoordinate2);
            } else {
                reader.setCursor(cursor);
                throw Vec3Argument.ERROR_NOT_COMPLETE.createWithContext(reader);
            }
        } else {
            reader.setCursor(cursor);
            throw Vec3Argument.ERROR_NOT_COMPLETE.createWithContext(reader);
        }
    }

    public static WorldCoordinates parseDouble(StringReader reader, boolean centerCorrect) throws CommandSyntaxException {
        int cursor = reader.getCursor();
        WorldCoordinate worldCoordinate = WorldCoordinate.parseDouble(reader, centerCorrect);
        if (reader.canRead() && reader.peek() == ' ') {
            reader.skip();
            WorldCoordinate worldCoordinate1 = WorldCoordinate.parseDouble(reader, false);
            if (reader.canRead() && reader.peek() == ' ') {
                reader.skip();
                WorldCoordinate worldCoordinate2 = WorldCoordinate.parseDouble(reader, centerCorrect);
                return new WorldCoordinates(worldCoordinate, worldCoordinate1, worldCoordinate2);
            } else {
                reader.setCursor(cursor);
                throw Vec3Argument.ERROR_NOT_COMPLETE.createWithContext(reader);
            }
        } else {
            reader.setCursor(cursor);
            throw Vec3Argument.ERROR_NOT_COMPLETE.createWithContext(reader);
        }
    }

    public static WorldCoordinates absolute(double x, double y, double z) {
        return new WorldCoordinates(new WorldCoordinate(false, x), new WorldCoordinate(false, y), new WorldCoordinate(false, z));
    }

    public static WorldCoordinates absolute(Vec2 vector) {
        return new WorldCoordinates(new WorldCoordinate(false, vector.x), new WorldCoordinate(false, vector.y), new WorldCoordinate(true, 0.0));
    }
}
