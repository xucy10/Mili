package net.minecraft.world.entity.animal.pig;

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

public record PigVariant(ModelAndTexture<PigVariant.ModelType> modelAndTexture, SpawnPrioritySelectors spawnConditions)
    implements PriorityProvider<SpawnContext, SpawnCondition> {
    public static final Codec<PigVariant> DIRECT_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                ModelAndTexture.codec(PigVariant.ModelType.CODEC, PigVariant.ModelType.NORMAL).forGetter(PigVariant::modelAndTexture),
                SpawnPrioritySelectors.CODEC.fieldOf("spawn_conditions").forGetter(PigVariant::spawnConditions)
            )
            .apply(instance, PigVariant::new)
    );
    public static final Codec<PigVariant> NETWORK_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(ModelAndTexture.codec(PigVariant.ModelType.CODEC, PigVariant.ModelType.NORMAL).forGetter(PigVariant::modelAndTexture))
            .apply(instance, PigVariant::new)
    );
    public static final Codec<Holder<PigVariant>> CODEC = RegistryFixedCodec.create(Registries.PIG_VARIANT);
    public static final StreamCodec<RegistryFriendlyByteBuf, Holder<PigVariant>> STREAM_CODEC = ByteBufCodecs.holderRegistry(Registries.PIG_VARIANT);

    private PigVariant(ModelAndTexture<PigVariant.ModelType> modelAndTexture) {
        this(modelAndTexture, SpawnPrioritySelectors.EMPTY);
    }

    @Override
    public List<PriorityProvider.Selector<SpawnContext, SpawnCondition>> selectors() {
        return this.spawnConditions.selectors();
    }

    public static enum ModelType implements StringRepresentable {
        NORMAL("normal"),
        COLD("cold");

        public static final Codec<PigVariant.ModelType> CODEC = StringRepresentable.fromEnum(PigVariant.ModelType::values);
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
