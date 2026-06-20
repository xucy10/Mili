package net.minecraft.world.level.storage.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.ItemContainerContents;

public interface ContainerComponentManipulators {
    ContainerComponentManipulator<ItemContainerContents> CONTAINER = new ContainerComponentManipulator<ItemContainerContents>() {
        @Override
        public DataComponentType<ItemContainerContents> type() {
            return DataComponents.CONTAINER;
        }

        @Override
        public Stream<ItemStack> getContents(ItemContainerContents contents) {
            return contents.stream();
        }

        @Override
        public ItemContainerContents empty() {
            return ItemContainerContents.EMPTY;
        }

        @Override
        public ItemContainerContents setContents(ItemContainerContents contents, Stream<ItemStack> items) {
            return ItemContainerContents.fromItems(items.toList());
        }
    };
    ContainerComponentManipulator<BundleContents> BUNDLE_CONTENTS = new ContainerComponentManipulator<BundleContents>() {
        @Override
        public DataComponentType<BundleContents> type() {
            return DataComponents.BUNDLE_CONTENTS;
        }

        @Override
        public BundleContents empty() {
            return BundleContents.EMPTY;
        }

        @Override
        public Stream<ItemStack> getContents(BundleContents contents) {
            return contents.itemCopyStream();
        }

        @Override
        public BundleContents setContents(BundleContents contents, Stream<ItemStack> items) {
            BundleContents.Mutable mutable = new BundleContents.Mutable(contents).clearItems();
            items.forEach(mutable::tryInsert);
            return mutable.toImmutable();
        }
    };
    ContainerComponentManipulator<ChargedProjectiles> CHARGED_PROJECTILES = new ContainerComponentManipulator<ChargedProjectiles>() {
        @Override
        public DataComponentType<ChargedProjectiles> type() {
            return DataComponents.CHARGED_PROJECTILES;
        }

        @Override
        public ChargedProjectiles empty() {
            return ChargedProjectiles.EMPTY;
        }

        @Override
        public Stream<ItemStack> getContents(ChargedProjectiles contents) {
            return contents.getItems().stream();
        }

        @Override
        public ChargedProjectiles setContents(ChargedProjectiles contents, Stream<ItemStack> items) {
            return ChargedProjectiles.of(items.toList());
        }
    };
    Map<DataComponentType<?>, ContainerComponentManipulator<?>> ALL_MANIPULATORS = Stream.of(CONTAINER, BUNDLE_CONTENTS, CHARGED_PROJECTILES)
        .collect(
            Collectors.toMap(
                ContainerComponentManipulator::type, containerComponentManipulator -> (ContainerComponentManipulator<?>)containerComponentManipulator
            )
        );
    Codec<ContainerComponentManipulator<?>> CODEC = BuiltInRegistries.DATA_COMPONENT_TYPE.byNameCodec().comapFlatMap(dataComponentType -> {
        ContainerComponentManipulator<?> containerComponentManipulator = ALL_MANIPULATORS.get(dataComponentType);
        return containerComponentManipulator != null ? DataResult.success(containerComponentManipulator) : DataResult.error(() -> "No items in component");
    }, ContainerComponentManipulator::type);
}
