package fun.bm.mili.config.modules;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;

public interface ConfigModule {
    void onLoaded(CommentedFileConfig configInstance);
    void onUnloaded(CommentedFileConfig configInstance);
}
