package net.minecraft.world.scores;

import com.google.common.collect.Sets;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.jspecify.annotations.Nullable;

public class PlayerTeam extends Team {
    private static final int BIT_FRIENDLY_FIRE = 0;
    private static final int BIT_SEE_INVISIBLES = 1;
    private final Scoreboard scoreboard;
    private final String name;
    private final Set<String> players = Sets.newHashSet();
    private Component displayName;
    private Component playerPrefix = CommonComponents.EMPTY;
    private Component playerSuffix = CommonComponents.EMPTY;
    private boolean allowFriendlyFire = true;
    private boolean seeFriendlyInvisibles = true;
    private Team.Visibility nameTagVisibility = Team.Visibility.ALWAYS;
    private Team.Visibility deathMessageVisibility = Team.Visibility.ALWAYS;
    private ChatFormatting color = ChatFormatting.RESET;
    private Team.CollisionRule collisionRule = Team.CollisionRule.ALWAYS;
    private final Style displayNameStyle;

    public PlayerTeam(Scoreboard scoreboard, String name) {
        this.scoreboard = scoreboard;
        this.name = name;
        this.displayName = Component.literal(name);
        this.displayNameStyle = Style.EMPTY.withInsertion(name).withHoverEvent(new HoverEvent.ShowText(Component.literal(name)));
    }

    public PlayerTeam.Packed pack() {
        return new PlayerTeam.Packed(
            this.name,
            Optional.of(this.displayName),
            this.color != ChatFormatting.RESET ? Optional.of(this.color) : Optional.empty(),
            this.allowFriendlyFire,
            this.seeFriendlyInvisibles,
            this.playerPrefix,
            this.playerSuffix,
            this.nameTagVisibility,
            this.deathMessageVisibility,
            this.collisionRule,
            List.copyOf(this.players)
        );
    }

    public Scoreboard getScoreboard() {
        return this.scoreboard;
    }

    @Override
    public String getName() {
        return this.name;
    }

    public Component getDisplayName() {
        return this.displayName;
    }

    public MutableComponent getFormattedDisplayName() {
        MutableComponent mutableComponent = ComponentUtils.wrapInSquareBrackets(this.displayName.copy().withStyle(this.displayNameStyle));
        ChatFormatting color = this.getColor();
        if (color != ChatFormatting.RESET) {
            mutableComponent.withStyle(color);
        }

        return mutableComponent;
    }

    public void setDisplayName(Component name) {
        if (name == null) {
            throw new IllegalArgumentException("Name cannot be null");
        } else {
            this.displayName = name;
            this.scoreboard.onTeamChanged(this);
        }
    }

    public void setPlayerPrefix(@Nullable Component playerPrefix) {
        this.playerPrefix = playerPrefix == null ? CommonComponents.EMPTY : playerPrefix;
        this.scoreboard.onTeamChanged(this);
    }

    public Component getPlayerPrefix() {
        return this.playerPrefix;
    }

    public void setPlayerSuffix(@Nullable Component playerSuffix) {
        this.playerSuffix = playerSuffix == null ? CommonComponents.EMPTY : playerSuffix;
        this.scoreboard.onTeamChanged(this);
    }

    public Component getPlayerSuffix() {
        return this.playerSuffix;
    }

    @Override
    public Collection<String> getPlayers() {
        return this.players;
    }

    @Override
    public MutableComponent getFormattedName(Component name) {
        MutableComponent mutableComponent = Component.empty().append(this.playerPrefix).append(name).append(this.playerSuffix);
        ChatFormatting color = this.getColor();
        if (color != ChatFormatting.RESET) {
            mutableComponent.withStyle(color);
        }

        return mutableComponent;
    }

    public static MutableComponent formatNameForTeam(@Nullable Team team, Component playerName) {
        return team == null ? playerName.copy() : team.getFormattedName(playerName);
    }

    @Override
    public boolean isAllowFriendlyFire() {
        return this.allowFriendlyFire;
    }

    public void setAllowFriendlyFire(boolean friendlyFire) {
        this.allowFriendlyFire = friendlyFire;
        this.scoreboard.onTeamChanged(this);
    }

    @Override
    public boolean canSeeFriendlyInvisibles() {
        return this.seeFriendlyInvisibles;
    }

    public void setSeeFriendlyInvisibles(boolean friendlyInvisibles) {
        this.seeFriendlyInvisibles = friendlyInvisibles;
        this.scoreboard.onTeamChanged(this);
    }

    @Override
    public Team.Visibility getNameTagVisibility() {
        return this.nameTagVisibility;
    }

    @Override
    public Team.Visibility getDeathMessageVisibility() {
        return this.deathMessageVisibility;
    }

    public void setNameTagVisibility(Team.Visibility visibility) {
        this.nameTagVisibility = visibility;
        this.scoreboard.onTeamChanged(this);
    }

    public void setDeathMessageVisibility(Team.Visibility visibility) {
        this.deathMessageVisibility = visibility;
        this.scoreboard.onTeamChanged(this);
    }

    @Override
    public Team.CollisionRule getCollisionRule() {
        return this.collisionRule;
    }

    public void setCollisionRule(Team.CollisionRule rule) {
        this.collisionRule = rule;
        this.scoreboard.onTeamChanged(this);
    }

    public int packOptions() {
        int i = 0;
        if (this.isAllowFriendlyFire()) {
            i |= 1;
        }

        if (this.canSeeFriendlyInvisibles()) {
            i |= 2;
        }

        return i;
    }

    public void unpackOptions(int flags) {
        this.setAllowFriendlyFire((flags & 1) > 0);
        this.setSeeFriendlyInvisibles((flags & 2) > 0);
    }

    public void setColor(ChatFormatting color) {
        this.color = color;
        this.scoreboard.onTeamChanged(this);
    }

    @Override
    public ChatFormatting getColor() {
        return this.color;
    }

    public record Packed(
        String name,
        Optional<Component> displayName,
        Optional<ChatFormatting> color,
        boolean allowFriendlyFire,
        boolean seeFriendlyInvisibles,
        Component memberNamePrefix,
        Component memberNameSuffix,
        Team.Visibility nameTagVisibility,
        Team.Visibility deathMessageVisibility,
        Team.CollisionRule collisionRule,
        List<String> players
    ) {
        public static final Codec<PlayerTeam.Packed> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Codec.STRING.fieldOf("Name").forGetter(PlayerTeam.Packed::name),
                    ComponentSerialization.CODEC.optionalFieldOf("DisplayName").forGetter(PlayerTeam.Packed::displayName),
                    io.papermc.paper.util.PaperCodecs.lenientCodec("TeamColor", ChatFormatting.COLOR_CODEC).forGetter(PlayerTeam.Packed::color), // Paper - better fail on decode
                    Codec.BOOL.optionalFieldOf("AllowFriendlyFire", true).forGetter(PlayerTeam.Packed::allowFriendlyFire),
                    Codec.BOOL.optionalFieldOf("SeeFriendlyInvisibles", true).forGetter(PlayerTeam.Packed::seeFriendlyInvisibles),
                    ComponentSerialization.CODEC.optionalFieldOf("MemberNamePrefix", CommonComponents.EMPTY).forGetter(PlayerTeam.Packed::memberNamePrefix),
                    ComponentSerialization.CODEC.optionalFieldOf("MemberNameSuffix", CommonComponents.EMPTY).forGetter(PlayerTeam.Packed::memberNameSuffix),
                    Team.Visibility.CODEC.optionalFieldOf("NameTagVisibility", Team.Visibility.ALWAYS).forGetter(PlayerTeam.Packed::nameTagVisibility),
                    Team.Visibility.CODEC
                        .optionalFieldOf("DeathMessageVisibility", Team.Visibility.ALWAYS)
                        .forGetter(PlayerTeam.Packed::deathMessageVisibility),
                    Team.CollisionRule.CODEC.optionalFieldOf("CollisionRule", Team.CollisionRule.ALWAYS).forGetter(PlayerTeam.Packed::collisionRule),
                    Codec.STRING.listOf().optionalFieldOf("Players", List.of()).forGetter(PlayerTeam.Packed::players)
                )
                .apply(instance, PlayerTeam.Packed::new)
        );
    }
}
