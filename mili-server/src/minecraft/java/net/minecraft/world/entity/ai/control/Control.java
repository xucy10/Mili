package net.minecraft.world.entity.ai.control;

import net.minecraft.util.Mth;

public interface Control {
    default float rotateTowards(float current, float wanted, float maxSpeed) {
        float f = Mth.degreesDifference(current, wanted);
        float f1 = Mth.clamp(f, -maxSpeed, maxSpeed);
        return current + f1;
    }
}
