package net.minecraft.world.entity.animal.cow;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SpellParticleOption;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SuspiciousEffectHolder;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import org.jspecify.annotations.Nullable;

public class MushroomCow extends AbstractCow implements Shearable {
    private static final EntityDataAccessor<Integer> DATA_TYPE = SynchedEntityData.defineId(MushroomCow.class, EntityDataSerializers.INT);
    private static final int MUTATE_CHANCE = 1024;
    private static final String TAG_STEW_EFFECTS = "stew_effects";
    public @Nullable SuspiciousStewEffects stewEffects;
    private @Nullable UUID lastLightningBoltUUID;

    public MushroomCow(EntityType<? extends MushroomCow> type, Level level) {
        super(type, level);
    }

    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        return level.getBlockState(pos.below()).is(Blocks.MYCELIUM) ? 10.0F : level.getPathfindingCostFromLightLevels(pos);
    }

    public static boolean checkMushroomSpawnRules(
        EntityType<MushroomCow> entityType, LevelAccessor level, EntitySpawnReason spawnReason, BlockPos pos, RandomSource random
    ) {
        return level.getBlockState(pos.below()).is(BlockTags.MOOSHROOMS_SPAWNABLE_ON) && isBrightEnoughToSpawn(level, pos);
    }

    @Override
    public void thunderHit(ServerLevel level, LightningBolt lightning) {
        UUID uuid = lightning.getUUID();
        if (!uuid.equals(this.lastLightningBoltUUID)) {
            this.setVariant(this.getVariant() == MushroomCow.Variant.RED ? MushroomCow.Variant.BROWN : MushroomCow.Variant.RED);
            this.lastLightningBoltUUID = uuid;
            this.playSound(SoundEvents.MOOSHROOM_CONVERT, 2.0F, 1.0F);
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_TYPE, MushroomCow.Variant.DEFAULT.id);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);
        if (itemInHand.is(Items.BOWL) && !this.isBaby()) {
            boolean flag = false;
            ItemStack itemStack;
            if (this.stewEffects != null) {
                flag = true;
                itemStack = new ItemStack(Items.SUSPICIOUS_STEW);
                itemStack.set(DataComponents.SUSPICIOUS_STEW_EFFECTS, this.stewEffects);
                this.stewEffects = null;
            } else {
                itemStack = new ItemStack(Items.MUSHROOM_STEW);
            }

            ItemStack itemStack1 = ItemUtils.createFilledResult(itemInHand, player, itemStack, false);
            player.setItemInHand(hand, itemStack1);
            SoundEvent soundEvent;
            if (flag) {
                soundEvent = SoundEvents.MOOSHROOM_MILK_SUSPICIOUSLY;
            } else {
                soundEvent = SoundEvents.MOOSHROOM_MILK;
            }

            this.playSound(soundEvent, 1.0F, 1.0F);
            return InteractionResult.SUCCESS;
        } else if (itemInHand.is(Items.SHEARS) && this.readyForShearing()) {
            if (this.level() instanceof ServerLevel serverLevel) {
                // CraftBukkit start
                // Paper start - custom shear drops
                java.util.List<ItemStack> drops = this.generateDefaultDrops(serverLevel, itemInHand);
                org.bukkit.event.player.PlayerShearEntityEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.handlePlayerShearEntityEvent(player, this, itemInHand, hand, drops);
                if (event != null) {
                    if (event.isCancelled()) return InteractionResult.PASS;
                    drops = org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(event.getDrops());
                    // Paper end - custom shear drops
                }
                // CraftBukkit end
                this.shear(serverLevel, SoundSource.PLAYERS, itemInHand, drops); // Paper - custom shear drops
                this.gameEvent(GameEvent.SHEAR, player);
                itemInHand.hurtAndBreak(1, player, hand.asEquipmentSlot());
            }

            return InteractionResult.SUCCESS;
        } else if (this.getVariant() == MushroomCow.Variant.BROWN) {
            Optional<SuspiciousStewEffects> effectsFromItemStack = this.getEffectsFromItemStack(itemInHand);
            if (effectsFromItemStack.isEmpty()) {
                return super.mobInteract(player, hand);
            } else {
                if (this.stewEffects != null) {
                    for (int i = 0; i < 2; i++) {
                        this.level()
                            .addParticle(
                                ParticleTypes.SMOKE,
                                this.getX() + this.random.nextDouble() / 2.0,
                                this.getY(0.5),
                                this.getZ() + this.random.nextDouble() / 2.0,
                                0.0,
                                this.random.nextDouble() / 5.0,
                                0.0
                            );
                    }
                } else {
                    itemInHand.consume(1, player);
                    SpellParticleOption spellParticleOption = SpellParticleOption.create(ParticleTypes.EFFECT, -1, 1.0F);

                    for (int i1 = 0; i1 < 4; i1++) {
                        this.level()
                            .addParticle(
                                spellParticleOption,
                                this.getX() + this.random.nextDouble() / 2.0,
                                this.getY(0.5),
                                this.getZ() + this.random.nextDouble() / 2.0,
                                0.0,
                                this.random.nextDouble() / 5.0,
                                0.0
                            );
                    }

                    this.stewEffects = effectsFromItemStack.get();
                    this.playSound(SoundEvents.MOOSHROOM_EAT, 2.0F, 1.0F);
                }

                return InteractionResult.SUCCESS;
            }
        } else {
            return super.mobInteract(player, hand);
        }
    }

    @Override
    public void shear(ServerLevel level, SoundSource source, ItemStack shears) {
        // Paper start - custom shear drops
        this.shear(level, source, shears, this.generateDefaultDrops(level, shears));
    }

    @Override
    public java.util.List<ItemStack> generateDefaultDrops(final ServerLevel level, final ItemStack shears) {
        final java.util.List<ItemStack> drops = new it.unimi.dsi.fastutil.objects.ObjectArrayList<>();
        this.dropFromShearingLootTable(level, BuiltInLootTables.SHEAR_MOOSHROOM, shears, (ignored, stack) -> {
            for (int i = 0; i < stack.getCount(); ++i) drops.add(stack.copyWithCount(1));
        });
        return drops;
    }

    @Override
    public void shear(ServerLevel level, SoundSource source, ItemStack shears, java.util.List<ItemStack> drops) {
        // Paper end
        level.playSound(null, this, SoundEvents.MOOSHROOM_SHEAR, source, 1.0F, 1.0F);
        this.convertTo(EntityType.COW, ConversionParams.single(this, false, false), cow -> {
            level.sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY(0.5), this.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
            // Paper start - custom shear drops; moved drop generation to separate method
            drops.forEach(drop -> {
                this.spawnAtLocation(level, new ItemEntity(this.level(), this.getX(), this.getY(1.0), this.getZ(), drop));
            });
            // Paper end - custom shear drops
        }, org.bukkit.event.entity.EntityTransformEvent.TransformReason.SHEARED, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SHEARED); // CraftBukkit
    }

    @Override
    public boolean readyForShearing() {
        return this.isAlive() && !this.isBaby();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store("Type", MushroomCow.Variant.CODEC, this.getVariant());
        output.storeNullable("stew_effects", SuspiciousStewEffects.CODEC, this.stewEffects);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setVariant(input.read("Type", MushroomCow.Variant.CODEC).orElse(MushroomCow.Variant.DEFAULT));
        this.stewEffects = input.read("stew_effects", SuspiciousStewEffects.CODEC).orElse(null);
    }

    private Optional<SuspiciousStewEffects> getEffectsFromItemStack(ItemStack stack) {
        SuspiciousEffectHolder suspiciousEffectHolder = SuspiciousEffectHolder.tryGet(stack.getItem());
        return suspiciousEffectHolder != null ? Optional.of(suspiciousEffectHolder.getSuspiciousEffects()) : Optional.empty();
    }

    public void setVariant(MushroomCow.Variant variant) {
        this.entityData.set(DATA_TYPE, variant.id);
    }

    public MushroomCow.Variant getVariant() {
        return MushroomCow.Variant.byId(this.entityData.get(DATA_TYPE));
    }

    @Override
    public <T> @Nullable T get(DataComponentType<? extends T> component) {
        return component == DataComponents.MOOSHROOM_VARIANT ? castComponentValue((DataComponentType<T>)component, this.getVariant()) : super.get(component);
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter componentGetter) {
        this.applyImplicitComponentIfPresent(componentGetter, DataComponents.MOOSHROOM_VARIANT);
        super.applyImplicitComponents(componentGetter);
    }

    @Override
    protected <T> boolean applyImplicitComponent(DataComponentType<T> component, T value) {
        if (component == DataComponents.MOOSHROOM_VARIANT) {
            this.setVariant(castComponentValue(DataComponents.MOOSHROOM_VARIANT, value));
            return true;
        } else {
            return super.applyImplicitComponent(component, value);
        }
    }

    @Override
    public @Nullable MushroomCow getBreedOffspring(ServerLevel level, AgeableMob partner) {
        MushroomCow mushroomCow = EntityType.MOOSHROOM.create(level, EntitySpawnReason.BREEDING);
        if (mushroomCow != null) {
            mushroomCow.setVariant(this.getOffspringVariant((MushroomCow)partner));
        }

        return mushroomCow;
    }

    private MushroomCow.Variant getOffspringVariant(MushroomCow partner) {
        MushroomCow.Variant variant = this.getVariant();
        MushroomCow.Variant variant1 = partner.getVariant();
        MushroomCow.Variant variant2;
        if (variant == variant1 && this.random.nextInt(1024) == 0) {
            variant2 = variant == MushroomCow.Variant.BROWN ? MushroomCow.Variant.RED : MushroomCow.Variant.BROWN;
        } else {
            variant2 = this.random.nextBoolean() ? variant : variant1;
        }

        return variant2;
    }

    public static enum Variant implements StringRepresentable {
        RED("red", 0, Blocks.RED_MUSHROOM.defaultBlockState()),
        BROWN("brown", 1, Blocks.BROWN_MUSHROOM.defaultBlockState());

        public static final MushroomCow.Variant DEFAULT = RED;
        public static final Codec<MushroomCow.Variant> CODEC = StringRepresentable.fromEnum(MushroomCow.Variant::values);
        private static final IntFunction<MushroomCow.Variant> BY_ID = ByIdMap.continuous(MushroomCow.Variant::id, values(), ByIdMap.OutOfBoundsStrategy.CLAMP);
        public static final StreamCodec<ByteBuf, MushroomCow.Variant> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, MushroomCow.Variant::id);
        private final String type;
        final int id;
        private final BlockState blockState;

        private Variant(final String type, final int id, final BlockState blockState) {
            this.type = type;
            this.id = id;
            this.blockState = blockState;
        }

        public BlockState getBlockState() {
            return this.blockState;
        }

        @Override
        public String getSerializedName() {
            return this.type;
        }

        private int id() {
            return this.id;
        }

        static MushroomCow.Variant byId(int id) {
            return BY_ID.apply(id);
        }
    }
}
