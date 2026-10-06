/*
 * This file is part of Leaves (https://github.com/LeavesMC/Leaves)
 *
 * Leaves is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Leaves is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Leaves. If not, see <https://www.gnu.org/licenses/>.
 */

package org.leavesmc.leaves.bot.agent.configs;

import com.mojang.brigadier.arguments.BoolArgumentType;
import fun.bm.mili.config.modules.function.FakeplayerConfig;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;
import org.leavesmc.leaves.command.CommandContext;

/**
 * Mili start - bot spawner support
 *
 * Controls whether this bot is counted as a "living player that affects spawning" for
 * mob spawners / phantom spawning.
 *
 * <p>By default {@code false}, preserving upstream Leaves behaviour where fakeplayers are
 * invisible to the spawner system. Enabling it makes the bot keep spawners within
 * {@code requiredPlayerRange} (normally 16 blocks) active, which is what a mob farm requires.
 *
 * <p>Note: this does <b>not</b> put the bot into Folia's NearbyPlayers map (that map is driven by
 * real network connections). Instead {@code EntityGetter} falls back to scanning
 * {@code ServerLevel#players()} and picks up bots whose flag is on. Consequence: the fast-path
 * chunk-section lookup no longer short-circuits for bots, so spawner checks near a bot are slightly
 * slower, but correctness is preserved.
 */
public class AffectsSpawningConfig extends AbstractBotConfig<Boolean, AffectsSpawningConfig> {
    private boolean value;

    public AffectsSpawningConfig() {
        super("affects_spawning", BoolArgumentType.bool(), AffectsSpawningConfig::new);
        this.value = FakeplayerConfig.botAffectsSpawning;
    }

    @Override
    public Boolean getValue() {
        return value;
    }

    @Override
    public AbstractBotConfig<Boolean, AffectsSpawningConfig> setBot(ServerBot bot) {
        super.setBot(bot);
        // The field is initialised straight from config in the constructor (so setValue never runs
        // for the default). Push it onto the entity now that we have a handle. This matters because
        // the inherited Paper field defaults to true: without this, a bot configured with
        // affects_spawning=false would still count as a real player for spawners.
        bot.affectsSpawning = this.value;
        return this;
    }

    @Override
    public void setValue(Boolean value) throws IllegalArgumentException {
        this.value = value;
        // Keep the inherited Paper field in sync so entity selectors (PLAYER_AFFECTS_SPAWNING)
        // agree with this config without duplicating the flag.
        if (this.bot != null) {
            this.bot.affectsSpawning = value;
        }
    }

    @Override
    public Boolean loadFromCommand(CommandContext context) {
        return context.getBoolean(getName());
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag nbt) {
        super.save(nbt);
        nbt.putBoolean(getName(), this.getValue());
        return nbt;
    }

    @Override
    public void load(@NotNull CompoundTag nbt) {
        this.setValue(nbt.getBooleanOr(getName(), FakeplayerConfig.botAffectsSpawning));
    }
}
// Mili end - bot spawner support