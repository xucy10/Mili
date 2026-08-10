package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.storage.loot.LootContext;

public class CuredZombieVillagerTrigger extends SimpleCriterionTrigger<CuredZombieVillagerTrigger.TriggerInstance> {
    @Override
    public Codec<CuredZombieVillagerTrigger.TriggerInstance> codec() {
        return CuredZombieVillagerTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, Zombie zombie, Villager villager) {
        LootContext lootContext = EntityPredicate.createContext(player, zombie);
        LootContext lootContext1 = EntityPredicate.createContext(player, villager);
        this.trigger(player, instance -> instance.matches(lootContext, lootContext1));
    }

    public record TriggerInstance(
        @Override Optional<ContextAwarePredicate> player, Optional<ContextAwarePredicate> zombie, Optional<ContextAwarePredicate> villager
    ) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<CuredZombieVillagerTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(CuredZombieVillagerTrigger.TriggerInstance::player),
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("zombie").forGetter(CuredZombieVillagerTrigger.TriggerInstance::zombie),
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("villager").forGetter(CuredZombieVillagerTrigger.TriggerInstance::villager)
                )
                .apply(instance, CuredZombieVillagerTrigger.TriggerInstance::new)
        );

        public static Criterion<CuredZombieVillagerTrigger.TriggerInstance> curedZombieVillager() {
            return CriteriaTriggers.CURED_ZOMBIE_VILLAGER
                .createCriterion(new CuredZombieVillagerTrigger.TriggerInstance(Optional.empty(), Optional.empty(), Optional.empty()));
        }

        public boolean matches(LootContext zombie, LootContext villager) {
            return (!this.zombie.isPresent() || this.zombie.get().matches(zombie)) && (!this.villager.isPresent() || this.villager.get().matches(villager));
        }

        @Override
        public void validate(CriterionValidator validator) {
            SimpleCriterionTrigger.SimpleInstance.super.validate(validator);
            validator.validateEntity(this.zombie, "zombie");
            validator.validateEntity(this.villager, "villager");
        }
    }
}
