package net.minecraft.world.level.saveddata.maps;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.MapPostProcessing;
import net.minecraft.world.item.component.TooltipProvider;

public record MapId(int id) implements TooltipProvider {
    public static final Codec<MapId> CODEC = Codec.INT.xmap(MapId::new, MapId::id);
    public static final StreamCodec<ByteBuf, MapId> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(MapId::new, MapId::id);
    private static final Component LOCKED_TEXT = Component.translatable("filled_map.locked").withStyle(ChatFormatting.GRAY);

    public String key() {
        return "map_" + this.id;
    }

    @Override
    public void addToTooltip(Item.TooltipContext context, Consumer<Component> tooltipAdder, TooltipFlag flag, DataComponentGetter componentGetter) {
        MapItemSavedData mapItemSavedData = context.mapData(this);
        if (mapItemSavedData == null) {
            tooltipAdder.accept(Component.translatable("filled_map.unknown").withStyle(ChatFormatting.GRAY));
        } else {
            MapPostProcessing mapPostProcessing = componentGetter.get(DataComponents.MAP_POST_PROCESSING);
            if (componentGetter.get(DataComponents.CUSTOM_NAME) == null && mapPostProcessing == null) {
                tooltipAdder.accept(Component.translatable("filled_map.id", this.id).withStyle(ChatFormatting.GRAY));
            }

            if (mapItemSavedData.locked || mapPostProcessing == MapPostProcessing.LOCK) {
                tooltipAdder.accept(LOCKED_TEXT);
            }

            if (flag.isAdvanced()) {
                int i = mapPostProcessing == MapPostProcessing.SCALE ? 1 : 0;
                int min = Math.min(mapItemSavedData.scale + i, 4);
                tooltipAdder.accept(Component.translatable("filled_map.scale", 1 << min).withStyle(ChatFormatting.GRAY));
                tooltipAdder.accept(Component.translatable("filled_map.level", min, 4).withStyle(ChatFormatting.GRAY));
            }
        }
    }
}
