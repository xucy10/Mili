package net.minecraft.gametest.framework;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TestBlock;
import net.minecraft.world.level.block.entity.TestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.TestBlockMode;

public class BlockBasedTestInstance extends GameTestInstance {
    public static final MapCodec<BlockBasedTestInstance> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(TestData.CODEC.forGetter(GameTestInstance::info)).apply(instance, BlockBasedTestInstance::new)
    );

    public BlockBasedTestInstance(TestData<Holder<TestEnvironmentDefinition>> info) {
        super(info);
    }

    @Override
    public void run(GameTestHelper helper) {
        BlockPos blockPos = this.findStartBlock(helper);
        TestBlockEntity testBlockEntity = helper.getBlockEntity(blockPos, TestBlockEntity.class);
        testBlockEntity.trigger();
        helper.onEachTick(() -> {
            List<BlockPos> list = this.findTestBlocks(helper, TestBlockMode.ACCEPT);
            if (list.isEmpty()) {
                helper.fail(Component.translatable("test_block.error.missing", TestBlockMode.ACCEPT.getDisplayName()));
            }

            boolean flag = list.stream().map(pos -> helper.getBlockEntity(pos, TestBlockEntity.class)).anyMatch(TestBlockEntity::hasTriggered);
            if (flag) {
                helper.succeed();
            } else {
                this.forAllTriggeredTestBlocks(helper, TestBlockMode.FAIL, blockEntity -> helper.fail(Component.literal(blockEntity.getMessage())));
                this.forAllTriggeredTestBlocks(helper, TestBlockMode.LOG, TestBlockEntity::trigger);
            }
        });
    }

    private void forAllTriggeredTestBlocks(GameTestHelper helper, TestBlockMode mode, Consumer<TestBlockEntity> onTrigger) {
        for (BlockPos blockPos : this.findTestBlocks(helper, mode)) {
            TestBlockEntity testBlockEntity = helper.getBlockEntity(blockPos, TestBlockEntity.class);
            if (testBlockEntity.hasTriggered()) {
                onTrigger.accept(testBlockEntity);
                testBlockEntity.reset();
            }
        }
    }

    private BlockPos findStartBlock(GameTestHelper helper) {
        List<BlockPos> list = this.findTestBlocks(helper, TestBlockMode.START);
        if (list.isEmpty()) {
            helper.fail(Component.translatable("test_block.error.missing", TestBlockMode.START.getDisplayName()));
        }

        if (list.size() != 1) {
            helper.fail(Component.translatable("test_block.error.too_many", TestBlockMode.START.getDisplayName()));
        }

        return list.getFirst();
    }

    private List<BlockPos> findTestBlocks(GameTestHelper helper, TestBlockMode mode) {
        List<BlockPos> list = new ArrayList<>();
        helper.forEveryBlockInStructure(pos -> {
            BlockState blockState = helper.getBlockState(pos);
            if (blockState.is(Blocks.TEST_BLOCK) && blockState.getValue(TestBlock.MODE) == mode) {
                list.add(pos.immutable());
            }
        });
        return list;
    }

    @Override
    public MapCodec<BlockBasedTestInstance> codec() {
        return CODEC;
    }

    @Override
    protected MutableComponent typeDescription() {
        return Component.translatable("test_instance.type.block_based");
    }
}
