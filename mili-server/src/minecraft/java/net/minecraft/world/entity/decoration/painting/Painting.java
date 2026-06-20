package net.minecraft.world.entity.decoration.painting;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.PaintingVariantTags;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.variant.VariantUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class Painting extends HangingEntity {
    private static final EntityDataAccessor<Holder<PaintingVariant>> DATA_PAINTING_VARIANT_ID = SynchedEntityData.defineId(
        Painting.class, EntityDataSerializers.PAINTING_VARIANT
    );
    public static final float DEPTH = 0.0625F;

    public Painting(EntityType<? extends Painting> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PAINTING_VARIANT_ID, VariantUtils.getAny(this.registryAccess(), Registries.PAINTING_VARIANT));
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_PAINTING_VARIANT_ID.equals(key)) {
            this.recalculateBoundingBox();
        }
    }

    public void setVariant(Holder<PaintingVariant> variant) {
        this.entityData.set(DATA_PAINTING_VARIANT_ID, variant);
    }

    public Holder<PaintingVariant> getVariant() {
        return this.entityData.get(DATA_PAINTING_VARIANT_ID);
    }

    @Override
    public <T> @Nullable T get(DataComponentType<? extends T> component) {
        return component == DataComponents.PAINTING_VARIANT ? castComponentValue((DataComponentType<T>)component, this.getVariant()) : super.get(component);
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter componentGetter) {
        this.applyImplicitComponentIfPresent(componentGetter, DataComponents.PAINTING_VARIANT);
        super.applyImplicitComponents(componentGetter);
    }

    @Override
    protected <T> boolean applyImplicitComponent(DataComponentType<T> component, T value) {
        if (component == DataComponents.PAINTING_VARIANT) {
            this.setVariant(castComponentValue(DataComponents.PAINTING_VARIANT, value));
            return true;
        } else {
            return super.applyImplicitComponent(component, value);
        }
    }

    public static Optional<Painting> create(Level level, BlockPos pos, Direction direction) {
        Painting painting = new Painting(level, pos);
        List<Holder<PaintingVariant>> list = new ArrayList<>();
        level.registryAccess().lookupOrThrow(Registries.PAINTING_VARIANT).getTagOrEmpty(PaintingVariantTags.PLACEABLE).forEach(list::add);
        if (list.isEmpty()) {
            return Optional.empty();
        } else {
            painting.setDirection(direction);
            list.removeIf(holder -> {
                painting.setVariant((Holder<PaintingVariant>)holder);
                return !painting.survives();
            });
            if (list.isEmpty()) {
                return Optional.empty();
            } else {
                int i = list.stream().mapToInt(Painting::variantArea).max().orElse(0);
                list.removeIf(holder -> variantArea((Holder<PaintingVariant>)holder) < i);
                Optional<Holder<PaintingVariant>> randomSafe = Util.getRandomSafe(list, painting.random);
                if (randomSafe.isEmpty()) {
                    return Optional.empty();
                } else {
                    painting.setVariant(randomSafe.get());
                    painting.setDirection(direction);
                    return Optional.of(painting);
                }
            }
        }
    }

    private static int variantArea(Holder<PaintingVariant> variant) {
        return variant.value().area();
    }

    private Painting(Level level, BlockPos pos) {
        super(EntityType.PAINTING, level, pos);
    }

    public Painting(Level level, BlockPos pos, Direction direction, Holder<PaintingVariant> variant) {
        this(level, pos);
        this.setVariant(variant);
        this.setDirection(direction);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.store("facing", Direction.LEGACY_ID_CODEC_2D, this.getDirection());
        super.addAdditionalSaveData(output);
        VariantUtils.writeVariant(output, this.getVariant());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        Direction direction = input.read("facing", Direction.LEGACY_ID_CODEC_2D).orElse(Direction.SOUTH);
        super.readAdditionalSaveData(input);
        this.setDirection(direction);
        VariantUtils.readVariant(input, Registries.PAINTING_VARIANT).ifPresent(this::setVariant);
    }

    @Override
    protected AABB calculateBoundingBox(BlockPos pos, Direction direction) {
        // CraftBukkit start
        PaintingVariant variant = (PaintingVariant) this.getVariant().value();
        return Painting.calculateBoundingBoxStatic(pos, direction, variant.width(), variant.height());
    }

    public static AABB calculateBoundingBoxStatic(BlockPos pos, Direction direction, int width, int height) {
        // CraftBukkit end
        float f = 0.46875F;
        Vec3 vec3 = Vec3.atCenterOf(pos).relative(direction, -0.46875);
        // CraftBukkit start
        double d = Painting.offsetForPaintingSize(width);
        double d1 = Painting.offsetForPaintingSize(height);
        // CraftBukkit end
        Direction counterClockWise = direction.getCounterClockWise();
        Vec3 vec31 = vec3.relative(counterClockWise, d).relative(Direction.UP, d1);
        Direction.Axis axis = direction.getAxis();
        // CraftBukkit start
        double d2 = axis == Direction.Axis.X ? 0.0625 : width;
        double d3 = height;
        double d4 = axis == Direction.Axis.Z ? 0.0625 : width;
        // CraftBukkit end
        return AABB.ofSize(vec31, d2, d3, d4);
    }

    private static double offsetForPaintingSize(int size) { // CraftBukkit - static
        return size % 2 == 0 ? 0.5 : 0.0;
    }

    @Override
    public void dropItem(ServerLevel level, @Nullable Entity entity) {
        if (level.getGameRules().get(GameRules.ENTITY_DROPS)) {
            this.playSound(SoundEvents.PAINTING_BREAK, 1.0F, 1.0F);
            if (!(entity instanceof Player player && player.hasInfiniteMaterials())) {
                this.spawnAtLocation(level, Items.PAINTING);
            }
        }
    }

    @Override
    public void playPlacementSound() {
        this.playSound(SoundEvents.PAINTING_PLACE, 1.0F, 1.0F);
    }

    @Override
    public void snapTo(double x, double y, double z, float yRot, float xRot) {
        this.setPos(x, y, z);
    }

    @Override
    public Vec3 trackingPosition() {
        return Vec3.atLowerCornerOf(this.pos);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity entity) {
        return new ClientboundAddEntityPacket(this, this.getDirection().get3DDataValue(), this.getPos());
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        this.setDirection(Direction.from3DDataValue(packet.getData()));
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(Items.PAINTING);
    }
}
