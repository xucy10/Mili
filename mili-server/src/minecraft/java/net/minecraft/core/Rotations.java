package net.minecraft.core;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Util;

public record Rotations(float x, float y, float z) {
    public static final Codec<Rotations> CODEC = Codec.FLOAT
        .listOf()
        .comapFlatMap(
            list -> Util.fixedSize((List<Float>)list, 3).map(list1 -> new Rotations(list1.get(0), list1.get(1), list1.get(2))),
            rotations -> List.of(rotations.x(), rotations.y(), rotations.z())
        );
    public static final StreamCodec<ByteBuf, Rotations> STREAM_CODEC = new StreamCodec<ByteBuf, Rotations>() {
        @Override
        public Rotations decode(ByteBuf buffer) {
            return new Rotations(buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
        }

        @Override
        public void encode(ByteBuf buffer, Rotations value) {
            buffer.writeFloat(value.x);
            buffer.writeFloat(value.y);
            buffer.writeFloat(value.z);
        }
    };
    // Paper start - add internal method for skipping validation for plugins using userdev
    private static boolean SKIP_VALIDATION = false;
    public static Rotations createWithoutValidityChecks(float x, float y, float z) {
        SKIP_VALIDATION = true;
        Rotations rotations = new Rotations(x, y, z);
        SKIP_VALIDATION = false;
        return rotations;
    }
    // Paper end  - add internal method for skipping validation for plugins using userdev

    public Rotations(float x, float y, float z) {
        if (!SKIP_VALIDATION) { // Paper - add internal method for skipping validation for plugins using userdev
        x = !Float.isInfinite(x) && !Float.isNaN(x) ? x % 360.0F : 0.0F;
        y = !Float.isInfinite(y) && !Float.isNaN(y) ? y % 360.0F : 0.0F;
        z = !Float.isInfinite(z) && !Float.isNaN(z) ? z % 360.0F : 0.0F;
        } // Paper - add internal method for skipping validation for plugins using userdev
        this.x = x;
        this.y = y;
        this.z = z;
    }
}
