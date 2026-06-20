package net.minecraft.server.commands;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.Dynamic4CommandExceptionType;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec2Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.scores.Team;

public class SpreadPlayersCommand {
    private static final int MAX_ITERATION_COUNT = 10000;
    private static final Dynamic4CommandExceptionType ERROR_FAILED_TO_SPREAD_TEAMS = new Dynamic4CommandExceptionType(
        (teamCount, x, z, suggestedSpread) -> Component.translatableEscape("commands.spreadplayers.failed.teams", teamCount, x, z, suggestedSpread)
    );
    private static final Dynamic4CommandExceptionType ERROR_FAILED_TO_SPREAD_ENTITIES = new Dynamic4CommandExceptionType(
        (entityCount, x, z, suggestedSpread) -> Component.translatableEscape("commands.spreadplayers.failed.entities", entityCount, x, z, suggestedSpread)
    );
    private static final Dynamic2CommandExceptionType ERROR_INVALID_MAX_HEIGHT = new Dynamic2CommandExceptionType(
        (maxHeight, worldMin) -> Component.translatableEscape("commands.spreadplayers.failed.invalid.height", maxHeight, worldMin)
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("spreadplayers")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(
                    Commands.argument("center", Vec2Argument.vec2())
                        .then(
                            Commands.argument("spreadDistance", FloatArgumentType.floatArg(0.0F))
                                .then(
                                    Commands.argument("maxRange", FloatArgumentType.floatArg(1.0F))
                                        .then(
                                            Commands.argument("respectTeams", BoolArgumentType.bool())
                                                .then(
                                                    Commands.argument("targets", EntityArgument.entities())
                                                        .executes(
                                                            commandContext -> spreadPlayers(
                                                                commandContext.getSource(),
                                                                Vec2Argument.getVec2(commandContext, "center"),
                                                                FloatArgumentType.getFloat(commandContext, "spreadDistance"),
                                                                FloatArgumentType.getFloat(commandContext, "maxRange"),
                                                                commandContext.getSource().getLevel().getMaxY() + 1,
                                                                BoolArgumentType.getBool(commandContext, "respectTeams"),
                                                                EntityArgument.getEntities(commandContext, "targets")
                                                            )
                                                        )
                                                )
                                        )
                                        .then(
                                            Commands.literal("under")
                                                .then(
                                                    Commands.argument("maxHeight", IntegerArgumentType.integer())
                                                        .then(
                                                            Commands.argument("respectTeams", BoolArgumentType.bool())
                                                                .then(
                                                                    Commands.argument("targets", EntityArgument.entities())
                                                                        .executes(
                                                                            context -> spreadPlayers(
                                                                                context.getSource(),
                                                                                Vec2Argument.getVec2(context, "center"),
                                                                                FloatArgumentType.getFloat(context, "spreadDistance"),
                                                                                FloatArgumentType.getFloat(context, "maxRange"),
                                                                                IntegerArgumentType.getInteger(context, "maxHeight"),
                                                                                BoolArgumentType.getBool(context, "respectTeams"),
                                                                                EntityArgument.getEntities(context, "targets")
                                                                            )
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

    private static int spreadPlayers(
        CommandSourceStack source, Vec2 center, float spreadDistance, float maxRange, int maxHeight, boolean respectTeams, Collection<? extends Entity> targets
    ) throws CommandSyntaxException {
        ServerLevel level = source.getLevel();
        int minY = level.getMinY();
        if (maxHeight < minY) {
            throw ERROR_INVALID_MAX_HEIGHT.create(maxHeight, minY);
        } else {
            RandomSource randomSource = RandomSource.create();
            double d = center.x - maxRange;
            double d1 = center.y - maxRange;
            double d2 = center.x + maxRange;
            double d3 = center.y + maxRange;
            SpreadPlayersCommand.Position[] positions = createInitialPositions(
                randomSource, respectTeams ? getNumberOfTeams(targets) : targets.size(), d, d1, d2, d3
            );
            spreadPositions(center, spreadDistance, level, randomSource, d, d1, d2, d3, maxHeight, positions, respectTeams);
            double d4 = setPlayerPositions(targets, level, positions, maxHeight, respectTeams);
            source.sendSuccess(
                () -> Component.translatable(
                    "commands.spreadplayers.success." + (respectTeams ? "teams" : "entities"),
                    positions.length,
                    center.x,
                    center.y,
                    String.format(Locale.ROOT, "%.2f", d4)
                ),
                true
            );
            return positions.length;
        }
    }

    private static int getNumberOfTeams(Collection<? extends Entity> entities) {
        Set<Team> set = Sets.newHashSet();

        for (Entity entity : entities) {
            if (entity instanceof Player) {
                set.add(entity.getTeam());
            } else {
                set.add(null);
            }
        }

        return set.size();
    }

    private static void spreadPositions(
        Vec2 center,
        double spreadDistance,
        ServerLevel level,
        RandomSource random,
        double minX,
        double minZ,
        double maxX,
        double maxZ,
        int maxHeight,
        SpreadPlayersCommand.Position[] positions,
        boolean respectTeams
    ) throws CommandSyntaxException {
        boolean flag = true;
        double d = Float.MAX_VALUE;

        int i;
        for (i = 0; i < 10000 && flag; i++) {
            flag = false;
            d = Float.MAX_VALUE;

            for (int i1 = 0; i1 < positions.length; i1++) {
                SpreadPlayersCommand.Position position = positions[i1];
                int i2 = 0;
                SpreadPlayersCommand.Position position1 = new SpreadPlayersCommand.Position();

                for (int i3 = 0; i3 < positions.length; i3++) {
                    if (i1 != i3) {
                        SpreadPlayersCommand.Position position2 = positions[i3];
                        double d1 = position.dist(position2);
                        d = Math.min(d1, d);
                        if (d1 < spreadDistance) {
                            i2++;
                            position1.x = position1.x + (position2.x - position.x);
                            position1.z = position1.z + (position2.z - position.z);
                        }
                    }
                }

                if (i2 > 0) {
                    position1.x /= i2;
                    position1.z /= i2;
                    double length = position1.getLength();
                    if (length > 0.0) {
                        position1.normalize();
                        position.moveAway(position1);
                    } else {
                        position.randomize(random, minX, minZ, maxX, maxZ);
                    }

                    flag = true;
                }

                if (position.clamp(minX, minZ, maxX, maxZ)) {
                    flag = true;
                }
            }

            if (!flag) {
                for (SpreadPlayersCommand.Position position1 : positions) {
                    if (!position1.isSafe(level, maxHeight)) {
                        position1.randomize(random, minX, minZ, maxX, maxZ);
                        flag = true;
                    }
                }
            }
        }

        if (d == Float.MAX_VALUE) {
            d = 0.0;
        }

        if (i >= 10000) {
            if (respectTeams) {
                throw ERROR_FAILED_TO_SPREAD_TEAMS.create(positions.length, center.x, center.y, String.format(Locale.ROOT, "%.2f", d));
            } else {
                throw ERROR_FAILED_TO_SPREAD_ENTITIES.create(positions.length, center.x, center.y, String.format(Locale.ROOT, "%.2f", d));
            }
        }
    }

    private static double setPlayerPositions(
        Collection<? extends Entity> targets, ServerLevel level, SpreadPlayersCommand.Position[] positions, int maxHeight, boolean respectTeams
    ) {
        double d = 0.0;
        int i = 0;
        Map<Team, SpreadPlayersCommand.Position> map = Maps.newHashMap();

        for (Entity entity : targets) {
            SpreadPlayersCommand.Position position;
            if (respectTeams) {
                Team team = entity instanceof Player ? entity.getTeam() : null;
                if (!map.containsKey(team)) {
                    map.put(team, positions[i++]);
                }

                position = map.get(team);
            } else {
                position = positions[i++];
            }

            entity.teleportTo(
                level,
                Mth.floor(position.x) + 0.5,
                position.getSpawnY(level, maxHeight),
                Mth.floor(position.z) + 0.5,
                Set.of(),
                entity.getYRot(),
                entity.getXRot(),
                true
                , org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.COMMAND // CraftBukkit - handle teleport reason
            );
            double d1 = Double.MAX_VALUE;

            for (SpreadPlayersCommand.Position position1 : positions) {
                if (position != position1) {
                    double d2 = position.dist(position1);
                    d1 = Math.min(d2, d1);
                }
            }

            d += d1;
        }

        return targets.size() < 2 ? 0.0 : d / targets.size();
    }

    private static SpreadPlayersCommand.Position[] createInitialPositions(RandomSource random, int count, double minX, double minZ, double maxX, double maxZ) {
        SpreadPlayersCommand.Position[] positions = new SpreadPlayersCommand.Position[count];

        for (int i = 0; i < positions.length; i++) {
            SpreadPlayersCommand.Position position = new SpreadPlayersCommand.Position();
            position.randomize(random, minX, minZ, maxX, maxZ);
            positions[i] = position;
        }

        return positions;
    }

    static class Position {
        double x;
        double z;

        double dist(SpreadPlayersCommand.Position other) {
            double d = this.x - other.x;
            double d1 = this.z - other.z;
            return Math.sqrt(d * d + d1 * d1);
        }

        void normalize() {
            double length = this.getLength();
            this.x /= length;
            this.z /= length;
        }

        double getLength() {
            return Math.sqrt(this.x * this.x + this.z * this.z);
        }

        public void moveAway(SpreadPlayersCommand.Position other) {
            this.x = this.x - other.x;
            this.z = this.z - other.z;
        }

        public boolean clamp(double minX, double minZ, double maxX, double maxZ) {
            boolean flag = false;
            if (this.x < minX) {
                this.x = minX;
                flag = true;
            } else if (this.x > maxX) {
                this.x = maxX;
                flag = true;
            }

            if (this.z < minZ) {
                this.z = minZ;
                flag = true;
            } else if (this.z > maxZ) {
                this.z = maxZ;
                flag = true;
            }

            return flag;
        }

        public int getSpawnY(BlockGetter level, int y) {
            BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos(this.x, (double)(y + 1), this.z);
            boolean isAir = level.getBlockState(mutableBlockPos).isAir();
            mutableBlockPos.move(Direction.DOWN);
            boolean isAir1 = level.getBlockState(mutableBlockPos).isAir();

            while (mutableBlockPos.getY() > level.getMinY()) {
                mutableBlockPos.move(Direction.DOWN);
                boolean isAir2 = level.getBlockState(mutableBlockPos).isAir();
                if (!isAir2 && isAir1 && isAir) {
                    return mutableBlockPos.getY() + 1;
                }

                isAir = isAir1;
                isAir1 = isAir2;
            }

            return y + 1;
        }

        public boolean isSafe(BlockGetter level, int y) {
            BlockPos blockPos = BlockPos.containing(this.x, this.getSpawnY(level, y) - 1, this.z);
            BlockState blockState = level.getBlockState(blockPos);
            return blockPos.getY() < y && !blockState.liquid() && !blockState.is(BlockTags.FIRE);
        }

        public void randomize(RandomSource random, double minX, double minZ, double maxX, double maxZ) {
            this.x = Mth.nextDouble(random, minX, maxX);
            this.z = Mth.nextDouble(random, minZ, maxZ);
        }
    }
}
