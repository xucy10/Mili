package net.minecraft.world.waypoints;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;

public interface Waypoint {
    int MAX_RANGE = 60000000;
    AttributeModifier WAYPOINT_TRANSMIT_RANGE_HIDE_MODIFIER = new AttributeModifier(
        Identifier.withDefaultNamespace("waypoint_transmit_range_hide"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
    );

    static Item.Properties addHideAttribute(Item.Properties properties) {
        return properties.component(
            DataComponents.ATTRIBUTE_MODIFIERS,
            ItemAttributeModifiers.builder()
                .add(
                    Attributes.WAYPOINT_TRANSMIT_RANGE, WAYPOINT_TRANSMIT_RANGE_HIDE_MODIFIER, EquipmentSlotGroup.HEAD, ItemAttributeModifiers.Display.hidden()
                )
                .build()
        );
    }

    public static class Icon {
        public static final Codec<Waypoint.Icon> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    ResourceKey.codec(WaypointStyleAssets.ROOT_ID).fieldOf("style").forGetter(icon -> icon.style),
                    ExtraCodecs.RGB_COLOR_CODEC.optionalFieldOf("color").forGetter(icon -> icon.color)
                )
                .apply(instance, Waypoint.Icon::new)
        );
        public static final StreamCodec<ByteBuf, Waypoint.Icon> STREAM_CODEC = StreamCodec.composite(
            ResourceKey.streamCodec(WaypointStyleAssets.ROOT_ID),
            icon -> icon.style,
            ByteBufCodecs.optional(ByteBufCodecs.RGB_COLOR),
            icon -> icon.color,
            Waypoint.Icon::new
        );
        public static final Waypoint.Icon NULL = new Waypoint.Icon();
        public ResourceKey<WaypointStyleAsset> style = WaypointStyleAssets.DEFAULT;
        public Optional<Integer> color = Optional.empty();

        public Icon() {
        }

        private Icon(ResourceKey<WaypointStyleAsset> style, Optional<Integer> color) {
            this.style = style;
            this.color = color;
        }

        public boolean hasData() {
            return this.style != WaypointStyleAssets.DEFAULT || this.color.isPresent();
        }

        public Waypoint.Icon cloneAndAssignStyle(LivingEntity entity) {
            ResourceKey<WaypointStyleAsset> overrideStyle = this.getOverrideStyle();
            Optional<Integer> optional = this.color
                .or(
                    () -> Optional.ofNullable(entity.getTeam())
                        .map(playerTeam -> playerTeam.getColor().getColor())
                        .map(integer -> integer == 0 ? -13619152 : integer)
                );
            return overrideStyle == this.style && optional.isEmpty() ? this : new Waypoint.Icon(overrideStyle, optional);
        }

        public void copyFrom(Waypoint.Icon icon) {
            this.color = icon.color;
            this.style = icon.style;
        }

        private ResourceKey<WaypointStyleAsset> getOverrideStyle() {
            return this.style != WaypointStyleAssets.DEFAULT ? this.style : WaypointStyleAssets.DEFAULT;
        }
    }
}
