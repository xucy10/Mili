package net.minecraft.world.level.dimension.end;

import com.google.common.collect.ImmutableList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.SpikeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.SpikeConfiguration;

public enum DragonRespawnAnimation {
    START {
        @Override
        public void tick(ServerLevel level, EndDragonFight manager, List<EndCrystal> crystals, int ticks, BlockPos pos) {
            BlockPos blockPos = new BlockPos(0, 128, 0);

            for (EndCrystal endCrystal : crystals) {
                endCrystal.setBeamTarget(blockPos);
            }

            manager.setRespawnStage(PREPARING_TO_SUMMON_PILLARS);
        }
    },
    PREPARING_TO_SUMMON_PILLARS {
        @Override
        public void tick(ServerLevel level, EndDragonFight manager, List<EndCrystal> crystals, int ticks, BlockPos pos) {
            if (ticks < 100) {
                if (ticks == 0 || ticks == 50 || ticks == 51 || ticks == 52 || ticks >= 95) {
                    level.levelEvent(LevelEvent.ANIMATION_DRAGON_SUMMON_ROAR, new BlockPos(0, 128, 0), 0);
                }
            } else {
                manager.setRespawnStage(SUMMONING_PILLARS);
            }
        }
    },
    SUMMONING_PILLARS {
        @Override
        public void tick(ServerLevel level, EndDragonFight manager, List<EndCrystal> crystals, int ticks, BlockPos pos) {
            int i = 40;
            boolean flag = ticks % 40 == 0;
            boolean flag1 = ticks % 40 == 39;
            if (flag || flag1) {
                List<SpikeFeature.EndSpike> spikesForLevel = SpikeFeature.getSpikesForLevel(level);
                int i1 = ticks / 40;
                if (i1 < spikesForLevel.size()) {
                    SpikeFeature.EndSpike endSpike = spikesForLevel.get(i1);
                    if (flag) {
                        for (EndCrystal endCrystal : crystals) {
                            endCrystal.setBeamTarget(new BlockPos(endSpike.getCenterX(), endSpike.getHeight() + 1, endSpike.getCenterZ()));
                        }
                    } else {
                        int i2 = 10;

                        for (BlockPos blockPos : BlockPos.betweenClosed(
                            new BlockPos(endSpike.getCenterX() - 10, endSpike.getHeight() - 10, endSpike.getCenterZ() - 10),
                            new BlockPos(endSpike.getCenterX() + 10, endSpike.getHeight() + 10, endSpike.getCenterZ() + 10)
                        )) {
                            level.removeBlock(blockPos, false);
                        }

                        level.explode(
                            null, endSpike.getCenterX() + 0.5F, endSpike.getHeight(), endSpike.getCenterZ() + 0.5F, 5.0F, Level.ExplosionInteraction.BLOCK
                        );
                        SpikeConfiguration spikeConfiguration = new SpikeConfiguration(true, ImmutableList.of(endSpike), new BlockPos(0, 128, 0));
                        Feature.END_SPIKE
                            .place(
                                spikeConfiguration,
                                level,
                                level.getChunkSource().getGenerator(),
                                RandomSource.create(),
                                new BlockPos(endSpike.getCenterX(), 45, endSpike.getCenterZ())
                            );
                    }
                } else if (flag) {
                    manager.setRespawnStage(SUMMONING_DRAGON);
                }
            }
        }
    },
    SUMMONING_DRAGON {
        @Override
        public void tick(ServerLevel level, EndDragonFight manager, List<EndCrystal> crystals, int ticks, BlockPos pos) {
            if (ticks >= 100) {
                manager.setRespawnStage(END);
                manager.resetSpikeCrystals();

                for (EndCrystal endCrystal : crystals) {
                    endCrystal.setBeamTarget(null);
                    level.explode(endCrystal, endCrystal.getX(), endCrystal.getY(), endCrystal.getZ(), 6.0F, Level.ExplosionInteraction.NONE);
                    endCrystal.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.EXPLODE); // CraftBukkit - add Bukkit remove cause
                }
            } else if (ticks >= 80) {
                level.levelEvent(LevelEvent.ANIMATION_DRAGON_SUMMON_ROAR, new BlockPos(0, 128, 0), 0);
            } else if (ticks == 0) {
                for (EndCrystal endCrystal : crystals) {
                    endCrystal.setBeamTarget(new BlockPos(0, 128, 0));
                }
            } else if (ticks < 5) {
                level.levelEvent(LevelEvent.ANIMATION_DRAGON_SUMMON_ROAR, new BlockPos(0, 128, 0), 0);
            }
        }
    },
    END {
        @Override
        public void tick(ServerLevel level, EndDragonFight manager, List<EndCrystal> crystals, int ticks, BlockPos pos) {
        }
    };

    public abstract void tick(ServerLevel level, EndDragonFight manager, List<EndCrystal> crystals, int ticks, BlockPos pos);
}
