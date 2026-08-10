package net.minecraft.util.datafix.fixes;

import com.google.common.collect.Sets;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Optional;
import java.util.Set;

public class EntityHealthFix extends DataFix {
    private static final Set<String> ENTITIES = Sets.newHashSet(
        "ArmorStand",
        "Bat",
        "Blaze",
        "CaveSpider",
        "Chicken",
        "Cow",
        "Creeper",
        "EnderDragon",
        "Enderman",
        "Endermite",
        "EntityHorse",
        "Ghast",
        "Giant",
        "Guardian",
        "LavaSlime",
        "MushroomCow",
        "Ozelot",
        "Pig",
        "PigZombie",
        "Rabbit",
        "Sheep",
        "Shulker",
        "Silverfish",
        "Skeleton",
        "Slime",
        "SnowMan",
        "Spider",
        "Squid",
        "Villager",
        "VillagerGolem",
        "Witch",
        "WitherBoss",
        "Wolf",
        "Zombie"
    );

    public EntityHealthFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    public Dynamic<?> fixTag(Dynamic<?> tag) {
        Optional<Number> optional = tag.get("HealF").asNumber().result();
        Optional<Number> optional1 = tag.get("Health").asNumber().result();
        float f;
        if (optional.isPresent()) {
            f = optional.get().floatValue();
            tag = tag.remove("HealF");
        } else {
            if (!optional1.isPresent()) {
                return tag;
            }

            f = optional1.get().floatValue();
        }

        return tag.set("Health", tag.createFloat(f));
    }

    @Override
    public TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped(
            "EntityHealthFix", this.getInputSchema().getType(References.ENTITY), typed -> typed.update(DSL.remainderFinder(), this::fixTag)
        );
    }
}
