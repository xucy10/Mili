package net.minecraft.world.entity.ai.behavior;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.schedule.Activity;

public class SetRaidStatus {
    public static BehaviorControl<LivingEntity> create() {
        return BehaviorBuilder.create(instance -> instance.point((level, entity, gameTime) -> {
            if (level.random.nextInt(20) != 0) {
                return false;
            } else {
                Brain<?> brain = entity.getBrain();
                Raid raidAt = level.getRaidAt(entity.blockPosition());
                if (raidAt != null) {
                    if (raidAt.hasFirstWaveSpawned() && !raidAt.isBetweenWaves()) {
                        brain.setDefaultActivity(Activity.RAID);
                        brain.setActiveActivityIfPossible(Activity.RAID);
                    } else {
                        brain.setDefaultActivity(Activity.PRE_RAID);
                        brain.setActiveActivityIfPossible(Activity.PRE_RAID);
                    }
                }

                return true;
            }
        }));
    }
}
