package net.minecraft.world.scores;

import java.util.Objects;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.numbers.NumberFormat;
import org.jspecify.annotations.Nullable;

public interface ReadOnlyScoreInfo {
    int value();

    boolean isLocked();

    @Nullable NumberFormat numberFormat();

    default MutableComponent formatValue(NumberFormat format) {
        return Objects.requireNonNullElse(this.numberFormat(), format).format(this.value());
    }

    static MutableComponent safeFormatValue(@Nullable ReadOnlyScoreInfo scoreInfo, NumberFormat format) {
        return scoreInfo != null ? scoreInfo.formatValue(format) : format.format(0);
    }
}
