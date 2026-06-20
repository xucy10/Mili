package net.minecraft.world.damagesource;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.Fireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class DamageSources {
    private final Registry<DamageType> damageTypes;
    private final DamageSource inFire;
    private final DamageSource campfire;
    private final DamageSource lightningBolt;
    private final DamageSource onFire;
    private final DamageSource lava;
    private final DamageSource hotFloor;
    private final DamageSource inWall;
    private final DamageSource cramming;
    private final DamageSource drown;
    private final DamageSource starve;
    private final DamageSource cactus;
    private final DamageSource fall;
    private final DamageSource enderPearl;
    private final DamageSource flyIntoWall;
    private final DamageSource fellOutOfWorld;
    private final DamageSource generic;
    private final DamageSource magic;
    private final DamageSource wither;
    private final DamageSource dragonBreath;
    private final DamageSource dryOut;
    private final DamageSource sweetBerryBush;
    private final DamageSource freeze;
    private final DamageSource stalagmite;
    private final DamageSource outsideBorder;
    private final DamageSource genericKill;

    public DamageSources(RegistryAccess registry) {
        this.damageTypes = registry.lookupOrThrow(Registries.DAMAGE_TYPE);
        this.inFire = this.source(DamageTypes.IN_FIRE);
        this.campfire = this.source(DamageTypes.CAMPFIRE);
        this.lightningBolt = this.source(DamageTypes.LIGHTNING_BOLT);
        this.onFire = this.source(DamageTypes.ON_FIRE);
        this.lava = this.source(DamageTypes.LAVA);
        this.hotFloor = this.source(DamageTypes.HOT_FLOOR);
        this.inWall = this.source(DamageTypes.IN_WALL);
        this.cramming = this.source(DamageTypes.CRAMMING);
        this.drown = this.source(DamageTypes.DROWN);
        this.starve = this.source(DamageTypes.STARVE);
        this.cactus = this.source(DamageTypes.CACTUS);
        this.fall = this.source(DamageTypes.FALL);
        this.enderPearl = this.source(DamageTypes.ENDER_PEARL);
        this.flyIntoWall = this.source(DamageTypes.FLY_INTO_WALL);
        this.fellOutOfWorld = this.source(DamageTypes.FELL_OUT_OF_WORLD);
        this.generic = this.source(DamageTypes.GENERIC);
        this.magic = this.source(DamageTypes.MAGIC);
        this.wither = this.source(DamageTypes.WITHER);
        this.dragonBreath = this.source(DamageTypes.DRAGON_BREATH);
        this.dryOut = this.source(DamageTypes.DRY_OUT);
        this.sweetBerryBush = this.source(DamageTypes.SWEET_BERRY_BUSH);
        this.freeze = this.source(DamageTypes.FREEZE);
        this.stalagmite = this.source(DamageTypes.STALAGMITE);
        this.outsideBorder = this.source(DamageTypes.OUTSIDE_BORDER);
        this.genericKill = this.source(DamageTypes.GENERIC_KILL);
    }

    private DamageSource source(ResourceKey<DamageType> damageTypeKey) {
        return new DamageSource(this.damageTypes.getOrThrow(damageTypeKey));
    }

    private DamageSource source(ResourceKey<DamageType> damageTypeKey, @Nullable Entity entity) {
        return new DamageSource(this.damageTypes.getOrThrow(damageTypeKey), entity);
    }

    private DamageSource source(ResourceKey<DamageType> damageTypeKey, @Nullable Entity causingEntity, @Nullable Entity directEntity) {
        return new DamageSource(this.damageTypes.getOrThrow(damageTypeKey), causingEntity, directEntity);
    }

    public DamageSource inFire() {
        return this.inFire;
    }

    public DamageSource campfire() {
        return this.campfire;
    }

    public DamageSource lightningBolt() {
        return this.lightningBolt;
    }

    public DamageSource onFire() {
        return this.onFire;
    }

    public DamageSource lava() {
        return this.lava;
    }

    public DamageSource hotFloor() {
        return this.hotFloor;
    }

    public DamageSource inWall() {
        return this.inWall;
    }

    public DamageSource cramming() {
        return this.cramming;
    }

    public DamageSource drown() {
        return this.drown;
    }

    public DamageSource starve() {
        return this.starve;
    }

    public DamageSource cactus() {
        return this.cactus;
    }

    public DamageSource fall() {
        return this.fall;
    }

    public DamageSource enderPearl() {
        return this.enderPearl;
    }

    public DamageSource flyIntoWall() {
        return this.flyIntoWall;
    }

    public DamageSource fellOutOfWorld() {
        return this.fellOutOfWorld;
    }

    public DamageSource generic() {
        return this.generic;
    }

    public DamageSource magic() {
        return this.magic;
    }

    public DamageSource wither() {
        return this.wither;
    }

    public DamageSource dragonBreath() {
        return this.dragonBreath;
    }

    public DamageSource dryOut() {
        return this.dryOut;
    }

    public DamageSource sweetBerryBush() {
        return this.sweetBerryBush;
    }

    public DamageSource freeze() {
        return this.freeze;
    }

    public DamageSource stalagmite() {
        return this.stalagmite;
    }

    public DamageSource fallingBlock(Entity entity) {
        return this.source(DamageTypes.FALLING_BLOCK, entity);
    }

    public DamageSource anvil(Entity entity) {
        return this.source(DamageTypes.FALLING_ANVIL, entity);
    }

    public DamageSource fallingStalactite(Entity entity) {
        return this.source(DamageTypes.FALLING_STALACTITE, entity);
    }

    public DamageSource sting(LivingEntity entity) {
        return this.source(DamageTypes.STING, entity);
    }

    public DamageSource mobAttack(LivingEntity mob) {
        return this.source(DamageTypes.MOB_ATTACK, mob);
    }

    public DamageSource noAggroMobAttack(LivingEntity mob) {
        return this.source(DamageTypes.MOB_ATTACK_NO_AGGRO, mob);
    }

    public DamageSource playerAttack(Player player) {
        return this.source(DamageTypes.PLAYER_ATTACK, player);
    }

    public DamageSource arrow(AbstractArrow arrow, @Nullable Entity shooter) {
        return this.source(DamageTypes.ARROW, arrow, shooter);
    }

    public DamageSource trident(Entity trident, @Nullable Entity thrower) {
        return this.source(DamageTypes.TRIDENT, trident, thrower);
    }

    public DamageSource mobProjectile(Entity projectile, @Nullable LivingEntity thrower) {
        return this.source(DamageTypes.MOB_PROJECTILE, projectile, thrower);
    }

    public DamageSource spit(Entity spit, @Nullable LivingEntity thrower) {
        return this.source(DamageTypes.SPIT, spit, thrower);
    }

    public DamageSource windCharge(Entity windCharge, @Nullable LivingEntity thrower) {
        return this.source(DamageTypes.WIND_CHARGE, windCharge, thrower);
    }

    public DamageSource fireworks(FireworkRocketEntity firework, @Nullable Entity shooter) {
        return this.source(DamageTypes.FIREWORKS, firework, shooter);
    }

    public DamageSource fireball(Fireball fireball, @Nullable Entity thrower) {
        return thrower == null ? this.source(DamageTypes.UNATTRIBUTED_FIREBALL, fireball) : this.source(DamageTypes.FIREBALL, fireball, thrower);
    }

    public DamageSource witherSkull(WitherSkull witherSkull, Entity shooter) {
        return this.source(DamageTypes.WITHER_SKULL, witherSkull, shooter);
    }

    public DamageSource thrown(Entity causingEntity, @Nullable Entity directEntity) {
        return this.source(DamageTypes.THROWN, causingEntity, directEntity);
    }

    public DamageSource indirectMagic(Entity causingEntity, @Nullable Entity directEntity) {
        return this.source(DamageTypes.INDIRECT_MAGIC, causingEntity, directEntity);
    }

    public DamageSource thorns(Entity entity) {
        return this.source(DamageTypes.THORNS, entity);
    }

    public DamageSource explosion(@Nullable Explosion explosion) {
        return explosion != null ? this.explosion(explosion.getDirectSourceEntity(), explosion.getIndirectSourceEntity()) : this.explosion(null, null);
    }

    public DamageSource explosion(@Nullable Entity causingEntity, @Nullable Entity directEntity) {
        return this.source(directEntity != null && causingEntity != null ? DamageTypes.PLAYER_EXPLOSION : DamageTypes.EXPLOSION, causingEntity, directEntity);
    }

    public DamageSource sonicBoom(Entity entity) {
        return this.source(DamageTypes.SONIC_BOOM, entity);
    }

    public DamageSource badRespawnPointExplosion(Vec3 position) {
        return new DamageSource(this.damageTypes.getOrThrow(DamageTypes.BAD_RESPAWN_POINT), position);
    }

    public DamageSource outOfBorder() {
        return this.outsideBorder;
    }

    public DamageSource genericKill() {
        return this.genericKill;
    }

    public DamageSource mace(Entity entity) {
        return this.source(DamageTypes.MACE_SMASH, entity);
    }
}
