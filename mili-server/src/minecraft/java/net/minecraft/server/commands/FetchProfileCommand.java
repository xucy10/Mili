package net.minecraft.server.commands;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.objects.PlayerSprite;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.ProfileResolver;
import net.minecraft.util.Util;
import net.minecraft.world.item.component.ResolvableProfile;

public class FetchProfileCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("fetchprofile")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(
                    Commands.literal("name")
                        .then(
                            Commands.argument("name", StringArgumentType.greedyString())
                                .executes(context -> resolveName(context.getSource(), StringArgumentType.getString(context, "name")))
                        )
                )
                .then(
                    Commands.literal("id")
                        .then(
                            Commands.argument("id", UuidArgument.uuid())
                                .executes(context -> resolveId(context.getSource(), UuidArgument.getUuid(context, "id")))
                        )
                )
        );
    }

    private static void reportResolvedProfile(CommandSourceStack source, GameProfile profile, String successKey, Component resolved) {
        ResolvableProfile resolvableProfile = ResolvableProfile.createResolved(profile);
        ResolvableProfile.CODEC
            .encodeStart(NbtOps.INSTANCE, resolvableProfile)
            .ifSuccess(
                tag -> {
                    String string = tag.toString();
                    MutableComponent mutableComponent = Component.object(new PlayerSprite(resolvableProfile, true));
                    ComponentSerialization.CODEC
                        .encodeStart(NbtOps.INSTANCE, mutableComponent)
                        .ifSuccess(
                            tag1 -> {
                                String string1 = tag1.toString();
                                source.sendSuccess(
                                    () -> {
                                        Component component = ComponentUtils.formatList(
                                            List.of(
                                                Component.translatable("commands.fetchprofile.copy_component")
                                                    .withStyle(style -> style.withClickEvent(new ClickEvent.CopyToClipboard(string))),
                                                Component.translatable("commands.fetchprofile.give_item")
                                                    .withStyle(
                                                        style -> style.withClickEvent(
                                                            new ClickEvent.RunCommand("give @s minecraft:player_head[profile=" + string + "]")
                                                        )
                                                    ),
                                                Component.translatable("commands.fetchprofile.summon_mannequin")
                                                    .withStyle(
                                                        style -> style.withClickEvent(
                                                            new ClickEvent.RunCommand("summon minecraft:mannequin ~ ~ ~ {profile:" + string + "}")
                                                        )
                                                    ),
                                                Component.translatable("commands.fetchprofile.copy_text", mutableComponent.withStyle(ChatFormatting.WHITE))
                                                    .withStyle(style -> style.withClickEvent(new ClickEvent.CopyToClipboard(string1)))
                                            ),
                                            CommonComponents.SPACE,
                                            mutableComponent1 -> ComponentUtils.wrapInSquareBrackets(mutableComponent1.withStyle(ChatFormatting.GREEN))
                                        );
                                        return Component.translatable(successKey, resolved, component);
                                    },
                                    false
                                );
                            }
                        )
                        .ifError(error -> source.sendFailure(Component.translatable("commands.fetchprofile.failed_to_serialize", error.message())));
                }
            )
            .ifError(error -> source.sendFailure(Component.translatable("commands.fetchprofile.failed_to_serialize", error.message())));
    }

    private static int resolveName(CommandSourceStack source, String name) {
        MinecraftServer server = source.getServer();
        ProfileResolver profileResolver = server.services().profileResolver();
        Util.nonCriticalIoPool()
            .execute(
                () -> {
                    Component component = Component.literal(name);
                    Optional<GameProfile> optional = profileResolver.fetchByName(name);
                    io.papermc.paper.threadedregions.RegionizedServer.getInstance().addTask( // Folia - region threading
                        () -> optional.ifPresentOrElse(
                            gameProfile -> reportResolvedProfile(source, gameProfile, "commands.fetchprofile.name.success", component),
                            () -> source.sendFailure(Component.translatable("commands.fetchprofile.name.failure", component))
                        )
                    );
                }
            );
        return 1;
    }

    private static int resolveId(CommandSourceStack source, UUID id) {
        MinecraftServer server = source.getServer();
        ProfileResolver profileResolver = server.services().profileResolver();
        Util.nonCriticalIoPool()
            .execute(
                () -> {
                    Component component = Component.translationArg(id);
                    Optional<GameProfile> optional = profileResolver.fetchById(id);
                    io.papermc.paper.threadedregions.RegionizedServer.getInstance().addTask( // Folia - region threading
                        () -> optional.ifPresentOrElse(
                            gameProfile -> reportResolvedProfile(source, gameProfile, "commands.fetchprofile.id.success", component),
                            () -> source.sendFailure(Component.translatable("commands.fetchprofile.id.failure", component))
                        )
                    );
                }
            );
        return 1;
    }
}
