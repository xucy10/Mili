package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class SummonCommand {
    private static final SimpleCommandExceptionType ERROR_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.summon.failed"));
    private static final SimpleCommandExceptionType ERROR_FAILED_PEACEFUL = new SimpleCommandExceptionType(
        Component.translatable("commands.summon.failed.peaceful")
    );
    private static final SimpleCommandExceptionType ERROR_DUPLICATE_UUID = new SimpleCommandExceptionType(Component.translatable("commands.summon.failed.uuid"));
    private static final SimpleCommandExceptionType INVALID_POSITION = new SimpleCommandExceptionType(Component.translatable("commands.summon.invalidPosition"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(
            Commands.literal("summon")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(
                    Commands.argument("entity", ResourceArgument.resource(context, Registries.ENTITY_TYPE))
                        .suggests(SuggestionProviders.cast(SuggestionProviders.SUMMONABLE_ENTITIES))
                        .executes(
                            commandContext -> spawnEntity(
                                commandContext.getSource(),
                                ResourceArgument.getSummonableEntityType(commandContext, "entity"),
                                commandContext.getSource().getPosition(),
                                new CompoundTag(),
                                true
                            )
                        )
                        .then(
                            Commands.argument("pos", Vec3Argument.vec3())
                                .executes(
                                    context1 -> spawnEntity(
                                        context1.getSource(),
                                        ResourceArgument.getSummonableEntityType(context1, "entity"),
                                        Vec3Argument.getVec3(context1, "pos"),
                                        new CompoundTag(),
                                        true
                                    )
                                )
                                .then(
                                    Commands.argument("nbt", CompoundTagArgument.compoundTag())
                                        .executes(
                                            context1 -> spawnEntity(
                                                context1.getSource(),
                                                ResourceArgument.getSummonableEntityType(context1, "entity"),
                                                Vec3Argument.getVec3(context1, "pos"),
                                                CompoundTagArgument.getCompoundTag(context1, "nbt"),
                                                false
                                            )
                                        )
                                )
                        )
                )
        );
    }

    public static Entity createEntity(CommandSourceStack source, Holder.Reference<EntityType<?>> type, Vec3 pos, CompoundTag tag, boolean randomizeProperties) throws CommandSyntaxException {
        BlockPos blockPos = BlockPos.containing(pos);
        if (!Level.isInSpawnableBounds(blockPos)) {
            throw INVALID_POSITION.create();
        } else if (source.getLevel().getDifficulty() == Difficulty.PEACEFUL && !type.value().isAllowedInPeaceful(tag)) { // Paper - check peaceful override
            throw ERROR_FAILED_PEACEFUL.create();
        } else {
            CompoundTag compoundTag = tag.copy();
            compoundTag.putString("id", type.key().identifier().toString());
            ServerLevel level = source.getLevel();
            Entity entity = EntityType.loadEntityRecursive(compoundTag, level, EntitySpawnReason.COMMAND, entity1 -> {
                entity1.snapTo(pos.x, pos.y, pos.z, entity1.getYRot(), entity1.getXRot());
                entity1.spawnReason = org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.COMMAND; // Paper - Entity#getEntitySpawnReason
                return entity1;
            });
            if (entity == null) {
                throw ERROR_FAILED.create();
            } else {
                // Folia start - region threading
                io.papermc.paper.threadedregions.RegionizedServer.getInstance().taskQueue.queueTickTaskQueue(
                        level, entity.chunkPosition().x, entity.chunkPosition().z, () -> {
                            // Folia end - region threading
                if (randomizeProperties && entity instanceof Mob mob) {
                    mob.finalizeSpawn(source.getLevel(), source.getLevel().getCurrentDifficultyAt(entity.blockPosition()), EntitySpawnReason.COMMAND, null);
                }
                level.tryAddFreshEntityWithPassengers(entity, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.COMMAND); // Folia - region threading
                }); // Folia - region threading

                if (false) { // CraftBukkit - pass a spawn reason of "COMMAND" // Folia - region threading
                    throw ERROR_DUPLICATE_UUID.create();
                } else {
                    return entity;
                }
            }
        }
    }

    private static int spawnEntity(CommandSourceStack source, Holder.Reference<EntityType<?>> type, Vec3 pos, CompoundTag tag, boolean randomizeProperties) throws CommandSyntaxException {
        Entity entity = createEntity(source, type, pos, tag, randomizeProperties);
        source.sendSuccess(() -> Component.translatable("commands.summon.success", entity.getDisplayName()), true);
        return 1;
    }
}
