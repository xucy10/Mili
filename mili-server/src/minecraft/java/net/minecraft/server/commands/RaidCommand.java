package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.entity.raid.Raids;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class RaidCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(
            Commands.literal("raid")
                .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                .then(
                    Commands.literal("start")
                        .then(
                            Commands.argument("omenlvl", IntegerArgumentType.integer(0))
                                .executes(commandContext -> start(commandContext.getSource(), IntegerArgumentType.getInteger(commandContext, "omenlvl")))
                        )
                )
                .then(Commands.literal("stop").executes(context1 -> stop(context1.getSource())))
                .then(Commands.literal("check").executes(context1 -> check(context1.getSource())))
                .then(
                    Commands.literal("sound")
                        .then(
                            Commands.argument("type", ComponentArgument.textComponent(context))
                                .executes(context1 -> playSound(context1.getSource(), ComponentArgument.getResolvedComponent(context1, "type")))
                        )
                )
                .then(Commands.literal("spawnleader").executes(context1 -> spawnLeader(context1.getSource())))
                .then(
                    Commands.literal("setomen")
                        .then(
                            Commands.argument("level", IntegerArgumentType.integer(0))
                                .executes(context1 -> setRaidOmenLevel(context1.getSource(), IntegerArgumentType.getInteger(context1, "level")))
                        )
                )
                .then(Commands.literal("glow").executes(context1 -> glow(context1.getSource())))
        );
    }

    private static int glow(CommandSourceStack source) throws CommandSyntaxException {
        Raid raid = getRaid(source.getPlayerOrException());
        if (raid != null) {
            for (Raider raider : raid.getAllRaiders()) {
                raider.addEffect(new MobEffectInstance(MobEffects.GLOWING, 1000, 1));
            }
        }

        return 1;
    }

    private static int setRaidOmenLevel(CommandSourceStack source, int level) throws CommandSyntaxException {
        Raid raid = getRaid(source.getPlayerOrException());
        if (raid != null) {
            int maxRaidOmenLevel = raid.getMaxRaidOmenLevel();
            if (level > maxRaidOmenLevel) {
                source.sendFailure(Component.literal("Sorry, the max raid omen level you can set is " + maxRaidOmenLevel));
            } else {
                int raidOmenLevel = raid.getRaidOmenLevel();
                raid.setRaidOmenLevel(level);
                source.sendSuccess(() -> Component.literal("Changed village's raid omen level from " + raidOmenLevel + " to " + level), false);
            }
        } else {
            source.sendFailure(Component.literal("No raid found here"));
        }

        return 1;
    }

    private static int spawnLeader(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Spawned a raid captain"), false);
        Raider raider = EntityType.PILLAGER.create(source.getLevel(), EntitySpawnReason.COMMAND);
        if (raider == null) {
            source.sendFailure(Component.literal("Pillager failed to spawn"));
            return 0;
        } else {
            raider.setPatrolLeader(true);
            raider.setItemSlot(EquipmentSlot.HEAD, Raid.getOminousBannerInstance(source.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN)));
            raider.setPos(source.getPosition().x, source.getPosition().y, source.getPosition().z);
            raider.finalizeSpawn(
                source.getLevel(), source.getLevel().getCurrentDifficultyAt(BlockPos.containing(source.getPosition())), EntitySpawnReason.COMMAND, null
            );
            source.getLevel().addFreshEntityWithPassengers(raider);
            return 1;
        }
    }

    private static int playSound(CommandSourceStack source, @Nullable Component type) {
        if (type != null && type.getString().equals("local")) {
            ServerLevel level = source.getLevel();
            Vec3 vec3 = source.getPosition().add(5.0, 0.0, 0.0);
            level.playSeededSound(null, vec3.x, vec3.y, vec3.z, SoundEvents.RAID_HORN, SoundSource.NEUTRAL, 2.0F, 1.0F, level.random.nextLong());
        }

        return 1;
    }

    private static int start(CommandSourceStack source, int badOmenLevel) throws CommandSyntaxException {
        ServerPlayer playerOrException = source.getPlayerOrException();
        BlockPos blockPos = playerOrException.blockPosition();
        if (playerOrException.level().isRaided(blockPos)) {
            source.sendFailure(Component.literal("Raid already started close by"));
            return -1;
        } else {
            Raids raids = playerOrException.level().getRaids();
            Raid raid = raids.createOrExtendRaid(playerOrException, playerOrException.blockPosition());
            if (raid != null) {
                raid.setRaidOmenLevel(badOmenLevel);
                raids.setDirty();
                source.sendSuccess(() -> Component.literal("Created a raid in your local village"), false);
            } else {
                source.sendFailure(Component.literal("Failed to create a raid in your local village"));
            }

            return 1;
        }
    }

    private static int stop(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer playerOrException = source.getPlayerOrException();
        BlockPos blockPos = playerOrException.blockPosition();
        Raid raidAt = playerOrException.level().getRaidAt(blockPos);
        if (raidAt != null) {
            raidAt.stop();
            source.sendSuccess(() -> Component.literal("Stopped raid"), false);
            return 1;
        } else {
            source.sendFailure(Component.literal("No raid here"));
            return -1;
        }
    }

    private static int check(CommandSourceStack source) throws CommandSyntaxException {
        Raid raid = getRaid(source.getPlayerOrException());
        if (raid != null) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("Found a started raid! ");
            source.sendSuccess(() -> Component.literal(stringBuilder.toString()), false);
            StringBuilder stringBuilder1 = new StringBuilder();
            stringBuilder1.append("Num groups spawned: ");
            stringBuilder1.append(raid.getGroupsSpawned());
            stringBuilder1.append(" Raid omen level: ");
            stringBuilder1.append(raid.getRaidOmenLevel());
            stringBuilder1.append(" Num mobs: ");
            stringBuilder1.append(raid.getTotalRaidersAlive());
            stringBuilder1.append(" Raid health: ");
            stringBuilder1.append(raid.getHealthOfLivingRaiders());
            stringBuilder1.append(" / ");
            stringBuilder1.append(raid.getTotalHealth());
            source.sendSuccess(() -> Component.literal(stringBuilder1.toString()), false);
            return 1;
        } else {
            source.sendFailure(Component.literal("Found no started raids"));
            return 0;
        }
    }

    private static @Nullable Raid getRaid(ServerPlayer player) {
        return player.level().getRaidAt(player.blockPosition());
    }
}
