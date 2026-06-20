package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ColorRGBA;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.sounds.AmbientDesertBlockSoundsPlayer;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class SandBlock extends ColoredFallingBlock {
    public static final MapCodec<SandBlock> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(ColorRGBA.CODEC.fieldOf("falling_dust_color").forGetter(sandBlock -> sandBlock.dustColor), propertiesCodec())
            .apply(instance, SandBlock::new)
    );

    @Override
    public MapCodec<SandBlock> codec() {
        return CODEC;
    }

    public SandBlock(ColorRGBA dustColor, BlockBehaviour.Properties properties) {
        super(dustColor, properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        super.animateTick(state, level, pos, random);
        AmbientDesertBlockSoundsPlayer.playAmbientSandSounds(level, pos, random);
    }
}
