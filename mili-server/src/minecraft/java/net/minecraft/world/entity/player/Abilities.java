package net.minecraft.world.entity.player;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public class Abilities {
    private static final boolean DEFAULT_INVULNERABLE = false;
    private static final boolean DEFAULY_FLYING = false;
    private static final boolean DEFAULT_MAY_FLY = false;
    private static final boolean DEFAULT_INSTABUILD = false;
    private static final boolean DEFAULT_MAY_BUILD = true;
    private static final float DEFAULT_FLYING_SPEED = 0.05F;
    private static final float DEFAULT_WALKING_SPEED = 0.1F;
    public boolean invulnerable;
    public boolean flying;
    public boolean mayfly;
    public boolean instabuild;
    public boolean mayBuild = true;
    public float flyingSpeed = 0.05F;
    public float walkingSpeed = 0.1F;

    public float getFlyingSpeed() {
        return this.flyingSpeed;
    }

    public void setFlyingSpeed(float flyingSpeed) {
        this.flyingSpeed = flyingSpeed;
    }

    public float getWalkingSpeed() {
        return this.walkingSpeed;
    }

    public void setWalkingSpeed(float walkingSpeed) {
        this.walkingSpeed = walkingSpeed;
    }

    public Abilities.Packed pack() {
        return new Abilities.Packed(this.invulnerable, this.flying, this.mayfly, this.instabuild, this.mayBuild, this.flyingSpeed, this.walkingSpeed);
    }

    public void apply(Abilities.Packed abilities) {
        this.invulnerable = abilities.invulnerable;
        this.flying = abilities.flying;
        this.mayfly = abilities.mayFly;
        this.instabuild = abilities.instabuild;
        this.mayBuild = abilities.mayBuild;
        this.flyingSpeed = abilities.flyingSpeed;
        this.walkingSpeed = abilities.walkingSpeed;
    }

    public record Packed(boolean invulnerable, boolean flying, boolean mayFly, boolean instabuild, boolean mayBuild, float flyingSpeed, float walkingSpeed) {
        public static final Codec<Abilities.Packed> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Codec.BOOL.fieldOf("invulnerable").orElse(false).forGetter(Abilities.Packed::invulnerable),
                    Codec.BOOL.fieldOf("flying").orElse(false).forGetter(Abilities.Packed::flying),
                    Codec.BOOL.fieldOf("mayfly").orElse(false).forGetter(Abilities.Packed::mayFly),
                    Codec.BOOL.fieldOf("instabuild").orElse(false).forGetter(Abilities.Packed::instabuild),
                    Codec.BOOL.fieldOf("mayBuild").orElse(true).forGetter(Abilities.Packed::mayBuild),
                    Codec.FLOAT.fieldOf("flySpeed").orElse(0.05F).forGetter(Abilities.Packed::flyingSpeed),
                    Codec.FLOAT.fieldOf("walkSpeed").orElse(0.1F).forGetter(Abilities.Packed::walkingSpeed)
                )
                .apply(instance, Abilities.Packed::new)
        );
    }
}
