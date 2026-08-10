package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

public class NoteBlock extends Block {
    public static final MapCodec<NoteBlock> CODEC = simpleCodec(NoteBlock::new);
    public static final EnumProperty<NoteBlockInstrument> INSTRUMENT = BlockStateProperties.NOTEBLOCK_INSTRUMENT;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final IntegerProperty NOTE = BlockStateProperties.NOTE;
    public static final int NOTE_VOLUME = 3;

    @Override
    public MapCodec<NoteBlock> codec() {
        return CODEC;
    }

    public NoteBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(INSTRUMENT, NoteBlockInstrument.HARP).setValue(NOTE, 0).setValue(POWERED, false));
    }

    private BlockState setInstrument(LevelReader level, BlockPos pos, BlockState state) {
        NoteBlockInstrument noteBlockInstrument = level.getBlockState(pos.above()).instrument();
        if (noteBlockInstrument.worksAboveNoteBlock()) {
            return state.setValue(INSTRUMENT, noteBlockInstrument);
        } else {
            NoteBlockInstrument noteBlockInstrument1 = level.getBlockState(pos.below()).instrument();
            NoteBlockInstrument noteBlockInstrument2 = noteBlockInstrument1.worksAboveNoteBlock() ? NoteBlockInstrument.HARP : noteBlockInstrument1;
            return state.setValue(INSTRUMENT, noteBlockInstrument2);
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (io.papermc.paper.configuration.GlobalConfiguration.get().blockUpdates.disableNoteblockUpdates) return this.defaultBlockState(); // Paper - place without considering instrument
        return this.setInstrument(context.getLevel(), context.getClickedPos(), this.defaultBlockState());
    }

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
        if (io.papermc.paper.configuration.GlobalConfiguration.get().blockUpdates.disableNoteblockUpdates) return state; // Paper - prevent noteblock instrument from updating
        boolean flag = direction.getAxis() == Direction.Axis.Y;
        return flag
            ? this.setInstrument(level, pos, state)
            : super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston) {
        if (io.papermc.paper.configuration.GlobalConfiguration.get().blockUpdates.disableNoteblockUpdates) return; // Paper - prevent noteblock powered-state from updating
        boolean hasNeighborSignal = level.hasNeighborSignal(pos);
        if (hasNeighborSignal != state.getValue(POWERED)) {
            if (hasNeighborSignal) {
                this.playNote(null, state, level, pos);
                state = level.getBlockState(pos); // CraftBukkit - SPIGOT-5617: update in case changed in event
            }

            level.setBlock(pos, state.setValue(POWERED, hasNeighborSignal), Block.UPDATE_ALL);
        }
    }

    private void playNote(@Nullable Entity entity, BlockState state, Level level, BlockPos pos) {
        if (state.getValue(INSTRUMENT).worksAboveNoteBlock() || level.getBlockState(pos.above()).isAir()) {
            level.blockEvent(pos, this, 0, 0);
            level.gameEvent(entity, GameEvent.NOTE_BLOCK_PLAY, pos);
        }
    }

    @Override
    protected InteractionResult useItemOn(
        ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult
    ) {
        return (InteractionResult)(stack.is(ItemTags.NOTE_BLOCK_TOP_INSTRUMENTS) && hitResult.getDirection() == Direction.UP
            ? InteractionResult.PASS
            : super.useItemOn(stack, state, level, pos, player, hand, hitResult));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            if (!io.papermc.paper.configuration.GlobalConfiguration.get().blockUpdates.disableNoteblockUpdates) state = state.cycle(NoteBlock.NOTE); // Paper - prevent noteblock note from updating
            level.setBlock(pos, state, Block.UPDATE_ALL);
            this.playNote(player, state, level, pos);
            player.awardStat(Stats.TUNE_NOTEBLOCK);
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide()) {
            this.playNote(player, state, level, pos);
            player.awardStat(Stats.PLAY_NOTEBLOCK);
        }
    }

    public static float getPitchFromNote(int note) {
        return (float)Math.pow(2.0, (note - 12) / 12.0);
    }

    @Override
    protected boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int param) {
        NoteBlockInstrument noteBlockInstrument = state.getValue(INSTRUMENT);
        // Paper start - move NotePlayEvent call to fix instrument/note changes
        org.bukkit.event.block.NotePlayEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callNotePlayEvent(level, pos, noteBlockInstrument, state.getValue(NOTE));
        if (event.isCancelled()) return false;
        // Paper end - move NotePlayEvent call to fix instrument/note changes
        float pitchFromNote;
        if (noteBlockInstrument.isTunable()) {
            int noteValue = event.getNote().getId(); // Paper - move NotePlayEvent call to fix instrument/note changes
            pitchFromNote = getPitchFromNote(noteValue);
            level.addParticle(ParticleTypes.NOTE, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, noteValue / 24.0, 0.0, 0.0);
        } else {
            pitchFromNote = 1.0F;
        }

        Holder<SoundEvent> holder;
        if (noteBlockInstrument.hasCustomSound()) {
            Identifier customSoundId = this.getCustomSoundId(level, pos);
            if (customSoundId == null) {
                return false;
            }

            holder = Holder.direct(SoundEvent.createVariableRangeEvent(customSoundId));
        } else {
            holder = org.bukkit.craftbukkit.block.data.CraftBlockData.toNMS(event.getInstrument(), NoteBlockInstrument.class).getSoundEvent(); // Paper - move NotePlayEvent call to fix instrument/note changes
        }

        level.playSeededSound(
            null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, holder, SoundSource.RECORDS, 3.0F, pitchFromNote, level.random.nextLong()
        );
        return true;
    }

    private @Nullable Identifier getCustomSoundId(Level level, BlockPos pos) {
        return level.getBlockEntity(pos.above()) instanceof SkullBlockEntity skullBlockEntity ? skullBlockEntity.getNoteBlockSound() : null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(INSTRUMENT, POWERED, NOTE);
    }
}
