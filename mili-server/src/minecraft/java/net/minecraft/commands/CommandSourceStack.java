package net.minecraft.commands;

import com.google.common.collect.Lists;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandExceptionType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BinaryOperator;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.commands.execution.TraceCallbacks;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.TaskChainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class CommandSourceStack implements ExecutionCommandSource<CommandSourceStack>, SharedSuggestionProvider, io.papermc.paper.command.brigadier.PaperCommandSourceStack { // Paper - Brigadier API
    public static final SimpleCommandExceptionType ERROR_NOT_PLAYER = new SimpleCommandExceptionType(Component.translatable("permissions.requires.player"));
    public static final SimpleCommandExceptionType ERROR_NOT_ENTITY = new SimpleCommandExceptionType(Component.translatable("permissions.requires.entity"));
    public final CommandSource source;
    private final Vec3 worldPosition;
    private final ServerLevel level;
    private final PermissionSet permissions;
    private final String textName;
    private final Component displayName;
    private final MinecraftServer server;
    private final boolean silent;
    private final @Nullable Entity entity;
    private final CommandResultCallback resultCallback;
    private final EntityAnchorArgument.Anchor anchor;
    private final Vec2 rotation;
    private final CommandSigningContext signingContext;
    private final TaskChainer chatMessageChainer;
    public boolean bypassSelectorPermissions = false; // Paper - add bypass for selector permissions

    public CommandSourceStack(
        CommandSource source,
        Vec3 worldPosition,
        Vec2 rotation,
        ServerLevel level,
        PermissionSet permissions,
        String textName,
        Component displayName,
        MinecraftServer server,
        @Nullable Entity entity
    ) {
        this(
            source,
            worldPosition,
            rotation,
            level,
            permissions,
            textName,
            displayName,
            server,
            entity,
            false,
            CommandResultCallback.EMPTY,
            EntityAnchorArgument.Anchor.FEET,
            CommandSigningContext.ANONYMOUS,
            TaskChainer.immediate((Runnable run) -> { io.papermc.paper.threadedregions.RegionizedServer.getInstance().addTask(run);}) // Folia - region threading
        );
    }

    private CommandSourceStack(
        CommandSource source,
        Vec3 worldPosition,
        Vec2 rotation,
        ServerLevel level,
        PermissionSet permissions,
        String textName,
        Component displayName,
        MinecraftServer server,
        @Nullable Entity entity,
        boolean silent,
        CommandResultCallback resultCallback,
        EntityAnchorArgument.Anchor anchor,
        CommandSigningContext signingContext,
        TaskChainer chatMessageChainer
    ) {
        this.source = source;
        this.worldPosition = worldPosition;
        this.level = level;
        this.silent = silent;
        this.entity = entity;
        this.permissions = permissions;
        this.textName = textName;
        this.displayName = displayName;
        this.server = server;
        this.resultCallback = resultCallback;
        this.anchor = anchor;
        this.rotation = rotation;
        this.signingContext = signingContext;
        this.chatMessageChainer = chatMessageChainer;
    }

    public CommandSourceStack withSource(CommandSource source) {
        return this.source == source
            ? this
            : new CommandSourceStack(
                source,
                this.worldPosition,
                this.rotation,
                this.level,
                this.permissions,
                this.textName,
                this.displayName,
                this.server,
                this.entity,
                this.silent,
                this.resultCallback,
                this.anchor,
                this.signingContext,
                this.chatMessageChainer
            );
    }

    public CommandSourceStack withEntity(Entity entity) {
        return this.entity == entity
            ? this
            : new CommandSourceStack(
                this.source,
                this.worldPosition,
                this.rotation,
                this.level,
                this.permissions,
                entity.getPlainTextName(),
                entity.getDisplayName(),
                this.server,
                entity,
                this.silent,
                this.resultCallback,
                this.anchor,
                this.signingContext,
                this.chatMessageChainer
            );
    }

    public CommandSourceStack withPosition(Vec3 pos) {
        return this.worldPosition.equals(pos)
            ? this
            : new CommandSourceStack(
                this.source,
                pos,
                this.rotation,
                this.level,
                this.permissions,
                this.textName,
                this.displayName,
                this.server,
                this.entity,
                this.silent,
                this.resultCallback,
                this.anchor,
                this.signingContext,
                this.chatMessageChainer
            );
    }

    // Paper start - Expose 'with' functions from the CommandSourceStack
    @Override
    public CommandSourceStack withLocation(org.bukkit.Location location) {
        return this.getLocation().equals(location)
            ? this
            : new CommandSourceStack(
            this.source,
            new Vec3(location.x(), location.y(), location.z()),
            new Vec2(location.getPitch(), location.getYaw()),
            ((org.bukkit.craftbukkit.CraftWorld) location.getWorld()).getHandle(),
            this.permissions,
            this.textName,
            this.displayName,
            this.server,
            this.entity,
            this.silent,
            this.resultCallback,
            this.anchor,
            this.signingContext,
            this.chatMessageChainer
        );
    }
    // Paper end - Expose 'with' functions from the CommandSourceStack

    public CommandSourceStack withRotation(Vec2 rotation) {
        return this.rotation.equals(rotation)
            ? this
            : new CommandSourceStack(
                this.source,
                this.worldPosition,
                rotation,
                this.level,
                this.permissions,
                this.textName,
                this.displayName,
                this.server,
                this.entity,
                this.silent,
                this.resultCallback,
                this.anchor,
                this.signingContext,
                this.chatMessageChainer
            );
    }

    @Override
    public CommandSourceStack withCallback(CommandResultCallback callback) {
        return Objects.equals(this.resultCallback, callback)
            ? this
            : new CommandSourceStack(
                this.source,
                this.worldPosition,
                this.rotation,
                this.level,
                this.permissions,
                this.textName,
                this.displayName,
                this.server,
                this.entity,
                this.silent,
                callback,
                this.anchor,
                this.signingContext,
                this.chatMessageChainer
            );
    }

    public CommandSourceStack withCallback(CommandResultCallback callback, BinaryOperator<CommandResultCallback> operator) {
        CommandResultCallback commandResultCallback = operator.apply(this.resultCallback, callback);
        return this.withCallback(commandResultCallback);
    }

    public CommandSourceStack withSuppressedOutput() {
        return !this.silent && !this.source.alwaysAccepts()
            ? new CommandSourceStack(
                this.source,
                this.worldPosition,
                this.rotation,
                this.level,
                this.permissions,
                this.textName,
                this.displayName,
                this.server,
                this.entity,
                true,
                this.resultCallback,
                this.anchor,
                this.signingContext,
                this.chatMessageChainer
            )
            : this;
    }

    public CommandSourceStack withPermission(PermissionSet permissions) {
        return permissions == this.permissions
            ? this
            : new CommandSourceStack(
                this.source,
                this.worldPosition,
                this.rotation,
                this.level,
                permissions,
                this.textName,
                this.displayName,
                this.server,
                this.entity,
                this.silent,
                this.resultCallback,
                this.anchor,
                this.signingContext,
                this.chatMessageChainer
            );
    }

    public CommandSourceStack withMaximumPermission(PermissionSet permissions) {
        return this.withPermission(this.permissions.union(permissions));
    }

    public CommandSourceStack withAnchor(EntityAnchorArgument.Anchor anchor) {
        return anchor == this.anchor
            ? this
            : new CommandSourceStack(
                this.source,
                this.worldPosition,
                this.rotation,
                this.level,
                this.permissions,
                this.textName,
                this.displayName,
                this.server,
                this.entity,
                this.silent,
                this.resultCallback,
                anchor,
                this.signingContext,
                this.chatMessageChainer
            );
    }

    public CommandSourceStack withLevel(ServerLevel level) {
        if (level == this.level) {
            return this;
        } else {
            double teleportationScale = DimensionType.getTeleportationScale(this.level.dimensionType(), level.dimensionType());
            Vec3 vec3 = new Vec3(this.worldPosition.x * teleportationScale, this.worldPosition.y, this.worldPosition.z * teleportationScale);
            return new CommandSourceStack(
                this.source,
                vec3,
                this.rotation,
                level,
                this.permissions,
                this.textName,
                this.displayName,
                this.server,
                this.entity,
                this.silent,
                this.resultCallback,
                this.anchor,
                this.signingContext,
                this.chatMessageChainer
            );
        }
    }

    public CommandSourceStack facing(Entity entity, EntityAnchorArgument.Anchor anchor) {
        return this.facing(anchor.apply(entity));
    }

    public CommandSourceStack facing(Vec3 lookPos) {
        Vec3 vec3 = this.anchor.apply(this);
        double d = lookPos.x - vec3.x;
        double d1 = lookPos.y - vec3.y;
        double d2 = lookPos.z - vec3.z;
        double squareRoot = Math.sqrt(d * d + d2 * d2);
        float f = Mth.wrapDegrees((float)(-(Mth.atan2(d1, squareRoot) * 180.0F / (float)Math.PI)));
        float f1 = Mth.wrapDegrees((float)(Mth.atan2(d2, d) * 180.0F / (float)Math.PI) - 90.0F);
        return this.withRotation(new Vec2(f, f1));
    }

    public CommandSourceStack withSigningContext(CommandSigningContext signingContext, TaskChainer chatMessageChainer) {
        return signingContext == this.signingContext && chatMessageChainer == this.chatMessageChainer
            ? this
            : new CommandSourceStack(
                this.source,
                this.worldPosition,
                this.rotation,
                this.level,
                this.permissions,
                this.textName,
                this.displayName,
                this.server,
                this.entity,
                this.silent,
                this.resultCallback,
                this.anchor,
                signingContext,
                chatMessageChainer
            );
    }

    public Component getDisplayName() {
        return this.displayName;
    }

    public String getTextName() {
        return this.textName;
    }

    @Override
    public PermissionSet permissions() {
        return this.permissions;
    }

    // Paper start - Fix permission levels for command blocks
    private boolean forceRespectPermissionLevel() {
        return this.source == CommandSource.NULL || (this.source instanceof final net.minecraft.world.level.BaseCommandBlock commandBlock && commandBlock.getLevel().paperConfig().commandBlocks.forceFollowPermLevel);
    }
    // Paper end - Fix permission levels for command blocks

    // CraftBukkit start
    public boolean hasPermission(net.minecraft.server.permissions.Permission permission, String bukkitPermission) {
        // Paper start - Fix permission levels for command blocks
        final java.util.function.BooleanSupplier hasBukkitPerm = () -> this.source == CommandSource.NULL /*treat NULL as having all bukkit perms*/ || this.getBukkitSender().hasPermission(bukkitPermission); // lazily check bukkit perms to the benefit of custom permission setups
        // if the server is null, we must check the vanilla perm level system
        // if ignoreVanillaPermissions is true, we can skip vanilla perms and just run the bukkit perm check
        //noinspection ConstantValue
        if (this.getServer() == null || !this.getServer().server.ignoreVanillaPermissions) { // server & level are null for command function loading
            final boolean hasPermLevel = this.permissions.hasPermission(permission);
            if (this.forceRespectPermissionLevel()) { // NULL CommandSource and command blocks (if setting is enabled) should always pass the vanilla perm check
                return hasPermLevel && hasBukkitPerm.getAsBoolean();
            } else { // otherwise check vanilla perm first then bukkit perm, matching upstream behavior
                return hasPermLevel || hasBukkitPerm.getAsBoolean();
            }
        }
        return hasBukkitPerm.getAsBoolean();
        // Paper end - Fix permission levels for command blocks
    }
    // CraftBukkit end

    public Vec3 getPosition() {
        return this.worldPosition;
    }

    public ServerLevel getLevel() {
        return this.level;
    }

    public @Nullable Entity getEntity() {
        return this.entity;
    }

    public Entity getEntityOrException() throws CommandSyntaxException {
        if (this.entity == null) {
            throw ERROR_NOT_ENTITY.create();
        } else {
            return this.entity;
        }
    }

    public ServerPlayer getPlayerOrException() throws CommandSyntaxException {
        if (this.entity instanceof ServerPlayer serverPlayer) {
            return serverPlayer;
        } else {
            throw ERROR_NOT_PLAYER.create();
        }
    }

    public @Nullable ServerPlayer getPlayer() {
        return this.entity instanceof ServerPlayer serverPlayer ? serverPlayer : null;
    }

    public boolean isPlayer() {
        return this.entity instanceof ServerPlayer;
    }

    public Vec2 getRotation() {
        return this.rotation;
    }

    public MinecraftServer getServer() {
        return this.server;
    }

    public EntityAnchorArgument.Anchor getAnchor() {
        return this.anchor;
    }

    public CommandSigningContext getSigningContext() {
        return this.signingContext;
    }

    public TaskChainer getChatMessageChainer() {
        return this.chatMessageChainer;
    }

    public boolean shouldFilterMessageTo(ServerPlayer receiver) {
        ServerPlayer player = this.getPlayer();
        return receiver != player && (player != null && player.isTextFilteringEnabled() || receiver.isTextFilteringEnabled());
    }

    public void sendChatMessage(OutgoingChatMessage message, boolean shouldFilter, ChatType.Bound boundChatType) {
        if (!this.silent) {
            ServerPlayer player = this.getPlayer();
            if (player != null) {
                player.sendChatMessage(message, shouldFilter, boundChatType);
            } else {
                this.source.sendSystemMessage(boundChatType.decorate(message.content()));
            }
        }
    }

    public void sendSystemMessage(Component message) {
        if (!this.silent) {
            ServerPlayer player = this.getPlayer();
            if (player != null) {
                player.sendSystemMessage(message);
            } else {
                this.source.sendSystemMessage(message);
            }
        }
    }

    public void sendSuccess(Supplier<Component> messageSupplier, boolean allowLogging) {
        boolean flag = this.source.acceptsSuccess() && !this.silent;
        boolean flag1 = allowLogging && this.source.shouldInformAdmins() && !this.silent;
        if (flag || flag1) {
            Component component = messageSupplier.get();
            if (flag) {
                this.source.sendSystemMessage(component);
            }

            if (flag1) {
                this.broadcastToAdmins(component);
            }
        }
    }

    private void broadcastToAdmins(Component message) {
        Component component = Component.translatable("chat.type.admin", this.getDisplayName(), message).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
        GameRules gameRules = this.level.getGameRules();
        if (gameRules.get(GameRules.SEND_COMMAND_FEEDBACK)) {
            for (ServerPlayer serverPlayer : this.server.getPlayerList().getPlayers()) {
                if (serverPlayer.commandSource() != this.source && serverPlayer.getBukkitEntity().hasPermission("minecraft.admin.command_feedback")) { // CraftBukkit
                    serverPlayer.sendSystemMessage(component);
                }
            }
        }

        if (this.source != this.server && gameRules.get(GameRules.LOG_ADMIN_COMMANDS) && !org.spigotmc.SpigotConfig.silentCommandBlocks) { // Spigot
            this.server.sendSystemMessage(component);
        }
    }

    public void sendFailure(Component message) {
        // Paper start - Add UnknownCommandEvent
        this.sendFailure(message, true);
    }
    public void sendFailure(Component message, boolean withStyle) {
        // Paper end - Add UnknownCommandEvent
        if (this.source.acceptsFailure() && !this.silent) {
            this.source.sendSystemMessage(withStyle ? Component.empty().append(message).withStyle(ChatFormatting.RED) : message); // Paper - Add UnknownCommandEvent
        }
    }

    @Override
    public CommandResultCallback callback() {
        return this.resultCallback;
    }

    @Override
    public Collection<String> getOnlinePlayerNames() {
        return this.entity instanceof ServerPlayer sourcePlayer && !(sourcePlayer instanceof org.leavesmc.leaves.replay.ServerPhotographer) && !sourcePlayer.getBukkitEntity().hasPermission("paper.bypass-visibility.tab-completion") ? this.getServer().getPlayerList().getPlayers().stream().filter(serverPlayer -> sourcePlayer.getBukkitEntity().canSee(serverPlayer.getBukkitEntity())).map(serverPlayer -> serverPlayer.getGameProfile().name()).toList() : Lists.newArrayList(this.server.getPlayerNames()); // Paper - Make CommandSourceStack respect hidden players // Leaves - only real player
    }

    @Override
    public Collection<String> getAllTeams() {
        return this.server.getScoreboard().getTeamNames();
    }

    @Override
    public Stream<Identifier> getAvailableSounds() {
        return BuiltInRegistries.SOUND_EVENT.stream().map(SoundEvent::location);
    }

    @Override
    public CompletableFuture<Suggestions> customSuggestion(CommandContext<?> context) {
        return Suggestions.empty();
    }

    @Override
    public CompletableFuture<Suggestions> suggestRegistryElements(
        ResourceKey<? extends Registry<?>> registryKey,
        SharedSuggestionProvider.ElementSuggestionType type,
        SuggestionsBuilder builder,
        CommandContext<?> context
    ) {
        if (registryKey == Registries.RECIPE) {
            return SharedSuggestionProvider.suggestResource(
                this.server.getRecipeManager().getRecipes().stream().map(recipeHolder -> recipeHolder.id().identifier()), builder
            );
        } else if (registryKey == Registries.ADVANCEMENT) {
            Collection<AdvancementHolder> allAdvancements = this.server.getAdvancements().getAllAdvancements();
            return SharedSuggestionProvider.suggestResource(allAdvancements.stream().map(AdvancementHolder::id), builder);
        } else {
            return this.getLookup(registryKey).map(holderLookup -> {
                this.suggestRegistryElements((HolderLookup<?>)holderLookup, type, builder);
                return builder.buildFuture();
            }).orElseGet(Suggestions::empty);
        }
    }

    private Optional<? extends HolderLookup<?>> getLookup(ResourceKey<? extends Registry<?>> registryKey) {
        Optional<? extends Registry<?>> optional = this.registryAccess().lookup(registryKey);
        return optional.isPresent() ? optional : this.server.reloadableRegistries().lookup().lookup(registryKey);
    }

    @Override
    public Set<ResourceKey<Level>> levels() {
        return this.server.levelKeys();
    }

    @Override
    public RegistryAccess registryAccess() {
        return this.server.registryAccess();
    }

    @Override
    public FeatureFlagSet enabledFeatures() {
        return this.level.enabledFeatures();
    }

    @Override
    public CommandDispatcher<CommandSourceStack> dispatcher() {
        return this.getServer().getFunctions().getDispatcher();
    }

    @Override
    public void handleError(CommandExceptionType exceptionType, Message message, boolean success, @Nullable TraceCallbacks tracer) {
        if (tracer != null) {
            tracer.onError(message.getString());
        }

        if (!success) {
            this.sendFailure(ComponentUtils.fromMessage(message));
        }
    }

    @Override
    public boolean isSilent() {
        return this.silent;
    }

    // Paper start
    @Override
    public CommandSourceStack getHandle() {
        return this;
    }
    // Paper end
    // CraftBukkit start
    public org.bukkit.command.CommandSender getBukkitSender() {
        return this.source.getBukkitSender(this);
    }
    // CraftBukkit end
}
