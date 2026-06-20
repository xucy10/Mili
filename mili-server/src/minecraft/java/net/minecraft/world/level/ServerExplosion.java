package net.minecraft.world.level;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

// CraftBukkit start
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.craftbukkit.util.CraftLocation;
import org.bukkit.Location;
// CraftBukkit end

public class ServerExplosion implements Explosion {
    private static final ExplosionDamageCalculator EXPLOSION_DAMAGE_CALCULATOR = new ExplosionDamageCalculator();
    private static final int MAX_DROPS_PER_COMBINED_STACK = 16;
    private static final float LARGE_EXPLOSION_RADIUS = 2.0F;
    private final boolean fire;
    private final Explosion.BlockInteraction blockInteraction;
    private final ServerLevel level;
    private final Vec3 center;
    private final @Nullable Entity source;
    private final float radius;
    private final DamageSource damageSource;
    private final ExplosionDamageCalculator damageCalculator;
    private final Map<Player, Vec3> hitPlayers = new HashMap<>();
    // CraftBukkit - add field
    public boolean wasCanceled = false;
    public float yield;
    // CraftBukkit end
    public boolean excludeSourceFromDamage = true; // Paper - Allow explosions to damage source
    // Paper start - collisions optimisations
    private static final double[] CACHED_RAYS;
    static {
        final it.unimi.dsi.fastutil.doubles.DoubleArrayList rayCoords = new it.unimi.dsi.fastutil.doubles.DoubleArrayList();

        for (int x = 0; x <= 15; ++x) {
            for (int y = 0; y <= 15; ++y) {
                for (int z = 0; z <= 15; ++z) {
                    if ((x == 0 || x == 15) || (y == 0 || y == 15) || (z == 0 || z == 15)) {
                        double xDir = (double)((float)x / 15.0F * 2.0F - 1.0F);
                        double yDir = (double)((float)y / 15.0F * 2.0F - 1.0F);
                        double zDir = (double)((float)z / 15.0F * 2.0F - 1.0F);

                        double mag = Math.sqrt(
                                xDir * xDir + yDir * yDir + zDir * zDir
                        );

                        rayCoords.add((xDir / mag) * (double)0.3F);
                        rayCoords.add((yDir / mag) * (double)0.3F);
                        rayCoords.add((zDir / mag) * (double)0.3F);
                    }
                }
            }
        }

        CACHED_RAYS = rayCoords.toDoubleArray();
    }

    private static final int CHUNK_CACHE_SHIFT = 2;
    private static final int CHUNK_CACHE_MASK = (1 << CHUNK_CACHE_SHIFT) - 1;
    private static final int CHUNK_CACHE_WIDTH = 1 << CHUNK_CACHE_SHIFT;

    private static final int BLOCK_EXPLOSION_CACHE_SHIFT = 3;
    private static final int BLOCK_EXPLOSION_CACHE_MASK = (1 << BLOCK_EXPLOSION_CACHE_SHIFT) - 1;
    private static final int BLOCK_EXPLOSION_CACHE_WIDTH = 1 << BLOCK_EXPLOSION_CACHE_SHIFT;

    // resistance = (res + 0.3F) * 0.3F;
    // so for resistance = 0, we need res = -0.3F
    private static final Float ZERO_RESISTANCE = Float.valueOf(-0.3f);
    private it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<ca.spottedleaf.moonrise.patches.collisions.ExplosionBlockCache> blockCache = null;
    private long[] chunkPosCache = null;
    private net.minecraft.world.level.chunk.LevelChunk[] chunkCache = null;
    private ca.spottedleaf.moonrise.patches.collisions.ExplosionBlockCache[] directMappedBlockCache;
    private BlockPos.MutableBlockPos mutablePos;

    private ca.spottedleaf.moonrise.patches.collisions.ExplosionBlockCache getOrCacheExplosionBlock(final int x, final int y, final int z,
                                                                                                    final long key, final boolean calculateResistance) {
        ca.spottedleaf.moonrise.patches.collisions.ExplosionBlockCache ret = this.blockCache.get(key);
        if (ret != null) {
            return ret;
        }

        BlockPos pos = new BlockPos(x, y, z);

        if (!this.level.isInWorldBounds(pos)) {
            ret = new ca.spottedleaf.moonrise.patches.collisions.ExplosionBlockCache(key, pos, null, null, 0.0f, true);
        } else {
            net.minecraft.world.level.chunk.LevelChunk chunk;
            long chunkKey = ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkKey(x >> 4, z >> 4);
            int chunkCacheKey = ((x >> 4) & CHUNK_CACHE_MASK) | (((z >> 4) << CHUNK_CACHE_SHIFT) & (CHUNK_CACHE_MASK << CHUNK_CACHE_SHIFT));
            if (this.chunkPosCache[chunkCacheKey] == chunkKey) {
                chunk = this.chunkCache[chunkCacheKey];
            } else {
                this.chunkPosCache[chunkCacheKey] = chunkKey;
                this.chunkCache[chunkCacheKey] = chunk = this.level.getChunk(x >> 4, z >> 4);
            }

            BlockState blockState = ((ca.spottedleaf.moonrise.patches.getblock.GetBlockChunk)chunk).moonrise$getBlock(x, y, z);
            FluidState fluidState = blockState.getFluidState();

            Optional<Float> resistance = !calculateResistance ? Optional.empty() : this.damageCalculator.getBlockExplosionResistance((Explosion)(Object)this, this.level, pos, blockState, fluidState);

            ret = new ca.spottedleaf.moonrise.patches.collisions.ExplosionBlockCache(
                    key, pos, blockState, fluidState,
                    (resistance.orElse(ZERO_RESISTANCE).floatValue() + 0.3f) * 0.3f,
                    false
            );
        }

        this.blockCache.put(key, ret);

        return ret;
    }

    private boolean clipsAnything(final Vec3 from, final Vec3 to,
                                  final ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.LazyEntityCollisionContext context,
                                  final ca.spottedleaf.moonrise.patches.collisions.ExplosionBlockCache[] blockCache,
                                  final BlockPos.MutableBlockPos currPos) {
        // assume that context.delegated = false
        final double adjX = ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON * (from.x - to.x);
        final double adjY = ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON * (from.y - to.y);
        final double adjZ = ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON * (from.z - to.z);

        if (adjX == 0.0 && adjY == 0.0 && adjZ == 0.0) {
            return false;
        }

        final double toXAdj = to.x - adjX;
        final double toYAdj = to.y - adjY;
        final double toZAdj = to.z - adjZ;
        final double fromXAdj = from.x + adjX;
        final double fromYAdj = from.y + adjY;
        final double fromZAdj = from.z + adjZ;

        int currX = Mth.floor(fromXAdj);
        int currY = Mth.floor(fromYAdj);
        int currZ = Mth.floor(fromZAdj);

        final double diffX = toXAdj - fromXAdj;
        final double diffY = toYAdj - fromYAdj;
        final double diffZ = toZAdj - fromZAdj;

        final double dxDouble = Math.signum(diffX);
        final double dyDouble = Math.signum(diffY);
        final double dzDouble = Math.signum(diffZ);

        final int dx = (int)dxDouble;
        final int dy = (int)dyDouble;
        final int dz = (int)dzDouble;

        final double normalizedDiffX = diffX == 0.0 ? Double.MAX_VALUE : dxDouble / diffX;
        final double normalizedDiffY = diffY == 0.0 ? Double.MAX_VALUE : dyDouble / diffY;
        final double normalizedDiffZ = diffZ == 0.0 ? Double.MAX_VALUE : dzDouble / diffZ;

        double normalizedCurrX = normalizedDiffX * (diffX > 0.0 ? (1.0 - Mth.frac(fromXAdj)) : Mth.frac(fromXAdj));
        double normalizedCurrY = normalizedDiffY * (diffY > 0.0 ? (1.0 - Mth.frac(fromYAdj)) : Mth.frac(fromYAdj));
        double normalizedCurrZ = normalizedDiffZ * (diffZ > 0.0 ? (1.0 - Mth.frac(fromZAdj)) : Mth.frac(fromZAdj));

        for (;;) {
            currPos.set(currX, currY, currZ);

            // ClipContext.Block.COLLIDER -> BlockBehaviour.BlockStateBase::getCollisionShape
            // ClipContext.Fluid.NONE -> ignore fluids

            // read block from cache
            final long key = BlockPos.asLong(currX, currY, currZ);

            final int cacheKey =
                    (currX & BLOCK_EXPLOSION_CACHE_MASK) |
                    (currY & BLOCK_EXPLOSION_CACHE_MASK) << (BLOCK_EXPLOSION_CACHE_SHIFT) |
                    (currZ & BLOCK_EXPLOSION_CACHE_MASK) << (BLOCK_EXPLOSION_CACHE_SHIFT + BLOCK_EXPLOSION_CACHE_SHIFT);
            ca.spottedleaf.moonrise.patches.collisions.ExplosionBlockCache cachedBlock = blockCache[cacheKey];
            if (cachedBlock == null || cachedBlock.key != key) {
                blockCache[cacheKey] = cachedBlock = this.getOrCacheExplosionBlock(currX, currY, currZ, key, false);
            }

            final BlockState blockState = cachedBlock.blockState;
            if (blockState != null && !((ca.spottedleaf.moonrise.patches.collisions.block.CollisionBlockState)blockState).moonrise$emptyContextCollisionShape()) {
                net.minecraft.world.phys.shapes.VoxelShape collision = cachedBlock.cachedCollisionShape;
                if (collision == null) {
                    collision = ((ca.spottedleaf.moonrise.patches.collisions.block.CollisionBlockState)blockState).moonrise$getConstantContextCollisionShape();
                    if (collision == null) {
                        collision = blockState.getCollisionShape(this.level, currPos, context);
                        if (!context.isDelegated()) {
                            // if it was not delegated during this call, assume that for any future ones it will not be delegated
                            // again, and cache the result
                            cachedBlock.cachedCollisionShape = collision;
                        }
                    } else {
                        cachedBlock.cachedCollisionShape = collision;
                    }
                }

                if (!collision.isEmpty() && collision.clip(from, to, currPos) != null) {
                    return true;
                }
            }

            if (normalizedCurrX > 1.0 && normalizedCurrY > 1.0 && normalizedCurrZ > 1.0) {
                return false;
            }

            // inc the smallest normalized coordinate

            if (normalizedCurrX < normalizedCurrY) {
                if (normalizedCurrX < normalizedCurrZ) {
                    currX += dx;
                    normalizedCurrX += normalizedDiffX;
                } else {
                    // x < y && x >= z <--> z < y && z <= x
                    currZ += dz;
                    normalizedCurrZ += normalizedDiffZ;
                }
            } else if (normalizedCurrY < normalizedCurrZ) {
                // y <= x && y < z
                currY += dy;
                normalizedCurrY += normalizedDiffY;
            } else {
                // y <= x && z <= y <--> z <= y && z <= x
                currZ += dz;
                normalizedCurrZ += normalizedDiffZ;
            }
        }
    }

    private float getSeenFraction(final Vec3 source, final Entity target,
                                   final ca.spottedleaf.moonrise.patches.collisions.ExplosionBlockCache[] blockCache,
                                   final BlockPos.MutableBlockPos blockPos) {
        final AABB boundingBox = target.getBoundingBox();
        final double diffX = boundingBox.maxX - boundingBox.minX;
        final double diffY = boundingBox.maxY - boundingBox.minY;
        final double diffZ = boundingBox.maxZ - boundingBox.minZ;

        final double incX = 1.0 / (diffX * 2.0 + 1.0);
        final double incY = 1.0 / (diffY * 2.0 + 1.0);
        final double incZ = 1.0 / (diffZ * 2.0 + 1.0);

        if (incX < 0.0 || incY < 0.0 || incZ < 0.0) {
            return 0.0f;
        }

        final double offX = (1.0 - Math.floor(1.0 / incX) * incX) * 0.5 + boundingBox.minX;
        final double offY = boundingBox.minY;
        final double offZ = (1.0 - Math.floor(1.0 / incZ) * incZ) * 0.5 + boundingBox.minZ;

        final ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.LazyEntityCollisionContext context = new ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.LazyEntityCollisionContext(target);

        int totalRays = 0;
        int missedRays = 0;

        for (double dx = 0.0; dx <= 1.0; dx += incX) {
            final double fromX = Math.fma(dx, diffX, offX);
            for (double dy = 0.0; dy <= 1.0; dy += incY) {
                final double fromY = Math.fma(dy, diffY, offY);
                for (double dz = 0.0; dz <= 1.0; dz += incZ) {
                    ++totalRays;

                    final Vec3 from = new Vec3(
                            fromX,
                            fromY,
                            Math.fma(dz, diffZ, offZ)
                    );

                    if (!this.clipsAnything(from, source, context, blockCache, blockPos)) {
                        ++missedRays;
                    }
                }
            }
        }

        return (float)missedRays / (float)totalRays;
    }
    // Paper end - collisions optimisations

    public ServerExplosion(
        ServerLevel level,
        @Nullable Entity source,
        @Nullable DamageSource damageSource,
        @Nullable ExplosionDamageCalculator damageCalculator,
        Vec3 center,
        float radius,
        boolean fire,
        Explosion.BlockInteraction blockInteraction
    ) {
        this.level = level;
        this.source = source;
        this.radius = radius;
        this.center = center;
        this.fire = fire;
        this.blockInteraction = blockInteraction;
        this.damageSource = damageSource == null ? level.damageSources().explosion(this) : damageSource;
        this.damageCalculator = damageCalculator == null ? this.makeDamageCalculator(source) : damageCalculator;
        // Paper start - add yield
        this.yield = this.blockInteraction == Explosion.BlockInteraction.DESTROY_WITH_DECAY ? 1.0F / this.radius : 1.0F;
        this.yield = Double.isFinite(this.yield) ? this.yield : 0; // Paper - Don't allow infinite default yields
        // Paper end - add yield
    }

    private ExplosionDamageCalculator makeDamageCalculator(@Nullable Entity entity) {
        return (ExplosionDamageCalculator)(entity == null ? EXPLOSION_DAMAGE_CALCULATOR : new EntityBasedExplosionDamageCalculator(entity));
    }

    public static float getSeenPercent(Vec3 explosionVector, Entity entity) {
        AABB boundingBox = entity.getBoundingBox();
        double d = 1.0 / ((boundingBox.maxX - boundingBox.minX) * 2.0 + 1.0);
        double d1 = 1.0 / ((boundingBox.maxY - boundingBox.minY) * 2.0 + 1.0);
        double d2 = 1.0 / ((boundingBox.maxZ - boundingBox.minZ) * 2.0 + 1.0);
        double d3 = (1.0 - Math.floor(1.0 / d) * d) / 2.0;
        double d4 = (1.0 - Math.floor(1.0 / d2) * d2) / 2.0;
        if (!(d < 0.0) && !(d1 < 0.0) && !(d2 < 0.0)) {
            int i = 0;
            int i1 = 0;

            for (double d5 = 0.0; d5 <= 1.0; d5 += d) {
                for (double d6 = 0.0; d6 <= 1.0; d6 += d1) {
                    for (double d7 = 0.0; d7 <= 1.0; d7 += d2) {
                        double d8 = Mth.lerp(d5, boundingBox.minX, boundingBox.maxX);
                        double d9 = Mth.lerp(d6, boundingBox.minY, boundingBox.maxY);
                        double d10 = Mth.lerp(d7, boundingBox.minZ, boundingBox.maxZ);
                        Vec3 vec3 = new Vec3(d8 + d3, d9, d10 + d4);
                        if (entity.level().clip(new ClipContext(vec3, explosionVector, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity)).getType()
                            == HitResult.Type.MISS) {
                            i++;
                        }

                        i1++;
                    }
                }
            }

            return (float)i / i1;
        } else {
            return 0.0F;
        }
    }

    @Override
    public float radius() {
        return this.radius;
    }

    @Override
    public Vec3 center() {
        return this.center;
    }

    private List<BlockPos> calculateExplodedPositions() {
        // Paper start - collision optimisations
        final ObjectArrayList<BlockPos> ret = new ObjectArrayList<>();

        final Vec3 center = this.center;

        final ca.spottedleaf.moonrise.patches.collisions.ExplosionBlockCache[] blockCache = this.directMappedBlockCache;

        // use initial cache value that is most likely to be used: the source position
        final ca.spottedleaf.moonrise.patches.collisions.ExplosionBlockCache initialCache;
        {
            final int blockX = Mth.floor(center.x);
            final int blockY = Mth.floor(center.y);
            final int blockZ = Mth.floor(center.z);

            final long key = BlockPos.asLong(blockX, blockY, blockZ);

            initialCache = this.getOrCacheExplosionBlock(blockX, blockY, blockZ, key, true);
        }

        // only ~1/3rd of the loop iterations in vanilla will result in a ray, as it is iterating the perimeter of
        // a 16x16x16 cube
        // we can cache the rays and their normals as well, so that we eliminate the excess iterations / checks and
        // calculations in one go
        // additional aggressive caching of block retrieval is very significant, as at low power (i.e tnt) most
        // block retrievals are not unique
        for (int ray = 0, len = CACHED_RAYS.length; ray < len;) {
            ca.spottedleaf.moonrise.patches.collisions.ExplosionBlockCache cachedBlock = initialCache;

            double currX = center.x;
            double currY = center.y;
            double currZ = center.z;

            final double incX = CACHED_RAYS[ray];
            final double incY = CACHED_RAYS[ray + 1];
            final double incZ = CACHED_RAYS[ray + 2];

            ray += 3;

            float power = this.radius * (0.7F + this.level.random.nextFloat() * 0.6F);

            do {
                final int blockX = Mth.floor(currX);
                final int blockY = Mth.floor(currY);
                final int blockZ = Mth.floor(currZ);

                final long key = BlockPos.asLong(blockX, blockY, blockZ);

                if (cachedBlock.key != key) {
                    final int cacheKey =
                        (blockX & BLOCK_EXPLOSION_CACHE_MASK) |
                            (blockY & BLOCK_EXPLOSION_CACHE_MASK) << (BLOCK_EXPLOSION_CACHE_SHIFT) |
                            (blockZ & BLOCK_EXPLOSION_CACHE_MASK) << (BLOCK_EXPLOSION_CACHE_SHIFT + BLOCK_EXPLOSION_CACHE_SHIFT);
                    cachedBlock = blockCache[cacheKey];
                    if (cachedBlock == null || cachedBlock.key != key) {
                        blockCache[cacheKey] = cachedBlock = this.getOrCacheExplosionBlock(blockX, blockY, blockZ, key, true);
                    }
                }

                if (cachedBlock.outOfWorld) {
                    break;
                }
                final BlockState iblockdata = cachedBlock.blockState;

                power -= cachedBlock.resistance;

                if (power > 0.0f && cachedBlock.shouldExplode == null) {
                    // note: we expect shouldBlockExplode to be pure with respect to power, as Vanilla currently is.
                    // basically, it is unused, which allows us to cache the result
                    final boolean shouldExplode = iblockdata.isDestroyable() && this.damageCalculator.shouldBlockExplode((Explosion)(Object)this, this.level, cachedBlock.immutablePos, cachedBlock.blockState, power); // Paper - Protect Bedrock and End Portal/Frames from being destroyed
                    cachedBlock.shouldExplode = shouldExplode ? Boolean.TRUE : Boolean.FALSE;
                    if (shouldExplode) {
                        if (this.fire || !cachedBlock.blockState.isAir()) {
                            ret.add(cachedBlock.immutablePos);
                            // Paper start - prevent headless pistons from forming
                            if (!io.papermc.paper.configuration.GlobalConfiguration.get().unsupportedSettings.allowHeadlessPistons && iblockdata.getBlock() == net.minecraft.world.level.block.Blocks.MOVING_PISTON) {
                                net.minecraft.world.level.block.entity.BlockEntity extension = this.level.getBlockEntity(cachedBlock.immutablePos); // Paper - optimise collisions
                                if (extension instanceof net.minecraft.world.level.block.piston.PistonMovingBlockEntity blockEntity && blockEntity.isSourcePiston()) {
                                    net.minecraft.core.Direction direction = iblockdata.getValue(net.minecraft.world.level.block.piston.PistonHeadBlock.FACING);
                                    ret.add(cachedBlock.immutablePos.relative(direction.getOpposite())); // Paper - optimise collisions
                                }
                            }

                            // Paper end - prevent headless pistons from forming
                        }
                    }
                }

                power -= 0.22500001F;
                currX += incX;
                currY += incY;
                currZ += incZ;
            } while (power > 0.0f);
        }

        return ret;
        // Paper end - collision optimisations
    }

    private void hurtEntities() {
        if (!(this.radius < 1.0E-5F)) {
            float f = this.radius * 2.0F;
            int floor = Mth.floor(this.center.x - f - 1.0);
            int floor1 = Mth.floor(this.center.x + f + 1.0);
            int floor2 = Mth.floor(this.center.y - f - 1.0);
            int floor3 = Mth.floor(this.center.y + f + 1.0);
            int floor4 = Mth.floor(this.center.z - f - 1.0);
            int floor5 = Mth.floor(this.center.z + f + 1.0);
            List <Entity> list = this.level.getEntities(this.excludeSourceFromDamage ? this.source : null, new AABB(floor, floor2, floor4, floor1, floor3, floor5), entity -> entity.isAlive() && !entity.isSpectator()); // Paper - Fix lag from explosions processing dead entities, Allow explosions to damage source
            for (Entity entity : list) { // Paper - used in loop
                if (!entity.ignoreExplosion(this)) {
                    double d = Math.sqrt(entity.distanceToSqr(this.center)) / f;
                    if (!(d > 1.0)) {
                        Vec3 vec3 = entity instanceof PrimedTnt ? entity.position() : entity.getEyePosition();
                        Vec3 vec31 = vec3.subtract(this.center).normalize();
                        boolean shouldDamageEntity = this.damageCalculator.shouldDamageEntity(this, entity);
                        float knockbackMultiplier = this.damageCalculator.getKnockbackMultiplier(entity);
                        float f1 = !shouldDamageEntity && knockbackMultiplier == 0.0F ? 0.0F : this.getBlockDensity(this.center, entity); // Paper - Optimize explosions
                        if (shouldDamageEntity) {
                            // CraftBukkit start

                            // Special case ender dragon only give knockback if no damage is cancelled
                            // Thinks to note:
                            // - Setting a velocity to a EnderDragonPart is ignored (and therefore not needed)
                            // - Damaging EnderDragonPart while forward the damage to EnderDragon
                            // - Damaging EnderDragon does nothing
                            // - EnderDragon hitbox always covers the other parts and is therefore always present
                            if (entity instanceof EnderDragonPart) {
                                continue;
                            }

                            entity.lastDamageCancelled = false;

                            if (entity instanceof EnderDragon) {
                                for (EnderDragonPart dragonPart : ((EnderDragon) entity).getSubEntities()) {
                                    // Calculate damage separately for each EntityComplexPart
                                    if (list.contains(dragonPart)) {
                                        dragonPart.hurtServer(this.level, this.damageSource, this.damageCalculator.getEntityDamageAmount(this, dragonPart, f1));
                                    }
                                }
                            } else {
                                entity.hurtServer(this.level, this.damageSource, this.damageCalculator.getEntityDamageAmount(this, entity, f1));
                            }

                            if (entity.lastDamageCancelled) { // SPIGOT-5339, SPIGOT-6252, SPIGOT-6777: Skip entity if damage event was cancelled
                                continue;
                            }
                            // CraftBukkit end
                        }

                        double d1 = entity instanceof LivingEntity livingEntity
                            ? livingEntity.getAttributeValue(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE)
                            : 0.0;
                        double d2 = entity instanceof Player && this.level.paperConfig().environment.disableExplosionKnockback ? 0 : (1.0 - d) * f1 * knockbackMultiplier * (1.0 - d1); // Paper
                        Vec3 vec32 = vec31.scale(d2);
                        // CraftBukkit start - Call EntityKnockbackEvent
                        if (entity instanceof LivingEntity) {
                            // Paper start - knockback events
                            io.papermc.paper.event.entity.EntityKnockbackEvent event = CraftEventFactory.callEntityKnockbackEvent((org.bukkit.craftbukkit.entity.CraftLivingEntity) entity.getBukkitEntity(), this.source, this.damageSource.getEntity() != null ? this.damageSource.getEntity() : this.source, io.papermc.paper.event.entity.EntityKnockbackEvent.Cause.EXPLOSION, d2, vec32);
                            vec32 = event.isCancelled() ? Vec3.ZERO : org.bukkit.craftbukkit.util.CraftVector.toVec3(event.getKnockback());
                            // Paper end - knockback events
                        }
                        // CraftBukkit end
                        entity.push(vec32);
                        if (entity.getType().is(EntityTypeTags.REDIRECTABLE_PROJECTILE) && entity instanceof Projectile projectile) {
                            projectile.setOwner(this.damageSource.getEntity());
                        } else if (entity instanceof Player player && !player.isSpectator() && (!player.isCreative() || !player.getAbilities().flying) && !level.paperConfig().environment.disableExplosionKnockback) { // Paper - Option to disable explosion knockback
                            this.hitPlayers.put(player, vec32);
                        }

                        entity.onExplosionHit(this.source);
                    }
                }
            }
        }
    }

    private void interactWithBlocks(List<BlockPos> blocks) {
        List<ServerExplosion.StackCollector> list = new ArrayList<>();
        Util.shuffle(blocks, this.level.random);

        // CraftBukkit start
        Location location = CraftLocation.toBukkit(this.center, this.level);
        List<org.bukkit.block.Block> blockList = new ObjectArrayList<>();
        for (int i1 = blocks.size() - 1; i1 >= 0; i1--) {
            org.bukkit.block.Block bblock = org.bukkit.craftbukkit.block.CraftBlock.at(this.level, blocks.get(i1));
            if (!bblock.getType().isAir()) {
                blockList.add(bblock);
            }
        }

        List<org.bukkit.block.Block> bukkitBlocks;

        if (this.source != null) {
            org.bukkit.event.entity.EntityExplodeEvent event = CraftEventFactory.callEntityExplodeEvent(this.source, blockList, this.yield, this.getBlockInteraction());
            this.wasCanceled = event.isCancelled();
            bukkitBlocks = event.blockList();
            this.yield = event.getYield();
        } else {
            org.bukkit.block.Block block = location.getBlock();
            org.bukkit.block.BlockState blockState = (this.damageSource.causingBlockSnapshot() != null) ? this.damageSource.causingBlockSnapshot() : block.getState();
            org.bukkit.event.block.BlockExplodeEvent event = CraftEventFactory.callBlockExplodeEvent(block, blockState, blockList, this.yield, this.getBlockInteraction());
            this.wasCanceled = event.isCancelled();
            bukkitBlocks = event.blockList();
            this.yield = event.getYield();
        }

        blocks.clear();
        for (org.bukkit.block.Block bblock : bukkitBlocks) {
            blocks.add(((org.bukkit.craftbukkit.block.CraftBlock) bblock).getPosition());
        }

        if (this.wasCanceled) {
            return;
        }
        // CraftBukkit end

        for (BlockPos blockPos : blocks) {
            // CraftBukkit start - TNTPrimeEvent
            BlockState state = this.level.getBlockState(blockPos);
            Block block = state.getBlock();
            if (level.getGameRules().get(GameRules.TNT_EXPLODES) && block instanceof net.minecraft.world.level.block.TntBlock) {
                Entity sourceEntity = this.source == null ? null : this.source;
                BlockPos sourceBlock = sourceEntity == null ? BlockPos.containing(this.center) : null;
                if (!CraftEventFactory.callTNTPrimeEvent(this.level, blockPos, org.bukkit.event.block.TNTPrimeEvent.PrimeCause.EXPLOSION, sourceEntity, sourceBlock)) {
                    this.level.sendBlockUpdated(blockPos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), state, Block.UPDATE_ALL); // Update the block on the client
                    continue;
                }
            }
            // CraftBukkit end

            this.level
                .getBlockState(blockPos)
                .onExplosionHit(this.level, blockPos, this, (itemStack, blockPos1) -> addOrAppendStack(list, itemStack, blockPos1));
        }

        for (ServerExplosion.StackCollector stackCollector : list) {
            Block.popResource(this.level, stackCollector.pos, stackCollector.stack);
        }
    }

    private void createFire(List<BlockPos> blocks) {
        for (BlockPos blockPos : blocks) {
            if (this.level.random.nextInt(3) == 0 && this.level.getBlockState(blockPos).isAir() && this.level.getBlockState(blockPos.below()).isSolidRender()) {
                // CraftBukkit start - Ignition by explosion
                if (!org.bukkit.craftbukkit.event.CraftEventFactory.callBlockIgniteEvent(this.level, blockPos, this).isCancelled()) {
                    this.level.setBlockAndUpdate(blockPos, BaseFireBlock.getState(this.level, blockPos));
                }
                // CraftBukkit end
            }
        }
    }

    public int explode() {
        // Paper start - collision optimisations
        this.blockCache = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
        this.chunkPosCache = new long[CHUNK_CACHE_WIDTH * CHUNK_CACHE_WIDTH];
        java.util.Arrays.fill(this.chunkPosCache, ChunkPos.INVALID_CHUNK_POS);
        this.chunkCache = new net.minecraft.world.level.chunk.LevelChunk[CHUNK_CACHE_WIDTH * CHUNK_CACHE_WIDTH];
        this.directMappedBlockCache = new ca.spottedleaf.moonrise.patches.collisions.ExplosionBlockCache[BLOCK_EXPLOSION_CACHE_WIDTH * BLOCK_EXPLOSION_CACHE_WIDTH * BLOCK_EXPLOSION_CACHE_WIDTH];
        this.mutablePos = new BlockPos.MutableBlockPos();
        // Paper end - collision optimisations
        this.level.gameEvent(this.source, GameEvent.EXPLODE, this.center);
        List<BlockPos> list = this.calculateExplodedPositions();
        this.hurtEntities();
        if (this.interactsWithBlocks()) {
            ProfilerFiller profilerFiller = Profiler.get();
            profilerFiller.push("explosion_blocks");
            this.interactWithBlocks(list);
            profilerFiller.pop();
        }

        if (this.fire) {
            this.createFire(list);
        }

        // Paper start - collision optimisations
        this.blockCache = null;
        this.chunkPosCache = null;
        this.chunkCache = null;
        this.directMappedBlockCache = null;
        this.mutablePos = null;
        // Paper end - collision optimisations
        return list.size();
    }

    private static void addOrAppendStack(List<ServerExplosion.StackCollector> stackCollectors, ItemStack stack, BlockPos pos) {
        for (ServerExplosion.StackCollector stackCollector : stackCollectors) {
            stackCollector.tryMerge(stack);
            if (stack.isEmpty()) {
                return;
            }
        }

        stackCollectors.add(new ServerExplosion.StackCollector(pos, stack));
    }

    private boolean interactsWithBlocks() {
        return this.blockInteraction != Explosion.BlockInteraction.KEEP;
    }

    public Map<Player, Vec3> getHitPlayers() {
        return this.hitPlayers;
    }

    @Override
    public ServerLevel level() {
        return this.level;
    }

    @Override
    public @Nullable LivingEntity getIndirectSourceEntity() {
        return Explosion.getIndirectSourceEntity(this.source);
    }

    @Override
    public @Nullable Entity getDirectSourceEntity() {
        return this.source;
    }

    public DamageSource getDamageSource() {
        return this.damageSource;
    }

    @Override
    public Explosion.BlockInteraction getBlockInteraction() {
        return this.blockInteraction;
    }

    @Override
    public boolean canTriggerBlocks() {
        return this.blockInteraction == Explosion.BlockInteraction.TRIGGER_BLOCK
            && (this.source == null || this.source.getType() != EntityType.BREEZE_WIND_CHARGE || this.level.getGameRules().get(GameRules.MOB_GRIEFING));
    }

    @Override
    public boolean shouldAffectBlocklikeEntities() {
        boolean flag = this.level.getGameRules().get(GameRules.MOB_GRIEFING);
        boolean flag1 = this.source == null || this.source.getType() != EntityType.BREEZE_WIND_CHARGE && this.source.getType() != EntityType.WIND_CHARGE;
        return flag ? flag1 : this.blockInteraction.shouldAffectBlocklikeEntities() && flag1;
    }

    public boolean isSmall() {
        return this.radius < 2.0F || !this.interactsWithBlocks();
    }

    static class StackCollector {
        final BlockPos pos;
        ItemStack stack;

        StackCollector(BlockPos pos, ItemStack stack) {
            this.pos = pos;
            this.stack = stack;
        }

        public void tryMerge(ItemStack stack) {
            if (ItemEntity.areMergable(this.stack, stack)) {
                this.stack = ItemEntity.merge(this.stack, stack, 16);
            }
        }
    }


    // Paper start - Optimize explosions
    private float getBlockDensity(Vec3 vec3d, Entity entity) {
        if (!this.level.paperConfig().environment.optimizeExplosions) {
            return this.getSeenFraction(vec3d, entity, this.directMappedBlockCache, this.mutablePos); // Paper - collision optimisations
        }
        io.papermc.paper.threadedregions.RegionizedWorldData worldData = this.level.getCurrentWorldData(); // Folia - region threading
        CacheKey key = new CacheKey(this, entity.getBoundingBox());
        Float blockDensity = worldData.explosionDensityCache.get(key); // Folia - region threading
        if (blockDensity == null) {
            blockDensity = this.getSeenFraction(vec3d, entity, this.directMappedBlockCache, this.mutablePos); // Paper - collision optimisations
            worldData.explosionDensityCache.put(key, blockDensity); // Folia - region threading
        }

        return blockDensity;
    }

    public static class CacheKey { // Folia - region threading - public
        private final Level world;
        private final double posX, posY, posZ;
        private final double minX, minY, minZ;
        private final double maxX, maxY, maxZ;

        public CacheKey(Explosion explosion, AABB aabb) {
            this.world = explosion.level();
            this.posX = explosion.center().x;
            this.posY = explosion.center().y;
            this.posZ = explosion.center().z;
            this.minX = aabb.minX;
            this.minY = aabb.minY;
            this.minZ = aabb.minZ;
            this.maxX = aabb.maxX;
            this.maxY = aabb.maxY;
            this.maxZ = aabb.maxZ;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            CacheKey cacheKey = (CacheKey) o;

            if (Double.compare(cacheKey.posX, posX) != 0) return false;
            if (Double.compare(cacheKey.posY, posY) != 0) return false;
            if (Double.compare(cacheKey.posZ, posZ) != 0) return false;
            if (Double.compare(cacheKey.minX, minX) != 0) return false;
            if (Double.compare(cacheKey.minY, minY) != 0) return false;
            if (Double.compare(cacheKey.minZ, minZ) != 0) return false;
            if (Double.compare(cacheKey.maxX, maxX) != 0) return false;
            if (Double.compare(cacheKey.maxY, maxY) != 0) return false;
            if (Double.compare(cacheKey.maxZ, maxZ) != 0) return false;
            return world.equals(cacheKey.world);
        }

        @Override
        public int hashCode() {
            int result;
            long temp;
            result = world.hashCode();
            temp = Double.doubleToLongBits(posX);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(posY);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(posZ);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(minX);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(minY);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(minZ);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(maxX);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(maxY);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(maxZ);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            return result;
        }
    }
    // Paper end
}
