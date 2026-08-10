package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class EndGatewayBlock extends BaseEntityBlock implements Portal {
    public static final MapCodec<EndGatewayBlock> CODEC = simpleCodec(EndGatewayBlock::new);

    @Override
    public MapCodec<EndGatewayBlock> codec() {
        return CODEC;
    }

    protected EndGatewayBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TheEndGatewayBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return createTickerHelper(
            blockEntityType,
            BlockEntityType.END_GATEWAY,
            level.isClientSide() ? TheEndGatewayBlockEntity::beamAnimationTick : TheEndGatewayBlockEntity::portalTick
        );
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof TheEndGatewayBlockEntity) {
            int particleAmount = ((TheEndGatewayBlockEntity)blockEntity).getParticleAmount();

            for (int i = 0; i < particleAmount; i++) {
                double d = pos.getX() + random.nextDouble();
                double d1 = pos.getY() + random.nextDouble();
                double d2 = pos.getZ() + random.nextDouble();
                double d3 = (random.nextDouble() - 0.5) * 0.5;
                double d4 = (random.nextDouble() - 0.5) * 0.5;
                double d5 = (random.nextDouble() - 0.5) * 0.5;
                int i1 = random.nextInt(2) * 2 - 1;
                if (random.nextBoolean()) {
                    d2 = pos.getZ() + 0.5 + 0.25 * i1;
                    d5 = random.nextFloat() * 2.0F * i1;
                } else {
                    d = pos.getX() + 0.5 + 0.25 * i1;
                    d3 = random.nextFloat() * 2.0F * i1;
                }

                level.addParticle(ParticleTypes.PORTAL, d, d1, d2, d3, d4, d5);
            }
        }
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return ItemStack.EMPTY;
    }

    @Override
    protected boolean canBeReplaced(BlockState state, Fluid fluid) {
        return false;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean pastEdges) {
        if (!new io.papermc.paper.event.entity.EntityInsideBlockEvent(entity.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(level, pos)).callEvent()) { return; } // Paper - Add EntityInsideBlockEvent
        if (entity.canUsePortal(false)
            && !level.isClientSide()
            && level.getBlockEntity(pos) instanceof TheEndGatewayBlockEntity theEndGatewayBlockEntity
            && !theEndGatewayBlockEntity.isCoolingDown()) {
            // Paper start - call EntityPortalEnterEvent
            org.bukkit.event.entity.EntityPortalEnterEvent event = new org.bukkit.event.entity.EntityPortalEnterEvent(entity.getBukkitEntity(), org.bukkit.craftbukkit.util.CraftLocation.toBukkit(pos, level), org.bukkit.PortalType.END_GATEWAY); // Paper - add portal type
            if (!event.callEvent()) return;
            // Paper end - call EntityPortalEnterEvent
            entity.setAsInsidePortal(this, pos);
            TheEndGatewayBlockEntity.triggerCooldown(level, pos, state, theEndGatewayBlockEntity);
        }
    }

    @Override
    public @Nullable TeleportTransition getPortalDestination(ServerLevel level, Entity entity, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof TheEndGatewayBlockEntity theEndGatewayBlockEntity) {
            Vec3 portalPosition = theEndGatewayBlockEntity.getPortalPosition(level, pos);

            // Mili start - force void trade
            if (fun.bm.mili.config.modules.function.VillagerTradeConfig.forceVoidTrade && portalPosition != null && entity instanceof net.minecraft.server.level.ServerPlayer player) {
                if (player.containerMenu instanceof net.minecraft.world.inventory.MerchantMenu merchantMenu) {
                    if (merchantMenu.trader instanceof net.minecraft.world.entity.npc.villager.AbstractVillager villager) {
                        villager.setVoidTrade();
                    }
                }
            }
            // Mili end - force void trade

            if (portalPosition == null) {
                return null;
            } else {
                return getTeleportTransition(level, entity, portalPosition); // Folia - region threading
            }
        } else {
            return null;
        }
    }

    // Folia start - region threading
    public static TeleportTransition getTeleportTransition(ServerLevel level, Entity entity, Vec3 portalPosition) {
                return entity instanceof ThrownEnderpearl
                        ? new TeleportTransition(level, portalPosition, Vec3.ZERO, 0.0F, 0.0F, Set.of(), TeleportTransition.PLACE_PORTAL_TICKET, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.END_GATEWAY) // CraftBukkit
                        : new TeleportTransition(
                        level, portalPosition, Vec3.ZERO, 0.0F, 0.0F, Relative.union(Relative.DELTA, Relative.ROTATION), TeleportTransition.PLACE_PORTAL_TICKET, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.END_GATEWAY // CraftBukkit
                );
    }

    @Override
    public boolean portalAsync(ServerLevel sourceWorld, Entity portalTarget, BlockPos portalPos) {
        if (!ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(portalTarget)) {
            return false;
        }
        if (!ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(sourceWorld, portalPos)) {
            return false;
        }

        BlockEntity tile = sourceWorld.getBlockEntity(portalPos);

        if (!(tile instanceof TheEndGatewayBlockEntity endGateway)) {
            return false;
        }

        return TheEndGatewayBlockEntity.teleportRegionThreading(
            sourceWorld, portalPos, portalTarget, endGateway, TeleportTransition.PLACE_PORTAL_TICKET
        );
    }
    // Folia end - region threading

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }
}
