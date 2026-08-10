package net.minecraft.server.commands;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Collection;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

public class EffectCommands {
    private static final SimpleCommandExceptionType ERROR_GIVE_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.effect.give.failed"));
    private static final SimpleCommandExceptionType ERROR_CLEAR_EVERYTHING_FAILED = new SimpleCommandExceptionType(
        Component.translatable("commands.effect.clear.everything.failed")
    );
    private static final SimpleCommandExceptionType ERROR_CLEAR_SPECIFIC_FAILED = new SimpleCommandExceptionType(
        Component.translatable("commands.effect.clear.specific.failed")
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(
            Commands.literal("effect")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(
                    Commands.literal("clear")
                        .executes(
                            commandContext -> clearEffects(commandContext.getSource(), ImmutableList.of(commandContext.getSource().getEntityOrException()))
                        )
                        .then(
                            Commands.argument("targets", EntityArgument.entities())
                                .executes(context1 -> clearEffects(context1.getSource(), EntityArgument.getEntities(context1, "targets")))
                                .then(
                                    Commands.argument("effect", ResourceArgument.resource(context, Registries.MOB_EFFECT))
                                        .executes(
                                            context1 -> clearEffect(
                                                context1.getSource(),
                                                EntityArgument.getEntities(context1, "targets"),
                                                ResourceArgument.getMobEffect(context1, "effect")
                                            )
                                        )
                                )
                        )
                )
                .then(
                    Commands.literal("give")
                        .then(
                            Commands.argument("targets", EntityArgument.entities())
                                .then(
                                    Commands.argument("effect", ResourceArgument.resource(context, Registries.MOB_EFFECT))
                                        .executes(
                                            context1 -> giveEffect(
                                                context1.getSource(),
                                                EntityArgument.getEntities(context1, "targets"),
                                                ResourceArgument.getMobEffect(context1, "effect"),
                                                null,
                                                0,
                                                true
                                            )
                                        )
                                        .then(
                                            Commands.argument("seconds", IntegerArgumentType.integer(1, 1000000))
                                                .executes(
                                                    context1 -> giveEffect(
                                                        context1.getSource(),
                                                        EntityArgument.getEntities(context1, "targets"),
                                                        ResourceArgument.getMobEffect(context1, "effect"),
                                                        IntegerArgumentType.getInteger(context1, "seconds"),
                                                        0,
                                                        true
                                                    )
                                                )
                                                .then(
                                                    Commands.argument("amplifier", IntegerArgumentType.integer(0, 255))
                                                        .executes(
                                                            context1 -> giveEffect(
                                                                context1.getSource(),
                                                                EntityArgument.getEntities(context1, "targets"),
                                                                ResourceArgument.getMobEffect(context1, "effect"),
                                                                IntegerArgumentType.getInteger(context1, "seconds"),
                                                                IntegerArgumentType.getInteger(context1, "amplifier"),
                                                                true
                                                            )
                                                        )
                                                        .then(
                                                            Commands.argument("hideParticles", BoolArgumentType.bool())
                                                                .executes(
                                                                    context1 -> giveEffect(
                                                                        context1.getSource(),
                                                                        EntityArgument.getEntities(context1, "targets"),
                                                                        ResourceArgument.getMobEffect(context1, "effect"),
                                                                        IntegerArgumentType.getInteger(context1, "seconds"),
                                                                        IntegerArgumentType.getInteger(context1, "amplifier"),
                                                                        !BoolArgumentType.getBool(context1, "hideParticles")
                                                                    )
                                                                )
                                                        )
                                                )
                                        )
                                        .then(
                                            Commands.literal("infinite")
                                                .executes(
                                                    context1 -> giveEffect(
                                                        context1.getSource(),
                                                        EntityArgument.getEntities(context1, "targets"),
                                                        ResourceArgument.getMobEffect(context1, "effect"),
                                                        -1,
                                                        0,
                                                        true
                                                    )
                                                )
                                                .then(
                                                    Commands.argument("amplifier", IntegerArgumentType.integer(0, 255))
                                                        .executes(
                                                            context1 -> giveEffect(
                                                                context1.getSource(),
                                                                EntityArgument.getEntities(context1, "targets"),
                                                                ResourceArgument.getMobEffect(context1, "effect"),
                                                                -1,
                                                                IntegerArgumentType.getInteger(context1, "amplifier"),
                                                                true
                                                            )
                                                        )
                                                        .then(
                                                            Commands.argument("hideParticles", BoolArgumentType.bool())
                                                                .executes(
                                                                    context1 -> giveEffect(
                                                                        context1.getSource(),
                                                                        EntityArgument.getEntities(context1, "targets"),
                                                                        ResourceArgument.getMobEffect(context1, "effect"),
                                                                        -1,
                                                                        IntegerArgumentType.getInteger(context1, "amplifier"),
                                                                        !BoolArgumentType.getBool(context1, "hideParticles")
                                                                    )
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                )
        );
    }

    private static int giveEffect(
        CommandSourceStack source,
        Collection<? extends Entity> targets,
        Holder<MobEffect> effect,
        @Nullable Integer seconds,
        int amplifier,
        boolean showParticles
    ) throws CommandSyntaxException {
        MobEffect mobEffect = effect.value();
        int i = 0;
        int i1;
        if (seconds != null) {
            if (mobEffect.isInstantenous()) {
                i1 = seconds;
            } else if (seconds == -1) {
                i1 = -1;
            } else {
                i1 = seconds * 20;
            }
        } else if (mobEffect.isInstantenous()) {
            i1 = 1;
        } else {
            i1 = 600;
        }

        for (Entity entity : targets) {
            if (entity instanceof LivingEntity) {
                MobEffectInstance mobEffectInstance = new MobEffectInstance(effect, i1, amplifier, false, showParticles);
                // Folia start - region threading
                entity.getBukkitEntity().taskScheduler.schedule((LivingEntity nmsEntity) -> {
                    ((LivingEntity)nmsEntity).addEffect(mobEffectInstance, source.getEntity(), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.COMMAND);
                }, null, 1L);
                // Folia end - region threading
                if (true) { // CraftBukkit // Folia - region threading
                    i++;
                }
            }
        }

        if (i == 0) {
            throw ERROR_GIVE_FAILED.create();
        } else {
            if (targets.size() == 1) {
                source.sendSuccess(
                    () -> Component.translatable(
                        "commands.effect.give.success.single", mobEffect.getDisplayName(), targets.iterator().next().getDisplayName(), i1 / 20
                    ),
                    true
                );
            } else {
                source.sendSuccess(
                    () -> Component.translatable("commands.effect.give.success.multiple", mobEffect.getDisplayName(), targets.size(), i1 / 20), true
                );
            }

            return i;
        }
    }

    private static int clearEffects(CommandSourceStack source, Collection<? extends Entity> targets) throws CommandSyntaxException {
        int i = 0;

        for (Entity entity : targets) {
            if (entity instanceof LivingEntity && true) { // CraftBukkit // Folia - region threading
                // Folia start - region threading
                entity.getBukkitEntity().taskScheduler.schedule((LivingEntity nmsEntity) -> {
                    ((LivingEntity)nmsEntity).removeAllEffects(org.bukkit.event.entity.EntityPotionEffectEvent.Cause.COMMAND);
                }, null, 1L);
                // Folia end - region threading
                i++;
            }
        }

        if (i == 0) {
            throw ERROR_CLEAR_EVERYTHING_FAILED.create();
        } else {
            if (targets.size() == 1) {
                source.sendSuccess(
                    () -> Component.translatable("commands.effect.clear.everything.success.single", targets.iterator().next().getDisplayName()), true
                );
            } else {
                source.sendSuccess(() -> Component.translatable("commands.effect.clear.everything.success.multiple", targets.size()), true);
            }

            return i;
        }
    }

    private static int clearEffect(CommandSourceStack source, Collection<? extends Entity> targets, Holder<MobEffect> effect) throws CommandSyntaxException {
        MobEffect mobEffect = effect.value();
        int i = 0;

        for (Entity entity : targets) {
            if (entity instanceof LivingEntity && true) { // CraftBukkit // Folia - region threading
                // Folia start - region threading
                entity.getBukkitEntity().taskScheduler.schedule((LivingEntity nmsEntity) -> {
                    ((LivingEntity)nmsEntity).removeEffect(effect, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.COMMAND);
                }, null, 1L);
                // Folia end - region threading
                i++;
            }
        }

        if (i == 0) {
            throw ERROR_CLEAR_SPECIFIC_FAILED.create();
        } else {
            if (targets.size() == 1) {
                source.sendSuccess(
                    () -> Component.translatable(
                        "commands.effect.clear.specific.success.single", mobEffect.getDisplayName(), targets.iterator().next().getDisplayName()
                    ),
                    true
                );
            } else {
                source.sendSuccess(
                    () -> Component.translatable("commands.effect.clear.specific.success.multiple", mobEffect.getDisplayName(), targets.size()), true
                );
            }

            return i;
        }
    }
}
