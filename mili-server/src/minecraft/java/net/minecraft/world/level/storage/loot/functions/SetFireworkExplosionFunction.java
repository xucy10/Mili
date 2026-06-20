package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class SetFireworkExplosionFunction extends LootItemConditionalFunction {
    public static final MapCodec<SetFireworkExplosionFunction> CODEC = RecordCodecBuilder.mapCodec(
        instance -> commonFields(instance)
            .and(
                instance.group(
                    FireworkExplosion.Shape.CODEC.optionalFieldOf("shape").forGetter(setFireworkExplosionFunction -> setFireworkExplosionFunction.shape),
                    FireworkExplosion.COLOR_LIST_CODEC.optionalFieldOf("colors").forGetter(setFireworkExplosionFunction -> setFireworkExplosionFunction.colors),
                    FireworkExplosion.COLOR_LIST_CODEC
                        .optionalFieldOf("fade_colors")
                        .forGetter(setFireworkExplosionFunction -> setFireworkExplosionFunction.fadeColors),
                    Codec.BOOL.optionalFieldOf("trail").forGetter(setFireworkExplosionFunction -> setFireworkExplosionFunction.trail),
                    Codec.BOOL.optionalFieldOf("twinkle").forGetter(setFireworkExplosionFunction -> setFireworkExplosionFunction.twinkle)
                )
            )
            .apply(instance, SetFireworkExplosionFunction::new)
    );
    public static final FireworkExplosion DEFAULT_VALUE = new FireworkExplosion(FireworkExplosion.Shape.SMALL_BALL, IntList.of(), IntList.of(), false, false);
    final Optional<FireworkExplosion.Shape> shape;
    final Optional<IntList> colors;
    final Optional<IntList> fadeColors;
    final Optional<Boolean> trail;
    final Optional<Boolean> twinkle;

    public SetFireworkExplosionFunction(
        List<LootItemCondition> predicates,
        Optional<FireworkExplosion.Shape> shape,
        Optional<IntList> colors,
        Optional<IntList> fadeColors,
        Optional<Boolean> trail,
        Optional<Boolean> twinkle
    ) {
        super(predicates);
        this.shape = shape;
        this.colors = colors;
        this.fadeColors = fadeColors;
        this.trail = trail;
        this.twinkle = twinkle;
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        stack.update(DataComponents.FIREWORK_EXPLOSION, DEFAULT_VALUE, this::apply);
        return stack;
    }

    private FireworkExplosion apply(FireworkExplosion fireworkExplosion) {
        return new FireworkExplosion(
            this.shape.orElseGet(fireworkExplosion::shape),
            this.colors.orElseGet(fireworkExplosion::colors),
            this.fadeColors.orElseGet(fireworkExplosion::fadeColors),
            this.trail.orElseGet(fireworkExplosion::hasTrail),
            this.twinkle.orElseGet(fireworkExplosion::hasTwinkle)
        );
    }

    @Override
    public LootItemFunctionType<SetFireworkExplosionFunction> getType() {
        return LootItemFunctions.SET_FIREWORK_EXPLOSION;
    }
}
