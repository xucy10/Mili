package net.minecraft.world.entity.ai.behavior;

import java.util.Optional;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

public class AssignProfessionFromJobSite {
    public static BehaviorControl<Villager> create() {
        return BehaviorBuilder.create(
            instance -> instance.group(instance.present(MemoryModuleType.POTENTIAL_JOB_SITE), instance.registered(MemoryModuleType.JOB_SITE))
                .apply(
                    instance,
                    (potentialJobSite, jobSite) -> (level, villager, gameTime) -> {
                        GlobalPos globalPos = instance.get(potentialJobSite);
                        if (!globalPos.pos().closerToCenterThan(villager.position(), 2.0) && !villager.assignProfessionWhenSpawned()) {
                            return false;
                        } else {
                            potentialJobSite.erase();
                            jobSite.set(globalPos);
                            level.broadcastEntityEvent(villager, EntityEvent.VILLAGER_HAPPY);
                            if (!villager.getVillagerData().profession().is(VillagerProfession.NONE)) {
                                return true;
                            } else {
                                MinecraftServer server = level.getServer();
                                Optional.ofNullable(server.getLevel(globalPos.dimension()))
                                    .flatMap(posLevel -> posLevel.getPoiManager().getType(globalPos.pos()))
                                    .flatMap(
                                        poi -> BuiltInRegistries.VILLAGER_PROFESSION
                                            .listElements()
                                            .filter(reference -> reference.value().heldJobSite().test((Holder<PoiType>)poi))
                                            .findFirst()
                                    )
                                    .ifPresent(reference -> {
                                        // CraftBukkit start - Fire VillagerCareerChangeEvent where Villager gets employed
                                        org.bukkit.event.entity.VillagerCareerChangeEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callVillagerCareerChangeEvent(villager, org.bukkit.craftbukkit.entity.CraftVillager.CraftProfession.minecraftHolderToBukkit(reference), org.bukkit.event.entity.VillagerCareerChangeEvent.ChangeReason.EMPLOYED);
                                        if (event.isCancelled()) {
                                            return;
                                        }

                                        villager.setVillagerData(villager.getVillagerData().withProfession(org.bukkit.craftbukkit.entity.CraftVillager.CraftProfession.bukkitToMinecraftHolder(event.getProfession())));
                                        // CraftBukkit end
                                        villager.refreshBrain(level);
                                    });
                                return true;
                            }
                        }
                    }
                )
        );
    }
}
