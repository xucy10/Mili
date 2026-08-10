package net.minecraft.commands.arguments.item;

import com.mojang.brigadier.ImmutableStringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.Dynamic;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.advancements.criterion.MinMaxBounds;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.predicates.DataComponentPredicate;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Unit;
import net.minecraft.util.Util;
import net.minecraft.util.parsing.packrat.commands.ParserBasedArgument;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class ItemPredicateArgument extends ParserBasedArgument<ItemPredicateArgument.Result> {
    private static final Collection<String> EXAMPLES = Arrays.asList("stick", "minecraft:stick", "#stick", "#stick{foo:'bar'}");
    static final DynamicCommandExceptionType ERROR_UNKNOWN_ITEM = new DynamicCommandExceptionType(
        item -> Component.translatableEscape("argument.item.id.invalid", item)
    );
    static final DynamicCommandExceptionType ERROR_UNKNOWN_TAG = new DynamicCommandExceptionType(
        tag -> Component.translatableEscape("arguments.item.tag.unknown", tag)
    );
    static final DynamicCommandExceptionType ERROR_UNKNOWN_COMPONENT = new DynamicCommandExceptionType(
        component -> Component.translatableEscape("arguments.item.component.unknown", component)
    );
    static final Dynamic2CommandExceptionType ERROR_MALFORMED_COMPONENT = new Dynamic2CommandExceptionType(
        (component, value) -> Component.translatableEscape("arguments.item.component.malformed", component, value)
    );
    static final DynamicCommandExceptionType ERROR_UNKNOWN_PREDICATE = new DynamicCommandExceptionType(
        predicate -> Component.translatableEscape("arguments.item.predicate.unknown", predicate)
    );
    static final Dynamic2CommandExceptionType ERROR_MALFORMED_PREDICATE = new Dynamic2CommandExceptionType(
        (predicate, value) -> Component.translatableEscape("arguments.item.predicate.malformed", predicate, value)
    );
    private static final Identifier COUNT_ID = Identifier.withDefaultNamespace("count");
    static final Map<Identifier, ItemPredicateArgument.ComponentWrapper> PSEUDO_COMPONENTS = Stream.of(
            new ItemPredicateArgument.ComponentWrapper(
                COUNT_ID, itemStack -> true, MinMaxBounds.Ints.CODEC.map(ints -> itemStack -> ints.matches(itemStack.getCount()))
            )
        )
        .collect(
            Collectors.toUnmodifiableMap(
                ItemPredicateArgument.ComponentWrapper::id, componentWrapper -> (ItemPredicateArgument.ComponentWrapper)componentWrapper
            )
        );
    static final Map<Identifier, ItemPredicateArgument.PredicateWrapper> PSEUDO_PREDICATES = Stream.of(
            new ItemPredicateArgument.PredicateWrapper(COUNT_ID, MinMaxBounds.Ints.CODEC.map(ints -> itemStack -> ints.matches(itemStack.getCount())))
        )
        .collect(
            Collectors.toUnmodifiableMap(
                ItemPredicateArgument.PredicateWrapper::id, predicateWrapper -> (ItemPredicateArgument.PredicateWrapper)predicateWrapper
            )
        );

    private static ItemPredicateArgument.PredicateWrapper createComponentExistencePredicate(Holder.Reference<DataComponentType<?>> reference) {
        Predicate<ItemStack> predicate = itemStack -> itemStack.has(reference.value());
        return new ItemPredicateArgument.PredicateWrapper(reference.key().identifier(), Unit.CODEC.map(unit -> predicate));
    }

    public ItemPredicateArgument(CommandBuildContext context) {
        super(ComponentPredicateParser.createGrammar(new ItemPredicateArgument.Context(context)).mapResult(list -> Util.allOf(list)::test));
    }

    public static ItemPredicateArgument itemPredicate(CommandBuildContext context) {
        return new ItemPredicateArgument(context);
    }

    public static ItemPredicateArgument.Result getItemPredicate(CommandContext<CommandSourceStack> context, String name) {
        return context.getArgument(name, ItemPredicateArgument.Result.class);
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    record ComponentWrapper(Identifier id, Predicate<ItemStack> presenceChecker, Decoder<? extends Predicate<ItemStack>> valueChecker) {
        public static <T> ItemPredicateArgument.ComponentWrapper create(ImmutableStringReader reader, Identifier id, DataComponentType<T> component) throws CommandSyntaxException {
            Codec<T> codec = component.codec();
            if (codec == null) {
                throw ItemPredicateArgument.ERROR_UNKNOWN_COMPONENT.createWithContext(reader, id);
            } else {
                return new ItemPredicateArgument.ComponentWrapper(id, itemStack -> itemStack.has(component), codec.map(object -> itemStack -> {
                    T object1 = itemStack.get(component);
                    return Objects.equals(object, object1);
                }));
            }
        }

        public Predicate<ItemStack> decode(ImmutableStringReader reader, Dynamic<?> data) throws CommandSyntaxException {
            DataResult<? extends Predicate<ItemStack>> dataResult = this.valueChecker.parse(data);
            return (Predicate<ItemStack>)dataResult.getOrThrow(
                string -> ItemPredicateArgument.ERROR_MALFORMED_COMPONENT.createWithContext(reader, this.id.toString(), string)
            );
        }
    }

    static class Context
        implements ComponentPredicateParser.Context<Predicate<ItemStack>, ItemPredicateArgument.ComponentWrapper, ItemPredicateArgument.PredicateWrapper> {
        private final HolderLookup.Provider registries;
        private final HolderLookup.RegistryLookup<Item> items;
        private final HolderLookup.RegistryLookup<DataComponentType<?>> components;
        private final HolderLookup.RegistryLookup<DataComponentPredicate.Type<?>> predicates;

        Context(HolderLookup.Provider registries) {
            this.registries = registries;
            this.items = registries.lookupOrThrow(Registries.ITEM);
            this.components = registries.lookupOrThrow(Registries.DATA_COMPONENT_TYPE);
            this.predicates = registries.lookupOrThrow(Registries.DATA_COMPONENT_PREDICATE_TYPE);
        }

        @Override
        public Predicate<ItemStack> forElementType(ImmutableStringReader reader, Identifier elementType) throws CommandSyntaxException {
            Holder.Reference<Item> reference = this.items
                .get(ResourceKey.create(Registries.ITEM, elementType))
                .orElseThrow(() -> ItemPredicateArgument.ERROR_UNKNOWN_ITEM.createWithContext(reader, elementType));
            return itemStack -> itemStack.is(reference);
        }

        @Override
        public Predicate<ItemStack> forTagType(ImmutableStringReader reader, Identifier tagType) throws CommandSyntaxException {
            HolderSet<Item> holderSet = this.items
                .get(TagKey.create(Registries.ITEM, tagType))
                .orElseThrow(() -> ItemPredicateArgument.ERROR_UNKNOWN_TAG.createWithContext(reader, tagType));
            return itemStack -> itemStack.is(holderSet);
        }

        @Override
        public ItemPredicateArgument.ComponentWrapper lookupComponentType(ImmutableStringReader reader, Identifier componentType) throws CommandSyntaxException {
            ItemPredicateArgument.ComponentWrapper componentWrapper = ItemPredicateArgument.PSEUDO_COMPONENTS.get(componentType);
            if (componentWrapper != null) {
                return componentWrapper;
            } else {
                DataComponentType<?> dataComponentType = this.components
                    .get(ResourceKey.create(Registries.DATA_COMPONENT_TYPE, componentType))
                    .map(Holder::value)
                    .orElseThrow(() -> ItemPredicateArgument.ERROR_UNKNOWN_COMPONENT.createWithContext(reader, componentType));
                return ItemPredicateArgument.ComponentWrapper.create(reader, componentType, dataComponentType);
            }
        }

        @Override
        public Predicate<ItemStack> createComponentTest(ImmutableStringReader reader, ItemPredicateArgument.ComponentWrapper context, Dynamic<?> data) throws CommandSyntaxException {
            return context.decode(reader, RegistryOps.injectRegistryContext(data, this.registries));
        }

        @Override
        public Predicate<ItemStack> createComponentTest(ImmutableStringReader reader, ItemPredicateArgument.ComponentWrapper context) {
            return context.presenceChecker;
        }

        @Override
        public ItemPredicateArgument.PredicateWrapper lookupPredicateType(ImmutableStringReader reader, Identifier predicateType) throws CommandSyntaxException {
            ItemPredicateArgument.PredicateWrapper predicateWrapper = ItemPredicateArgument.PSEUDO_PREDICATES.get(predicateType);
            return predicateWrapper != null
                ? predicateWrapper
                : this.predicates
                    .get(ResourceKey.create(Registries.DATA_COMPONENT_PREDICATE_TYPE, predicateType))
                    .map(ItemPredicateArgument.PredicateWrapper::new)
                    .or(
                        () -> this.components
                            .get(ResourceKey.create(Registries.DATA_COMPONENT_TYPE, predicateType))
                            .map(ItemPredicateArgument::createComponentExistencePredicate)
                    )
                    .orElseThrow(() -> ItemPredicateArgument.ERROR_UNKNOWN_PREDICATE.createWithContext(reader, predicateType));
        }

        @Override
        public Predicate<ItemStack> createPredicateTest(ImmutableStringReader reader, ItemPredicateArgument.PredicateWrapper predicateType, Dynamic<?> data) throws CommandSyntaxException {
            return predicateType.decode(reader, RegistryOps.injectRegistryContext(data, this.registries));
        }

        @Override
        public Stream<Identifier> listElementTypes() {
            return this.items.listElementIds().map(ResourceKey::identifier);
        }

        @Override
        public Stream<Identifier> listTagTypes() {
            return this.items.listTagIds().map(TagKey::location);
        }

        @Override
        public Stream<Identifier> listComponentTypes() {
            return Stream.concat(
                ItemPredicateArgument.PSEUDO_COMPONENTS.keySet().stream(),
                this.components.listElements().filter(reference -> !reference.value().isTransient()).map(reference -> reference.key().identifier())
            );
        }

        @Override
        public Stream<Identifier> listPredicateTypes() {
            return Stream.concat(ItemPredicateArgument.PSEUDO_PREDICATES.keySet().stream(), this.predicates.listElementIds().map(ResourceKey::identifier));
        }

        @Override
        public Predicate<ItemStack> negate(Predicate<ItemStack> value) {
            return value.negate();
        }

        @Override
        public Predicate<ItemStack> anyOf(List<Predicate<ItemStack>> values) {
            return Util.anyOf(values);
        }
    }

    record PredicateWrapper(Identifier id, Decoder<? extends Predicate<ItemStack>> type) {
        public PredicateWrapper(Holder.Reference<DataComponentPredicate.Type<?>> predicate) {
            this(predicate.key().identifier(), predicate.value().codec().map(dataComponentPredicate -> dataComponentPredicate::matches));
        }

        public Predicate<ItemStack> decode(ImmutableStringReader reader, Dynamic<?> data) throws CommandSyntaxException {
            DataResult<? extends Predicate<ItemStack>> dataResult = this.type.parse(data);
            return (Predicate<ItemStack>)dataResult.getOrThrow(
                string -> ItemPredicateArgument.ERROR_MALFORMED_PREDICATE.createWithContext(reader, this.id.toString(), string)
            );
        }
    }

    public interface Result extends Predicate<ItemStack> {
    }
}
