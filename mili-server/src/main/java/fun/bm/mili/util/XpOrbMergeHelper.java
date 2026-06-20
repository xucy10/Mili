package fun.bm.mili.util;

import fun.bm.mili.config.modules.misc.ItemEntityPerfConfig;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

/**
 * mili - Experience orb merge optimization helper.
 *
 * <p>Provides enhanced cross-value merging: orbs with different XP values
 * can merge up to a configurable maximum, significantly reducing entity
 * count during mob farming.
 */
public final class XpOrbMergeHelper {
    private XpOrbMergeHelper() {}

    /**
     * Attempts to merge this orb with nearby orbs of different values.
     * Called after the vanilla scanForMerges pass.
     *
     * @param orb the experience orb to merge
     */
    public static void enhancedMerge(ExperienceOrb orb) {
        int maxValue = ItemEntityPerfConfig.xpOrbMaxValue;
        if (maxValue <= 0 || orb.isRemoved() || orb.getValue() >= maxValue) {
            return;
        }

        double mergeRadius = ItemEntityPerfConfig.xpOrbMergeRadius > 0
            ? ItemEntityPerfConfig.xpOrbMergeRadius : 0.5;

        AABB searchBox = orb.getBoundingBox().inflate(mergeRadius);

        // 1.21.11: Level#getEntitiesOfClass(Class) was removed. Use
        // getEntities(EntityTypeTest<Entity, ExperienceOrb>, AABB, Predicate)
        // with EntityTypeTest.forClass instead.
        for (ExperienceOrb other : orb.level().getEntities(
            EntityTypeTest.forClass(ExperienceOrb.class), searchBox,
            e -> e != orb && !e.isRemoved() && e.getValue() < maxValue
        )) {
            int combinedValue = orb.getValue() + other.getValue();
            if (combinedValue <= maxValue) {
                orb.setValue(combinedValue);
                orb.count += other.count;
                other.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.MERGE);
                break;
            }
        }
    }
}
