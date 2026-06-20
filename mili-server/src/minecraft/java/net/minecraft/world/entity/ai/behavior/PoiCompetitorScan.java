package net.minecraft.world.entity.ai.behavior;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

public class PoiCompetitorScan {
    public static BehaviorControl<Villager> create() {
        return BehaviorBuilder.create(
            instance -> instance.group(instance.present(MemoryModuleType.JOB_SITE), instance.present(MemoryModuleType.NEAREST_LIVING_ENTITIES))
                .apply(
                    instance,
                    (jobSite, nearestLivingEntities) -> (level, villager, gameTime) -> {
                        GlobalPos globalPos = instance.get(jobSite);
                        // Folia start - region threading
                        if (globalPos.dimension() != level.dimension() || !ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(level, globalPos.pos())) {
                            return true;
                        }
                        // Folia end - region threading
                        // Luminol start - POI fixes
                        var blockPosOfJobSite = globalPos.pos();
                        var sectionPosOfJobSite = net.minecraft.core.SectionPos.asLong(blockPosOfJobSite);
                        var poiManager = level.getPoiManager();
                        // we don't care if we should clear the memory of JOB_SITE
                        // as it will be automatically removed in SetWalkTargetFromBlockMemory
                        // so simply break down if it's not loaded
                        var poiChunk = me.earthme.luminol.config.modules.fixes.POIRangeFixes.doNotCompetePOIIfUnloaded ? poiManager.get(sectionPosOfJobSite) : poiManager.getOrLoad(sectionPosOfJobSite);
                        poiChunk.flatMap(poiSection -> poiSection.getType(blockPosOfJobSite))
                        // Luminol end - POI fixes
                        /*level.getPoiManager() // Luminol - POI fixes
                            .getType(globalPos.pos())*/ // Luminol - POI fixes
                            .ifPresent(
                                // Paper start - Improve performance of PoiCompetitorScan by unrolling stream
                                // The previous logic used Stream#reduce to simulate a form of single-iteration bubble sort
                                // in which the "winning" villager would maintain MemoryModuleType.JOB_SITE while all others
                                // would lose said memory module type by passing each "current winner" and incoming next
                                // villager to #selectWinner.
                                poi -> {
                                    final List<LivingEntity> livingEntities = instance.get(nearestLivingEntities);

                                    Villager winner = villager;
                                    for (final LivingEntity other : livingEntities) {
                                        if (other == villager) {
                                            continue;
                                        }
                                        if (!(other instanceof final net.minecraft.world.entity.npc.villager.Villager otherVillager)) {
                                            continue;
                                        }
                                        if (!other.isAlive()) {
                                            continue;
                                        }
                                        if (!competesForSameJobsite(globalPos, poi, otherVillager)) {
                                            continue;
                                        }
                                        winner = selectWinner(winner, otherVillager);
                                    }
                                }
                                // Paper end - Improve performance of PoiCompetitorScan by unrolling stream
                            );
                        return true;
                    }
                )
        );
    }

    private static Villager selectWinner(Villager villagerA, Villager villagerB) {
        Villager villager;
        Villager villager1;
        if (villagerA.getVillagerXp() > villagerB.getVillagerXp()) {
            villager = villagerA;
            villager1 = villagerB;
        } else {
            villager = villagerB;
            villager1 = villagerA;
        }

        villager1.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
        return villager;
    }

    private static boolean competesForSameJobsite(GlobalPos jobSitePos, Holder<PoiType> poiType, Villager villager) {
        Optional<GlobalPos> memory = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
        return memory.isPresent() && jobSitePos.equals(memory.get()) && hasMatchingProfession(poiType, villager.getVillagerData().profession());
    }

    private static boolean hasMatchingProfession(Holder<PoiType> poiType, Holder<VillagerProfession> profession) {
        return profession.value().heldJobSite().test(poiType);
    }
}
