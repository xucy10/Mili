package net.minecraft.world.level.levelgen.structure.templatesystem.rule.blockentity;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

public class AppendStatic implements RuleBlockEntityModifier {
    public static final MapCodec<AppendStatic> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(CompoundTag.CODEC.fieldOf("data").forGetter(appendStatic -> appendStatic.tag)).apply(instance, AppendStatic::new)
    );
    private final CompoundTag tag;

    public AppendStatic(CompoundTag tag) {
        this.tag = tag;
    }

    @Override
    public CompoundTag apply(RandomSource random, @Nullable CompoundTag tag) {
        return tag == null ? this.tag.copy() : tag.merge(this.tag);
    }

    @Override
    public RuleBlockEntityModifierType<?> getType() {
        return RuleBlockEntityModifierType.APPEND_STATIC;
    }
}
