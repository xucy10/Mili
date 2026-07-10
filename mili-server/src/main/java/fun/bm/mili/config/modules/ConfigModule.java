package fun.bm.mili.config.modules;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import me.earthme.luminol.config.IConfigModule;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public interface ConfigModule extends IConfigModule {
    void onLoaded(CommentedFileConfig configInstance);

    void onUnloaded(CommentedFileConfig configInstance);

    @Override
    default void onLoaded(CommentedFileConfig configInstance, @Nullable Set<Exception> e) {
        onLoaded(configInstance);
    }
}