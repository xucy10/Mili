package net.minecraft.server.dedicated;

import java.nio.file.Path;
import java.util.function.UnaryOperator;

public class DedicatedServerSettings {
    private final Path source;
    private DedicatedServerProperties properties;

    // CraftBukkit start
    public DedicatedServerSettings(joptsimple.OptionSet optionset) {
        this.source = ((java.io.File) optionset.valueOf("config")).toPath();
        this.properties = DedicatedServerProperties.fromFile(this.source, optionset);
        // CraftBukkit end
    }

    public DedicatedServerProperties getProperties() {
        return this.properties;
    }

    public void forceSave() {
        this.properties.store(this.source);
    }

    public DedicatedServerSettings update(UnaryOperator<DedicatedServerProperties> propertiesOperator) {
        (this.properties = propertiesOperator.apply(this.properties)).store(this.source);
        return this;
    }
}
