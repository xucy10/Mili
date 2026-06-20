package net.minecraft.world.item;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.criterion.BlockPredicate;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class AdventureModePredicate {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Codec<AdventureModePredicate> CODEC = ExtraCodecs.compactListCodec(
            BlockPredicate.CODEC, ExtraCodecs.nonEmptyList(BlockPredicate.CODEC.listOf())
        )
        .xmap(AdventureModePredicate::new, adventureModePredicate -> adventureModePredicate.predicates);
    public static final StreamCodec<RegistryFriendlyByteBuf, AdventureModePredicate> STREAM_CODEC = StreamCodec.composite(
        BlockPredicate.STREAM_CODEC.apply(ByteBufCodecs.list()), adventureModePredicate -> adventureModePredicate.predicates, AdventureModePredicate::new
    );
    public static final Component CAN_BREAK_HEADER = Component.translatable("item.canBreak").withStyle(ChatFormatting.GRAY);
    public static final Component CAN_PLACE_HEADER = Component.translatable("item.canPlace").withStyle(ChatFormatting.GRAY);
    private static final Component UNKNOWN_USE = Component.translatable("item.canUse.unknown").withStyle(ChatFormatting.GRAY);
    public final List<BlockPredicate> predicates;
    private @Nullable List<Component> cachedTooltip;
    private @Nullable BlockInWorld lastCheckedBlock;
    private boolean lastResult;
    private boolean checksBlockEntity;

    public AdventureModePredicate(List<BlockPredicate> predicates) {
        this.predicates = predicates;
    }

    private static boolean areSameBlocks(BlockInWorld first, @Nullable BlockInWorld second, boolean checkNbt) {
        if (second == null || first.getState() != second.getState()) {
            return false;
        } else if (!checkNbt) {
            return true;
        } else if (first.getEntity() == null && second.getEntity() == null) {
            return true;
        } else if (first.getEntity() != null && second.getEntity() != null) {
            boolean var7;
            try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(LOGGER)) {
                RegistryAccess registryAccess = first.getLevel().registryAccess();
                CompoundTag compoundTag = saveBlockEntity(first.getEntity(), registryAccess, scopedCollector);
                CompoundTag compoundTag1 = saveBlockEntity(second.getEntity(), registryAccess, scopedCollector);
                var7 = Objects.equals(compoundTag, compoundTag1);
            }

            return var7;
        } else {
            return false;
        }
    }

    private static CompoundTag saveBlockEntity(BlockEntity blockEntity, RegistryAccess registryAccess, ProblemReporter problemReporter) {
        TagValueOutput tagValueOutput = TagValueOutput.createWithContext(problemReporter.forChild(blockEntity.problemPath()), registryAccess);
        blockEntity.saveWithId(tagValueOutput);
        return tagValueOutput.buildResult();
    }

    public boolean test(BlockInWorld block) {
        if (areSameBlocks(block, this.lastCheckedBlock, this.checksBlockEntity)) {
            return this.lastResult;
        } else {
            this.lastCheckedBlock = block;
            this.checksBlockEntity = false;

            for (BlockPredicate blockPredicate : this.predicates) {
                if (blockPredicate.matches(block)) {
                    this.checksBlockEntity = this.checksBlockEntity | blockPredicate.requiresNbt();
                    this.lastResult = true;
                    return true;
                }
            }

            this.lastResult = false;
            return false;
        }
    }

    private List<Component> tooltip() {
        if (this.cachedTooltip == null) {
            this.cachedTooltip = computeTooltip(this.predicates);
        }

        return this.cachedTooltip;
    }

    public void addToTooltip(Consumer<Component> tooltipAdder) {
        this.tooltip().forEach(tooltipAdder);
    }

    private static List<Component> computeTooltip(List<BlockPredicate> predicates) {
        for (BlockPredicate blockPredicate : predicates) {
            if (blockPredicate.blocks().isEmpty()) {
                return List.of(UNKNOWN_USE);
            }
        }

        return predicates.stream()
            .flatMap(predicate -> predicate.blocks().orElseThrow().stream())
            .distinct()
            .<Component>map(holder -> holder.value().getName().withStyle(ChatFormatting.DARK_GRAY))
            .toList();
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof AdventureModePredicate adventureModePredicate && this.predicates.equals(adventureModePredicate.predicates);
    }

    @Override
    public int hashCode() {
        return this.predicates.hashCode();
    }

    @Override
    public String toString() {
        return "AdventureModePredicate{predicates=" + this.predicates + "}";
    }
}
