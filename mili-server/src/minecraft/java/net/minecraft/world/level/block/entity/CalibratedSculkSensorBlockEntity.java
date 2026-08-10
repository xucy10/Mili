package net.minecraft.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CalibratedSculkSensorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import org.jspecify.annotations.Nullable;

public class CalibratedSculkSensorBlockEntity extends SculkSensorBlockEntity {
    public CalibratedSculkSensorBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityType.CALIBRATED_SCULK_SENSOR, pos, blockState);
    }

    @Override
    public VibrationSystem.User createVibrationUser() {
        return new CalibratedSculkSensorBlockEntity.VibrationUser(this.getBlockPos());
    }
    // Paper start - Configurable sculk sensor listener range
    @Override
    protected void saveRangeOverride(final net.minecraft.world.level.storage.ValueOutput output) {
        if (this.rangeOverride != null && this.rangeOverride != 16) output.putInt(PAPER_LISTENER_RANGE_NBT_KEY, this.rangeOverride); // only save if it's different from the default
    }
    // Paper end - Configurable sculk sensor listener range

    protected class VibrationUser extends SculkSensorBlockEntity.VibrationUser {
        public VibrationUser(final BlockPos blockPos1) {
            super(blockPos1);
        }

        @Override
        public int getListenerRadius() {
            if (CalibratedSculkSensorBlockEntity.this.rangeOverride != null) return CalibratedSculkSensorBlockEntity.this.rangeOverride; // Paper - Configurable sculk sensor listener range
            return 16;
        }

        @Override
        public boolean canReceiveVibration(ServerLevel level, BlockPos pos, Holder<GameEvent> gameEvent, GameEvent.@Nullable Context context) {
            int backSignal = this.getBackSignal(level, this.blockPos, CalibratedSculkSensorBlockEntity.this.getBlockState());
            return (backSignal == 0 || VibrationSystem.getGameEventFrequency(gameEvent) == backSignal)
                && super.canReceiveVibration(level, pos, gameEvent, context);
        }

        private int getBackSignal(Level level, BlockPos pos, BlockState state) {
            Direction opposite = state.getValue(CalibratedSculkSensorBlock.FACING).getOpposite();
            return level.getSignal(pos.relative(opposite), opposite);
        }
    }
}
