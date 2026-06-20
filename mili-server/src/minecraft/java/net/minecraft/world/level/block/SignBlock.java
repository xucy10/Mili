package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.Arrays;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SignApplicator;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public abstract class SignBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final VoxelShape SHAPE = Block.column(8.0, 0.0, 16.0);
    private final WoodType type;

    protected SignBlock(WoodType type, BlockBehaviour.Properties properties) {
        super(properties);
        this.type = type;
    }

    @Override
    protected abstract MapCodec<? extends SignBlock> codec();

    @Override
    protected BlockState updateShape(
        BlockState state,
        LevelReader level,
        ScheduledTickAccess scheduledTickAccess,
        BlockPos pos,
        Direction direction,
        BlockPos neighborPos,
        BlockState neighborState,
        RandomSource random
    ) {
        if (state.getValue(WATERLOGGED)) {
            scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }

        return super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public boolean isPossibleToRespawnInThis(BlockState state) {
        return true;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SignBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(
        ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult
    ) {
        if (level.getBlockEntity(pos) instanceof SignBlockEntity signBlockEntity) {
            SignApplicator signApplicator1 = stack.getItem() instanceof SignApplicator signApplicator ? signApplicator : null;
            boolean flag = signApplicator1 != null && player.mayBuild();
            if (level instanceof ServerLevel serverLevel) {
                if (flag && !signBlockEntity.isWaxed() && !this.otherPlayerIsEditingSign(player, signBlockEntity)) {
                    boolean isFacingFrontText = signBlockEntity.isFacingFrontText(player);
                    if (signApplicator1.canApplyToSign(signBlockEntity.getText(isFacingFrontText), player)
                        && signApplicator1.tryApplyToSign(serverLevel, signBlockEntity, isFacingFrontText, player)) {
                        signBlockEntity.executeClickCommandsIfPresent(serverLevel, player, pos, isFacingFrontText);
                        player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
                        serverLevel.gameEvent(
                            GameEvent.BLOCK_CHANGE, signBlockEntity.getBlockPos(), GameEvent.Context.of(player, signBlockEntity.getBlockState())
                        );
                        stack.consume(1, player);
                        return InteractionResult.SUCCESS;
                    } else {
                        return InteractionResult.TRY_WITH_EMPTY_HAND;
                    }
                } else {
                    return InteractionResult.TRY_WITH_EMPTY_HAND;
                }
            } else {
                return !flag && !signBlockEntity.isWaxed() ? InteractionResult.CONSUME : InteractionResult.SUCCESS;
            }
        } else {
            return InteractionResult.PASS;
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof SignBlockEntity signBlockEntity) {
            if (level instanceof ServerLevel serverLevel) {
                boolean isFacingFrontText = signBlockEntity.isFacingFrontText(player);
                boolean flag = signBlockEntity.executeClickCommandsIfPresent(serverLevel, player, pos, isFacingFrontText);
                if (signBlockEntity.isWaxed()) {
                    serverLevel.playSound(null, signBlockEntity.getBlockPos(), signBlockEntity.getSignInteractionFailedSoundEvent(), SoundSource.BLOCKS);
                    return InteractionResult.SUCCESS_SERVER;
                } else if (flag) {
                    return InteractionResult.SUCCESS_SERVER;
                } else if (!this.otherPlayerIsEditingSign(player, signBlockEntity)
                    && player.mayBuild()
                    && this.hasEditableText(player, signBlockEntity, isFacingFrontText)) {
                    this.openTextEdit(player, signBlockEntity, isFacingFrontText, io.papermc.paper.event.player.PlayerOpenSignEvent.Cause.INTERACT); // Paper - Add PlayerOpenSignEvent
                    return InteractionResult.SUCCESS_SERVER;
                } else {
                    return InteractionResult.PASS;
                }
            } else {
                Util.pauseInIde(new IllegalStateException("Expected to only call this on server"));
                return InteractionResult.CONSUME;
            }
        } else {
            return InteractionResult.PASS;
        }
    }

    private boolean hasEditableText(Player player, SignBlockEntity signEntity, boolean isFrontText) {
        SignText text = signEntity.getText(isFrontText);
        return Arrays.stream(text.getMessages(player.isTextFilteringEnabled()))
            .allMatch(line -> line.equals(CommonComponents.EMPTY) || line.getContents() instanceof PlainTextContents);
    }

    public abstract float getYRotationDegrees(BlockState state);

    public Vec3 getSignHitboxCenterPosition(BlockState state) {
        return new Vec3(0.5, 0.5, 0.5);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    public WoodType type() {
        return this.type;
    }

    public static WoodType getWoodType(Block block) {
        WoodType woodType;
        if (block instanceof SignBlock) {
            woodType = ((SignBlock)block).type();
        } else {
            woodType = WoodType.OAK;
        }

        return woodType;
    }

    @Deprecated @io.papermc.paper.annotation.DoNotUse // Paper - Add PlayerOpenSignEvent
    public void openTextEdit(Player player, SignBlockEntity signEntity, boolean isFrontText) {
        // Paper start - Add PlayerOpenSignEvent
        this.openTextEdit(player, signEntity, isFrontText, io.papermc.paper.event.player.PlayerOpenSignEvent.Cause.UNKNOWN);
    }

    public void openTextEdit(Player player, SignBlockEntity signEntity, boolean isFrontText, io.papermc.paper.event.player.PlayerOpenSignEvent.Cause cause) {
        org.bukkit.entity.Player bukkitPlayer = (org.bukkit.entity.Player) player.getBukkitEntity();
        org.bukkit.block.Block bukkitBlock = org.bukkit.craftbukkit.block.CraftBlock.at(signEntity.getLevel(), signEntity.getBlockPos());
        org.bukkit.craftbukkit.block.CraftSign<?> bukkitSign = (org.bukkit.craftbukkit.block.CraftSign<?>) org.bukkit.craftbukkit.block.CraftBlockStates.getBlockState(bukkitBlock);
        io.papermc.paper.event.player.PlayerOpenSignEvent event = new io.papermc.paper.event.player.PlayerOpenSignEvent(
            bukkitPlayer,
            bukkitSign,
            isFrontText ? org.bukkit.block.sign.Side.FRONT : org.bukkit.block.sign.Side.BACK,
            cause);
        if (!event.callEvent()) return;
        if (org.bukkit.event.player.PlayerSignOpenEvent.getHandlerList().getRegisteredListeners().length > 0) {
            final org.bukkit.event.player.PlayerSignOpenEvent.Cause legacyCause = switch (cause) {
                case PLACE -> org.bukkit.event.player.PlayerSignOpenEvent.Cause.PLACE;
                case PLUGIN -> org.bukkit.event.player.PlayerSignOpenEvent.Cause.PLUGIN;
                case INTERACT -> org.bukkit.event.player.PlayerSignOpenEvent.Cause.INTERACT;
                case UNKNOWN -> org.bukkit.event.player.PlayerSignOpenEvent.Cause.UNKNOWN;
            };
        if (!org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerSignOpenEvent(player, signEntity, isFrontText, legacyCause)) {
        // Paper end - Add PlayerOpenSignEvent
            return;
        }
        } // Paper - Add PlayerOpenSignEvent
        signEntity.setAllowedPlayerEditor(player.getUUID());
        player.openTextEdit(signEntity, isFrontText);
    }

    private boolean otherPlayerIsEditingSign(Player player, SignBlockEntity signEntity) {
        UUID playerWhoMayEdit = signEntity.getPlayerWhoMayEdit();
        return playerWhoMayEdit != null && !playerWhoMayEdit.equals(player.getUUID());
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return null; // CraftBukkit - remove unnecessary sign ticking
    }
}
