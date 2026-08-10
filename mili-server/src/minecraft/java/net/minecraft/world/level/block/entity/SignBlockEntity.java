package net.minecraft.world.level.block.entity;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.FilteredText;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class SignBlockEntity extends BlockEntity {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_TEXT_LINE_WIDTH = 90;
    private static final int TEXT_LINE_HEIGHT = 10;
    private static final boolean DEFAULT_IS_WAXED = false;
    public @Nullable UUID playerWhoMayEdit;
    private SignText frontText;
    private SignText backText;
    private boolean isWaxed = false;

    public SignBlockEntity(BlockPos pos, BlockState blockState) {
        this(BlockEntityType.SIGN, pos, blockState);
    }

    public SignBlockEntity(BlockEntityType type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
        this.frontText = this.createDefaultSignText();
        this.backText = this.createDefaultSignText();
    }

    protected SignText createDefaultSignText() {
        return new SignText();
    }

    public boolean isFacingFrontText(Player player) {
        // Paper start - More Sign Block API
        return this.isFacingFrontText(player.getX(), player.getZ());
    }
    public boolean isFacingFrontText(double x, double z) {
        // Paper end - More Sign Block API
        if (this.getBlockState().getBlock() instanceof SignBlock signBlock) {
            Vec3 signHitboxCenterPosition = signBlock.getSignHitboxCenterPosition(this.getBlockState());
            double d = x - (this.getBlockPos().getX() + signHitboxCenterPosition.x); // Paper - More Sign Block API
            double d1 = z - (this.getBlockPos().getZ() + signHitboxCenterPosition.z); // Paper - More Sign Block AP
            float yRotationDegrees = signBlock.getYRotationDegrees(this.getBlockState());
            float f = (float)(Mth.atan2(d1, d) * 180.0F / (float)Math.PI) - 90.0F;
            return Mth.degreesDifferenceAbs(yRotationDegrees, f) <= 90.0F;
        } else {
            return false;
        }
    }

    public SignText getText(boolean isFrontText) {
        return isFrontText ? this.frontText : this.backText;
    }

    public SignText getFrontText() {
        return this.frontText;
    }

    public SignText getBackText() {
        return this.backText;
    }

    public int getTextLineHeight() {
        return 10;
    }

    public int getMaxTextLineWidth() {
        return 90;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("front_text", SignText.DIRECT_CODEC, this.frontText);
        output.store("back_text", SignText.DIRECT_CODEC, this.backText);
        output.putBoolean("is_waxed", this.isWaxed);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.frontText = input.read("front_text", SignText.DIRECT_CODEC).map(this::loadLines).orElseGet(SignText::new);
        this.backText = input.read("back_text", SignText.DIRECT_CODEC).map(this::loadLines).orElseGet(SignText::new);
        this.isWaxed = input.getBooleanOr("is_waxed", false);
    }

    private SignText loadLines(SignText text) {
        for (int i = 0; i < 4; i++) {
            Component component = this.loadLine(text.getMessage(i, false));
            Component component1 = this.loadLine(text.getMessage(i, true));
            text = text.setMessage(i, component, component1);
        }

        return text;
    }

    private Component loadLine(Component lineText) {
        if (this.level instanceof ServerLevel serverLevel) {
            try {
                return ComponentUtils.updateForEntity(createCommandSourceStack(null, serverLevel, this.worldPosition), lineText, null, 0);
            } catch (CommandSyntaxException var4) {
            }
        }

        return lineText;
    }

    public void updateSignText(Player player, boolean isFrontText, List<FilteredText> filteredText) {
        if (!this.isWaxed() && player.getUUID().equals(this.getPlayerWhoMayEdit()) && this.level != null) {
            this.updateText(signText -> this.setMessages(player, filteredText, signText, isFrontText), isFrontText); // CraftBukkit
            this.setAllowedPlayerEditor(null);
            this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), Block.UPDATE_ALL);
        } else {
            LOGGER.warn("Player {} just tried to change non-editable sign", player.getPlainTextName());
            if (player.distanceToSqr(this.getBlockPos().getX(), this.getBlockPos().getY(), this.getBlockPos().getZ()) < Mth.square(32)) // Paper - Don't send far away sign update
            ((net.minecraft.server.level.ServerPlayer) player).connection.send(this.getUpdatePacket()); // CraftBukkit
        }
    }

    public boolean updateText(UnaryOperator<SignText> updater, boolean isFrontText) {
        SignText text = this.getText(isFrontText);
        return this.setText(updater.apply(text), isFrontText);
    }

    private SignText setMessages(Player player, List<FilteredText> filteredText, SignText text, boolean front) { // CraftBukkit
        SignText originalText = text; // CraftBukkit
        for (int i = 0; i < filteredText.size(); i++) {
            FilteredText filteredText1 = filteredText.get(i);
            Style style = text.getMessage(i, player.isTextFilteringEnabled()).getStyle();
            if (player.isTextFilteringEnabled()) {
                text = text.setMessage(i, Component.literal(net.minecraft.util.StringUtil.filterText(filteredText1.filteredOrEmpty())).setStyle(style)); // Paper - filter sign text to chat only
            } else {
                text = text.setMessage(
                    i, Component.literal(filteredText1.raw()).setStyle(style), Component.literal(net.minecraft.util.StringUtil.filterText(filteredText1.filteredOrEmpty())).setStyle(style) // Paper - filter sign text to chat only
                );
            }
        }

        // CraftBukkit start
        org.bukkit.entity.Player apiPlayer = ((net.minecraft.server.level.ServerPlayer) player).getBukkitEntity();
        List<net.kyori.adventure.text.Component> lines = new java.util.ArrayList<>(); // Paper - adventure

        for (int i = 0; i < filteredText.size(); ++i) {
            lines.add(io.papermc.paper.adventure.PaperAdventure.asAdventure(text.getMessage(i, player.isTextFilteringEnabled()))); // Paper - Adventure
        }

        org.bukkit.event.block.SignChangeEvent event = new org.bukkit.event.block.SignChangeEvent(org.bukkit.craftbukkit.block.CraftBlock.at(this.level, this.worldPosition), apiPlayer, new java.util.ArrayList<>(lines), (front) ? org.bukkit.block.sign.Side.FRONT : org.bukkit.block.sign.Side.BACK); // Paper - Adventure
        if (!event.callEvent()) {
            return originalText;
        }

        Component[] components = org.bukkit.craftbukkit.block.CraftSign.sanitizeLines(event.lines()); // Paper - Adventure
        for (int i = 0; i < components.length; i++) {
            if (!java.util.Objects.equals(lines.get(i), event.line(i))) { // Paper - Adventure
                text = text.setMessage(i, components[i]);
            }
        }
        // CraftBukkit end

        return text;
    }

    public boolean setText(SignText text, boolean isFrontText) {
        return isFrontText ? this.setFrontText(text) : this.setBackText(text);
    }

    private boolean setBackText(SignText text) {
        if (text != this.backText) {
            this.backText = text;
            this.markUpdated();
            return true;
        } else {
            return false;
        }
    }

    private boolean setFrontText(SignText text) {
        if (text != this.frontText) {
            this.frontText = text;
            this.markUpdated();
            return true;
        } else {
            return false;
        }
    }

    public boolean canExecuteClickCommands(boolean isFrontText, Player player) {
        return this.isWaxed() && this.getText(isFrontText).hasAnyClickCommands(player);
    }

    public boolean executeClickCommandsIfPresent(ServerLevel level, Player player, BlockPos pos, boolean isFrontText) {
        boolean flag = false;

        for (Component component : this.getText(isFrontText).getMessages(player.isTextFilteringEnabled())) {
            Style style = component.getStyle();
            switch (style.getClickEvent()) {
                case ClickEvent.RunCommand runCommand:
                    // Paper start - Fix commands from signs not firing command events
                    String command = runCommand.command().startsWith("/") ? runCommand.command() : "/" + runCommand.command();
                    if (org.spigotmc.SpigotConfig.logCommands)  {
                        LOGGER.info("{} issued server command: {}", player.getScoreboardName(), command);
                    }
                    io.papermc.paper.event.player.PlayerSignCommandPreprocessEvent event = new io.papermc.paper.event.player.PlayerSignCommandPreprocessEvent(
                        (org.bukkit.entity.Player) player.getBukkitEntity(),
                        command,
                        new org.bukkit.craftbukkit.util.LazyPlayerSet(level.getServer()),
                        (org.bukkit.block.Sign) org.bukkit.craftbukkit.block.CraftBlock.at(this.level, this.worldPosition).getState(),
                        isFrontText ? org.bukkit.block.sign.Side.FRONT : org.bukkit.block.sign.Side.BACK
                    );
                    if (!event.callEvent()) {
                        return false;
                    }
                    level.getServer().getCommands().performPrefixedCommand(createCommandSourceStack(((org.bukkit.craftbukkit.entity.CraftPlayer) event.getPlayer()).getHandle(), level, pos), event.getMessage());
                    // Paper end - Fix commands from signs not firing command events
                    flag = true;
                    break;
                case ClickEvent.ShowDialog showDialog:
                    player.openDialog(showDialog.dialog());
                    flag = true;
                    break;
                case ClickEvent.Custom custom:
                    level.getServer().handleCustomClickAction(custom.id(), custom.payload());
                    flag = true;
                    break;
                case null:
                default:
            }
        }

        return flag;
    }

    // CraftBukkit start
    private final CommandSource commandSource = new CommandSource() {

        @Override
        public void sendSystemMessage(Component message) {}

        @Override
        public org.bukkit.command.CommandSender getBukkitSender(CommandSourceStack commandSourceStack) {
            return commandSourceStack.getEntity() != null ? commandSourceStack.getEntity().getBukkitEntity() : new org.bukkit.craftbukkit.command.CraftBlockCommandSender(commandSourceStack, SignBlockEntity.this);
        }

        @Override
        public boolean acceptsSuccess() {
            return false;
        }

        @Override
        public boolean acceptsFailure() {
            return false;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }
    };

    private CommandSourceStack createCommandSourceStack(@Nullable Player player, ServerLevel level, BlockPos pos) {
        // CraftBukkit end
        String string = player == null ? "Sign" : player.getPlainTextName();
        Component component = (Component)(player == null ? Component.literal("Sign") : player.getDisplayName());

        // Paper start - Fix commands from signs not firing command events
        CommandSource commandSource = level.paperConfig().misc.showSignClickCommandFailureMsgsToPlayer ? new io.papermc.paper.commands.DelegatingCommandSource(this.commandSource) {
            @Override
            public void sendSystemMessage(Component message) {
                if (player instanceof final net.minecraft.server.level.ServerPlayer serverPlayer) {
                    serverPlayer.sendSystemMessage(message);
                }
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }
        } : this.commandSource;
        // Paper end - Fix commands from signs not firing command events
        // CraftBukkit - this
        return new CommandSourceStack(commandSource, Vec3.atCenterOf(pos), Vec2.ZERO, level, LevelBasedPermissionSet.GAMEMASTER, string, component, level.getServer(), player); // Paper - Fix commands from signs not firing command events
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    public void setAllowedPlayerEditor(@Nullable UUID playWhoMayEdit) {
        this.playerWhoMayEdit = playWhoMayEdit;
    }

    public @Nullable UUID getPlayerWhoMayEdit() {
        // CraftBukkit start - unnecessary sign ticking removed, so do this lazily
        if (this.level != null && this.playerWhoMayEdit != null) {
            this.clearInvalidPlayerWhoMayEdit(this, this.level, this.playerWhoMayEdit);
        }
        // CraftBukkit end
        return this.playerWhoMayEdit;
    }

    private void markUpdated() {
        this.setChanged();
        if (this.level != null) this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), Block.UPDATE_ALL); // CraftBukkit - skip notify if world is null (SPIGOT-5122)
    }

    public boolean isWaxed() {
        return this.isWaxed;
    }

    public boolean setWaxed(boolean isWaxed) {
        if (this.isWaxed != isWaxed) {
            this.isWaxed = isWaxed;
            this.markUpdated();
            return true;
        } else {
            return false;
        }
    }

    public boolean playerIsTooFarAwayToEdit(UUID uuid) {
        Player playerByUuid = this.level.getPlayerByUUID(uuid);
        return playerByUuid == null || !playerByUuid.isWithinBlockInteractionRange(this.getBlockPos(), 4.0);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, SignBlockEntity sign) {
        UUID playerWhoMayEdit = sign.getPlayerWhoMayEdit();
        if (playerWhoMayEdit != null) {
            sign.clearInvalidPlayerWhoMayEdit(sign, level, playerWhoMayEdit);
        }
    }

    private void clearInvalidPlayerWhoMayEdit(SignBlockEntity sign, Level level, UUID uuid) {
        if (sign.playerIsTooFarAwayToEdit(uuid)) {
            sign.setAllowedPlayerEditor(null);
        }
    }

    public SoundEvent getSignInteractionFailedSoundEvent() {
        return SoundEvents.WAXED_SIGN_INTERACT_FAIL;
    }
}
