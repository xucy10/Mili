package net.minecraft.world.entity.boss.enderdragon.phases;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public interface DragonPhaseInstance {
    boolean isSitting();

    void doClientTick();

    void doServerTick(ServerLevel level);

    void onCrystalDestroyed(EndCrystal crystal, BlockPos pos, DamageSource damageSource, @Nullable Player player);

    void begin();

    void end();

    float getFlySpeed();

    float getTurnSpeed();

    EnderDragonPhase<? extends DragonPhaseInstance> getPhase();

    @Nullable Vec3 getFlyTargetLocation();

    float onHurt(DamageSource damageSource, float amount);
}
