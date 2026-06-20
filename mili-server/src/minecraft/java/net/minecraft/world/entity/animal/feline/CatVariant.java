package net.minecraft.world.entity.animal.feline;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.ClientAsset;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.world.entity.variant.PriorityProvider;
import net.minecraft.world.entity.variant.SpawnCondition;
import net.minecraft.world.entity.variant.SpawnContext;
import net.minecraft.world.entity.variant.SpawnPrioritySelectors;

public record CatVariant(ClientAsset.ResourceTexture assetInfo, SpawnPrioritySelectors spawnConditions)
    implements PriorityProvider<SpawnContext, SpawnCondition> {
    public static final Codec<CatVariant> DIRECT_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                ClientAsset.ResourceTexture.DEFAULT_FIELD_CODEC.forGetter(CatVariant::assetInfo),
                SpawnPrioritySelectors.CODEC.fieldOf("spawn_conditions").forGetter(CatVariant::spawnConditions)
            )
            .apply(instance, CatVariant::new)
    );
    public static final Codec<CatVariant> NETWORK_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(ClientAsset.ResourceTexture.DEFAULT_FIELD_CODEC.forGetter(CatVariant::assetInfo)).apply(instance, CatVariant::new)
    );
    public static final Codec<Holder<CatVariant>> CODEC = RegistryFixedCodec.create(Registries.CAT_VARIANT);
    public static final StreamCodec<RegistryFriendlyByteBuf, Holder<CatVariant>> STREAM_CODEC = ByteBufCodecs.holderRegistry(Registries.CAT_VARIANT);

    private CatVariant(ClientAsset.ResourceTexture assetInfo) {
        this(assetInfo, SpawnPrioritySelectors.EMPTY);
    }

    @Override
    public List<PriorityProvider.Selector<SpawnContext, SpawnCondition>> selectors() {
        return this.spawnConditions.selectors();
    }
}
