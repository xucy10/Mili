package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.DSL.TypeReference;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public class MobEffectIdFix extends DataFix {
    private static final Int2ObjectMap<String> ID_MAP = Util.make(new Int2ObjectOpenHashMap<>(), map -> {
        map.put(1, "minecraft:speed");
        map.put(2, "minecraft:slowness");
        map.put(3, "minecraft:haste");
        map.put(4, "minecraft:mining_fatigue");
        map.put(5, "minecraft:strength");
        map.put(6, "minecraft:instant_health");
        map.put(7, "minecraft:instant_damage");
        map.put(8, "minecraft:jump_boost");
        map.put(9, "minecraft:nausea");
        map.put(10, "minecraft:regeneration");
        map.put(11, "minecraft:resistance");
        map.put(12, "minecraft:fire_resistance");
        map.put(13, "minecraft:water_breathing");
        map.put(14, "minecraft:invisibility");
        map.put(15, "minecraft:blindness");
        map.put(16, "minecraft:night_vision");
        map.put(17, "minecraft:hunger");
        map.put(18, "minecraft:weakness");
        map.put(19, "minecraft:poison");
        map.put(20, "minecraft:wither");
        map.put(21, "minecraft:health_boost");
        map.put(22, "minecraft:absorption");
        map.put(23, "minecraft:saturation");
        map.put(24, "minecraft:glowing");
        map.put(25, "minecraft:levitation");
        map.put(26, "minecraft:luck");
        map.put(27, "minecraft:unluck");
        map.put(28, "minecraft:slow_falling");
        map.put(29, "minecraft:conduit_power");
        map.put(30, "minecraft:dolphins_grace");
        map.put(31, "minecraft:bad_omen");
        map.put(32, "minecraft:hero_of_the_village");
        map.put(33, "minecraft:darkness");
    });
    private static final Set<String> MOB_EFFECT_INSTANCE_CARRIER_ITEMS = Set.of(
        "minecraft:potion", "minecraft:splash_potion", "minecraft:lingering_potion", "minecraft:tipped_arrow"
    );

    public MobEffectIdFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    private static <T> Optional<Dynamic<T>> getAndConvertMobEffectId(Dynamic<T> dynamic, String key) {
        return dynamic.get(key).asNumber().result().map(number -> ID_MAP.get(number.intValue())).map(dynamic::createString);
    }

    private static <T> Dynamic<T> updateMobEffectIdField(Dynamic<T> oldDynamic, String oldName, Dynamic<T> newDynamic, String newName) {
        Optional<Dynamic<T>> andConvertMobEffectId = getAndConvertMobEffectId(oldDynamic, oldName);
        return newDynamic.replaceField(oldName, newName, andConvertMobEffectId);
    }

    private static <T> Dynamic<T> updateMobEffectIdField(Dynamic<T> dynamic, String oldName, String newName) {
        return updateMobEffectIdField(dynamic, oldName, dynamic, newName);
    }

    private static <T> Dynamic<T> updateMobEffectInstance(Dynamic<T> dynamic) {
        dynamic = updateMobEffectIdField(dynamic, "Id", "id");
        dynamic = dynamic.renameField("Ambient", "ambient");
        dynamic = dynamic.renameField("Amplifier", "amplifier");
        dynamic = dynamic.renameField("Duration", "duration");
        dynamic = dynamic.renameField("ShowParticles", "show_particles");
        dynamic = dynamic.renameField("ShowIcon", "show_icon");
        Optional<Dynamic<T>> optional = dynamic.get("HiddenEffect").result().map(MobEffectIdFix::updateMobEffectInstance);
        return dynamic.replaceField("HiddenEffect", "hidden_effect", optional);
    }

    private static <T> Dynamic<T> updateMobEffectInstanceList(Dynamic<T> tag, String oldName, String newName) {
        Optional<Dynamic<T>> optional = tag.get(oldName)
            .asStreamOpt()
            .result()
            .map(stream -> tag.createList(stream.map(MobEffectIdFix::updateMobEffectInstance)));
        return tag.replaceField(oldName, newName, optional);
    }

    private static <T> Dynamic<T> updateSuspiciousStewEntry(Dynamic<T> oldDynamic, Dynamic<T> newDynamic) {
        newDynamic = updateMobEffectIdField(oldDynamic, "EffectId", newDynamic, "id");
        Optional<Dynamic<T>> optional = oldDynamic.get("EffectDuration").result();
        return newDynamic.replaceField("EffectDuration", "duration", optional);
    }

    private static <T> Dynamic<T> updateSuspiciousStewEntry(Dynamic<T> suspiciousStewEntry) {
        return updateSuspiciousStewEntry(suspiciousStewEntry, suspiciousStewEntry);
    }

    private Typed<?> updateNamedChoice(Typed<?> typed, TypeReference reference, String id, Function<Dynamic<?>, Dynamic<?>> fixer) {
        Type<?> choiceType = this.getInputSchema().getChoiceType(reference, id);
        Type<?> choiceType1 = this.getOutputSchema().getChoiceType(reference, id);
        return typed.updateTyped(DSL.namedChoice(id, choiceType), choiceType1, typed1 -> typed1.update(DSL.remainderFinder(), fixer));
    }

    private TypeRewriteRule blockEntityFixer() {
        Type<?> type = this.getInputSchema().getType(References.BLOCK_ENTITY);
        return this.fixTypeEverywhereTyped(
            "BlockEntityMobEffectIdFix", type, typed -> this.updateNamedChoice(typed, References.BLOCK_ENTITY, "minecraft:beacon", dynamic -> {
                dynamic = updateMobEffectIdField(dynamic, "Primary", "primary_effect");
                return updateMobEffectIdField(dynamic, "Secondary", "secondary_effect");
            })
        );
    }

    private static <T> Dynamic<T> fixMooshroomTag(Dynamic<T> mooshroomTag) {
        Dynamic<T> dynamic = mooshroomTag.emptyMap();
        Dynamic<T> dynamic1 = updateSuspiciousStewEntry(mooshroomTag, dynamic);
        if (!dynamic1.equals(dynamic)) {
            mooshroomTag = mooshroomTag.set("stew_effects", mooshroomTag.createList(Stream.of(dynamic1)));
        }

        return mooshroomTag.remove("EffectId").remove("EffectDuration");
    }

    private static <T> Dynamic<T> fixArrowTag(Dynamic<T> arrowTag) {
        return updateMobEffectInstanceList(arrowTag, "CustomPotionEffects", "custom_potion_effects");
    }

    private static <T> Dynamic<T> fixAreaEffectCloudTag(Dynamic<T> areaEffectCloudTag) {
        return updateMobEffectInstanceList(areaEffectCloudTag, "Effects", "effects");
    }

    private static Dynamic<?> updateLivingEntityTag(Dynamic<?> livingEntityTag) {
        return updateMobEffectInstanceList(livingEntityTag, "ActiveEffects", "active_effects");
    }

    private TypeRewriteRule entityFixer() {
        Type<?> type = this.getInputSchema().getType(References.ENTITY);
        return this.fixTypeEverywhereTyped("EntityMobEffectIdFix", type, typed -> {
            typed = this.updateNamedChoice(typed, References.ENTITY, "minecraft:mooshroom", MobEffectIdFix::fixMooshroomTag);
            typed = this.updateNamedChoice(typed, References.ENTITY, "minecraft:arrow", MobEffectIdFix::fixArrowTag);
            typed = this.updateNamedChoice(typed, References.ENTITY, "minecraft:area_effect_cloud", MobEffectIdFix::fixAreaEffectCloudTag);
            return typed.update(DSL.remainderFinder(), MobEffectIdFix::updateLivingEntityTag);
        });
    }

    private TypeRewriteRule playerFixer() {
        Type<?> type = this.getInputSchema().getType(References.PLAYER);
        return this.fixTypeEverywhereTyped("PlayerMobEffectIdFix", type, typed -> typed.update(DSL.remainderFinder(), MobEffectIdFix::updateLivingEntityTag));
    }

    private static <T> Dynamic<T> fixSuspiciousStewTag(Dynamic<T> suspiciousStewTag) {
        Optional<Dynamic<T>> optional = suspiciousStewTag.get("Effects")
            .asStreamOpt()
            .result()
            .map(stream -> suspiciousStewTag.createList(stream.map(MobEffectIdFix::updateSuspiciousStewEntry)));
        return suspiciousStewTag.replaceField("Effects", "effects", optional);
    }

    private TypeRewriteRule itemStackFixer() {
        OpticFinder<Pair<String, String>> opticFinder = DSL.fieldFinder("id", DSL.named(References.ITEM_NAME.typeName(), NamespacedSchema.namespacedString()));
        Type<?> type = this.getInputSchema().getType(References.ITEM_STACK);
        OpticFinder<?> opticFinder1 = type.findField("tag");
        return this.fixTypeEverywhereTyped(
            "ItemStackMobEffectIdFix",
            type,
            typed -> {
                Optional<Pair<String, String>> optional = typed.getOptional(opticFinder);
                if (optional.isPresent()) {
                    String string = optional.get().getSecond();
                    if (string.equals("minecraft:suspicious_stew")) {
                        return typed.updateTyped(opticFinder1, typed1 -> typed1.update(DSL.remainderFinder(), MobEffectIdFix::fixSuspiciousStewTag));
                    }

                    if (MOB_EFFECT_INSTANCE_CARRIER_ITEMS.contains(string)) {
                        return typed.updateTyped(
                            opticFinder1,
                            typed1 -> typed1.update(
                                DSL.remainderFinder(), dynamic -> updateMobEffectInstanceList(dynamic, "CustomPotionEffects", "custom_potion_effects")
                            )
                        );
                    }
                }

                return typed;
            }
        );
    }

    @Override
    protected TypeRewriteRule makeRule() {
        return TypeRewriteRule.seq(this.blockEntityFixer(), this.entityFixer(), this.playerFixer(), this.itemStackFixer());
    }
}
