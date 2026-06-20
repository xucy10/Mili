package net.minecraft.world.entity.animal.wolf;

import net.minecraft.core.ClientAsset;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.variant.BiomeCheck;
import net.minecraft.world.entity.variant.SpawnPrioritySelectors;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

public class WolfVariants {
    public static final ResourceKey<WolfVariant> PALE = createKey("pale");
    public static final ResourceKey<WolfVariant> SPOTTED = createKey("spotted");
    public static final ResourceKey<WolfVariant> SNOWY = createKey("snowy");
    public static final ResourceKey<WolfVariant> BLACK = createKey("black");
    public static final ResourceKey<WolfVariant> ASHEN = createKey("ashen");
    public static final ResourceKey<WolfVariant> RUSTY = createKey("rusty");
    public static final ResourceKey<WolfVariant> WOODS = createKey("woods");
    public static final ResourceKey<WolfVariant> CHESTNUT = createKey("chestnut");
    public static final ResourceKey<WolfVariant> STRIPED = createKey("striped");
    public static final ResourceKey<WolfVariant> DEFAULT = PALE;

    private static ResourceKey<WolfVariant> createKey(String name) {
        return ResourceKey.create(Registries.WOLF_VARIANT, Identifier.withDefaultNamespace(name));
    }

    private static void register(BootstrapContext<WolfVariant> context, ResourceKey<WolfVariant> key, String name, ResourceKey<Biome> biome) {
        register(context, key, name, highPrioBiome(HolderSet.direct(context.lookup(Registries.BIOME).getOrThrow(biome))));
    }

    private static void register(BootstrapContext<WolfVariant> context, ResourceKey<WolfVariant> key, String name, TagKey<Biome> biomes) {
        register(context, key, name, highPrioBiome(context.lookup(Registries.BIOME).getOrThrow(biomes)));
    }

    private static SpawnPrioritySelectors highPrioBiome(HolderSet<Biome> biomes) {
        return SpawnPrioritySelectors.single(new BiomeCheck(biomes), 1);
    }

    private static void register(BootstrapContext<WolfVariant> context, ResourceKey<WolfVariant> key, String name, SpawnPrioritySelectors spawnConditions) {
        Identifier identifier = Identifier.withDefaultNamespace("entity/wolf/" + name);
        Identifier identifier1 = Identifier.withDefaultNamespace("entity/wolf/" + name + "_tame");
        Identifier identifier2 = Identifier.withDefaultNamespace("entity/wolf/" + name + "_angry");
        context.register(
            key,
            new WolfVariant(
                new WolfVariant.AssetInfo(
                    new ClientAsset.ResourceTexture(identifier), new ClientAsset.ResourceTexture(identifier1), new ClientAsset.ResourceTexture(identifier2)
                ),
                spawnConditions
            )
        );
    }

    public static void bootstrap(BootstrapContext<WolfVariant> context) {
        register(context, PALE, "wolf", SpawnPrioritySelectors.fallback(0));
        register(context, SPOTTED, "wolf_spotted", BiomeTags.IS_SAVANNA);
        register(context, SNOWY, "wolf_snowy", Biomes.GROVE);
        register(context, BLACK, "wolf_black", Biomes.OLD_GROWTH_PINE_TAIGA);
        register(context, ASHEN, "wolf_ashen", Biomes.SNOWY_TAIGA);
        register(context, RUSTY, "wolf_rusty", BiomeTags.IS_JUNGLE);
        register(context, WOODS, "wolf_woods", Biomes.FOREST);
        register(context, CHESTNUT, "wolf_chestnut", Biomes.OLD_GROWTH_SPRUCE_TAIGA);
        register(context, STRIPED, "wolf_striped", BiomeTags.IS_BADLANDS);
    }
}
