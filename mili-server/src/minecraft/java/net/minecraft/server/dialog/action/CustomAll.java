package net.minecraft.server.dialog.action;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.resources.Identifier;

public record CustomAll(Identifier id, Optional<CompoundTag> additions) implements Action {
    public static final MapCodec<CustomAll> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                Identifier.CODEC.fieldOf("id").forGetter(CustomAll::id), CompoundTag.CODEC.optionalFieldOf("additions").forGetter(CustomAll::additions)
            )
            .apply(instance, CustomAll::new)
    );

    @Override
    public MapCodec<CustomAll> codec() {
        return MAP_CODEC;
    }

    @Override
    public Optional<ClickEvent> createAction(Map<String, Action.ValueGetter> valueGetters) {
        CompoundTag compoundTag = this.additions.<CompoundTag>map(CompoundTag::copy).orElseGet(CompoundTag::new);
        valueGetters.forEach((string, valueGetter) -> compoundTag.put(string, valueGetter.asTag()));
        return Optional.of(new ClickEvent.Custom(this.id, Optional.of(compoundTag)));
    }
}
