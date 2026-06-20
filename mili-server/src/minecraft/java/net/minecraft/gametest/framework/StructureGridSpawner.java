package net.minecraft.gametest.framework;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

public class StructureGridSpawner implements GameTestRunner.StructureSpawner {
    private static final int SPACE_BETWEEN_COLUMNS = 5;
    private static final int SPACE_BETWEEN_ROWS = 6;
    private final int testsPerRow;
    private int currentRowCount;
    private AABB rowBounds;
    private final BlockPos.MutableBlockPos nextTestNorthWestCorner;
    private final BlockPos firstTestNorthWestCorner;
    private final boolean clearOnBatch;
    private float maxX = -1.0F;
    private final Collection<GameTestInfo> testInLastBatch = new ArrayList<>();

    public StructureGridSpawner(BlockPos firstTestNorthWestCorner, int testsPerRow, boolean clearOnBatch) {
        this.testsPerRow = testsPerRow;
        this.nextTestNorthWestCorner = firstTestNorthWestCorner.mutable();
        this.rowBounds = new AABB(this.nextTestNorthWestCorner);
        this.firstTestNorthWestCorner = firstTestNorthWestCorner;
        this.clearOnBatch = clearOnBatch;
    }

    @Override
    public void onBatchStart(ServerLevel level) {
        if (this.clearOnBatch) {
            this.testInLastBatch.forEach(gameTestInfo -> {
                BoundingBox structureBoundingBox = gameTestInfo.getTestInstanceBlockEntity().getStructureBoundingBox();
                StructureUtils.clearSpaceForStructure(structureBoundingBox, level);
            });
            this.testInLastBatch.clear();
            this.rowBounds = new AABB(this.firstTestNorthWestCorner);
            this.nextTestNorthWestCorner.set(this.firstTestNorthWestCorner);
        }
    }

    @Override
    public Optional<GameTestInfo> spawnStructure(GameTestInfo gameTestInfo) {
        BlockPos blockPos = new BlockPos(this.nextTestNorthWestCorner);
        gameTestInfo.setTestBlockPos(blockPos);
        GameTestInfo gameTestInfo1 = gameTestInfo.prepareTestStructure();
        if (gameTestInfo1 == null) {
            return Optional.empty();
        } else {
            gameTestInfo1.startExecution(1);
            AABB structureBounds = gameTestInfo.getTestInstanceBlockEntity().getStructureBounds();
            this.rowBounds = this.rowBounds.minmax(structureBounds);
            this.nextTestNorthWestCorner.move((int)structureBounds.getXsize() + 5, 0, 0);
            if (this.nextTestNorthWestCorner.getX() > this.maxX) {
                this.maxX = this.nextTestNorthWestCorner.getX();
            }

            if (++this.currentRowCount >= this.testsPerRow) {
                this.currentRowCount = 0;
                this.nextTestNorthWestCorner.move(0, 0, (int)this.rowBounds.getZsize() + 6);
                this.nextTestNorthWestCorner.setX(this.firstTestNorthWestCorner.getX());
                this.rowBounds = new AABB(this.nextTestNorthWestCorner);
            }

            this.testInLastBatch.add(gameTestInfo);
            return Optional.of(gameTestInfo);
        }
    }
}
