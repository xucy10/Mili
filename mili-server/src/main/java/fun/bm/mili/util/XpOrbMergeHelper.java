package fun.bm.mili.util;

import fun.bm.mili.config.modules.misc.ItemEntityPerfConfig;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * mili - Experience orb merge optimization helper.
 *
 * <p>Provides enhanced cross-value merging: orbs with different XP values
 * can merge up to a configurable maximum, significantly reducing entity
 * count during mob farming.
 *
 * <p>REFACTORED: 修复并发访问导致的实体异常问题
 * - 使用快照列表避免遍历期间修改集合
 * - 添加双重检查确保实体状态一致性
 * - 同步合并操作防止竞态条件
 */
public final class XpOrbMergeHelper {
    private XpOrbMergeHelper() {}

    /**
     * Attempts to merge this orb with nearby orbs of different values.
     * Called after the vanilla scanForMerges pass.
     *
     * <p>安全说明: 此方法现在使用快照列表进行遍历，并在合并时使用
     * 同步块确保线程安全，避免在Folia多线程环境下出现实体状态异常。
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

        // REFACTORED: 创建快照列表避免遍历期间修改原始集合
        List<ExperienceOrb> candidates = new ArrayList<>();
        try {
            for (ExperienceOrb other : orb.level().getEntities(
                EntityTypeTest.forClass(ExperienceOrb.class), searchBox,
                e -> e != orb && !e.isRemoved() && e.getValue() < maxValue
            )) {
                candidates.add(other);
            }
        } catch (Exception e) {
            // 如果实体查询失败（例如实体已被移除），安全地返回
            return;
        }

        // 处理候选列表
        for (ExperienceOrb other : candidates) {
            // 双重检查: 确保实体仍然有效且未被并发移除
            if (other == null || other.isRemoved() || !other.isAlive()) {
                continue;
            }

            // 同步块: 确保合并操作的原子性
            synchronized (XpOrbMergeHelper.class) {
                // 在同步块内再次检查状态
                if (orb.isRemoved() || other.isRemoved() || !orb.isAlive() || !other.isAlive()) {
                    continue;
                }

                int currentValue = orb.getValue();
                int otherValue = other.getValue();

                // 确保数值在配置范围内
                if (currentValue >= maxValue || otherValue >= maxValue) {
                    continue;
                }

                int combinedValue = currentValue + otherValue;
                if (combinedValue <= maxValue) {
                    orb.setValue(combinedValue);
                    orb.count += other.count;
                    other.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.MERGE);
                    break;
                }
            }
        }
    }
}
