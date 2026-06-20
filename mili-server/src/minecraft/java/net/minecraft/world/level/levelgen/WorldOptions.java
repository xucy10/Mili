package net.minecraft.world.level.levelgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.OptionalLong;
import net.minecraft.util.RandomSource;
import org.apache.commons.lang3.StringUtils;

public class WorldOptions {
    // Leaf start - Matter - Secure Seed
    private static final com.google.gson.Gson gson = new com.google.gson.Gson();
    private static final boolean isSecureSeedEnabled = me.earthme.luminol.config.modules.function.SecureSeedConfig.enabled;
    public static final MapCodec<WorldOptions> CODEC = RecordCodecBuilder.mapCodec(
        instance -> isSecureSeedEnabled
            ? instance.group(
                Codec.LONG.fieldOf("seed").stable().forGetter(WorldOptions::seed),
                Codec.STRING.fieldOf("feature_seed").orElse(gson.toJson(su.plo.matter.Globals.createRandomWorldSeed())).stable().forGetter(WorldOptions::featureSeedSerialize),
                Codec.BOOL.fieldOf("generate_features").orElse(true).stable().forGetter(WorldOptions::generateStructures),
                Codec.BOOL.fieldOf("bonus_chest").orElse(false).stable().forGetter(WorldOptions::generateBonusChest),
                Codec.STRING.lenientOptionalFieldOf("legacy_custom_options").stable().forGetter(worldOptions -> worldOptions.legacyCustomOptions)
            )
            .apply(instance, instance.stable(WorldOptions::new))
            : instance.group(
                Codec.LONG.fieldOf("seed").stable().forGetter(WorldOptions::seed),
                Codec.BOOL.fieldOf("generate_features").orElse(true).stable().forGetter(WorldOptions::generateStructures),
                Codec.BOOL.fieldOf("bonus_chest").orElse(false).stable().forGetter(WorldOptions::generateBonusChest),
                Codec.STRING.lenientOptionalFieldOf("legacy_custom_options").stable().forGetter(worldOptions -> worldOptions.legacyCustomOptions)
            )
            .apply(instance, instance.stable(WorldOptions::new))
    );
    // Leaf end - Matter - Secure Seed
    // Leaf start - Matter - Secure Seed
    public static final WorldOptions DEMO_OPTIONS = isSecureSeedEnabled
            ? new WorldOptions((long) "North Carolina".hashCode(), su.plo.matter.Globals.createRandomWorldSeed(), true, true)
            : new WorldOptions("North Carolina".hashCode(), true, true);
    // Leaf end - Matter - Secure Seed
    private final long seed;
    private long[] featureSeed = su.plo.matter.Globals.createRandomWorldSeed(); // Leaf - Matter - Secure Seed
    private final boolean generateStructures;
    private final boolean generateBonusChest;
    private final Optional<String> legacyCustomOptions;

    public WorldOptions(long seed, boolean generateStructures, boolean generateBonusChest) {
        this(seed, generateStructures, generateBonusChest, Optional.empty());
    }

    // Leaf start - Matter - Secure Seed
    public WorldOptions(long seed, long[] featureSeed, boolean generateStructures, boolean bonusChest) {
        this(seed, featureSeed, generateStructures, bonusChest, Optional.empty());
    }

    private WorldOptions(long seed, String featureSeedJson, boolean generateStructures, boolean bonusChest, Optional<String> legacyCustomOptions) {
        this(seed, gson.fromJson(featureSeedJson, long[].class), generateStructures, bonusChest, legacyCustomOptions);
    }
    // Leaf end - Matter - Secure Seed

    public static WorldOptions defaultWithRandomSeed() {
        // Leaf start - Matter - Secure Seed
        return isSecureSeedEnabled
                ? new WorldOptions(randomSeed(), su.plo.matter.Globals.createRandomWorldSeed(), true, false)
                : new WorldOptions(randomSeed(), true, false);
        // Leaf end - Matter - Secure Seed
    }

    public static WorldOptions testWorldWithRandomSeed() {
        return new WorldOptions(randomSeed(), false, false);
    }

    // Leaf start - Matter - Secure Seed
    private WorldOptions(long seed, long[] featureSeed, boolean generateStructures, boolean bonusChest, Optional<String> legacyCustomOptions) {
        this(seed, generateStructures, bonusChest, legacyCustomOptions);
        this.featureSeed = featureSeed;
    }
    // Leaf end - Matter - Secure Seed

    private WorldOptions(long seed, boolean generateStructures, boolean generateBonusChest, Optional<String> legacyCustomOptions) {
        this.seed = seed;
        this.generateStructures = generateStructures;
        this.generateBonusChest = generateBonusChest;
        this.legacyCustomOptions = legacyCustomOptions;
    }

    public long seed() {
        return this.seed;
    }

    // Leaf start - Matter - Secure Seed
    public long[] featureSeed() {
        return this.featureSeed;
    }

    private String featureSeedSerialize() {
        return gson.toJson(this.featureSeed);
    }
    // Leaf end - Matter - Secure Seed

    public boolean generateStructures() {
        return this.generateStructures;
    }

    public boolean generateBonusChest() {
        return this.generateBonusChest;
    }

    public boolean isOldCustomizedWorld() {
        return this.legacyCustomOptions.isPresent();
    }

    // Leaf start - Matter -  Secure Seed
    public WorldOptions withBonusChest(boolean generateBonusChest) {
        return isSecureSeedEnabled
                ? new WorldOptions(this.seed, this.featureSeed, this.generateStructures, generateBonusChest, this.legacyCustomOptions)
                : new WorldOptions(this.seed, this.generateStructures, generateBonusChest, this.legacyCustomOptions);
    }

    public WorldOptions withStructures(boolean generateStructures) {
        return isSecureSeedEnabled
                ? new WorldOptions(this.seed, this.featureSeed, generateStructures, this.generateBonusChest, this.legacyCustomOptions)
                : new WorldOptions(this.seed, generateStructures, this.generateBonusChest, this.legacyCustomOptions);
    }

    public WorldOptions withSeed(OptionalLong seed) {
        return isSecureSeedEnabled
                ? new WorldOptions(seed.orElse(randomSeed()), su.plo.matter.Globals.createRandomWorldSeed(), this.generateStructures, this.generateBonusChest, this.legacyCustomOptions)
                : new WorldOptions(seed.orElse(randomSeed()), this.generateStructures, this.generateBonusChest, this.legacyCustomOptions);
    }
    // Leaf end - Matter - Secure Seed

    public static OptionalLong parseSeed(String seed) {
        seed = seed.trim();
        if (StringUtils.isEmpty(seed)) {
            return OptionalLong.empty();
        } else {
            try {
                return OptionalLong.of(Long.parseLong(seed));
            } catch (NumberFormatException var2) {
                return OptionalLong.of(seed.hashCode());
            }
        }
    }

    public static long randomSeed() {
        return RandomSource.create().nextLong();
    }
}
