package net.minecraft.world.level.levelgen;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import java.util.function.Function;
import net.minecraft.world.level.dimension.DimensionType;

public interface VerticalAnchor {
    Codec<VerticalAnchor> CODEC = Codec.xor(VerticalAnchor.Absolute.CODEC, Codec.xor(VerticalAnchor.AboveBottom.CODEC, VerticalAnchor.BelowTop.CODEC))
        .xmap(VerticalAnchor::merge, VerticalAnchor::split);
    VerticalAnchor BOTTOM = aboveBottom(0);
    VerticalAnchor TOP = belowTop(0);

    static VerticalAnchor absolute(int value) {
        return new VerticalAnchor.Absolute(value);
    }

    static VerticalAnchor aboveBottom(int value) {
        return new VerticalAnchor.AboveBottom(value);
    }

    static VerticalAnchor belowTop(int value) {
        return new VerticalAnchor.BelowTop(value);
    }

    static VerticalAnchor bottom() {
        return BOTTOM;
    }

    static VerticalAnchor top() {
        return TOP;
    }

    private static VerticalAnchor merge(Either<VerticalAnchor.Absolute, Either<VerticalAnchor.AboveBottom, VerticalAnchor.BelowTop>> anchor) {
        return anchor.map(Function.identity(), Either::unwrap);
    }

    private static Either<VerticalAnchor.Absolute, Either<VerticalAnchor.AboveBottom, VerticalAnchor.BelowTop>> split(VerticalAnchor anchor) {
        return anchor instanceof VerticalAnchor.Absolute
            ? Either.left((VerticalAnchor.Absolute)anchor)
            : Either.right(
                anchor instanceof VerticalAnchor.AboveBottom ? Either.left((VerticalAnchor.AboveBottom)anchor) : Either.right((VerticalAnchor.BelowTop)anchor)
            );
    }

    int resolveY(WorldGenerationContext context);

    public record AboveBottom(int offset) implements VerticalAnchor {
        public static final Codec<VerticalAnchor.AboveBottom> CODEC = Codec.intRange(DimensionType.MIN_Y, DimensionType.MAX_Y)
            .fieldOf("above_bottom")
            .xmap(VerticalAnchor.AboveBottom::new, VerticalAnchor.AboveBottom::offset)
            .codec();

        @Override
        public int resolveY(WorldGenerationContext context) {
            return context.getMinGenY() + this.offset;
        }

        @Override
        public String toString() {
            return this.offset + " above bottom";
        }
    }

    public record Absolute(int y) implements VerticalAnchor {
        public static final Codec<VerticalAnchor.Absolute> CODEC = Codec.intRange(DimensionType.MIN_Y, DimensionType.MAX_Y)
            .fieldOf("absolute")
            .xmap(VerticalAnchor.Absolute::new, VerticalAnchor.Absolute::y)
            .codec();

        @Override
        public int resolveY(WorldGenerationContext context) {
            return this.y;
        }

        @Override
        public String toString() {
            return this.y + " absolute";
        }
    }

    public record BelowTop(int offset) implements VerticalAnchor {
        public static final Codec<VerticalAnchor.BelowTop> CODEC = Codec.intRange(DimensionType.MIN_Y, DimensionType.MAX_Y)
            .fieldOf("below_top")
            .xmap(VerticalAnchor.BelowTop::new, VerticalAnchor.BelowTop::offset)
            .codec();

        @Override
        public int resolveY(WorldGenerationContext context) {
            return context.getGenDepth() - 1 + context.getMinGenY() - this.offset;
        }

        @Override
        public String toString() {
            return this.offset + " below top";
        }
    }
}
