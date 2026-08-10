package net.minecraft.data;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import net.minecraft.SharedConstants;
import net.minecraft.SuppressForbidden;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.data.advancements.packs.VanillaAdvancementProvider;
import net.minecraft.data.info.BiomeParametersDumpReport;
import net.minecraft.data.info.BlockListReport;
import net.minecraft.data.info.CommandsReport;
import net.minecraft.data.info.DatapackStructureReport;
import net.minecraft.data.info.ItemListReport;
import net.minecraft.data.info.PacketReport;
import net.minecraft.data.info.RegistryDumpReport;
import net.minecraft.data.loot.packs.TradeRebalanceLootTableProvider;
import net.minecraft.data.loot.packs.VanillaLootTableProvider;
import net.minecraft.data.metadata.PackMetadataGenerator;
import net.minecraft.data.recipes.packs.VanillaRecipeProvider;
import net.minecraft.data.registries.RegistriesDatapackGenerator;
import net.minecraft.data.registries.TradeRebalanceRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.data.structures.NbtToSnbt;
import net.minecraft.data.structures.SnbtToNbt;
import net.minecraft.data.structures.StructureUpdater;
import net.minecraft.data.tags.BannerPatternTagsProvider;
import net.minecraft.data.tags.BiomeTagsProvider;
import net.minecraft.data.tags.DamageTypeTagsProvider;
import net.minecraft.data.tags.DialogTagsProvider;
import net.minecraft.data.tags.EntityTypeTagsProvider;
import net.minecraft.data.tags.FlatLevelGeneratorPresetTagsProvider;
import net.minecraft.data.tags.FluidTagsProvider;
import net.minecraft.data.tags.GameEventTagsProvider;
import net.minecraft.data.tags.InstrumentTagsProvider;
import net.minecraft.data.tags.PaintingVariantTagsProvider;
import net.minecraft.data.tags.PoiTypeTagsProvider;
import net.minecraft.data.tags.StructureTagsProvider;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.data.tags.TimelineTagsProvider;
import net.minecraft.data.tags.TradeRebalanceEnchantmentTagsProvider;
import net.minecraft.data.tags.VanillaBlockTagsProvider;
import net.minecraft.data.tags.VanillaEnchantmentTagsProvider;
import net.minecraft.data.tags.VanillaItemTagsProvider;
import net.minecraft.data.tags.WorldPresetTagsProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.obfuscate.DontObfuscate;
import net.minecraft.server.jsonrpc.dataprovider.JsonRpcApiSchema;
import net.minecraft.util.Util;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.levelgen.structure.Structure;

public class Main {
    @SuppressForbidden(reason = "System.out needed before bootstrap")
    @DontObfuscate
    public static void main(String[] args) throws IOException {
        SharedConstants.tryDetectVersion();
        OptionParser optionParser = new OptionParser();
        OptionSpec<Void> optionSpec = optionParser.accepts("help", "Show the help menu").forHelp();
        OptionSpec<Void> optionSpec1 = optionParser.accepts("server", "Include server generators");
        OptionSpec<Void> optionSpec2 = optionParser.accepts("dev", "Include development tools");
        OptionSpec<Void> optionSpec3 = optionParser.accepts("reports", "Include data reports");
        optionParser.accepts("validate", "Validate inputs");
        OptionSpec<Void> optionSpec4 = optionParser.accepts("all", "Include all generators");
        OptionSpec<String> optionSpec5 = optionParser.accepts("output", "Output folder").withRequiredArg().defaultsTo("generated");
        OptionSpec<String> optionSpec6 = optionParser.accepts("input", "Input folder").withRequiredArg();
        OptionSet optionSet = optionParser.parse(args);
        if (!optionSet.has(optionSpec) && optionSet.hasOptions()) {
            Path path = Paths.get(optionSpec5.value(optionSet));
            boolean hasOptionSpec = optionSet.has(optionSpec4);
            boolean flag = hasOptionSpec || optionSet.has(optionSpec1);
            boolean flag1 = hasOptionSpec || optionSet.has(optionSpec2);
            boolean flag2 = hasOptionSpec || optionSet.has(optionSpec3);
            Collection<Path> collection = optionSet.valuesOf(optionSpec6).stream().map(string -> Paths.get(string)).toList();
            DataGenerator dataGenerator = new DataGenerator(path, SharedConstants.getCurrentVersion(), true);
            addServerProviders(dataGenerator, collection, flag, flag1, flag2);
            dataGenerator.run();
            Util.shutdownExecutors();
        } else {
            optionParser.printHelpOn(System.out);
        }
    }

    private static <T extends DataProvider> DataProvider.Factory<T> bindRegistries(
        BiFunction<PackOutput, CompletableFuture<HolderLookup.Provider>, T> tagProviderFactory, CompletableFuture<HolderLookup.Provider> lookupProvider
    ) {
        return output -> tagProviderFactory.apply(output, lookupProvider);
    }

    public static void addServerProviders(DataGenerator dataGenerator, Collection<Path> paths, boolean server, boolean dev, boolean reports) {
        DataGenerator.PackGenerator vanillaPack = dataGenerator.getVanillaPack(server);
        vanillaPack.addProvider(output -> new SnbtToNbt(output, paths).addFilter(new StructureUpdater()));
        CompletableFuture<HolderLookup.Provider> completableFuture = CompletableFuture.supplyAsync(VanillaRegistries::createLookup, Util.backgroundExecutor());
        DataGenerator.PackGenerator vanillaPack1 = dataGenerator.getVanillaPack(server);
        vanillaPack1.addProvider(bindRegistries(RegistriesDatapackGenerator::new, completableFuture));
        vanillaPack1.addProvider(bindRegistries(VanillaAdvancementProvider::create, completableFuture));
        vanillaPack1.addProvider(bindRegistries(VanillaLootTableProvider::create, completableFuture));
        vanillaPack1.addProvider(bindRegistries(VanillaRecipeProvider.Runner::new, completableFuture));
        TagsProvider<Block> tagsProvider = vanillaPack1.addProvider(bindRegistries(VanillaBlockTagsProvider::new, completableFuture));
        TagsProvider<Item> tagsProvider1 = vanillaPack1.addProvider(bindRegistries(VanillaItemTagsProvider::new, completableFuture));
        TagsProvider<Biome> tagsProvider2 = vanillaPack1.addProvider(bindRegistries(BiomeTagsProvider::new, completableFuture));
        TagsProvider<BannerPattern> tagsProvider3 = vanillaPack1.addProvider(bindRegistries(BannerPatternTagsProvider::new, completableFuture));
        TagsProvider<Structure> tagsProvider4 = vanillaPack1.addProvider(bindRegistries(StructureTagsProvider::new, completableFuture));
        vanillaPack1.addProvider(bindRegistries(DamageTypeTagsProvider::new, completableFuture));
        vanillaPack1.addProvider(bindRegistries(DialogTagsProvider::new, completableFuture));
        vanillaPack1.addProvider(bindRegistries(EntityTypeTagsProvider::new, completableFuture));
        vanillaPack1.addProvider(bindRegistries(FlatLevelGeneratorPresetTagsProvider::new, completableFuture));
        vanillaPack1.addProvider(bindRegistries(FluidTagsProvider::new, completableFuture));
        vanillaPack1.addProvider(bindRegistries(GameEventTagsProvider::new, completableFuture));
        vanillaPack1.addProvider(bindRegistries(InstrumentTagsProvider::new, completableFuture));
        vanillaPack1.addProvider(bindRegistries(PaintingVariantTagsProvider::new, completableFuture));
        vanillaPack1.addProvider(bindRegistries(PoiTypeTagsProvider::new, completableFuture));
        vanillaPack1.addProvider(bindRegistries(WorldPresetTagsProvider::new, completableFuture));
        vanillaPack1.addProvider(bindRegistries(VanillaEnchantmentTagsProvider::new, completableFuture));
        vanillaPack1.addProvider(bindRegistries(TimelineTagsProvider::new, completableFuture));
        vanillaPack1 = dataGenerator.getVanillaPack(dev);
        vanillaPack1.addProvider(output -> new NbtToSnbt(output, paths));
        vanillaPack1 = dataGenerator.getVanillaPack(reports);
        vanillaPack1.addProvider(bindRegistries(BiomeParametersDumpReport::new, completableFuture));
        vanillaPack1.addProvider(bindRegistries(ItemListReport::new, completableFuture));
        vanillaPack1.addProvider(bindRegistries(BlockListReport::new, completableFuture));
        vanillaPack1.addProvider(bindRegistries(CommandsReport::new, completableFuture));
        vanillaPack1.addProvider(RegistryDumpReport::new);
        vanillaPack1.addProvider(PacketReport::new);
        vanillaPack1.addProvider(DatapackStructureReport::new);
        vanillaPack1.addProvider(JsonRpcApiSchema::new);
        CompletableFuture<RegistrySetBuilder.PatchedRegistries> completableFuture1 = TradeRebalanceRegistries.createLookup(completableFuture);
        CompletableFuture<HolderLookup.Provider> completableFuture2 = completableFuture1.thenApply(RegistrySetBuilder.PatchedRegistries::patches);
        DataGenerator.PackGenerator builtinDatapack = dataGenerator.getBuiltinDatapack(server, "trade_rebalance");
        builtinDatapack.addProvider(bindRegistries(RegistriesDatapackGenerator::new, completableFuture2));
        builtinDatapack.addProvider(
            output -> PackMetadataGenerator.forFeaturePack(
                output, Component.translatable("dataPack.trade_rebalance.description"), FeatureFlagSet.of(FeatureFlags.TRADE_REBALANCE)
            )
        );
        builtinDatapack.addProvider(bindRegistries(TradeRebalanceLootTableProvider::create, completableFuture));
        builtinDatapack.addProvider(bindRegistries(TradeRebalanceEnchantmentTagsProvider::new, completableFuture));
        vanillaPack1 = dataGenerator.getBuiltinDatapack(server, "redstone_experiments");
        vanillaPack1.addProvider(
            output -> PackMetadataGenerator.forFeaturePack(
                output, Component.translatable("dataPack.redstone_experiments.description"), FeatureFlagSet.of(FeatureFlags.REDSTONE_EXPERIMENTS)
            )
        );
        vanillaPack1 = dataGenerator.getBuiltinDatapack(server, "minecart_improvements");
        vanillaPack1.addProvider(
            output -> PackMetadataGenerator.forFeaturePack(
                output, Component.translatable("dataPack.minecart_improvements.description"), FeatureFlagSet.of(FeatureFlags.MINECART_IMPROVEMENTS)
            )
        );
    }
}
