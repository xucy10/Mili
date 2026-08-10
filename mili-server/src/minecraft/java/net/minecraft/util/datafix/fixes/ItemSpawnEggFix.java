package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.util.datafix.ExtraDataFixUtils;
import net.minecraft.util.datafix.schemas.NamespacedSchema;
import org.jspecify.annotations.Nullable;

public class ItemSpawnEggFix extends DataFix {
    public static final @Nullable String[] ID_TO_ENTITY = DataFixUtils.make(new String[256], array -> {
        array[1] = "Item";
        array[2] = "XPOrb";
        array[7] = "ThrownEgg";
        array[8] = "LeashKnot";
        array[9] = "Painting";
        array[10] = "Arrow";
        array[11] = "Snowball";
        array[12] = "Fireball";
        array[13] = "SmallFireball";
        array[14] = "ThrownEnderpearl";
        array[15] = "EyeOfEnderSignal";
        array[16] = "ThrownPotion";
        array[17] = "ThrownExpBottle";
        array[18] = "ItemFrame";
        array[19] = "WitherSkull";
        array[20] = "PrimedTnt";
        array[21] = "FallingSand";
        array[22] = "FireworksRocketEntity";
        array[23] = "TippedArrow";
        array[24] = "SpectralArrow";
        array[25] = "ShulkerBullet";
        array[26] = "DragonFireball";
        array[30] = "ArmorStand";
        array[41] = "Boat";
        array[42] = "MinecartRideable";
        array[43] = "MinecartChest";
        array[44] = "MinecartFurnace";
        array[45] = "MinecartTNT";
        array[46] = "MinecartHopper";
        array[47] = "MinecartSpawner";
        array[40] = "MinecartCommandBlock";
        array[50] = "Creeper";
        array[51] = "Skeleton";
        array[52] = "Spider";
        array[53] = "Giant";
        array[54] = "Zombie";
        array[55] = "Slime";
        array[56] = "Ghast";
        array[57] = "PigZombie";
        array[58] = "Enderman";
        array[59] = "CaveSpider";
        array[60] = "Silverfish";
        array[61] = "Blaze";
        array[62] = "LavaSlime";
        array[63] = "EnderDragon";
        array[64] = "WitherBoss";
        array[65] = "Bat";
        array[66] = "Witch";
        array[67] = "Endermite";
        array[68] = "Guardian";
        array[69] = "Shulker";
        array[90] = "Pig";
        array[91] = "Sheep";
        array[92] = "Cow";
        array[93] = "Chicken";
        array[94] = "Squid";
        array[95] = "Wolf";
        array[96] = "MushroomCow";
        array[97] = "SnowMan";
        array[98] = "Ozelot";
        array[99] = "VillagerGolem";
        array[100] = "EntityHorse";
        array[101] = "Rabbit";
        array[120] = "Villager";
        array[200] = "EnderCrystal";
    });

    public ItemSpawnEggFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    @Override
    public TypeRewriteRule makeRule() {
        Schema inputSchema = this.getInputSchema();
        Type<?> type = inputSchema.getType(References.ITEM_STACK);
        OpticFinder<Pair<String, String>> opticFinder = DSL.fieldFinder("id", DSL.named(References.ITEM_NAME.typeName(), NamespacedSchema.namespacedString()));
        OpticFinder<String> opticFinder1 = DSL.fieldFinder("id", DSL.string());
        OpticFinder<?> opticFinder2 = type.findField("tag");
        OpticFinder<?> opticFinder3 = opticFinder2.type().findField("EntityTag");
        OpticFinder<?> opticFinder4 = DSL.typeFinder(inputSchema.getTypeRaw(References.ENTITY));
        return this.fixTypeEverywhereTyped(
            "ItemSpawnEggFix",
            type,
            typed -> {
                Optional<Pair<String, String>> optional = typed.getOptional(opticFinder);
                if (optional.isPresent() && Objects.equals(optional.get().getSecond(), "minecraft:spawn_egg")) {
                    Dynamic<?> dynamic = typed.get(DSL.remainderFinder());
                    short _short = dynamic.get("Damage").asShort((short)0);
                    Optional<? extends Typed<?>> optionalTyped = typed.getOptionalTyped(opticFinder2);
                    Optional<? extends Typed<?>> optional1 = optionalTyped.flatMap(typed3 -> typed3.getOptionalTyped(opticFinder3));
                    Optional<? extends Typed<?>> optional2 = optional1.flatMap(typed3 -> typed3.getOptionalTyped(opticFinder4));
                    Optional<String> optional3 = optional2.flatMap(typed3 -> typed3.getOptional(opticFinder1));
                    Typed<?> typed1 = typed;
                    String string = ID_TO_ENTITY[_short & 255];
                    if (string != null && (optional3.isEmpty() || !Objects.equals(optional3.get(), string))) {
                        Typed<?> typed2 = typed.getOrCreateTyped(opticFinder2);
                        Dynamic<?> dynamic1 = DataFixUtils.orElse(
                            typed2.getOptionalTyped(opticFinder3).map(typed3 -> typed3.write().getOrThrow()), dynamic.emptyMap()
                        );
                        dynamic1 = dynamic1.set("id", dynamic1.createString(string));
                        typed1 = typed.set(opticFinder2, ExtraDataFixUtils.readAndSet(typed2, opticFinder3, dynamic1));
                    }

                    if (_short != 0) {
                        dynamic = dynamic.set("Damage", dynamic.createShort((short)0));
                        typed1 = typed1.set(DSL.remainderFinder(), dynamic);
                    }

                    return typed1;
                } else {
                    return typed;
                }
            }
        );
    }
}
