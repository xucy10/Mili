package net.minecraft.gametest.framework;

public interface GameTestBatchListener {
    void testBatchStarting(GameTestBatch batch);

    void testBatchFinished(GameTestBatch batch);
}
