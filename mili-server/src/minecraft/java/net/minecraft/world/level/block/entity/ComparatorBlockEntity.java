package net.minecraft.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class ComparatorBlockEntity extends BlockEntity {
    private static final int DEFAULT_OUTPUT = 0;
    private int output = 0;

    public ComparatorBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityType.COMPARATOR, pos, blockState);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("OutputSignal", this.output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.output = input.getIntOr("OutputSignal", 0);
    }

    public int getOutputSignal() {
        return this.output;
    }

    public void setOutputSignal(int output) {
        this.output = output;
    }

    // Leaves start - pca
    @Override
    public void setChanged() {
        super.setChanged();
        if (fun.bm.mili.config.modules.function.protocol.PcaSyncProtocolConfig.enable) {
            org.leavesmc.leaves.protocol.PcaSyncProtocol.syncBlockEntityToClient(this);
        }
    }
    // Leaves end - pca
}
