package net.minecraft.world;

import javax.annotation.concurrent.Immutable;
import net.minecraft.util.Mth;

@Immutable
public class DifficultyInstance {
    private static final float DIFFICULTY_TIME_GLOBAL_OFFSET = -72000.0F;
    private static final float MAX_DIFFICULTY_TIME_GLOBAL = 1440000.0F;
    private static final float MAX_DIFFICULTY_TIME_LOCAL = 3600000.0F;
    private final Difficulty base;
    private final float effectiveDifficulty;

    public DifficultyInstance(Difficulty base, long levelTime, long chunkInhabitedTime, float moonPhaseFactor) {
        this.base = base;
        this.effectiveDifficulty = this.calculateDifficulty(base, levelTime, chunkInhabitedTime, moonPhaseFactor);
    }

    public Difficulty getDifficulty() {
        return this.base;
    }

    public float getEffectiveDifficulty() {
        return this.effectiveDifficulty;
    }

    public boolean isHard() {
        return this.effectiveDifficulty >= Difficulty.HARD.ordinal();
    }

    public boolean isHarderThan(float difficulty) {
        return this.effectiveDifficulty > difficulty;
    }

    public float getSpecialMultiplier() {
        if (this.effectiveDifficulty < 2.0F) {
            return 0.0F;
        } else {
            return this.effectiveDifficulty > 4.0F ? 1.0F : (this.effectiveDifficulty - 2.0F) / 2.0F;
        }
    }

    private float calculateDifficulty(Difficulty difficulty, long levelTime, long chunkInhabitedTime, float moonPhaseFactor) {
        if (difficulty == Difficulty.PEACEFUL) {
            return 0.0F;
        } else {
            boolean flag = difficulty == Difficulty.HARD;
            float f = 0.75F;
            float f1 = Mth.clamp(((float)levelTime + -72000.0F) / 1440000.0F, 0.0F, 1.0F) * 0.25F;
            f += f1;
            float f2 = 0.0F;
            f2 += Mth.clamp((float)chunkInhabitedTime / 3600000.0F, 0.0F, 1.0F) * (flag ? 1.0F : 0.75F);
            f2 += Mth.clamp(moonPhaseFactor * 0.25F, 0.0F, f1);
            if (difficulty == Difficulty.EASY) {
                f2 *= 0.5F;
            }

            f += f2;
            return difficulty.getId() * f;
        }
    }
}
