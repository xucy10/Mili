package net.minecraft.world.level.storage.loot.predicates;

import com.mojang.serialization.MapCodec;
import java.util.Set;
import net.minecraft.util.RandomSource;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

public class ExplosionCondition implements LootItemCondition {
    private static final ExplosionCondition INSTANCE = new ExplosionCondition();
    public static final MapCodec<ExplosionCondition> CODEC = MapCodec.unit(INSTANCE);

    private ExplosionCondition() {
    }

    @Override
    public LootItemConditionType getType() {
        return LootItemConditions.SURVIVES_EXPLOSION;
    }

    @Override
    public Set<ContextKey<?>> getReferencedContextParams() {
        return Set.of(LootContextParams.EXPLOSION_RADIUS);
    }

    @Override
    public boolean test(LootContext context) {
        Float _float = context.getOptionalParameter(LootContextParams.EXPLOSION_RADIUS);
        if (_float != null) {
            RandomSource random = context.getRandom();
            float f = 1.0F / _float;
            // CraftBukkit - <= to < to allow for plugins to completely disable block drops from explosions
            return random.nextFloat() < f;
        } else {
            return true;
        }
    }

    public static LootItemCondition.Builder survivesExplosion() {
        return () -> INSTANCE;
    }
}
