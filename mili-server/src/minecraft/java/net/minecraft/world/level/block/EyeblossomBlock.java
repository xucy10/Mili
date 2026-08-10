package net.minecraft.world.level.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.TrailParticleOption;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

public class EyeblossomBlock extends FlowerBlock {
    public static final MapCodec<EyeblossomBlock> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(Codec.BOOL.fieldOf("open").forGetter(eyeblossomBlock -> eyeblossomBlock.type.open), propertiesCodec())
            .apply(instance, EyeblossomBlock::new)
    );
    private static final int EYEBLOSSOM_XZ_RANGE = 3;
    private static final int EYEBLOSSOM_Y_RANGE = 2;
    private final EyeblossomBlock.Type type;

    @Override
    public MapCodec<? extends EyeblossomBlock> codec() {
        return CODEC;
    }

    public EyeblossomBlock(EyeblossomBlock.Type type, BlockBehaviour.Properties properties) {
        super(type.effect, type.effectDuration, properties);
        this.type = type;
    }

    public EyeblossomBlock(boolean _open, BlockBehaviour.Properties properties) {
        super(EyeblossomBlock.Type.fromBoolean(_open).effect, EyeblossomBlock.Type.fromBoolean(_open).effectDuration, properties);
        this.type = EyeblossomBlock.Type.fromBoolean(_open);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (this.type.emitSounds() && random.nextInt(700) == 0) {
            BlockState blockState = level.getBlockState(pos.below());
            if (blockState.is(Blocks.PALE_MOSS_BLOCK)) {
                level.playLocalSound(pos.getX(), pos.getY(), pos.getZ(), SoundEvents.EYEBLOSSOM_IDLE, SoundSource.AMBIENT, 1.0F, 1.0F, false);
            }
        }
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (this.tryChangingState(state, level, pos, random)) {
            level.playSound(null, pos, this.type.transform().longSwitchSound, SoundSource.BLOCKS, 1.0F, 1.0F);
        }

        super.randomTick(state, level, pos, random);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (this.tryChangingState(state, level, pos, random)) {
            level.playSound(null, pos, this.type.transform().shortSwitchSound, SoundSource.BLOCKS, 1.0F, 1.0F);
        }

        super.tick(state, level, pos, random);
    }

    private boolean tryChangingState(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        boolean flag = level.environmentAttributes().getValue(EnvironmentAttributes.EYEBLOSSOM_OPEN, pos).toBoolean(this.type.open);
        if (flag == this.type.open) {
            return false;
        } else {
            EyeblossomBlock.Type type = this.type.transform();
            level.setBlock(pos, type.state(), Block.UPDATE_ALL);
            level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(state));
            type.spawnTransformParticle(level, pos, random);
            BlockPos.betweenClosed(pos.offset(-3, -2, -3), pos.offset(3, 2, 3)).forEach(checkPos -> {
                BlockState blockState = level.getBlockState(checkPos);
                if (blockState == state) {
                    double squareRoot = Math.sqrt(pos.distSqr(checkPos));
                    int randomInt = random.nextIntBetweenInclusive((int)(squareRoot * 5.0), (int)(squareRoot * 10.0));
                    level.scheduleTick(checkPos, state.getBlock(), randomInt);
                }
            });
            return true;
        }
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean pastEdges) {
        if (!new io.papermc.paper.event.entity.EntityInsideBlockEvent(entity.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(level, pos)).callEvent()) { return; } // Paper - Add EntityInsideBlockEvent
        if (!level.isClientSide()
            && level.getDifficulty() != Difficulty.PEACEFUL
            && entity instanceof Bee bee
            && Bee.attractsBees(state)
            && !bee.hasEffect(MobEffects.POISON)) {
            bee.addEffect(this.getBeeInteractionEffect());
        }
    }

    @Override
    public MobEffectInstance getBeeInteractionEffect() {
        return new MobEffectInstance(MobEffects.POISON, 25);
    }

    public static enum Type {
        OPEN(true, MobEffects.BLINDNESS, 11.0F, SoundEvents.EYEBLOSSOM_OPEN_LONG, SoundEvents.EYEBLOSSOM_OPEN, 16545810),
        CLOSED(false, MobEffects.NAUSEA, 7.0F, SoundEvents.EYEBLOSSOM_CLOSE_LONG, SoundEvents.EYEBLOSSOM_CLOSE, 6250335);

        final boolean open;
        final Holder<MobEffect> effect;
        final float effectDuration;
        final SoundEvent longSwitchSound;
        final SoundEvent shortSwitchSound;
        private final int particleColor;

        private Type(
            final boolean _open,
            final Holder<MobEffect> effect,
            final float effectDuration,
            final SoundEvent longSwitchSound,
            final SoundEvent shortSwitchSound,
            final int particleColor
        ) {
            this.open = _open;
            this.effect = effect;
            this.effectDuration = effectDuration;
            this.longSwitchSound = longSwitchSound;
            this.shortSwitchSound = shortSwitchSound;
            this.particleColor = particleColor;
        }

        public Block block() {
            return this.open ? Blocks.OPEN_EYEBLOSSOM : Blocks.CLOSED_EYEBLOSSOM;
        }

        public BlockState state() {
            return this.block().defaultBlockState();
        }

        public EyeblossomBlock.Type transform() {
            return fromBoolean(!this.open);
        }

        public boolean emitSounds() {
            return this.open;
        }

        public static EyeblossomBlock.Type fromBoolean(boolean _open) {
            return _open ? OPEN : CLOSED;
        }

        public void spawnTransformParticle(ServerLevel level, BlockPos pos, RandomSource random) {
            Vec3 center = pos.getCenter();
            double d = 0.5 + random.nextDouble();
            Vec3 vec3 = new Vec3(random.nextDouble() - 0.5, random.nextDouble() + 1.0, random.nextDouble() - 0.5);
            Vec3 vec31 = center.add(vec3.scale(d));
            TrailParticleOption trailParticleOption = new TrailParticleOption(vec31, this.particleColor, (int)(20.0 * d));
            level.sendParticles(trailParticleOption, center.x, center.y, center.z, 1, 0.0, 0.0, 0.0, 0.0);
        }

        public SoundEvent longSwitchSound() {
            return this.longSwitchSound;
        }
    }
}
