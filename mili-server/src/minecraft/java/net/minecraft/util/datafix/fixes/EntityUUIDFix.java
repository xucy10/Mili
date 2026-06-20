package net.minecraft.util.datafix.fixes;

import com.google.common.collect.Sets;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;

public class EntityUUIDFix extends AbstractUUIDFix {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> ABSTRACT_HORSES = Sets.newHashSet();
    private static final Set<String> TAMEABLE_ANIMALS = Sets.newHashSet();
    private static final Set<String> ANIMALS = Sets.newHashSet();
    private static final Set<String> MOBS = Sets.newHashSet();
    private static final Set<String> LIVING_ENTITIES = Sets.newHashSet();
    private static final Set<String> PROJECTILES = Sets.newHashSet();

    public EntityUUIDFix(Schema outputSchema) {
        super(outputSchema, References.ENTITY);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped("EntityUUIDFixes", this.getInputSchema().getType(this.typeReference), typed -> {
            typed = typed.update(DSL.remainderFinder(), EntityUUIDFix::updateEntityUUID);

            for (String string : ABSTRACT_HORSES) {
                typed = this.updateNamedChoice(typed, string, EntityUUIDFix::updateAnimalOwner);
            }

            for (String string : TAMEABLE_ANIMALS) {
                typed = this.updateNamedChoice(typed, string, EntityUUIDFix::updateAnimalOwner);
            }

            for (String string : ANIMALS) {
                typed = this.updateNamedChoice(typed, string, EntityUUIDFix::updateAnimal);
            }

            for (String string : MOBS) {
                typed = this.updateNamedChoice(typed, string, EntityUUIDFix::updateMob);
            }

            for (String string : LIVING_ENTITIES) {
                typed = this.updateNamedChoice(typed, string, EntityUUIDFix::updateLivingEntity);
            }

            for (String string : PROJECTILES) {
                typed = this.updateNamedChoice(typed, string, EntityUUIDFix::updateProjectile);
            }

            typed = this.updateNamedChoice(typed, "minecraft:bee", EntityUUIDFix::updateHurtBy);
            typed = this.updateNamedChoice(typed, "minecraft:zombified_piglin", EntityUUIDFix::updateHurtBy);
            typed = this.updateNamedChoice(typed, "minecraft:fox", EntityUUIDFix::updateFox);
            typed = this.updateNamedChoice(typed, "minecraft:item", EntityUUIDFix::updateItem);
            typed = this.updateNamedChoice(typed, "minecraft:shulker_bullet", EntityUUIDFix::updateShulkerBullet);
            typed = this.updateNamedChoice(typed, "minecraft:area_effect_cloud", EntityUUIDFix::updateAreaEffectCloud);
            typed = this.updateNamedChoice(typed, "minecraft:zombie_villager", EntityUUIDFix::updateZombieVillager);
            typed = this.updateNamedChoice(typed, "minecraft:evoker_fangs", EntityUUIDFix::updateEvokerFangs);
            return this.updateNamedChoice(typed, "minecraft:piglin", EntityUUIDFix::updatePiglin);
        });
    }

    private static Dynamic<?> updatePiglin(Dynamic<?> piglinTag) {
        return piglinTag.update(
            "Brain",
            dynamic -> dynamic.update(
                "memories", dynamic1 -> dynamic1.update("minecraft:angry_at", dynamic2 -> replaceUUIDString(dynamic2, "value", "value").orElseGet(() -> {
                    LOGGER.warn("angry_at has no value.");
                    return dynamic2;
                }))
            )
        );
    }

    private static Dynamic<?> updateEvokerFangs(Dynamic<?> evokerFangsTag) {
        return replaceUUIDLeastMost(evokerFangsTag, "OwnerUUID", "Owner").orElse(evokerFangsTag);
    }

    private static Dynamic<?> updateZombieVillager(Dynamic<?> zombieVillagerTag) {
        return replaceUUIDLeastMost(zombieVillagerTag, "ConversionPlayer", "ConversionPlayer").orElse(zombieVillagerTag);
    }

    private static Dynamic<?> updateAreaEffectCloud(Dynamic<?> areaEffectCloudTag) {
        return replaceUUIDLeastMost(areaEffectCloudTag, "OwnerUUID", "Owner").orElse(areaEffectCloudTag);
    }

    private static Dynamic<?> updateShulkerBullet(Dynamic<?> shulkerBulletTag) {
        shulkerBulletTag = replaceUUIDMLTag(shulkerBulletTag, "Owner", "Owner").orElse(shulkerBulletTag);
        return replaceUUIDMLTag(shulkerBulletTag, "Target", "Target").orElse(shulkerBulletTag);
    }

    private static Dynamic<?> updateItem(Dynamic<?> itemTag) {
        itemTag = replaceUUIDMLTag(itemTag, "Owner", "Owner").orElse(itemTag);
        return replaceUUIDMLTag(itemTag, "Thrower", "Thrower").orElse(itemTag);
    }

    private static Dynamic<?> updateFox(Dynamic<?> foxTag) {
        Optional<Dynamic<?>> optional = foxTag.get("TrustedUUIDs")
            .result()
            .map(dynamic -> foxTag.createList(dynamic.asStream().map(dynamic1 -> createUUIDFromML((Dynamic<?>)dynamic1).orElseGet(() -> {
                LOGGER.warn("Trusted contained invalid data.");
                return dynamic1;
            }))));
        return DataFixUtils.orElse(optional.map(dynamic -> foxTag.remove("TrustedUUIDs").set("Trusted", (Dynamic<?>)dynamic)), foxTag);
    }

    private static Dynamic<?> updateHurtBy(Dynamic<?> tag) {
        return replaceUUIDString(tag, "HurtBy", "HurtBy").orElse(tag);
    }

    private static Dynamic<?> updateAnimalOwner(Dynamic<?> ownerTag) {
        Dynamic<?> dynamic = updateAnimal(ownerTag);
        return replaceUUIDString(dynamic, "OwnerUUID", "Owner").orElse(dynamic);
    }

    private static Dynamic<?> updateAnimal(Dynamic<?> animalTag) {
        Dynamic<?> dynamic = updateMob(animalTag);
        return replaceUUIDLeastMost(dynamic, "LoveCause", "LoveCause").orElse(dynamic);
    }

    private static Dynamic<?> updateMob(Dynamic<?> mobTag) {
        return updateLivingEntity(mobTag).update("Leash", dynamic -> replaceUUIDLeastMost(dynamic, "UUID", "UUID").orElse(dynamic));
    }

    public static Dynamic<?> updateLivingEntity(Dynamic<?> livingEntityTag) {
        return livingEntityTag.update(
            "Attributes",
            dynamic -> livingEntityTag.createList(
                dynamic.asStream()
                    .map(
                        dynamic1 -> dynamic1.update(
                            "Modifiers",
                            dynamic2 -> dynamic1.createList(
                                dynamic2.asStream().map(dynamic3 -> replaceUUIDLeastMost((Dynamic<?>)dynamic3, "UUID", "UUID").orElse((Dynamic<?>)dynamic3))
                            )
                        )
                    )
            )
        );
    }

    private static Dynamic<?> updateProjectile(Dynamic<?> projectileTag) {
        return DataFixUtils.orElse(
            projectileTag.get("OwnerUUID").result().map(dynamic -> projectileTag.remove("OwnerUUID").set("Owner", (Dynamic<?>)dynamic)), projectileTag
        );
    }

    public static Dynamic<?> updateEntityUUID(Dynamic<?> entityTag) {
        return replaceUUIDLeastMost(entityTag, "UUID", "UUID").orElse(entityTag);
    }

    static {
        ABSTRACT_HORSES.add("minecraft:donkey");
        ABSTRACT_HORSES.add("minecraft:horse");
        ABSTRACT_HORSES.add("minecraft:llama");
        ABSTRACT_HORSES.add("minecraft:mule");
        ABSTRACT_HORSES.add("minecraft:skeleton_horse");
        ABSTRACT_HORSES.add("minecraft:trader_llama");
        ABSTRACT_HORSES.add("minecraft:zombie_horse");
        TAMEABLE_ANIMALS.add("minecraft:cat");
        TAMEABLE_ANIMALS.add("minecraft:parrot");
        TAMEABLE_ANIMALS.add("minecraft:wolf");
        ANIMALS.add("minecraft:bee");
        ANIMALS.add("minecraft:chicken");
        ANIMALS.add("minecraft:cow");
        ANIMALS.add("minecraft:fox");
        ANIMALS.add("minecraft:mooshroom");
        ANIMALS.add("minecraft:ocelot");
        ANIMALS.add("minecraft:panda");
        ANIMALS.add("minecraft:pig");
        ANIMALS.add("minecraft:polar_bear");
        ANIMALS.add("minecraft:rabbit");
        ANIMALS.add("minecraft:sheep");
        ANIMALS.add("minecraft:turtle");
        ANIMALS.add("minecraft:hoglin");
        MOBS.add("minecraft:bat");
        MOBS.add("minecraft:blaze");
        MOBS.add("minecraft:cave_spider");
        MOBS.add("minecraft:cod");
        MOBS.add("minecraft:creeper");
        MOBS.add("minecraft:dolphin");
        MOBS.add("minecraft:drowned");
        MOBS.add("minecraft:elder_guardian");
        MOBS.add("minecraft:ender_dragon");
        MOBS.add("minecraft:enderman");
        MOBS.add("minecraft:endermite");
        MOBS.add("minecraft:evoker");
        MOBS.add("minecraft:ghast");
        MOBS.add("minecraft:giant");
        MOBS.add("minecraft:guardian");
        MOBS.add("minecraft:husk");
        MOBS.add("minecraft:illusioner");
        MOBS.add("minecraft:magma_cube");
        MOBS.add("minecraft:pufferfish");
        MOBS.add("minecraft:zombified_piglin");
        MOBS.add("minecraft:salmon");
        MOBS.add("minecraft:shulker");
        MOBS.add("minecraft:silverfish");
        MOBS.add("minecraft:skeleton");
        MOBS.add("minecraft:slime");
        MOBS.add("minecraft:snow_golem");
        MOBS.add("minecraft:spider");
        MOBS.add("minecraft:squid");
        MOBS.add("minecraft:stray");
        MOBS.add("minecraft:tropical_fish");
        MOBS.add("minecraft:vex");
        MOBS.add("minecraft:villager");
        MOBS.add("minecraft:iron_golem");
        MOBS.add("minecraft:vindicator");
        MOBS.add("minecraft:pillager");
        MOBS.add("minecraft:wandering_trader");
        MOBS.add("minecraft:witch");
        MOBS.add("minecraft:wither");
        MOBS.add("minecraft:wither_skeleton");
        MOBS.add("minecraft:zombie");
        MOBS.add("minecraft:zombie_villager");
        MOBS.add("minecraft:phantom");
        MOBS.add("minecraft:ravager");
        MOBS.add("minecraft:piglin");
        LIVING_ENTITIES.add("minecraft:armor_stand");
        PROJECTILES.add("minecraft:arrow");
        PROJECTILES.add("minecraft:dragon_fireball");
        PROJECTILES.add("minecraft:firework_rocket");
        PROJECTILES.add("minecraft:fireball");
        PROJECTILES.add("minecraft:llama_spit");
        PROJECTILES.add("minecraft:small_fireball");
        PROJECTILES.add("minecraft:snowball");
        PROJECTILES.add("minecraft:spectral_arrow");
        PROJECTILES.add("minecraft:egg");
        PROJECTILES.add("minecraft:ender_pearl");
        PROJECTILES.add("minecraft:experience_bottle");
        PROJECTILES.add("minecraft:potion");
        PROJECTILES.add("minecraft:trident");
        PROJECTILES.add("minecraft:wither_skull");
    }
}
