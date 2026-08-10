package net.minecraft.world.entity.npc.villager;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;

public record VillagerData(Holder<VillagerType> type, Holder<VillagerProfession> profession, int level) {
    public static final int MIN_VILLAGER_LEVEL = 1;
    public static final int MAX_VILLAGER_LEVEL = 5;
    private static final int[] NEXT_LEVEL_XP_THRESHOLDS = new int[]{0, 10, 70, 150, 250};
    public static final Codec<VillagerData> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                BuiltInRegistries.VILLAGER_TYPE
                    .holderByNameCodec()
                    .fieldOf("type")
                    .orElseGet(() -> BuiltInRegistries.VILLAGER_TYPE.getOrThrow(VillagerType.PLAINS))
                    .forGetter(villagerData -> villagerData.type),
                BuiltInRegistries.VILLAGER_PROFESSION
                    .holderByNameCodec()
                    .fieldOf("profession")
                    .orElseGet(() -> BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(VillagerProfession.NONE))
                    .forGetter(villagerData -> villagerData.profession),
                Codec.INT.fieldOf("level").orElse(1).forGetter(villagerData -> villagerData.level)
            )
            .apply(instance, VillagerData::new)
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerData> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.holderRegistry(Registries.VILLAGER_TYPE),
        VillagerData::type,
        ByteBufCodecs.holderRegistry(Registries.VILLAGER_PROFESSION),
        VillagerData::profession,
        ByteBufCodecs.VAR_INT,
        VillagerData::level,
        VillagerData::new
    );

    public VillagerData(Holder<VillagerType> type, Holder<VillagerProfession> profession, int level) {
        level = Math.max(1, level);
        this.type = type;
        this.profession = profession;
        this.level = level;
    }

    public VillagerData withType(Holder<VillagerType> type) {
        return new VillagerData(type, this.profession, this.level);
    }

    public VillagerData withType(HolderGetter.Provider registries, ResourceKey<VillagerType> type) {
        return this.withType(registries.getOrThrow(type));
    }

    public VillagerData withProfession(Holder<VillagerProfession> profession) {
        return new VillagerData(this.type, profession, this.level);
    }

    public VillagerData withProfession(HolderGetter.Provider registries, ResourceKey<VillagerProfession> profession) {
        return this.withProfession(registries.getOrThrow(profession));
    }

    public VillagerData withLevel(int level) {
        return new VillagerData(this.type, this.profession, level);
    }

    public static int getMinXpPerLevel(int level) {
        return canLevelUp(level) ? NEXT_LEVEL_XP_THRESHOLDS[level - 1] : 0;
    }

    public static int getMaxXpPerLevel(int level) {
        return canLevelUp(level) ? NEXT_LEVEL_XP_THRESHOLDS[level] : 0;
    }

    public static boolean canLevelUp(int level) {
        return level >= 1 && level < 5;
    }
}
