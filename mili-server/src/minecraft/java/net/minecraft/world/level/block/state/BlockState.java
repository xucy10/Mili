package net.minecraft.world.level.block.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;

public class BlockState extends BlockBehaviour.BlockStateBase {
    public static final Codec<BlockState> CODEC = codec(BuiltInRegistries.BLOCK.byNameCodec(), Block::defaultBlockState).stable();

    // Paper start - optimise getType calls
    @javax.annotation.Nullable org.bukkit.Material cachedMaterial;

    public final org.bukkit.Material getBukkitMaterial() {
        if (this.cachedMaterial == null) {
            this.cachedMaterial = org.bukkit.craftbukkit.block.CraftBlockType.minecraftToBukkit(this.getBlock());
        }
        return this.cachedMaterial;
    }
    // Paper end - optimise getType calls

    public BlockState(Block owner, Reference2ObjectArrayMap<Property<?>, Comparable<?>> values, MapCodec<BlockState> propertiesCodec) {
        super(owner, values, propertiesCodec);
    }

    @Override
    protected BlockState asState() {
        return this;
    }
}
