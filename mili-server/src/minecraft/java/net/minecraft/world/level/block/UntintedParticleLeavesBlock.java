package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.ParticleUtils;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class UntintedParticleLeavesBlock extends LeavesBlock {
    public static final MapCodec<UntintedParticleLeavesBlock> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                ExtraCodecs.floatRange(0.0F, 1.0F).fieldOf("leaf_particle_chance").forGetter(block -> block.leafParticleChance),
                ParticleTypes.CODEC.fieldOf("leaf_particle").forGetter(block -> block.leafParticle),
                propertiesCodec()
            )
            .apply(instance, UntintedParticleLeavesBlock::new)
    );
    protected final ParticleOptions leafParticle;

    public UntintedParticleLeavesBlock(float leafParticleChance, ParticleOptions leafParticle, BlockBehaviour.Properties properties) {
        super(leafParticleChance, properties);
        this.leafParticle = leafParticle;
    }

    @Override
    protected void spawnFallingLeavesParticle(Level level, BlockPos pos, RandomSource random) {
        ParticleUtils.spawnParticleBelow(level, pos, random, this.leafParticle);
    }

    @Override
    public MapCodec<UntintedParticleLeavesBlock> codec() {
        return CODEC;
    }
}
