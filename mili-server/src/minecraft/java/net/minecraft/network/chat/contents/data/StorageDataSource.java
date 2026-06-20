package net.minecraft.network.chat.contents.data;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

public record StorageDataSource(Identifier id) implements DataSource {
    public static final MapCodec<StorageDataSource> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(Identifier.CODEC.fieldOf("storage").forGetter(StorageDataSource::id)).apply(instance, StorageDataSource::new)
    );

    @Override
    public Stream<CompoundTag> getData(CommandSourceStack source) {
        CompoundTag compoundTag = source.getServer().getCommandStorage().get(this.id);
        return Stream.of(compoundTag);
    }

    @Override
    public MapCodec<StorageDataSource> codec() {
        return MAP_CODEC;
    }

    @Override
    public String toString() {
        return "storage=" + this.id;
    }
}
