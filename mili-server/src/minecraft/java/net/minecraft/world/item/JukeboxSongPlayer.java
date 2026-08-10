package net.minecraft.world.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class JukeboxSongPlayer {
    public static final int PLAY_EVENT_INTERVAL_TICKS = 20;
    private long ticksSinceSongStarted;
    public @Nullable Holder<JukeboxSong> song;
    private final BlockPos blockPos;
    private final JukeboxSongPlayer.OnSongChanged onSongChanged;

    public JukeboxSongPlayer(JukeboxSongPlayer.OnSongChanged onSongChanged, BlockPos blockPos) {
        this.onSongChanged = onSongChanged;
        this.blockPos = blockPos;
    }

    public boolean isPlaying() {
        return this.song != null;
    }

    public @Nullable JukeboxSong getSong() {
        return this.song == null ? null : this.song.value();
    }

    public long getTicksSinceSongStarted() {
        return this.ticksSinceSongStarted;
    }

    public void setSongWithoutPlaying(Holder<JukeboxSong> song, long ticksSinceSongStarted) {
        if (!song.value().hasFinished(ticksSinceSongStarted)) {
            this.song = song;
            this.ticksSinceSongStarted = ticksSinceSongStarted;
        }
    }

    public void play(LevelAccessor level, Holder<JukeboxSong> song) {
        this.song = song;
        this.ticksSinceSongStarted = 0L;
        int id = level.registryAccess().lookupOrThrow(Registries.JUKEBOX_SONG).getId(this.song.value());
        level.levelEvent(null, LevelEvent.SOUND_PLAY_JUKEBOX_SONG, this.blockPos, id);
        this.onSongChanged.notifyChange();
    }

    public void stop(LevelAccessor level, @Nullable BlockState state) {
        if (this.song != null) {
            this.song = null;
            this.ticksSinceSongStarted = 0L;
            level.gameEvent(GameEvent.JUKEBOX_STOP_PLAY, this.blockPos, GameEvent.Context.of(state));
            level.levelEvent(LevelEvent.SOUND_STOP_JUKEBOX_SONG, this.blockPos, 0);
            this.onSongChanged.notifyChange();
        }
    }

    public void tick(LevelAccessor level, @Nullable BlockState state) {
        if (this.song != null) {
            if (this.song.value().hasFinished(this.ticksSinceSongStarted)) {
                this.stop(level, state);
            } else {
                if (this.shouldEmitJukeboxPlayingEvent()) {
                    level.gameEvent(GameEvent.JUKEBOX_PLAY, this.blockPos, GameEvent.Context.of(state));
                    spawnMusicParticles(level, this.blockPos);
                }

                this.ticksSinceSongStarted++;
            }
        }
    }

    private boolean shouldEmitJukeboxPlayingEvent() {
        return this.ticksSinceSongStarted % 20L == 0L;
    }

    private static void spawnMusicParticles(LevelAccessor level, BlockPos pos) {
        if (level instanceof ServerLevel serverLevel) {
            Vec3 vec3 = Vec3.atBottomCenterOf(pos).add(0.0, 1.2F, 0.0);
            float f = level.getRandom().nextInt(4) / 24.0F;
            serverLevel.sendParticles(ParticleTypes.NOTE, vec3.x(), vec3.y(), vec3.z(), 0, f, 0.0, 0.0, 1.0);
        }
    }

    @FunctionalInterface
    public interface OnSongChanged {
        void notifyChange();
    }
}
