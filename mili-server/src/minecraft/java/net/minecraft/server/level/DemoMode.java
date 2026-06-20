package net.minecraft.server.level;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

public class DemoMode extends ServerPlayerGameMode {
    public static final int DEMO_DAYS = 5;
    public static final int TOTAL_PLAY_TICKS = 120500;
    private boolean displayedIntro;
    private boolean demoHasEnded;
    private int demoEndedReminder;
    private int gameModeTicks;

    public DemoMode(ServerPlayer player) {
        super(player);
    }

    @Override
    public void tick() {
        super.tick();
        this.gameModeTicks++;
        long gameTime = this.level.getGameTime();
        long l = gameTime / 24000L + 1L;
        if (!this.displayedIntro && this.gameModeTicks > 20) {
            this.displayedIntro = true;
            this.player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.DEMO_EVENT, ClientboundGameEventPacket.DEMO_PARAM_INTRO));
        }

        this.demoHasEnded = gameTime > 120500L;
        if (this.demoHasEnded) {
            this.demoEndedReminder++;
        }

        if (gameTime % 24000L == 500L) {
            if (l <= 6L) {
                if (l == 6L) {
                    this.player
                        .connection
                        .send(new ClientboundGameEventPacket(ClientboundGameEventPacket.DEMO_EVENT, ClientboundGameEventPacket.DEMO_PARAM_HINT_4));
                } else {
                    this.player.sendSystemMessage(Component.translatable("demo.day." + l));
                }
            }
        } else if (l == 1L) {
            if (gameTime == 100L) {
                this.player
                    .connection
                    .send(new ClientboundGameEventPacket(ClientboundGameEventPacket.DEMO_EVENT, ClientboundGameEventPacket.DEMO_PARAM_HINT_1));
            } else if (gameTime == 175L) {
                this.player
                    .connection
                    .send(new ClientboundGameEventPacket(ClientboundGameEventPacket.DEMO_EVENT, ClientboundGameEventPacket.DEMO_PARAM_HINT_2));
            } else if (gameTime == 250L) {
                this.player
                    .connection
                    .send(new ClientboundGameEventPacket(ClientboundGameEventPacket.DEMO_EVENT, ClientboundGameEventPacket.DEMO_PARAM_HINT_3));
            }
        } else if (l == 5L && gameTime % 24000L == 22000L) {
            this.player.sendSystemMessage(Component.translatable("demo.day.warning"));
        }
    }

    private void outputDemoReminder() {
        if (this.demoEndedReminder > 100) {
            this.player.sendSystemMessage(Component.translatable("demo.reminder"));
            this.demoEndedReminder = 0;
        }
    }

    @Override
    public void handleBlockBreakAction(BlockPos pos, ServerboundPlayerActionPacket.Action action, Direction face, int maxBuildHeight, int sequence) {
        if (this.demoHasEnded) {
            this.outputDemoReminder();
        } else {
            super.handleBlockBreakAction(pos, action, face, maxBuildHeight, sequence);
        }
    }

    @Override
    public InteractionResult useItem(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand) {
        if (this.demoHasEnded) {
            this.outputDemoReminder();
            return InteractionResult.PASS;
        } else {
            return super.useItem(player, level, stack, hand);
        }
    }

    @Override
    public InteractionResult useItemOn(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand, BlockHitResult hitResult) {
        if (this.demoHasEnded) {
            this.outputDemoReminder();
            return InteractionResult.PASS;
        } else {
            return super.useItemOn(player, level, stack, hand, hitResult);
        }
    }
}
