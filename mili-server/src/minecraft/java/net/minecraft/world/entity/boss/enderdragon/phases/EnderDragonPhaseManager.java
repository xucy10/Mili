package net.minecraft.world.entity.boss.enderdragon.phases;

import com.mojang.logging.LogUtils;
import java.util.Objects;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class EnderDragonPhaseManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final EnderDragon dragon;
    private final @Nullable DragonPhaseInstance[] phases = new DragonPhaseInstance[EnderDragonPhase.getCount()];
    private @Nullable DragonPhaseInstance currentPhase;

    public EnderDragonPhaseManager(EnderDragon dragon) {
        this.dragon = dragon;
        this.setPhase(EnderDragonPhase.HOVERING);
    }

    public void setPhase(EnderDragonPhase<?> phase) {
        if (this.currentPhase == null || phase != this.currentPhase.getPhase()) {
            if (this.currentPhase != null) {
                this.currentPhase.end();
            }

            // CraftBukkit start - Call EnderDragonChangePhaseEvent
            org.bukkit.event.entity.EnderDragonChangePhaseEvent event = new org.bukkit.event.entity.EnderDragonChangePhaseEvent(
                (org.bukkit.craftbukkit.entity.CraftEnderDragon) this.dragon.getBukkitEntity(),
                (this.currentPhase == null) ? null : org.bukkit.craftbukkit.entity.CraftEnderDragon.getBukkitPhase(this.currentPhase.getPhase()),
                org.bukkit.craftbukkit.entity.CraftEnderDragon.getBukkitPhase(phase)
            );
            if (!event.callEvent()) {
                return;
            }
            phase = org.bukkit.craftbukkit.entity.CraftEnderDragon.getMinecraftPhase(event.getNewPhase());
            // CraftBukkit end

            this.currentPhase = this.getPhase((EnderDragonPhase<DragonPhaseInstance>)phase);
            if (!this.dragon.level().isClientSide()) {
                this.dragon.getEntityData().set(EnderDragon.DATA_PHASE, phase.getId());
            }

            LOGGER.debug("Dragon is now in phase {} on the {}", phase, this.dragon.level().isClientSide() ? "client" : "server");
            this.currentPhase.begin();
        }
    }

    public DragonPhaseInstance getCurrentPhase() {
        return Objects.requireNonNull(this.currentPhase);
    }

    public <T extends DragonPhaseInstance> T getPhase(EnderDragonPhase<T> phase) {
        int id = phase.getId();
        DragonPhaseInstance dragonPhaseInstance = this.phases[id];
        if (dragonPhaseInstance == null) {
            dragonPhaseInstance = phase.createInstance(this.dragon);
            this.phases[id] = dragonPhaseInstance;
        }

        return (T)dragonPhaseInstance;
    }
}
