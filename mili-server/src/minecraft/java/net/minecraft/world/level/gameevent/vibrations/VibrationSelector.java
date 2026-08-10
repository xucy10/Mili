package net.minecraft.world.level.gameevent.vibrations;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;

public class VibrationSelector {
    public static final Codec<VibrationSelector> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                VibrationInfo.CODEC.lenientOptionalFieldOf("event").forGetter(selector -> selector.currentVibrationData.map(Pair::getLeft)),
                Codec.LONG.fieldOf("tick").forGetter(selector -> selector.currentVibrationData.map(Pair::getRight).orElse(-1L))
            )
            .apply(instance, VibrationSelector::new)
    );
    private Optional<Pair<VibrationInfo, Long>> currentVibrationData;

    public VibrationSelector(Optional<VibrationInfo> event, long tick) {
        this.currentVibrationData = event.map(inf -> Pair.of(inf, tick));
    }

    public VibrationSelector() {
        this.currentVibrationData = Optional.empty();
    }

    public void addCandidate(VibrationInfo vibrationInfo, long tick) {
        if (this.shouldReplaceVibration(vibrationInfo, tick)) {
            this.currentVibrationData = Optional.of(Pair.of(vibrationInfo, tick));
        }
    }

    private boolean shouldReplaceVibration(VibrationInfo vibrationInfo, long tick) {
        if (this.currentVibrationData.isEmpty()) {
            return true;
        } else {
            Pair<VibrationInfo, Long> pair = this.currentVibrationData.get();
            long right = pair.getRight();
            if (tick != right) {
                return false;
            } else {
                VibrationInfo vibrationInfo1 = pair.getLeft();
                return vibrationInfo.distance() < vibrationInfo1.distance()
                    || !(vibrationInfo.distance() > vibrationInfo1.distance())
                        && VibrationSystem.getGameEventFrequency(vibrationInfo.gameEvent()) > VibrationSystem.getGameEventFrequency(vibrationInfo1.gameEvent());
            }
        }
    }

    public Optional<VibrationInfo> chosenCandidate(long tick) {
        if (this.currentVibrationData.isEmpty()) {
            return Optional.empty();
        } else {
            return this.currentVibrationData.get().getRight() < tick ? Optional.of(this.currentVibrationData.get().getLeft()) : Optional.empty();
        }
    }

    public void startOver() {
        this.currentVibrationData = Optional.empty();
    }
}
