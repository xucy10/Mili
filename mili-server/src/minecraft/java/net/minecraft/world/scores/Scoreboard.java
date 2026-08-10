package net.minecraft.world.scores;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class Scoreboard {
    public static final String HIDDEN_SCORE_PREFIX = "#";
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Object2ObjectMap<String, Objective> objectivesByName = new Object2ObjectOpenHashMap<>(16, 0.5F);
    private final Reference2ObjectMap<ObjectiveCriteria, List<Objective>> objectivesByCriteria = new Reference2ObjectOpenHashMap<>();
    private final Map<String, PlayerScores> playerScores = new Object2ObjectOpenHashMap<>(16, 0.5F);
    private final Map<DisplaySlot, Objective> displayObjectives = new EnumMap<>(DisplaySlot.class);
    private final Object2ObjectMap<String, PlayerTeam> teamsByName = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<String, PlayerTeam> teamsByPlayer = new Object2ObjectOpenHashMap<>();

    public @Nullable Objective getObjective(@Nullable String name) {
        return this.objectivesByName.get(name);
    }

    public Objective addObjective(
        String name,
        ObjectiveCriteria criteria,
        Component displayName,
        ObjectiveCriteria.RenderType renderType,
        boolean displayAutoUpdate,
        @Nullable NumberFormat numberFormat
    ) {
        if (this.objectivesByName.containsKey(name)) {
            throw new IllegalArgumentException("An objective with the name '" + name + "' already exists!");
        } else {
            Objective objective = new Objective(this, name, criteria, displayName, renderType, displayAutoUpdate, numberFormat);
            this.objectivesByCriteria.computeIfAbsent(criteria, object -> Lists.newArrayList()).add(objective);
            this.objectivesByName.put(name, objective);
            this.onObjectiveAdded(objective);
            return objective;
        }
    }

    public final void forAllObjectives(ObjectiveCriteria criteria, ScoreHolder scoreHolder, Consumer<ScoreAccess> action) {
        this.objectivesByCriteria
            .getOrDefault(criteria, Collections.emptyList())
            .forEach(objective -> action.accept(this.getOrCreatePlayerScore(scoreHolder, objective, true)));
    }

    private PlayerScores getOrCreatePlayerInfo(String playerName) {
        return this.playerScores.computeIfAbsent(playerName, string -> new PlayerScores());
    }

    public ScoreAccess getOrCreatePlayerScore(ScoreHolder scoreHolder, Objective objective) {
        return this.getOrCreatePlayerScore(scoreHolder, objective, false);
    }

    public ScoreAccess getOrCreatePlayerScore(final ScoreHolder scoreHolder, final Objective objective, boolean readOnly) {
        final boolean flag = readOnly || !objective.getCriteria().isReadOnly();
        PlayerScores playerInfo = this.getOrCreatePlayerInfo(scoreHolder.getScoreboardName());
        final MutableBoolean mutableBoolean = new MutableBoolean();
        final Score score = playerInfo.getOrCreate(objective, score1 -> mutableBoolean.setTrue());
        return new ScoreAccess() {
            @Override
            public int get() {
                return score.value();
            }

            @Override
            public void set(int value) {
                if (!flag) {
                    throw new IllegalStateException("Cannot modify read-only score");
                } else {
                    boolean isTrue = mutableBoolean.isTrue();
                    if (objective.displayAutoUpdate()) {
                        Component displayName = scoreHolder.getDisplayName();
                        if (displayName != null && !displayName.equals(score.display())) {
                            score.display(displayName);
                            isTrue = true;
                        }
                    }

                    if (value != score.value()) {
                        score.value(value);
                        isTrue = true;
                    }

                    if (isTrue) {
                        this.sendScoreToPlayers();
                    }
                }
            }

            @Override
            public @Nullable Component display() {
                return score.display();
            }

            @Override
            public void display(@Nullable Component value) {
                if (mutableBoolean.isTrue() || !Objects.equals(value, score.display())) {
                    score.display(value);
                    this.sendScoreToPlayers();
                }
            }

            @Override
            public void numberFormatOverride(@Nullable NumberFormat format) {
                score.numberFormat(format);
                this.sendScoreToPlayers();
            }

            @Override
            public boolean locked() {
                return score.isLocked();
            }

            @Override
            public void unlock() {
                this.setLocked(false);
            }

            @Override
            public void lock() {
                this.setLocked(true);
            }

            private void setLocked(boolean locked) {
                score.setLocked(locked);
                if (mutableBoolean.isTrue()) {
                    this.sendScoreToPlayers();
                }

                Scoreboard.this.onScoreLockChanged(scoreHolder, objective);
            }

            private void sendScoreToPlayers() {
                Scoreboard.this.onScoreChanged(scoreHolder, objective, score);
                mutableBoolean.setFalse();
            }
        };
    }

    public @Nullable ReadOnlyScoreInfo getPlayerScoreInfo(ScoreHolder scoreHolder, Objective objective) {
        PlayerScores playerScores = this.playerScores.get(scoreHolder.getScoreboardName());
        return playerScores != null ? playerScores.get(objective) : null;
    }

    public Collection<PlayerScoreEntry> listPlayerScores(Objective objective) {
        List<PlayerScoreEntry> list = new ArrayList<>();
        this.playerScores.forEach((string, playerScores) -> {
            Score score = playerScores.get(objective);
            if (score != null) {
                list.add(new PlayerScoreEntry(string, score.value(), score.display(), score.numberFormat()));
            }
        });
        return list;
    }

    public Collection<Objective> getObjectives() {
        return this.objectivesByName.values();
    }

    public Collection<String> getObjectiveNames() {
        return this.objectivesByName.keySet();
    }

    public Collection<ScoreHolder> getTrackedPlayers() {
        return this.playerScores.keySet().stream().map(ScoreHolder::forNameOnly).toList();
    }

    public void resetAllPlayerScores(ScoreHolder scoreHolder) {
        PlayerScores playerScores = this.playerScores.remove(scoreHolder.getScoreboardName());
        if (playerScores != null) {
            this.onPlayerRemoved(scoreHolder);
        }
    }

    public void resetSinglePlayerScore(ScoreHolder scoreHolder, Objective objective) {
        PlayerScores playerScores = this.playerScores.get(scoreHolder.getScoreboardName());
        if (playerScores != null) {
            boolean flag = playerScores.remove(objective);
            if (!playerScores.hasScores()) {
                PlayerScores playerScores1 = this.playerScores.remove(scoreHolder.getScoreboardName());
                if (playerScores1 != null) {
                    this.onPlayerRemoved(scoreHolder);
                }
            } else if (flag) {
                this.onPlayerScoreRemoved(scoreHolder, objective);
            }
        }
    }

    public Object2IntMap<Objective> listPlayerScores(ScoreHolder scoreHolder) {
        PlayerScores playerScores = this.playerScores.get(scoreHolder.getScoreboardName());
        return playerScores != null ? playerScores.listScores() : Object2IntMaps.emptyMap();
    }

    public void removeObjective(Objective objective) {
        this.objectivesByName.remove(objective.getName());

        for (DisplaySlot displaySlot : DisplaySlot.values()) {
            if (this.getDisplayObjective(displaySlot) == objective) {
                this.setDisplayObjective(displaySlot, null);
            }
        }

        List<Objective> list = this.objectivesByCriteria.get(objective.getCriteria());
        if (list != null) {
            list.remove(objective);
        }

        for (PlayerScores playerScores : this.playerScores.values()) {
            playerScores.remove(objective);
        }

        this.onObjectiveRemoved(objective);
    }

    public void setDisplayObjective(DisplaySlot slot, @Nullable Objective objective) {
        this.displayObjectives.put(slot, objective);
    }

    public @Nullable Objective getDisplayObjective(DisplaySlot slot) {
        return this.displayObjectives.get(slot);
    }

    public @Nullable PlayerTeam getPlayerTeam(String name) {
        return this.teamsByName.get(name);
    }

    public PlayerTeam addPlayerTeam(String name) {
        PlayerTeam playerTeam = this.getPlayerTeam(name);
        if (playerTeam != null) {
            LOGGER.warn("Requested creation of existing team '{}'", name);
            return playerTeam;
        } else {
            playerTeam = new PlayerTeam(this, name);
            this.teamsByName.put(name, playerTeam);
            this.onTeamAdded(playerTeam);
            return playerTeam;
        }
    }

    public void removePlayerTeam(PlayerTeam team) {
        this.teamsByName.remove(team.getName());

        for (String string : team.getPlayers()) {
            this.teamsByPlayer.remove(string);
        }

        this.onTeamRemoved(team);
    }

    public boolean addPlayerToTeam(String playerName, PlayerTeam team) {
        if (this.getPlayersTeam(playerName) != null) {
            this.removePlayerFromTeam(playerName);
        }

        this.teamsByPlayer.put(playerName, team);
        return team.getPlayers().add(playerName);
    }

    public boolean removePlayerFromTeam(String playerName) {
        PlayerTeam playersTeam = this.getPlayersTeam(playerName);
        if (playersTeam != null) {
            this.removePlayerFromTeam(playerName, playersTeam);
            return true;
        } else {
            return false;
        }
    }

    public void removePlayerFromTeam(String playerName, PlayerTeam team) {
        if (this.getPlayersTeam(playerName) != team) {
            throw new IllegalStateException("Player is either on another team or not on any team. Cannot remove from team '" + team.getName() + "'.");
        } else {
            this.teamsByPlayer.remove(playerName);
            team.getPlayers().remove(playerName);
        }
    }

    public Collection<String> getTeamNames() {
        return this.teamsByName.keySet();
    }

    public Collection<PlayerTeam> getPlayerTeams() {
        return this.teamsByName.values();
    }

    public @Nullable PlayerTeam getPlayersTeam(String playerName) {
        return this.teamsByPlayer.get(playerName);
    }

    public void onObjectiveAdded(Objective objective) {
    }

    public void onObjectiveChanged(Objective objective) {
    }

    public void onObjectiveRemoved(Objective objective) {
    }

    protected void onScoreChanged(ScoreHolder scoreHolder, Objective objective, Score score) {
    }

    protected void onScoreLockChanged(ScoreHolder scoreHolder, Objective objective) {
    }

    public void onPlayerRemoved(ScoreHolder scoreHolder) {
    }

    public void onPlayerScoreRemoved(ScoreHolder scoreHolder, Objective objective) {
    }

    public void onTeamAdded(PlayerTeam team) {
    }

    public void onTeamChanged(PlayerTeam team) {
    }

    public void onTeamRemoved(PlayerTeam team) {
    }

    public void entityRemoved(Entity entity) {
        if (!(entity instanceof Player) && !entity.isAlive()) {
            this.resetAllPlayerScores(entity);
            this.removePlayerFromTeam(entity.getScoreboardName());
        }
    }

    protected List<Scoreboard.PackedScore> packPlayerScores() {
        return this.playerScores
            .entrySet()
            .stream()
            .flatMap(
                entry -> {
                    String string = entry.getKey();
                    return entry.getValue()
                        .listRawScores()
                        .entrySet()
                        .stream()
                        .map(entry1 -> new Scoreboard.PackedScore(string, entry1.getKey().getName(), entry1.getValue().pack()));
                }
            )
            .toList();
    }

    protected void loadPlayerScore(Scoreboard.PackedScore score) {
        Objective objective = this.getObjective(score.objective);
        if (objective == null) {
            LOGGER.error("Unknown objective {} for name {}, ignoring", score.objective, score.owner);
        } else {
            this.getOrCreatePlayerInfo(score.owner).setScore(objective, new Score(score.score));
        }
    }

    protected List<PlayerTeam.Packed> packPlayerTeams() {
        return this.getPlayerTeams().stream().filter(p -> io.papermc.paper.configuration.GlobalConfiguration.get().scoreboards.saveEmptyScoreboardTeams || !p.getPlayers().isEmpty()).map(PlayerTeam::pack).toList(); // Paper - Don't save empty scoreboard teams to scoreboard.dat
    }

    protected void loadPlayerTeam(PlayerTeam.Packed packed) {
        PlayerTeam playerTeam = this.addPlayerTeam(packed.name());
        packed.displayName().ifPresent(playerTeam::setDisplayName);
        packed.color().ifPresent(playerTeam::setColor);
        playerTeam.setAllowFriendlyFire(packed.allowFriendlyFire());
        playerTeam.setSeeFriendlyInvisibles(packed.seeFriendlyInvisibles());
        playerTeam.setPlayerPrefix(packed.memberNamePrefix());
        playerTeam.setPlayerSuffix(packed.memberNameSuffix());
        playerTeam.setNameTagVisibility(packed.nameTagVisibility());
        playerTeam.setDeathMessageVisibility(packed.deathMessageVisibility());
        playerTeam.setCollisionRule(packed.collisionRule());

        for (String string : packed.players()) {
            this.addPlayerToTeam(string, playerTeam);
        }
    }

    protected List<Objective.Packed> packObjectives() {
        return this.getObjectives().stream().map(Objective::pack).toList();
    }

    protected void loadObjective(Objective.Packed packed) {
        this.addObjective(
            packed.name(), packed.criteria(), packed.displayName(), packed.renderType(), packed.displayAutoUpdate(), packed.numberFormat().orElse(null)
        );
    }

    protected Map<DisplaySlot, String> packDisplaySlots() {
        Map<DisplaySlot, String> map = new EnumMap<>(DisplaySlot.class);

        for (DisplaySlot displaySlot : DisplaySlot.values()) {
            Objective displayObjective = this.getDisplayObjective(displaySlot);
            if (displayObjective != null) {
                map.put(displaySlot, displayObjective.getName());
            }
        }

        return map;
    }

    public record PackedScore(String owner, String objective, Score.Packed score) {
        public static final Codec<Scoreboard.PackedScore> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Codec.STRING.fieldOf("Name").forGetter(Scoreboard.PackedScore::owner),
                    Codec.STRING.fieldOf("Objective").forGetter(Scoreboard.PackedScore::objective),
                    Score.Packed.MAP_CODEC.forGetter(Scoreboard.PackedScore::score)
                )
                .apply(instance, Scoreboard.PackedScore::new)
        );
    }
}
