package net.minecraft.world.item.component;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.Nullable;

public record BlockItemStateProperties(Map<String, String> properties) implements TooltipProvider {
    public static final BlockItemStateProperties EMPTY = new BlockItemStateProperties(Map.of());
    public static final Codec<BlockItemStateProperties> CODEC = Codec.unboundedMap(Codec.STRING, Codec.STRING)
        .xmap(BlockItemStateProperties::new, BlockItemStateProperties::properties);
    private static final StreamCodec<ByteBuf, Map<String, String>> PROPERTIES_STREAM_CODEC = ByteBufCodecs.map(
        Object2ObjectOpenHashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8
    );
    public static final StreamCodec<ByteBuf, BlockItemStateProperties> STREAM_CODEC = PROPERTIES_STREAM_CODEC.map(
        BlockItemStateProperties::new, BlockItemStateProperties::properties
    );

    public <T extends Comparable<T>> BlockItemStateProperties with(Property<T> property, T value) {
        return new BlockItemStateProperties(Util.copyAndPut(this.properties, property.getName(), property.getName(value)));
    }

    public <T extends Comparable<T>> BlockItemStateProperties with(Property<T> property, BlockState state) {
        return this.with(property, state.getValue(property));
    }

    public <T extends Comparable<T>> @Nullable T get(Property<T> property) {
        String string = this.properties.get(property.getName());
        return string == null ? null : property.getValue(string).orElse(null);
    }

    public BlockState apply(BlockState state) {
        StateDefinition<Block, BlockState> stateDefinition = state.getBlock().getStateDefinition();

        for (Entry<String, String> entry : this.properties.entrySet()) {
            Property<?> property = stateDefinition.getProperty(entry.getKey());
            if (property != null) {
                state = updateState(state, property, entry.getValue());
            }
        }

        return state;
    }

    private static <T extends Comparable<T>> BlockState updateState(BlockState state, Property<T> property, String propertyName) {
        return property.getValue(propertyName).map(value -> state.setValue(property, value)).orElse(state);
    }

    public boolean isEmpty() {
        return this.properties.isEmpty();
    }

    @Override
    public void addToTooltip(Item.TooltipContext context, Consumer<Component> tooltipAdder, TooltipFlag flag, DataComponentGetter componentGetter) {
        Integer integer = this.get(BeehiveBlock.HONEY_LEVEL);
        if (integer != null) {
            tooltipAdder.accept(Component.translatable("container.beehive.honey", integer, 5).withStyle(ChatFormatting.GRAY));
        }
    }
}
