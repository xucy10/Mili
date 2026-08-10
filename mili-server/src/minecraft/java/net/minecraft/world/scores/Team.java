package net.minecraft.world.scores;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.Collection;
import java.util.function.IntFunction;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.Nullable;

public abstract class Team {
    public boolean isAlliedTo(@Nullable Team other) {
        return other != null && this == other;
    }

    public abstract String getName();

    public abstract MutableComponent getFormattedName(Component name);

    public abstract boolean canSeeFriendlyInvisibles();

    public abstract boolean isAllowFriendlyFire();

    public abstract Team.Visibility getNameTagVisibility();

    public abstract ChatFormatting getColor();

    public abstract Collection<String> getPlayers();

    public abstract Team.Visibility getDeathMessageVisibility();

    public abstract Team.CollisionRule getCollisionRule();

    public static enum CollisionRule implements StringRepresentable {
        ALWAYS("always", 0),
        NEVER("never", 1),
        PUSH_OTHER_TEAMS("pushOtherTeams", 2),
        PUSH_OWN_TEAM("pushOwnTeam", 3);

        public static final Codec<Team.CollisionRule> CODEC = StringRepresentable.fromEnum(Team.CollisionRule::values);
        private static final IntFunction<Team.CollisionRule> BY_ID = ByIdMap.continuous(
            collisionRule -> collisionRule.id, values(), ByIdMap.OutOfBoundsStrategy.ZERO
        );
        public static final StreamCodec<ByteBuf, Team.CollisionRule> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, collisionRule -> collisionRule.id);
        public final String name;
        public final int id;

        private CollisionRule(final String name, final int id) {
            this.name = name;
            this.id = id;
        }

        public Component getDisplayName() {
            return Component.translatable("team.collision." + this.name);
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }

    public static enum Visibility implements StringRepresentable {
        ALWAYS("always", 0),
        NEVER("never", 1),
        HIDE_FOR_OTHER_TEAMS("hideForOtherTeams", 2),
        HIDE_FOR_OWN_TEAM("hideForOwnTeam", 3);

        public static final Codec<Team.Visibility> CODEC = StringRepresentable.fromEnum(Team.Visibility::values);
        private static final IntFunction<Team.Visibility> BY_ID = ByIdMap.continuous(visibility -> visibility.id, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
        public static final StreamCodec<ByteBuf, Team.Visibility> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, visibility -> visibility.id);
        public final String name;
        public final int id;

        private Visibility(final String name, final int id) {
            this.name = name;
            this.id = id;
        }

        public Component getDisplayName() {
            return Component.translatable("team.visibility." + this.name);
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }
}
