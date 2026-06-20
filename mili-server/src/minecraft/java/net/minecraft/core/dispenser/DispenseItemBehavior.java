package net.minecraft.core.dispenser;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.entity.animal.equine.AbstractChestedHorse;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.DispensibleContainerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.CarvedPumpkinBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.WitherSkullBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

public interface DispenseItemBehavior {
    Logger LOGGER = LogUtils.getLogger();
    DispenseItemBehavior NOOP = (blockSource, item) -> item;

    ItemStack dispense(BlockSource blockSource, ItemStack item);

    static void bootStrap() {
        DispenserBlock.registerProjectileBehavior(Items.ARROW);
        DispenserBlock.registerProjectileBehavior(Items.TIPPED_ARROW);
        DispenserBlock.registerProjectileBehavior(Items.SPECTRAL_ARROW);
        DispenserBlock.registerProjectileBehavior(Items.EGG);
        DispenserBlock.registerProjectileBehavior(Items.BLUE_EGG);
        DispenserBlock.registerProjectileBehavior(Items.BROWN_EGG);
        DispenserBlock.registerProjectileBehavior(Items.SNOWBALL);
        DispenserBlock.registerProjectileBehavior(Items.EXPERIENCE_BOTTLE);
        DispenserBlock.registerProjectileBehavior(Items.SPLASH_POTION);
        DispenserBlock.registerProjectileBehavior(Items.LINGERING_POTION);
        DispenserBlock.registerProjectileBehavior(Items.FIREWORK_ROCKET);
        DispenserBlock.registerProjectileBehavior(Items.FIRE_CHARGE);
        DispenserBlock.registerProjectileBehavior(Items.WIND_CHARGE);
        DefaultDispenseItemBehavior defaultDispenseItemBehavior = new DefaultDispenseItemBehavior() {
            @Override
            public ItemStack execute(BlockSource blockSource, ItemStack item) {
                Direction direction = blockSource.state().getValue(DispenserBlock.FACING);
                EntityType<?> type = ((SpawnEggItem)item.getItem()).getType(item);
                if (type == null) {
                    return item;
                } else {
                    // CraftBukkit start
                    ServerLevel serverLevel = blockSource.level();
                    ItemStack singleItemStack = item.copyWithCount(1);
                    org.bukkit.block.Block block = org.bukkit.craftbukkit.block.CraftBlock.at(serverLevel, blockSource.pos());
                    org.bukkit.craftbukkit.inventory.CraftItemStack craftItem = org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(singleItemStack);

                    org.bukkit.event.block.BlockDispenseEvent event = new org.bukkit.event.block.BlockDispenseEvent(block, craftItem.clone(), new org.bukkit.util.Vector(0, 0, 0));
                    serverLevel.getCraftServer().getPluginManager().callEvent(event);

                    if (event.isCancelled()) {
                        return item;
                    }

                    boolean shrink = true;
                    if (!event.getItem().equals(craftItem)) {
                        shrink = false;
                        // Chain to handler for new item
                        ItemStack eventStack = org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(event.getItem());
                        DispenseItemBehavior dispenseBehavior = DispenserBlock.getDispenseBehavior(blockSource, eventStack);
                        if (dispenseBehavior != DispenseItemBehavior.NOOP && dispenseBehavior != this) {
                            dispenseBehavior.dispense(blockSource, eventStack);
                            return item;
                        }
                        // Paper start - track changed items in the dispense event
                        singleItemStack = org.bukkit.craftbukkit.inventory.CraftItemStack.unwrap(event.getItem()); // unwrap is safe because the stack won't be modified
                        type = ((SpawnEggItem) singleItemStack.getItem()).getType(singleItemStack);
                        // Paper end - track changed item from dispense event
                    }
                    try {
                        type.spawn(
                            blockSource.level(),
                            singleItemStack, // Paper - track changed item in dispense event
                            null,
                            blockSource.pos().relative(direction),
                            EntitySpawnReason.DISPENSER,
                            direction != Direction.UP,
                            false
                        );
                    } catch (Exception var6) {
                        LOGGER.error("Error while dispensing spawn egg from dispenser at {}", blockSource.pos(), var6);
                        return ItemStack.EMPTY;
                    }

                    if (shrink) item.shrink(1);
                    // CraftBukkit end
                    blockSource.level().gameEvent(null, GameEvent.ENTITY_PLACE, blockSource.pos());
                    return item;
                }
            }
        };

        for (SpawnEggItem spawnEggItem : SpawnEggItem.eggs()) {
            DispenserBlock.registerBehavior(spawnEggItem, defaultDispenseItemBehavior);
        }

        DispenserBlock.registerBehavior(
            Items.ARMOR_STAND,
            new DefaultDispenseItemBehavior() {
                @Override
                public ItemStack execute(BlockSource blockSource, ItemStack item) {
                    Direction direction = blockSource.state().getValue(DispenserBlock.FACING);
                    BlockPos blockPos = blockSource.pos().relative(direction);
                    ServerLevel serverLevel = blockSource.level();
                    // CraftBukkit start
                    ItemStack singleItemStack = item.copyWithCount(1);
                    org.bukkit.block.Block block = org.bukkit.craftbukkit.block.CraftBlock.at(serverLevel, blockSource.pos());
                    org.bukkit.craftbukkit.inventory.CraftItemStack craftItem = org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(singleItemStack);

                    org.bukkit.event.block.BlockDispenseEvent event = new org.bukkit.event.block.BlockDispenseEvent(block, craftItem.clone(), new org.bukkit.util.Vector(0, 0, 0));
                    serverLevel.getCraftServer().getPluginManager().callEvent(event);

                    if (event.isCancelled()) {
                        return item;
                    }

                    boolean shrink = true;
                    if (!event.getItem().equals(craftItem)) {
                        shrink = false;
                        // Chain to handler for new item
                        ItemStack eventStack = org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(event.getItem());
                        DispenseItemBehavior dispenseBehavior = DispenserBlock.getDispenseBehavior(blockSource, eventStack);
                        if (dispenseBehavior != DispenseItemBehavior.NOOP && dispenseBehavior != this) {
                            dispenseBehavior.dispense(blockSource, eventStack);
                            return item;
                        }
                    }
                    // CraftBukkit end

                    final ItemStack newStack = org.bukkit.craftbukkit.inventory.CraftItemStack.unwrap(event.getItem()); // Paper - use event itemstack (unwrap is fine here because the stack won't be modified)
                    Consumer<ArmorStand> consumer = EntityType.appendDefaultStackConfig(
                        armorStand1 -> armorStand1.setYRot(direction.toYRot()), serverLevel, newStack, null // Paper - track changed items in the dispense event
                    );
                    ArmorStand armorStand = EntityType.ARMOR_STAND.spawn(serverLevel, consumer, blockPos, EntitySpawnReason.DISPENSER, false, false);
                    if (armorStand != null) {
                        if (shrink) item.shrink(1); // Paper
                    }

                    return item;
                }
            }
        );
        DispenserBlock.registerBehavior(
            Items.CHEST,
            new OptionalDispenseItemBehavior() {
                @Override
                public ItemStack execute(BlockSource blockSource, ItemStack item) {
                    BlockPos blockPos = blockSource.pos().relative(blockSource.state().getValue(DispenserBlock.FACING));

                    for (AbstractChestedHorse abstractChestedHorse : blockSource.level()
                        .getEntitiesOfClass(
                            AbstractChestedHorse.class,
                            new AABB(blockPos),
                            abstractChestedHorse1 -> abstractChestedHorse1.isAlive() && !abstractChestedHorse1.hasChest()
                        )) {
                        if (abstractChestedHorse.isTamed()) {
                            SlotAccess slot = abstractChestedHorse.getSlot(499);
                            // CraftBukkit start
                            if (slot != null/* && slot.set(item)*/) {
                                ItemStack singleCopy = item.copyWithCount(1);
                                ServerLevel world = blockSource.level();
                                org.bukkit.block.Block block = org.bukkit.craftbukkit.block.CraftBlock.at(world, blockSource.pos());
                                org.bukkit.craftbukkit.inventory.CraftItemStack craftItem = org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(singleCopy);
                                org.bukkit.event.block.BlockDispenseArmorEvent event = new org.bukkit.event.block.BlockDispenseArmorEvent(block, craftItem.clone(), abstractChestedHorse.getBukkitLivingEntity());
                                world.getCraftServer().getPluginManager().callEvent(event);

                                if (event.isCancelled()) {
                                    this.setSuccess(false);
                                    return item;
                                }

                                boolean shrink = true;
                                if (!event.getItem().equals(craftItem)) {
                                    shrink = false;
                                    // Chain to handler for new item
                                    ItemStack eventStack = org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(event.getItem());
                                    DispenseItemBehavior dispenseBehavior = DispenserBlock.getDispenseBehavior(blockSource, eventStack);
                                    if (dispenseBehavior != DispenseItemBehavior.NOOP && dispenseBehavior != this) {
                                        dispenseBehavior.dispense(blockSource, eventStack);
                                        return item;
                                    }
                                }
                                slot.set(org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(event.getItem()));
                                // CraftBukkit end

                                if (shrink) item.shrink(1); // Paper - actually handle here
                                this.setSuccess(true);
                                return item;
                            }
                        }
                    }

                    return super.execute(blockSource, item);
                }
            }
        );
        DispenserBlock.registerBehavior(Items.OAK_BOAT, new BoatDispenseItemBehavior(EntityType.OAK_BOAT));
        DispenserBlock.registerBehavior(Items.SPRUCE_BOAT, new BoatDispenseItemBehavior(EntityType.SPRUCE_BOAT));
        DispenserBlock.registerBehavior(Items.BIRCH_BOAT, new BoatDispenseItemBehavior(EntityType.BIRCH_BOAT));
        DispenserBlock.registerBehavior(Items.JUNGLE_BOAT, new BoatDispenseItemBehavior(EntityType.JUNGLE_BOAT));
        DispenserBlock.registerBehavior(Items.DARK_OAK_BOAT, new BoatDispenseItemBehavior(EntityType.DARK_OAK_BOAT));
        DispenserBlock.registerBehavior(Items.ACACIA_BOAT, new BoatDispenseItemBehavior(EntityType.ACACIA_BOAT));
        DispenserBlock.registerBehavior(Items.CHERRY_BOAT, new BoatDispenseItemBehavior(EntityType.CHERRY_BOAT));
        DispenserBlock.registerBehavior(Items.MANGROVE_BOAT, new BoatDispenseItemBehavior(EntityType.MANGROVE_BOAT));
        DispenserBlock.registerBehavior(Items.PALE_OAK_BOAT, new BoatDispenseItemBehavior(EntityType.PALE_OAK_BOAT));
        DispenserBlock.registerBehavior(Items.BAMBOO_RAFT, new BoatDispenseItemBehavior(EntityType.BAMBOO_RAFT));
        DispenserBlock.registerBehavior(Items.OAK_CHEST_BOAT, new BoatDispenseItemBehavior(EntityType.OAK_CHEST_BOAT));
        DispenserBlock.registerBehavior(Items.SPRUCE_CHEST_BOAT, new BoatDispenseItemBehavior(EntityType.SPRUCE_CHEST_BOAT));
        DispenserBlock.registerBehavior(Items.BIRCH_CHEST_BOAT, new BoatDispenseItemBehavior(EntityType.BIRCH_CHEST_BOAT));
        DispenserBlock.registerBehavior(Items.JUNGLE_CHEST_BOAT, new BoatDispenseItemBehavior(EntityType.JUNGLE_CHEST_BOAT));
        DispenserBlock.registerBehavior(Items.DARK_OAK_CHEST_BOAT, new BoatDispenseItemBehavior(EntityType.DARK_OAK_CHEST_BOAT));
        DispenserBlock.registerBehavior(Items.ACACIA_CHEST_BOAT, new BoatDispenseItemBehavior(EntityType.ACACIA_CHEST_BOAT));
        DispenserBlock.registerBehavior(Items.CHERRY_CHEST_BOAT, new BoatDispenseItemBehavior(EntityType.CHERRY_CHEST_BOAT));
        DispenserBlock.registerBehavior(Items.MANGROVE_CHEST_BOAT, new BoatDispenseItemBehavior(EntityType.MANGROVE_CHEST_BOAT));
        DispenserBlock.registerBehavior(Items.PALE_OAK_CHEST_BOAT, new BoatDispenseItemBehavior(EntityType.PALE_OAK_CHEST_BOAT));
        DispenserBlock.registerBehavior(Items.BAMBOO_CHEST_RAFT, new BoatDispenseItemBehavior(EntityType.BAMBOO_CHEST_RAFT));
        DispenseItemBehavior dispenseItemBehavior = new DefaultDispenseItemBehavior() {
            private final DefaultDispenseItemBehavior defaultDispenseItemBehavior = new DefaultDispenseItemBehavior();

            @Override
            public ItemStack execute(BlockSource blockSource, ItemStack item) {
                DispensibleContainerItem dispensibleContainerItem = (DispensibleContainerItem)item.getItem();
                BlockPos blockPos = blockSource.pos().relative(blockSource.state().getValue(DispenserBlock.FACING));
                Level level = blockSource.level();
                // CraftBukkit start
                BlockState state = level.getBlockState(blockPos);
                ItemStack dispensedItem = item; // Paper - track changed item from the dispense event
                // Paper start - correctly check if the bucket place will succeed
                /* Taken from SolidBucketItem#emptyContents */
                boolean willEmptyContentsSolidBucketItem = dispensibleContainerItem instanceof net.minecraft.world.item.SolidBucketItem && level.isInWorldBounds(blockPos) && state.isAir();
                /* Taken from BucketItem#emptyContents */
                boolean willEmptyBucketItem = dispensibleContainerItem instanceof final net.minecraft.world.item.BucketItem bucketItem && bucketItem.content instanceof net.minecraft.world.level.material.FlowingFluid && (state.isAir() || state.canBeReplaced(bucketItem.content) || (state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlockContainer liquidBlockContainer && liquidBlockContainer.canPlaceLiquid(null, level, blockPos, state, bucketItem.content)));
                if (willEmptyContentsSolidBucketItem || willEmptyBucketItem) {
                // Paper end - correctly check if the bucket place will succeed
                    org.bukkit.block.Block block = org.bukkit.craftbukkit.block.CraftBlock.at(level, blockSource.pos());
                    org.bukkit.craftbukkit.inventory.CraftItemStack craftItem = org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(item.copyWithCount(1)); // Paper - single item in event

                    org.bukkit.event.block.BlockDispenseEvent event = new org.bukkit.event.block.BlockDispenseEvent(block, craftItem.clone(), org.bukkit.craftbukkit.util.CraftVector.toBukkit(blockPos));
                    level.getCraftServer().getPluginManager().callEvent(event);

                    if (event.isCancelled()) {
                        return item;
                    }

                    if (!event.getItem().equals(craftItem)) {
                        // Chain to handler for new item
                        ItemStack eventStack = org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(event.getItem());
                        DispenseItemBehavior dispenseBehavior = DispenserBlock.getDispenseBehavior(blockSource, eventStack);
                        if (dispenseBehavior != DispenseItemBehavior.NOOP && dispenseBehavior != this) {
                            dispenseBehavior.dispense(blockSource, eventStack);
                            return item;
                        }
                    }

                    // Paper start - track changed item from dispense event
                    dispensedItem = org.bukkit.craftbukkit.inventory.CraftItemStack.unwrap(event.getItem()); // unwrap is safe here as the stack isn't mutated
                    dispensibleContainerItem = (DispensibleContainerItem) dispensedItem.getItem();
                    // Paper end - track changed item from dispense event
                }
                // CraftBukkit end

                if (dispensibleContainerItem.emptyContents(null, level, blockPos, null)) {
                    dispensibleContainerItem.checkExtraContent(null, level, dispensedItem, blockPos); // Paper - track changed item from dispense event
                    return this.consumeWithRemainder(blockSource, item, new ItemStack(Items.BUCKET));
                } else {
                    return this.defaultDispenseItemBehavior.dispense(blockSource, item);
                }
            }
        };
        DispenserBlock.registerBehavior(Items.LAVA_BUCKET, dispenseItemBehavior);
        DispenserBlock.registerBehavior(Items.WATER_BUCKET, dispenseItemBehavior);
        DispenserBlock.registerBehavior(Items.POWDER_SNOW_BUCKET, dispenseItemBehavior);
        DispenserBlock.registerBehavior(Items.SALMON_BUCKET, dispenseItemBehavior);
        DispenserBlock.registerBehavior(Items.COD_BUCKET, dispenseItemBehavior);
        DispenserBlock.registerBehavior(Items.PUFFERFISH_BUCKET, dispenseItemBehavior);
        DispenserBlock.registerBehavior(Items.TROPICAL_FISH_BUCKET, dispenseItemBehavior);
        DispenserBlock.registerBehavior(Items.AXOLOTL_BUCKET, dispenseItemBehavior);
        DispenserBlock.registerBehavior(Items.TADPOLE_BUCKET, dispenseItemBehavior);
        DispenserBlock.registerBehavior(Items.BUCKET, new DefaultDispenseItemBehavior() {
            @Override
            public ItemStack execute(BlockSource blockSource, ItemStack item) {
                LevelAccessor levelAccessor = blockSource.level();
                BlockPos blockPos = blockSource.pos().relative(blockSource.state().getValue(DispenserBlock.FACING));
                BlockState blockState = levelAccessor.getBlockState(blockPos);
                if (blockState.getBlock() instanceof BucketPickup bucketPickup) {
                    ItemStack itemStack = bucketPickup.pickupBlock(null, org.bukkit.craftbukkit.util.DummyGeneratorAccess.INSTANCE, blockPos, blockState); // CraftBukkit
                    if (itemStack.isEmpty()) {
                        return super.execute(blockSource, item);
                    } else {
                        levelAccessor.gameEvent(null, GameEvent.FLUID_PICKUP, blockPos);
                        Item item1 = itemStack.getItem();
                        // Paper start - Call BlockDispenseEvent
                        ItemStack result = org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockDispenseEvent(blockSource, blockPos, item, this);
                        if (result != null) {
                            return result;
                        }
                        // Paper end - Call BlockDispenseEvent
                        itemStack = bucketPickup.pickupBlock(null, levelAccessor, blockPos, blockState); // CraftBukkit - from above
                        return this.consumeWithRemainder(blockSource, item, new ItemStack(item1));
                    }
                } else {
                    return super.execute(blockSource, item);
                }
            }
        });
        DispenserBlock.registerBehavior(Items.FLINT_AND_STEEL, new OptionalDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource blockSource, ItemStack item) {
                ServerLevel serverLevel = blockSource.level();
                this.setSuccess(true);
                Direction direction = blockSource.state().getValue(DispenserBlock.FACING);
                BlockPos blockPos = blockSource.pos().relative(direction);
                // Paper start - Call BlockDispenseEvent
                ItemStack result = org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockDispenseEvent(blockSource, blockPos, item, this);
                if (result != null) {
                    this.setSuccess(false);
                    return result;
                }
                // Paper end - Call BlockDispenseEvent
                BlockState blockState = serverLevel.getBlockState(blockPos);
                if (BaseFireBlock.canBePlacedAt(serverLevel, blockPos, direction)) {
                    // CraftBukkit start - Ignition by dispensing flint and steel
                    if (!org.bukkit.craftbukkit.event.CraftEventFactory.callBlockIgniteEvent(serverLevel, blockPos, blockSource.pos()).isCancelled()) {
                        serverLevel.setBlockAndUpdate(blockPos, BaseFireBlock.getState(serverLevel, blockPos));
                        serverLevel.gameEvent(null, GameEvent.BLOCK_PLACE, blockPos);
                    }
                    // CraftBukkit end
                } else if (CampfireBlock.canLight(blockState) || CandleBlock.canLight(blockState) || CandleCakeBlock.canLight(blockState)) {
                    serverLevel.setBlockAndUpdate(blockPos, blockState.setValue(BlockStateProperties.LIT, true));
                    serverLevel.gameEvent(null, GameEvent.BLOCK_CHANGE, blockPos);
                } else if (blockState.getBlock() instanceof TntBlock) {
                    if (TntBlock.prime(serverLevel, blockPos, () -> org.bukkit.craftbukkit.event.CraftEventFactory.callTNTPrimeEvent(serverLevel, blockPos, org.bukkit.event.block.TNTPrimeEvent.PrimeCause.DISPENSER, null, blockSource.pos()))) { // CraftBukkit - TNTPrimeEvent
                        serverLevel.removeBlock(blockPos, false);
                    } else {
                        this.setSuccess(false);
                    }
                } else {
                    this.setSuccess(false);
                }

                if (this.isSuccess()) {
                    item.hurtAndBreak(1, serverLevel, null, item1 -> {});
                }

                return item;
            }
        });
        DispenserBlock.registerBehavior(Items.BONE_MEAL, new OptionalDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource blockSource, ItemStack item) {
                this.setSuccess(true);
                Level level = blockSource.level();
                BlockPos blockPos = blockSource.pos().relative(blockSource.state().getValue(DispenserBlock.FACING));
                // Paper start - Call BlockDispenseEvent
                ItemStack result = org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockDispenseEvent(blockSource, blockPos, item, this);
                if (result != null) {
                    this.setSuccess(false);
                    return result;
                }
                // Paper end - Call BlockDispenseEvent
                io.papermc.paper.threadedregions.RegionizedWorldData worldData = level.getCurrentWorldData(); // Folia - region threading
                worldData.captureTreeGeneration = true; // CraftBukkit // Folia - region threading
                if (!BoneMealItem.growCrop(item, level, blockPos) && !BoneMealItem.growWaterPlant(item, level, blockPos, null)) {
                    this.setSuccess(false);
                } else if (!level.isClientSide()) {
                    level.levelEvent(LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH, blockPos, 15);
                }
                // CraftBukkit start
                worldData.captureTreeGeneration = false; // Folia - region threading
                if (!worldData.capturedBlockStates.isEmpty()) { // Folia - region threading
                    org.bukkit.TreeType treeType = net.minecraft.world.level.block.SaplingBlock.treeTypeRT.get(); // Folia - region threading
                    net.minecraft.world.level.block.SaplingBlock.treeTypeRT.set(null); // Folia - region threading
                    org.bukkit.Location location = org.bukkit.craftbukkit.util.CraftLocation.toBukkit(blockPos, level);
                    List<org.bukkit.block.BlockState> states = new java.util.ArrayList<>(worldData.capturedBlockStates.values()); // Folia - region threading
                    worldData.capturedBlockStates.clear(); // Folia - region threading
                    org.bukkit.event.world.StructureGrowEvent structureEvent = null;
                    if (treeType != null) {
                        structureEvent = new org.bukkit.event.world.StructureGrowEvent(location, treeType, false, null, states);
                        org.bukkit.Bukkit.getPluginManager().callEvent(structureEvent);
                    }

                    org.bukkit.event.block.BlockFertilizeEvent fertilizeEvent = new org.bukkit.event.block.BlockFertilizeEvent(location.getBlock(), null, states);
                    fertilizeEvent.setCancelled(structureEvent != null && structureEvent.isCancelled());
                    org.bukkit.Bukkit.getPluginManager().callEvent(fertilizeEvent);

                    if (!fertilizeEvent.isCancelled()) {
                        for (org.bukkit.block.BlockState state : states) {
                            org.bukkit.craftbukkit.block.CraftBlockState craftBlockState = (org.bukkit.craftbukkit.block.CraftBlockState) state;
                            craftBlockState.place(craftBlockState.getFlags());
                            blockSource.level().checkCapturedTreeStateForObserverNotify(blockPos, craftBlockState); // Paper - notify observers even if grow failed
                        }
                    }
                }
                // CraftBukkit end

                return item;
            }
        });
        DispenserBlock.registerBehavior(Blocks.TNT, new OptionalDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource blockSource, ItemStack item) {
                ServerLevel serverLevel = blockSource.level();
                if (!serverLevel.getGameRules().get(GameRules.TNT_EXPLODES)) {
                    this.setSuccess(false);
                    return item;
                } else {
                    BlockPos blockPos = blockSource.pos().relative(blockSource.state().getValue(DispenserBlock.FACING));
                    // CraftBukkit start
                    ItemStack singleItemStack = item.copyWithCount(1); // Paper - shrink at end and single item in event
                    org.bukkit.block.Block block = org.bukkit.craftbukkit.block.CraftBlock.at(serverLevel, blockSource.pos());
                    org.bukkit.craftbukkit.inventory.CraftItemStack craftItem = org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(singleItemStack);

                    org.bukkit.event.block.BlockDispenseEvent event = new org.bukkit.event.block.BlockDispenseEvent(block, craftItem.clone(), new org.bukkit.util.Vector((double) blockPos.getX() + 0.5D, (double) blockPos.getY(), (double) blockPos.getZ() + 0.5D));
                    serverLevel.getCraftServer().getPluginManager().callEvent(event);

                    if (event.isCancelled()) {
                        return item;
                    }

                    boolean shrink = true;
                    if (!event.getItem().equals(craftItem)) {
                        shrink = false;
                        // Chain to handler for new item
                        ItemStack eventStack = org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(event.getItem());
                        DispenseItemBehavior dispenseBehavior = DispenserBlock.getDispenseBehavior(blockSource, eventStack);
                        if (dispenseBehavior != DispenseItemBehavior.NOOP && dispenseBehavior != this) {
                            dispenseBehavior.dispense(blockSource, eventStack);
                            return item;
                        }
                    }

                    PrimedTnt primedTnt = new PrimedTnt(serverLevel, event.getVelocity().getX(), event.getVelocity().getY(), event.getVelocity().getZ(), null);
                    // CraftBukkit end
                    serverLevel.addFreshEntity(primedTnt);
                    serverLevel.playSound(null, primedTnt.getX(), primedTnt.getY(), primedTnt.getZ(), SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0F, 1.0F);
                    serverLevel.gameEvent(null, GameEvent.ENTITY_PLACE, org.bukkit.craftbukkit.util.CraftVector.toBlockPos(event.getVelocity())); // Paper - update game event position
                    if (shrink) item.shrink(1); // Paper
                    this.setSuccess(true);
                    return item;
                }
            }
        });
        DispenserBlock.registerBehavior(
            Items.WITHER_SKELETON_SKULL,
            new OptionalDispenseItemBehavior() {
                @Override
                protected ItemStack execute(BlockSource blockSource, ItemStack item) {
                    Level level = blockSource.level();
                    Direction direction = blockSource.state().getValue(DispenserBlock.FACING);
                    BlockPos blockPos = blockSource.pos().relative(direction);
                    // Paper start - Call BlockDispenseEvent
                    ItemStack result = org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockDispenseEvent(blockSource, blockPos, item, this);
                    if (result != null) {
                        this.setSuccess(false);
                        return result;
                    }
                    // Paper end - Call BlockDispenseEvent
                    if (level.isEmptyBlock(blockPos) && WitherSkullBlock.canSpawnMob(level, blockPos, item)) {
                        level.setBlock(
                            blockPos,
                            Blocks.WITHER_SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION, RotationSegment.convertToSegment(direction)),
                            Block.UPDATE_ALL
                        );
                        level.gameEvent(null, GameEvent.BLOCK_PLACE, blockPos);
                        BlockEntity blockEntity = level.getBlockEntity(blockPos);
                        if (blockEntity instanceof SkullBlockEntity) {
                            WitherSkullBlock.checkSpawn(level, blockPos, (SkullBlockEntity)blockEntity);
                        }

                        item.shrink(1);
                        this.setSuccess(true);
                    } else {
                        this.setSuccess(EquipmentDispenseItemBehavior.dispenseEquipment(blockSource, item, this)); // Paper - fix possible StackOverflowError
                    }

                    return item;
                }
            }
        );
        DispenserBlock.registerBehavior(Blocks.CARVED_PUMPKIN, new OptionalDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource blockSource, ItemStack item) {
                Level level = blockSource.level();
                BlockPos blockPos = blockSource.pos().relative(blockSource.state().getValue(DispenserBlock.FACING));
                CarvedPumpkinBlock carvedPumpkinBlock = (CarvedPumpkinBlock)Blocks.CARVED_PUMPKIN;
                // Paper start - Call BlockDispenseEvent
                ItemStack result = org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockDispenseEvent(blockSource, blockPos, item, this);
                if (result != null) {
                    this.setSuccess(false);
                    return result;
                }
                // Paper end - Call BlockDispenseEvent
                if (level.isEmptyBlock(blockPos) && carvedPumpkinBlock.canSpawnGolem(level, blockPos)) {
                    if (!level.isClientSide()) {
                        level.setBlock(blockPos, carvedPumpkinBlock.defaultBlockState(), Block.UPDATE_ALL);
                        level.gameEvent(null, GameEvent.BLOCK_PLACE, blockPos);
                    }

                    item.shrink(1);
                    this.setSuccess(true);
                } else {
                    this.setSuccess(EquipmentDispenseItemBehavior.dispenseEquipment(blockSource, item, this)); // Paper - fix possible StackOverflowError
                }

                return item;
            }
        });
        DispenserBlock.registerBehavior(Blocks.SHULKER_BOX.asItem(), new ShulkerBoxDispenseBehavior());

        for (DyeColor dyeColor : DyeColor.values()) {
            DispenserBlock.registerBehavior(ShulkerBoxBlock.getBlockByColor(dyeColor).asItem(), new ShulkerBoxDispenseBehavior());
        }

        DispenserBlock.registerBehavior(
            Items.GLASS_BOTTLE.asItem(),
            new OptionalDispenseItemBehavior() {
                private ItemStack takeLiquid(BlockSource blockSource, ItemStack item, ItemStack filledItem) {
                    blockSource.level().gameEvent(null, GameEvent.FLUID_PICKUP, blockSource.pos());
                    return this.consumeWithRemainder(blockSource, item, filledItem);
                }

                @Override
                public ItemStack execute(BlockSource blockSource, ItemStack item) {
                    this.setSuccess(false);
                    ServerLevel serverLevel = blockSource.level();
                    BlockPos blockPos = blockSource.pos().relative(blockSource.state().getValue(DispenserBlock.FACING));
                    BlockState blockState = serverLevel.getBlockState(blockPos);
                    // Paper start - Call BlockDispenseEvent
                    ItemStack result = org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockDispenseEvent(blockSource, blockPos, item, this);
                    if (result != null) {
                        return result;
                    }
                    // Paper end - Call BlockDispenseEvent
                    if (blockState.is(
                            BlockTags.BEEHIVES,
                            blockStateBase -> blockStateBase.hasProperty(BeehiveBlock.HONEY_LEVEL) && blockStateBase.getBlock() instanceof BeehiveBlock
                        )
                        && blockState.getValue(BeehiveBlock.HONEY_LEVEL) >= 5) {
                        ((BeehiveBlock)blockState.getBlock())
                            .releaseBeesAndResetHoneyLevel(serverLevel, blockState, blockPos, null, BeehiveBlockEntity.BeeReleaseStatus.BEE_RELEASED);
                        this.setSuccess(true);
                        return this.takeLiquid(blockSource, item, new ItemStack(Items.HONEY_BOTTLE));
                    } else if (serverLevel.getFluidState(blockPos).is(FluidTags.WATER)) {
                        this.setSuccess(true);
                        return this.takeLiquid(blockSource, item, PotionContents.createItemStack(Items.POTION, Potions.WATER));
                    } else {
                        return super.execute(blockSource, item);
                    }
                }
            }
        );
        DispenserBlock.registerBehavior(Items.GLOWSTONE, new OptionalDispenseItemBehavior() {
            @Override
            public ItemStack execute(BlockSource blockSource, ItemStack item) {
                Direction direction = blockSource.state().getValue(DispenserBlock.FACING);
                BlockPos blockPos = blockSource.pos().relative(direction);
                Level level = blockSource.level();
                BlockState blockState = level.getBlockState(blockPos);
                this.setSuccess(true);
                if (blockState.is(Blocks.RESPAWN_ANCHOR)) {
                    if (blockState.getValue(RespawnAnchorBlock.CHARGE) != 4) {
                        // Paper start - Call BlockDispenseEvent
                        ItemStack result = org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockDispenseEvent(blockSource, blockPos, item, this);
                        if (result != null) {
                            this.setSuccess(false);
                            return result;
                        }
                        // Paper end - Call BlockDispenseEvent
                        RespawnAnchorBlock.charge(null, level, blockPos, blockState);
                        item.shrink(1);
                    } else {
                        this.setSuccess(false);
                    }

                    return item;
                } else {
                    return super.execute(blockSource, item);
                }
            }
        });
        DispenserBlock.registerBehavior(Items.SHEARS.asItem(), new ShearsDispenseItemBehavior());
        DispenserBlock.registerBehavior(Items.BRUSH.asItem(), new OptionalDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource blockSource, ItemStack item) {
                ServerLevel serverLevel = blockSource.level();
                BlockPos blockPos = blockSource.pos().relative(blockSource.state().getValue(DispenserBlock.FACING));
                List<Armadillo> entitiesOfClass = serverLevel.getEntitiesOfClass(Armadillo.class, new AABB(blockPos), EntitySelector.NO_SPECTATORS);
                if (entitiesOfClass.isEmpty()) {
                    this.setSuccess(false);
                    return item;
                } else {
                    // CraftBukkit start
                    org.bukkit.block.Block block = org.bukkit.craftbukkit.block.CraftBlock.at(serverLevel, blockSource.pos());
                    org.bukkit.craftbukkit.inventory.CraftItemStack craftItem = org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(item); // Paper - ignore stack size on damageable items

                    org.bukkit.event.block.BlockDispenseEvent event = new org.bukkit.event.block.BlockDispenseArmorEvent(block, craftItem.clone(), entitiesOfClass.get(0).getBukkitLivingEntity());
                    serverLevel.getCraftServer().getPluginManager().callEvent(event);

                    if (event.isCancelled()) {
                        this.setSuccess(false);
                        return item;
                    }

                    if (!event.getItem().equals(craftItem)) {
                        // Chain to handler for new item
                        ItemStack eventStack = org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(event.getItem());
                        DispenseItemBehavior dispenseBehavior = DispenserBlock.getDispenseBehavior(blockSource, eventStack);
                        if (dispenseBehavior != DispenseItemBehavior.NOOP && dispenseBehavior != this) {
                            dispenseBehavior.dispense(blockSource, eventStack);
                            return item;
                        }
                    }
                    // CraftBukkit end
                    for (Armadillo armadillo : entitiesOfClass) {
                        if (armadillo.brushOffScute(null, item)) {
                            item.hurtAndBreak(16, serverLevel, null, item1 -> {});
                            return item;
                        }
                    }

                    this.setSuccess(false);
                    return item;
                }
            }
        });
        DispenserBlock.registerBehavior(Items.HONEYCOMB, new OptionalDispenseItemBehavior() {
            @Override
            public ItemStack execute(BlockSource blockSource, ItemStack item) {
                BlockPos blockPos = blockSource.pos().relative(blockSource.state().getValue(DispenserBlock.FACING));
                Level level = blockSource.level();
                BlockState blockState = level.getBlockState(blockPos);
                Optional<BlockState> waxed = HoneycombItem.getWaxed(blockState);
                if (waxed.isPresent()) {
                    // Paper start - Call BlockDispenseEvent
                    ItemStack result = org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockDispenseEvent(blockSource, blockPos, item, this);
                    if (result != null) {
                        this.setSuccess(false);
                        return result;
                    }
                    // Paper end - Call BlockDispenseEvent
                    level.setBlockAndUpdate(blockPos, waxed.get());
                    level.levelEvent(LevelEvent.PARTICLES_AND_SOUND_WAX_ON, blockPos, 0);
                    item.shrink(1);
                    this.setSuccess(true);
                    return item;
                } else {
                    return super.execute(blockSource, item);
                }
            }
        });
        DispenserBlock.registerBehavior(
            Items.POTION,
            new DefaultDispenseItemBehavior() {
                private final DefaultDispenseItemBehavior defaultDispenseItemBehavior = new DefaultDispenseItemBehavior();

                @Override
                public ItemStack execute(BlockSource blockSource, ItemStack item) {
                    PotionContents potionContents = item.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
                    if (!potionContents.is(Potions.WATER)) {
                        return this.defaultDispenseItemBehavior.dispense(blockSource, item);
                    } else {
                        ServerLevel serverLevel = blockSource.level();
                        BlockPos blockPos = blockSource.pos();
                        BlockPos blockPos1 = blockSource.pos().relative(blockSource.state().getValue(DispenserBlock.FACING));
                        if (!serverLevel.getBlockState(blockPos1).is(BlockTags.CONVERTABLE_TO_MUD)) {
                            return this.defaultDispenseItemBehavior.dispense(blockSource, item);
                        } else {
                            // Paper start - Call BlockDispenseEvent
                            ItemStack result = org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockDispenseEvent(blockSource, blockPos1, item, this);
                            if (result != null) {
                                return result;
                            }
                            // Paper end - Call BlockDispenseEvent
                            if (!serverLevel.isClientSide()) {
                                for (int i = 0; i < 5; i++) {
                                    serverLevel.sendParticles(
                                        ParticleTypes.SPLASH,
                                        blockPos.getX() + serverLevel.random.nextDouble(),
                                        blockPos.getY() + 1,
                                        blockPos.getZ() + serverLevel.random.nextDouble(),
                                        1,
                                        0.0,
                                        0.0,
                                        0.0,
                                        1.0
                                    );
                                }
                            }

                            serverLevel.playSound(null, blockPos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
                            serverLevel.gameEvent(null, GameEvent.FLUID_PLACE, blockPos);
                            serverLevel.setBlockAndUpdate(blockPos1, Blocks.MUD.defaultBlockState());
                            return this.consumeWithRemainder(blockSource, item, new ItemStack(Items.GLASS_BOTTLE));
                        }
                    }
                }
            }
        );
        DispenserBlock.registerBehavior(Items.MINECART, new MinecartDispenseItemBehavior(EntityType.MINECART));
        DispenserBlock.registerBehavior(Items.CHEST_MINECART, new MinecartDispenseItemBehavior(EntityType.CHEST_MINECART));
        DispenserBlock.registerBehavior(Items.FURNACE_MINECART, new MinecartDispenseItemBehavior(EntityType.FURNACE_MINECART));
        DispenserBlock.registerBehavior(Items.TNT_MINECART, new MinecartDispenseItemBehavior(EntityType.TNT_MINECART));
        DispenserBlock.registerBehavior(Items.HOPPER_MINECART, new MinecartDispenseItemBehavior(EntityType.HOPPER_MINECART));
        DispenserBlock.registerBehavior(Items.COMMAND_BLOCK_MINECART, new MinecartDispenseItemBehavior(EntityType.COMMAND_BLOCK_MINECART));
    }
}
