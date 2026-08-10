package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class SetBannerPatternFunction extends LootItemConditionalFunction {
    public static final MapCodec<SetBannerPatternFunction> CODEC = RecordCodecBuilder.mapCodec(
        instance -> commonFields(instance)
            .and(
                instance.group(
                    BannerPatternLayers.CODEC.fieldOf("patterns").forGetter(setBannerPatternFunction -> setBannerPatternFunction.patterns),
                    Codec.BOOL.fieldOf("append").forGetter(setBannerPatternFunction -> setBannerPatternFunction.append)
                )
            )
            .apply(instance, SetBannerPatternFunction::new)
    );
    private final BannerPatternLayers patterns;
    private final boolean append;

    SetBannerPatternFunction(List<LootItemCondition> predicates, BannerPatternLayers patterns, boolean append) {
        super(predicates);
        this.patterns = patterns;
        this.append = append;
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        if (this.append) {
            stack.update(
                DataComponents.BANNER_PATTERNS,
                BannerPatternLayers.EMPTY,
                this.patterns,
                (bannerPatternLayers, bannerPatternLayers1) -> new BannerPatternLayers.Builder()
                    .addAll(bannerPatternLayers)
                    .addAll(bannerPatternLayers1)
                    .build()
            );
        } else {
            stack.set(DataComponents.BANNER_PATTERNS, this.patterns);
        }

        return stack;
    }

    @Override
    public LootItemFunctionType<SetBannerPatternFunction> getType() {
        return LootItemFunctions.SET_BANNER_PATTERN;
    }

    public static SetBannerPatternFunction.Builder setBannerPattern(boolean append) {
        return new SetBannerPatternFunction.Builder(append);
    }

    public static class Builder extends LootItemConditionalFunction.Builder<SetBannerPatternFunction.Builder> {
        private final BannerPatternLayers.Builder patterns = new BannerPatternLayers.Builder();
        private final boolean append;

        Builder(boolean append) {
            this.append = append;
        }

        @Override
        protected SetBannerPatternFunction.Builder getThis() {
            return this;
        }

        @Override
        public LootItemFunction build() {
            return new SetBannerPatternFunction(this.getConditions(), this.patterns.build(), this.append);
        }

        public SetBannerPatternFunction.Builder addPattern(Holder<BannerPattern> pattern, DyeColor color) {
            this.patterns.add(pattern, color);
            return this;
        }
    }
}
