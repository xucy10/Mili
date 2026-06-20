package net.minecraft.util;

import net.minecraft.network.chat.Component;

public interface ProgressListener {
    void progressStartNoAbort(Component component);

    void progressStart(Component header);

    void progressStage(Component stage);

    void progressStagePercentage(int progress);

    void stop();
}
