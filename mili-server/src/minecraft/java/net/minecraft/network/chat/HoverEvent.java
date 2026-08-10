package net.minecraft.network.chat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public interface HoverEvent {
    Codec<HoverEvent> CODEC = HoverEvent.Action.CODEC.dispatch("action", HoverEvent::action, action -> action.codec);

    HoverEvent.Action action();

    public static enum Action implements StringRepresentable {
        SHOW_TEXT("show_text", true, HoverEvent.ShowText.CODEC),
        SHOW_ITEM("show_item", true, HoverEvent.ShowItem.CODEC),
        SHOW_ENTITY("show_entity", true, HoverEvent.ShowEntity.CODEC);

        public static final Codec<HoverEvent.Action> UNSAFE_CODEC = StringRepresentable.fromValues(HoverEvent.Action::values);
        public static final Codec<HoverEvent.Action> CODEC = UNSAFE_CODEC.validate(HoverEvent.Action::filterForSerialization);
        private final String name;
        private final boolean allowFromServer;
        final MapCodec<? extends HoverEvent> codec;

        private Action(final String name, final boolean allowFromServer, final MapCodec<? extends HoverEvent> codec) {
            this.name = name;
            this.allowFromServer = allowFromServer;
            this.codec = codec;
        }

        public boolean isAllowedFromServer() {
            return this.allowFromServer;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        @Override
        public String toString() {
            return "<action " + this.name + ">";
        }

        private static DataResult<HoverEvent.Action> filterForSerialization(HoverEvent.Action action) {
            return !action.isAllowedFromServer() ? DataResult.error(() -> "Action not allowed: " + action) : DataResult.success(action, Lifecycle.stable());
        }
    }

    public static class EntityTooltipInfo {
        public static final MapCodec<HoverEvent.EntityTooltipInfo> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("id").forGetter(info -> info.type),
                    UUIDUtil.LENIENT_CODEC.fieldOf("uuid").forGetter(info -> info.uuid),
                    ComponentSerialization.CODEC.optionalFieldOf("name").forGetter(info -> info.name)
                )
                .apply(instance, HoverEvent.EntityTooltipInfo::new)
        );
        public final EntityType<?> type;
        public final UUID uuid;
        public final Optional<Component> name;
        private @Nullable List<Component> linesCache;

        public EntityTooltipInfo(EntityType<?> type, UUID uuid, @Nullable Component name) {
            this(type, uuid, Optional.ofNullable(name));
        }

        public EntityTooltipInfo(EntityType<?> type, UUID uuid, Optional<Component> name) {
            this.type = type;
            this.uuid = uuid;
            this.name = name;
        }

        public List<Component> getTooltipLines() {
            if (this.linesCache == null) {
                this.linesCache = new ArrayList<>();
                this.name.ifPresent(this.linesCache::add);
                this.linesCache.add(Component.translatable("gui.entity_tooltip.type", this.type.getDescription()));
                this.linesCache.add(Component.literal(this.uuid.toString()));
            }

            return this.linesCache;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            } else if (other != null && this.getClass() == other.getClass()) {
                HoverEvent.EntityTooltipInfo entityTooltipInfo = (HoverEvent.EntityTooltipInfo)other;
                return this.type.equals(entityTooltipInfo.type) && this.uuid.equals(entityTooltipInfo.uuid) && this.name.equals(entityTooltipInfo.name);
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            int hashCode = this.type.hashCode();
            hashCode = 31 * hashCode + this.uuid.hashCode();
            return 31 * hashCode + this.name.hashCode();
        }
    }

    public record ShowEntity(HoverEvent.EntityTooltipInfo entity) implements HoverEvent {
        public static final MapCodec<HoverEvent.ShowEntity> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(HoverEvent.EntityTooltipInfo.CODEC.forGetter(HoverEvent.ShowEntity::entity)).apply(instance, HoverEvent.ShowEntity::new)
        );

        @Override
        public HoverEvent.Action action() {
            return HoverEvent.Action.SHOW_ENTITY;
        }
    }

    public record ShowItem(ItemStack item) implements HoverEvent {
        public static final MapCodec<HoverEvent.ShowItem> CODEC = ItemStack.MAP_CODEC.xmap(HoverEvent.ShowItem::new, HoverEvent.ShowItem::item);

        public ShowItem(ItemStack item) {
            item = item.copy();
            this.item = item;
        }

        @Override
        public HoverEvent.Action action() {
            return HoverEvent.Action.SHOW_ITEM;
        }

        @Override
        public boolean equals(Object object) {
            return object instanceof HoverEvent.ShowItem showItem && ItemStack.matches(this.item, showItem.item);
        }

        @Override
        public int hashCode() {
            return ItemStack.hashItemAndComponents(this.item);
        }
    }

    public record ShowText(Component value) implements HoverEvent {
        public static final MapCodec<HoverEvent.ShowText> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(ComponentSerialization.CODEC.fieldOf("value").forGetter(HoverEvent.ShowText::value))
                .apply(instance, HoverEvent.ShowText::new)
        );

        @Override
        public HoverEvent.Action action() {
            return HoverEvent.Action.SHOW_TEXT;
        }
    }
}
