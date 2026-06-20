package net.minecraft.server;

import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundTickingStatePacket;
import net.minecraft.network.protocol.game.ClientboundTickingStepPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.TimeUtil;
import net.minecraft.world.TickRateManager;

public class ServerTickRateManager extends TickRateManager {
    private long remainingSprintTicks = 0L;
    private long sprintTickStartTime = 0L;
    private long sprintTimeSpend = 0L;
    private long scheduledCurrentSprintTicks = 0L;
    private boolean previousIsFrozen = false;
    private final MinecraftServer server;
    private boolean silent; // Paper - silence feedback when API requests sprint

    public ServerTickRateManager(MinecraftServer server) {
        this.server = server;
    }

    public boolean isSprinting() {
        return this.scheduledCurrentSprintTicks > 0L;
    }

    @Override
    public void setFrozen(boolean frozen) {
        super.setFrozen(frozen);
        this.updateStateToClients();
    }

    private void updateStateToClients() {
        this.server.getPlayerList().broadcastAll(ClientboundTickingStatePacket.from(this));
    }

    private void updateStepTicks() {
        this.server.getPlayerList().broadcastAll(ClientboundTickingStepPacket.from(this));
    }

    public boolean stepGameIfPaused(int ticks) {
        if (!this.isFrozen()) {
            return false;
        } else {
            this.frozenTicksToRun = ticks;
            this.updateStepTicks();
            return true;
        }
    }

    public boolean stopStepping() {
        if (this.frozenTicksToRun > 0) {
            this.frozenTicksToRun = 0;
            this.updateStepTicks();
            return true;
        } else {
            return false;
        }
    }

    public boolean stopSprinting() {
        if (this.remainingSprintTicks > 0L) {
            this.finishTickSprint();
            return true;
        } else {
            return false;
        }
    }

    public boolean requestGameToSprint(int sprintTime) {
        // Paper start - silence feedback when API requests sprint
        return requestGameToSprint(sprintTime, false);
    }

    public boolean requestGameToSprint(int sprintTime, boolean silent) {
        if (!isSprinting()) this.silent = silent;
        // Paper end - silence feedback when API requests sprint
        boolean flag = this.remainingSprintTicks > 0L;
        this.sprintTimeSpend = 0L;
        this.scheduledCurrentSprintTicks = sprintTime;
        this.remainingSprintTicks = sprintTime;
        this.previousIsFrozen = this.isFrozen();
        this.setFrozen(false);
        return flag;
    }

    private void finishTickSprint() {
        long l = this.scheduledCurrentSprintTicks - this.remainingSprintTicks;
        double d = Math.max(1.0, (double)this.sprintTimeSpend) / TimeUtil.NANOSECONDS_PER_MILLISECOND;
        int i = (int)(TimeUtil.MILLISECONDS_PER_SECOND * l / d);
        String string = String.format(Locale.ROOT, "%.2f", l == 0L ? this.millisecondsPerTick() : d / l);
        this.scheduledCurrentSprintTicks = 0L;
        this.sprintTimeSpend = 0L;
        // Paper start - silence feedback when API requests sprint
        if (!this.silent) this.server.createCommandSourceStack().sendSuccess(() -> Component.translatable("commands.tick.sprint.report", i, string), true);
        this.silent = false;
        // Paper end - silence feedback when API requests sprint
        this.remainingSprintTicks = 0L;
        this.setFrozen(this.previousIsFrozen);
        this.server.onTickRateChanged();
    }

    public boolean checkShouldSprintThisTick() {
        if (!this.runGameElements) {
            return false;
        } else if (this.remainingSprintTicks > 0L) {
            this.sprintTickStartTime = System.nanoTime();
            this.remainingSprintTicks--;
            return true;
        } else {
            this.finishTickSprint();
            return false;
        }
    }

    public void endTickWork() {
        this.sprintTimeSpend = this.sprintTimeSpend + (System.nanoTime() - this.sprintTickStartTime);
    }

    @Override
    public void setTickRate(float tickRate) {
        super.setTickRate(tickRate);
        this.server.onTickRateChanged();
        this.updateStateToClients();
    }

    public void updateJoiningPlayer(ServerPlayer player) {
        player.connection.send(ClientboundTickingStatePacket.from(this));
        player.connection.send(ClientboundTickingStepPacket.from(this));
    }
}
