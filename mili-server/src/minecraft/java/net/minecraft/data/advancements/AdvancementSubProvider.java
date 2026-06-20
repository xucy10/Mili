package net.minecraft.data.advancements;

import java.util.function.Consumer;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;

public interface AdvancementSubProvider {
    void generate(HolderLookup.Provider registries, Consumer<AdvancementHolder> writer);

    static AdvancementHolder createPlaceholder(String location) {
        return Advancement.Builder.advancement().build(Identifier.parse(location));
    }
}
