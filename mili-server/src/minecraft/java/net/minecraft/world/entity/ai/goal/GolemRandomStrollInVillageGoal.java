package net.minecraft.world.entity.ai.goal;

import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class GolemRandomStrollInVillageGoal extends RandomStrollGoal {
    private static final int POI_SECTION_SCAN_RADIUS = 2;
    private static final int VILLAGER_SCAN_RADIUS = 32;
    private static final int RANDOM_POS_XY_DISTANCE = 10;
    private static final int RANDOM_POS_Y_DISTANCE = 7;

    public GolemRandomStrollInVillageGoal(PathfinderMob mob, double speedModifier) {
        super(mob, speedModifier, 240, false);
    }

    @Override
    protected @Nullable Vec3 getPosition() {
        float randomFloat = this.mob.level().random.nextFloat();
        if (this.mob.level().random.nextFloat() < 0.3F) {
            return this.getPositionTowardsAnywhere();
        } else {
            Vec3 positionTowardsVillagerWhoWantsGolem;
            if (randomFloat < 0.7F) {
                positionTowardsVillagerWhoWantsGolem = this.getPositionTowardsVillagerWhoWantsGolem();
                if (positionTowardsVillagerWhoWantsGolem == null) {
                    positionTowardsVillagerWhoWantsGolem = this.getPositionTowardsPoi();
                }
            } else {
                positionTowardsVillagerWhoWantsGolem = this.getPositionTowardsPoi();
                if (positionTowardsVillagerWhoWantsGolem == null) {
                    positionTowardsVillagerWhoWantsGolem = this.getPositionTowardsVillagerWhoWantsGolem();
                }
            }

            return positionTowardsVillagerWhoWantsGolem == null ? this.getPositionTowardsAnywhere() : positionTowardsVillagerWhoWantsGolem;
        }
    }

    private @Nullable Vec3 getPositionTowardsAnywhere() {
        return LandRandomPos.getPos(this.mob, 10, 7);
    }

    private @Nullable Vec3 getPositionTowardsVillagerWhoWantsGolem() {
        ServerLevel serverLevel = (ServerLevel)this.mob.level();
        List<Villager> entities = serverLevel.getEntities(EntityType.VILLAGER, this.mob.getBoundingBox().inflate(32.0), this::doesVillagerWantGolem);
        if (entities.isEmpty()) {
            return null;
        } else {
            Villager villager = entities.get(this.mob.level().random.nextInt(entities.size()));
            Vec3 vec3 = villager.position();
            return LandRandomPos.getPosTowards(this.mob, 10, 7, vec3);
        }
    }

    private @Nullable Vec3 getPositionTowardsPoi() {
        SectionPos randomVillageSection = this.getRandomVillageSection();
        if (randomVillageSection == null) {
            return null;
        } else {
            BlockPos randomPoiWithinSection = this.getRandomPoiWithinSection(randomVillageSection);
            return randomPoiWithinSection == null ? null : LandRandomPos.getPosTowards(this.mob, 10, 7, Vec3.atBottomCenterOf(randomPoiWithinSection));
        }
    }

    private @Nullable SectionPos getRandomVillageSection() {
        ServerLevel serverLevel = (ServerLevel)this.mob.level();
        List<SectionPos> list = SectionPos.cube(SectionPos.of(this.mob), 2).filter(pos -> serverLevel.sectionsToVillage(pos) == 0).collect(Collectors.toList());
        return list.isEmpty() ? null : list.get(serverLevel.random.nextInt(list.size()));
    }

    private @Nullable BlockPos getRandomPoiWithinSection(SectionPos sectionPos) {
        ServerLevel serverLevel = (ServerLevel)this.mob.level();
        PoiManager poiManager = serverLevel.getPoiManager();
        List<BlockPos> list = poiManager.getInRange(holder -> true, sectionPos.center(), 8, PoiManager.Occupancy.IS_OCCUPIED)
            .map(PoiRecord::getPos)
            .collect(Collectors.toList());
        return list.isEmpty() ? null : list.get(serverLevel.random.nextInt(list.size()));
    }

    private boolean doesVillagerWantGolem(Villager villager) {
        return villager.wantsToSpawnGolem(this.mob.level().getGameTime());
    }
}
