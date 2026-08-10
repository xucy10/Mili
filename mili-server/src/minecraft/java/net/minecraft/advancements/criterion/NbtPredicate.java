package net.minecraft.advancements.criterion;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public record NbtPredicate(CompoundTag tag) {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Codec<NbtPredicate> CODEC = TagParser.LENIENT_CODEC.xmap(NbtPredicate::new, NbtPredicate::tag);
    public static final StreamCodec<ByteBuf, NbtPredicate> STREAM_CODEC = ByteBufCodecs.COMPOUND_TAG.map(NbtPredicate::new, NbtPredicate::tag);
    public static final String SELECTED_ITEM_TAG = "SelectedItem";

    public boolean matches(DataComponentGetter componentGetter) {
        CustomData customData = componentGetter.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return customData.matchedBy(this.tag);
    }

    public boolean matches(Entity entity) {
        return this.matches(getEntityTagToCompare(entity));
    }

    public boolean matches(@Nullable Tag tag) {
        return tag != null && NbtUtils.compareNbt(this.tag, tag, true);
    }

    public static CompoundTag getEntityTagToCompare(Entity entity) {
        CompoundTag var7;
        try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(entity.problemPath(), LOGGER)) {
            TagValueOutput tagValueOutput = TagValueOutput.createWithContext(scopedCollector, entity.registryAccess());
            entity.saveWithoutId(tagValueOutput);
            if (entity instanceof Player player) {
                ItemStack selectedItem = player.getInventory().getSelectedItem();
                if (!selectedItem.isEmpty()) {
                    tagValueOutput.store("SelectedItem", ItemStack.CODEC, selectedItem);
                }
            }

            var7 = tagValueOutput.buildResult();
        }

        return var7;
    }
}
