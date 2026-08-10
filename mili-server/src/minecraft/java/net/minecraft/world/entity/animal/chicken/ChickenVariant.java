package net.minecraft.world.entity.animal.chicken;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.variant.ModelAndTexture;
import net.minecraft.world.entity.variant.PriorityProvider;
import net.minecraft.world.entity.variant.SpawnCondition;
import net.minecraft.world.entity.variant.SpawnContext;
import net.minecraft.world.entity.variant.SpawnPrioritySelectors;

public record ChickenVariant(ModelAndTexture<ChickenVariant.ModelType> modelAndTexture, SpawnPrioritySelectors spawnConditions)
    implements PriorityProvider<SpawnContext, SpawnCondition> {
    public static final Codec<ChickenVariant> DIRECT_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                ModelAndTexture.codec(ChickenVariant.ModelType.CODEC, ChickenVariant.ModelType.NORMAL).forGetter(ChickenVariant::modelAndTexture),
                SpawnPrioritySelectors.CODEC.fieldOf("spawn_conditions").forGetter(ChickenVariant::spawnConditions)
            )
            .apply(instance, ChickenVariant::new)
    );
    public static final Codec<ChickenVariant> NETWORK_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                ModelAndTexture.codec(ChickenVariant.ModelType.CODEC, ChickenVariant.ModelType.NORMAL).forGetter(ChickenVariant::modelAndTexture)
            )
            .apply(instance, ChickenVariant::new)
    );
    public static final Codec<Holder<ChickenVariant>> CODEC = RegistryFixedCodec.create(Registries.CHICKEN_VARIANT);
    public static final StreamCodec<RegistryFriendlyByteBuf, Holder<ChickenVariant>> STREAM_CODEC = ByteBufCodecs.holderRegistry(Registries.CHICKEN_VARIANT);

    private ChickenVariant(ModelAndTexture<ChickenVariant.ModelType> modelAndTexture) {
        this(modelAndTexture, SpawnPrioritySelectors.EMPTY);
    }

    @Override
    public List<PriorityProvider.Selector<SpawnContext, SpawnCondition>> selectors() {
        return this.spawnConditions.selectors();
    }

    public static enum ModelType implements StringRepresentable {
        NORMAL("normal"),
        COLD("cold");

        public static final Codec<ChickenVariant.ModelType> CODEC = StringRepresentable.fromEnum(ChickenVariant.ModelType::values);
        private final String name;

        private ModelType(final String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }
}
