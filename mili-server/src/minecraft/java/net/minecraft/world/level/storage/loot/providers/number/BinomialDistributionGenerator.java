package net.minecraft.world.level.storage.loot.providers.number;

import com.google.common.collect.Sets;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import net.minecraft.util.RandomSource;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.level.storage.loot.LootContext;

public record BinomialDistributionGenerator(NumberProvider n, NumberProvider p) implements NumberProvider {
    public static final MapCodec<BinomialDistributionGenerator> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                NumberProviders.CODEC.fieldOf("n").forGetter(BinomialDistributionGenerator::n),
                NumberProviders.CODEC.fieldOf("p").forGetter(BinomialDistributionGenerator::p)
            )
            .apply(instance, BinomialDistributionGenerator::new)
    );

    @Override
    public LootNumberProviderType getType() {
        return NumberProviders.BINOMIAL;
    }

    @Override
    public int getInt(LootContext lootContext) {
        int _int = this.n.getInt(lootContext);
        float _float = this.p.getFloat(lootContext);
        RandomSource random = lootContext.getRandom();
        int i = 0;

        for (int i1 = 0; i1 < _int; i1++) {
            if (random.nextFloat() < _float) {
                i++;
            }
        }

        return i;
    }

    @Override
    public float getFloat(LootContext lootContext) {
        return this.getInt(lootContext);
    }

    public static BinomialDistributionGenerator binomial(int n, float p) {
        return new BinomialDistributionGenerator(ConstantValue.exactly(n), ConstantValue.exactly(p));
    }

    @Override
    public Set<ContextKey<?>> getReferencedContextParams() {
        return Sets.union(this.n.getReferencedContextParams(), this.p.getReferencedContextParams());
    }
}
