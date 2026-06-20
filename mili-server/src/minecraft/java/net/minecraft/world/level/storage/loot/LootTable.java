package net.minecraft.world.level.storage.loot;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.util.context.ContextKeySet;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.functions.FunctionUserBuilder;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctions;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import org.slf4j.Logger;

public class LootTable {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Codec<ResourceKey<LootTable>> KEY_CODEC = ResourceKey.codec(Registries.LOOT_TABLE);
    public static final ContextKeySet DEFAULT_PARAM_SET = LootContextParamSets.ALL_PARAMS;
    public static final long RANDOMIZE_SEED = 0L;
    public static final Codec<LootTable> DIRECT_CODEC = Codec.lazyInitialized(
        () -> RecordCodecBuilder.create(
            instance -> instance.group(
                    LootContextParamSets.CODEC.lenientOptionalFieldOf("type", DEFAULT_PARAM_SET).forGetter(lootTable -> lootTable.paramSet),
                    Identifier.CODEC.optionalFieldOf("random_sequence").forGetter(lootTable -> lootTable.randomSequence),
                    LootPool.CODEC.listOf().optionalFieldOf("pools", List.of()).forGetter(lootTable -> lootTable.pools),
                    LootItemFunctions.ROOT_CODEC.listOf().optionalFieldOf("functions", List.of()).forGetter(lootTable -> lootTable.functions)
                )
                .apply(instance, LootTable::new)
        )
    );
    public static final Codec<Holder<LootTable>> CODEC = RegistryFileCodec.create(Registries.LOOT_TABLE, DIRECT_CODEC);
    public static final LootTable EMPTY = new LootTable(LootContextParamSets.EMPTY, Optional.empty(), List.of(), List.of());
    private final ContextKeySet paramSet;
    private final Optional<Identifier> randomSequence;
    private final List<LootPool> pools;
    private final List<LootItemFunction> functions;
    private final BiFunction<ItemStack, LootContext, ItemStack> compositeFunction;
    public org.bukkit.craftbukkit.CraftLootTable craftLootTable; // CraftBukkit

    LootTable(ContextKeySet paramSet, Optional<Identifier> randomSequence, List<LootPool> pools, List<LootItemFunction> functions) {
        this.paramSet = paramSet;
        this.randomSequence = randomSequence;
        this.pools = pools;
        this.functions = functions;
        this.compositeFunction = LootItemFunctions.compose(functions);
    }

    public static Consumer<ItemStack> createStackSplitter(ServerLevel level, Consumer<ItemStack> output) {
        boolean skipSplitter = level != null && !level.paperConfig().fixes.splitOverstackedLoot; // Paper - preserve overstacked items
        return itemStack -> {
            if (itemStack.isItemEnabled(level.enabledFeatures())) {
                if (skipSplitter || itemStack.getCount() < itemStack.getMaxStackSize()) { // Paper - preserve overstacked items
                    output.accept(itemStack);
                } else {
                    int count = itemStack.getCount();

                    while (count > 0) {
                        ItemStack itemStack1 = itemStack.copyWithCount(Math.min(itemStack.getMaxStackSize(), count));
                        count -= itemStack1.getCount();
                        output.accept(itemStack1);
                    }
                }
            }
        };
    }

    public void getRandomItemsRaw(LootParams params, Consumer<ItemStack> output) {
        this.getRandomItemsRaw(new LootContext.Builder(params).create(this.randomSequence), output);
    }

    public void getRandomItemsRaw(LootContext context, Consumer<ItemStack> output) {
        LootContext.VisitedEntry<?> visitedEntry = LootContext.createVisitedEntry(this);
        if (context.pushVisitedElement(visitedEntry)) {
            Consumer<ItemStack> consumer = LootItemFunction.decorate(this.compositeFunction, output, context);

            for (LootPool lootPool : this.pools) {
                lootPool.addRandomItems(consumer, context);
            }

            context.popVisitedElement(visitedEntry);
        } else {
            LOGGER.warn("Detected infinite loop in loot tables");
        }
    }

    public void getRandomItems(LootParams params, long seed, Consumer<ItemStack> output) {
        this.getRandomItemsRaw(
            new LootContext.Builder(params).withOptionalRandomSeed(seed).create(this.randomSequence), createStackSplitter(params.getLevel(), output)
        );
    }

    public void getRandomItems(LootParams params, Consumer<ItemStack> output) {
        this.getRandomItemsRaw(params, createStackSplitter(params.getLevel(), output));
    }

    public void getRandomItems(LootContext contextData, Consumer<ItemStack> output) {
        this.getRandomItemsRaw(contextData, createStackSplitter(contextData.getLevel(), output));
    }

    public ObjectArrayList<ItemStack> getRandomItems(LootParams params, RandomSource random) {
        return this.getRandomItems(new LootContext.Builder(params).withOptionalRandomSource(random).create(this.randomSequence));
    }

    public ObjectArrayList<ItemStack> getRandomItems(LootParams params, long seed) {
        return this.getRandomItems(new LootContext.Builder(params).withOptionalRandomSeed(seed).create(this.randomSequence));
    }

    public ObjectArrayList<ItemStack> getRandomItems(LootParams params) {
        return this.getRandomItems(new LootContext.Builder(params).create(this.randomSequence));
    }

    private ObjectArrayList<ItemStack> getRandomItems(LootContext context) {
        ObjectArrayList<ItemStack> list = new ObjectArrayList<>();
        this.getRandomItems(context, list::add);
        return list;
    }

    public ContextKeySet getParamSet() {
        return this.paramSet;
    }

    public void validate(ValidationContext validator) {
        for (int i = 0; i < this.pools.size(); i++) {
            this.pools.get(i).validate(validator.forChild(new ProblemReporter.IndexedFieldPathElement("pools", i)));
        }

        for (int i = 0; i < this.functions.size(); i++) {
            this.functions.get(i).validate(validator.forChild(new ProblemReporter.IndexedFieldPathElement("functions", i)));
        }
    }

    public void fill(Container container, LootParams params, long seed) {
        // CraftBukkit start
        this.fill(container, params, seed == 0L ? null : RandomSource.create(seed), false);
    }

    public void fill(Container container, LootParams params, RandomSource randomSource, boolean plugin) {
        // CraftBukkit end
        LootContext lootContext = new LootContext.Builder(params).withOptionalRandomSource(randomSource).create(this.randomSequence);
        ObjectArrayList<ItemStack> randomItems = this.getRandomItems(lootContext);
        RandomSource random = lootContext.getRandom();
        // CraftBukkit start
        org.bukkit.event.world.LootGenerateEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callLootGenerateEvent(container, this, lootContext, randomItems, plugin);
        if (event.isCancelled()) {
            return;
        }
        randomItems = event.getLoot().stream().map(org.bukkit.craftbukkit.inventory.CraftItemStack::asNMSCopy).collect(ObjectArrayList.toList());
        // CraftBukkit end
        List<Integer> availableSlots = this.getAvailableSlots(container, random);
        this.shuffleAndSplitItems(randomItems, availableSlots.size(), random);

        for (ItemStack itemStack : randomItems) {
            if (availableSlots.isEmpty()) {
                LOGGER.warn("Tried to over-fill a container");
                return;
            }

            if (itemStack.isEmpty()) {
                container.setItem(availableSlots.remove(availableSlots.size() - 1), ItemStack.EMPTY);
            } else {
                container.setItem(availableSlots.remove(availableSlots.size() - 1), itemStack);
            }
        }
    }

    private void shuffleAndSplitItems(ObjectArrayList<ItemStack> stacks, int emptySlotsCount, RandomSource random) {
        List<ItemStack> list = Lists.newArrayList();
        Iterator<ItemStack> iterator = stacks.iterator();

        while (iterator.hasNext()) {
            ItemStack itemStack = iterator.next();
            if (itemStack.isEmpty()) {
                iterator.remove();
            } else if (itemStack.getCount() > 1) {
                list.add(itemStack);
                iterator.remove();
            }
        }

        while (emptySlotsCount - stacks.size() - list.size() > 0 && !list.isEmpty()) {
            ItemStack itemStack1 = list.remove(Mth.nextInt(random, 0, list.size() - 1));
            int randomInt = Mth.nextInt(random, 1, itemStack1.getCount() / 2);
            ItemStack itemStack2 = itemStack1.split(randomInt);
            if (itemStack1.getCount() > 1 && random.nextBoolean()) {
                list.add(itemStack1);
            } else {
                stacks.add(itemStack1);
            }

            if (itemStack2.getCount() > 1 && random.nextBoolean()) {
                list.add(itemStack2);
            } else {
                stacks.add(itemStack2);
            }
        }

        stacks.addAll(list);
        Util.shuffle(stacks, random);
    }

    private List<Integer> getAvailableSlots(Container inventory, RandomSource random) {
        ObjectArrayList<Integer> list = new ObjectArrayList<>();

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).isEmpty()) {
                list.add(i);
            }
        }

        Util.shuffle(list, random);
        return list;
    }

    public static LootTable.Builder lootTable() {
        return new LootTable.Builder();
    }

    public static class Builder implements FunctionUserBuilder<LootTable.Builder> {
        private final ImmutableList.Builder<LootPool> pools = ImmutableList.builder();
        private final ImmutableList.Builder<LootItemFunction> functions = ImmutableList.builder();
        private ContextKeySet paramSet = LootTable.DEFAULT_PARAM_SET;
        private Optional<Identifier> randomSequence = Optional.empty();

        public LootTable.Builder withPool(LootPool.Builder lootPool) {
            this.pools.add(lootPool.build());
            return this;
        }

        public LootTable.Builder setParamSet(ContextKeySet paramSet) {
            this.paramSet = paramSet;
            return this;
        }

        public LootTable.Builder setRandomSequence(Identifier randomSequence) {
            this.randomSequence = Optional.of(randomSequence);
            return this;
        }

        @Override
        public LootTable.Builder apply(LootItemFunction.Builder functionBuilder) {
            this.functions.add(functionBuilder.build());
            return this;
        }

        @Override
        public LootTable.Builder unwrap() {
            return this;
        }

        public LootTable build() {
            return new LootTable(this.paramSet, this.randomSequence, this.pools.build(), this.functions.build());
        }
    }
}
