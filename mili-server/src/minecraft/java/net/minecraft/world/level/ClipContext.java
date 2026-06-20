package net.minecraft.world.level;

import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ClipContext {
    private final Vec3 from;
    private final Vec3 to;
    private final ClipContext.Block block;
    public final ClipContext.Fluid fluid; // Paper - optimise collisions - public
    private final CollisionContext collisionContext;

    public ClipContext(Vec3 from, Vec3 to, ClipContext.Block block, ClipContext.Fluid fluid, Entity entity) {
        this(from, to, block, fluid, (entity == null) ? CollisionContext.empty() : CollisionContext.of(entity)); // CraftBukkit
    }

    public ClipContext(Vec3 from, Vec3 to, ClipContext.Block block, ClipContext.Fluid fluid, CollisionContext collisionContext) {
        this.from = from;
        this.to = to;
        this.block = block;
        this.fluid = fluid;
        this.collisionContext = collisionContext;
    }

    public Vec3 getTo() {
        return this.to;
    }

    public Vec3 getFrom() {
        return this.from;
    }

    public VoxelShape getBlockShape(BlockState state, BlockGetter level, BlockPos pos) {
        return this.block.get(state, level, pos, this.collisionContext);
    }

    public VoxelShape getFluidShape(FluidState state, BlockGetter level, BlockPos pos) {
        return this.fluid.canPick(state) ? state.getShape(level, pos) : Shapes.empty();
    }

    public static enum Block implements ClipContext.ShapeGetter {
        COLLIDER(BlockBehaviour.BlockStateBase::getCollisionShape),
        OUTLINE(BlockBehaviour.BlockStateBase::getShape),
        VISUAL(BlockBehaviour.BlockStateBase::getVisualShape),
        FALLDAMAGE_RESETTING(
            (state, level, pos, collisionContext) -> {
                if (state.is(BlockTags.FALL_DAMAGE_RESETTING)) {
                    return Shapes.block();
                } else {
                    if (collisionContext instanceof EntityCollisionContext entityCollisionContext
                        && entityCollisionContext.getEntity() != null
                        && entityCollisionContext.getEntity().getType() == EntityType.PLAYER) {
                        if (state.is(Blocks.END_GATEWAY) || state.is(Blocks.END_PORTAL)) {
                            return Shapes.block();
                        }

                        if (level instanceof ServerLevel serverLevel
                            && state.is(Blocks.NETHER_PORTAL)
                            && serverLevel.getGameRules().get(GameRules.PLAYERS_NETHER_PORTAL_DEFAULT_DELAY) == 0) {
                            return Shapes.block();
                        }
                    }

                    return Shapes.empty();
                }
            }
        );

        private final ClipContext.ShapeGetter shapeGetter;

        private Block(final ClipContext.ShapeGetter shapeGetter) {
            this.shapeGetter = shapeGetter;
        }

        @Override
        public VoxelShape get(BlockState state, BlockGetter level, BlockPos pos, CollisionContext collisionContext) {
            return this.shapeGetter.get(state, level, pos, collisionContext);
        }
    }

    public static enum Fluid {
        NONE(fluid -> false),
        SOURCE_ONLY(FluidState::isSource),
        ANY(fluid -> !fluid.isEmpty()),
        WATER(fluid -> fluid.is(FluidTags.WATER));

        private final Predicate<FluidState> canPick;

        private Fluid(final Predicate<FluidState> canPick) {
            this.canPick = canPick;
        }

        public boolean canPick(FluidState state) {
            return this.canPick.test(state);
        }
    }

    public interface ShapeGetter {
        VoxelShape get(BlockState state, BlockGetter level, BlockPos pos, CollisionContext collisionContext);
    }
}
