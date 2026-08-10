package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public class LevitationTrigger extends SimpleCriterionTrigger<LevitationTrigger.TriggerInstance> {
    @Override
    public Codec<LevitationTrigger.TriggerInstance> codec() {
        return LevitationTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, Vec3 startPos, int duration) {
        this.trigger(player, instance -> instance.matches(player, startPos, duration));
    }

    public record TriggerInstance(@Override Optional<ContextAwarePredicate> player, Optional<DistancePredicate> distance, MinMaxBounds.Ints duration)
        implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<LevitationTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(LevitationTrigger.TriggerInstance::player),
                    DistancePredicate.CODEC.optionalFieldOf("distance").forGetter(LevitationTrigger.TriggerInstance::distance),
                    MinMaxBounds.Ints.CODEC.optionalFieldOf("duration", MinMaxBounds.Ints.ANY).forGetter(LevitationTrigger.TriggerInstance::duration)
                )
                .apply(instance, LevitationTrigger.TriggerInstance::new)
        );

        public static Criterion<LevitationTrigger.TriggerInstance> levitated(DistancePredicate distance) {
            return CriteriaTriggers.LEVITATION
                .createCriterion(new LevitationTrigger.TriggerInstance(Optional.empty(), Optional.of(distance), MinMaxBounds.Ints.ANY));
        }

        public boolean matches(ServerPlayer player, Vec3 startPos, int duration) {
            return (!this.distance.isPresent() || this.distance.get().matches(startPos.x, startPos.y, startPos.z, player.getX(), player.getY(), player.getZ()))
                && this.duration.matches(duration);
        }
    }
}
