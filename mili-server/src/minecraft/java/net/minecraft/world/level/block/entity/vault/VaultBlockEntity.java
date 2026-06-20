package net.minecraft.world.level.block.entity.vault;

import com.google.common.annotations.VisibleForTesting;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.VaultBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class VaultBlockEntity extends BlockEntity {
    public final VaultServerData serverData = new VaultServerData();
    private final VaultSharedData sharedData = new VaultSharedData();
    private final VaultClientData clientData = new VaultClientData();
    private VaultConfig config = VaultConfig.DEFAULT;

    public VaultBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityType.VAULT, pos, blockState);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return Util.make(
            new CompoundTag(), tag -> tag.store("shared_data", VaultSharedData.CODEC, registries.createSerializationContext(NbtOps.INSTANCE), this.sharedData)
        );
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("config", VaultConfig.CODEC, this.config);
        output.store("shared_data", VaultSharedData.CODEC, this.sharedData);
        output.store("server_data", VaultServerData.CODEC, this.serverData);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.read("server_data", VaultServerData.CODEC).ifPresent(this.serverData::set);
        this.config = input.read("config", VaultConfig.CODEC).orElse(VaultConfig.DEFAULT);
        input.read("shared_data", VaultSharedData.CODEC).ifPresent(this.sharedData::set);
    }

    public @Nullable VaultServerData getServerData() {
        return this.level != null && !this.level.isClientSide() ? this.serverData : null;
    }

    public VaultSharedData getSharedData() {
        return this.sharedData;
    }

    public VaultClientData getClientData() {
        return this.clientData;
    }

    public VaultConfig getConfig() {
        return this.config;
    }

    @VisibleForTesting
    public void setConfig(VaultConfig config) {
        this.config = config;
    }

    public static final class Client {
        private static final int PARTICLE_TICK_RATE = 20;
        private static final float IDLE_PARTICLE_CHANCE = 0.5F;
        private static final float AMBIENT_SOUND_CHANCE = 0.02F;
        private static final int ACTIVATION_PARTICLE_COUNT = 20;
        private static final int DEACTIVATION_PARTICLE_COUNT = 20;

        public static void tick(Level level, BlockPos pos, BlockState state, VaultClientData clientData, VaultSharedData sharedData) {
            clientData.updateDisplayItemSpin();
            if (level.getGameTime() % 20L == 0L) {
                emitConnectionParticlesForNearbyPlayers(level, pos, state, sharedData);
            }

            emitIdleParticles(level, pos, sharedData, state.getValue(VaultBlock.OMINOUS) ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.SMALL_FLAME);
            playIdleSounds(level, pos, sharedData);
        }

        public static void emitActivationParticles(Level level, BlockPos pos, BlockState state, VaultSharedData sharedData, ParticleOptions particle) {
            emitConnectionParticlesForNearbyPlayers(level, pos, state, sharedData);
            RandomSource randomSource = level.random;

            for (int i = 0; i < 20; i++) {
                Vec3 vec3 = randomPosInsideCage(pos, randomSource);
                level.addParticle(ParticleTypes.SMOKE, vec3.x(), vec3.y(), vec3.z(), 0.0, 0.0, 0.0);
                level.addParticle(particle, vec3.x(), vec3.y(), vec3.z(), 0.0, 0.0, 0.0);
            }
        }

        public static void emitDeactivationParticles(Level level, BlockPos pos, ParticleOptions particle) {
            RandomSource randomSource = level.random;

            for (int i = 0; i < 20; i++) {
                Vec3 vec3 = randomPosCenterOfCage(pos, randomSource);
                Vec3 vec31 = new Vec3(randomSource.nextGaussian() * 0.02, randomSource.nextGaussian() * 0.02, randomSource.nextGaussian() * 0.02);
                level.addParticle(particle, vec3.x(), vec3.y(), vec3.z(), vec31.x(), vec31.y(), vec31.z());
            }
        }

        private static void emitIdleParticles(Level level, BlockPos pos, VaultSharedData sharedData, ParticleOptions particle) {
            RandomSource random = level.getRandom();
            if (random.nextFloat() <= 0.5F) {
                Vec3 vec3 = randomPosInsideCage(pos, random);
                level.addParticle(ParticleTypes.SMOKE, vec3.x(), vec3.y(), vec3.z(), 0.0, 0.0, 0.0);
                if (shouldDisplayActiveEffects(sharedData)) {
                    level.addParticle(particle, vec3.x(), vec3.y(), vec3.z(), 0.0, 0.0, 0.0);
                }
            }
        }

        private static void emitConnectionParticlesForPlayer(Level level, Vec3 pos, Player player) {
            RandomSource randomSource = level.random;
            Vec3 vec3 = pos.vectorTo(player.position().add(0.0, player.getBbHeight() / 2.0F, 0.0));
            int randomInt = Mth.nextInt(randomSource, 2, 5);

            for (int i = 0; i < randomInt; i++) {
                Vec3 vec31 = vec3.offsetRandom(randomSource, 1.0F);
                level.addParticle(ParticleTypes.VAULT_CONNECTION, pos.x(), pos.y(), pos.z(), vec31.x(), vec31.y(), vec31.z());
            }
        }

        private static void emitConnectionParticlesForNearbyPlayers(Level level, BlockPos pos, BlockState state, VaultSharedData sharedData) {
            Set<UUID> connectedPlayers = sharedData.getConnectedPlayers();
            if (!connectedPlayers.isEmpty()) {
                Vec3 vec3 = keyholePos(pos, state.getValue(VaultBlock.FACING));

                for (UUID uuid : connectedPlayers) {
                    Player playerByUuid = level.getPlayerByUUID(uuid);
                    if (playerByUuid != null && isWithinConnectionRange(pos, sharedData, playerByUuid)) {
                        emitConnectionParticlesForPlayer(level, vec3, playerByUuid);
                    }
                }
            }
        }

        private static boolean isWithinConnectionRange(BlockPos pos, VaultSharedData sharedData, Player player) {
            return player.blockPosition().distSqr(pos) <= Mth.square(sharedData.connectedParticlesRange());
        }

        private static void playIdleSounds(Level level, BlockPos pos, VaultSharedData sharedData) {
            if (shouldDisplayActiveEffects(sharedData)) {
                RandomSource random = level.getRandom();
                if (random.nextFloat() <= 0.02F) {
                    level.playLocalSound(
                        pos, SoundEvents.VAULT_AMBIENT, SoundSource.BLOCKS, random.nextFloat() * 0.25F + 0.75F, random.nextFloat() + 0.5F, false
                    );
                }
            }
        }

        public static boolean shouldDisplayActiveEffects(VaultSharedData sharedData) {
            return sharedData.hasDisplayItem();
        }

        private static Vec3 randomPosCenterOfCage(BlockPos pos, RandomSource random) {
            return Vec3.atLowerCornerOf(pos).add(Mth.nextDouble(random, 0.4, 0.6), Mth.nextDouble(random, 0.4, 0.6), Mth.nextDouble(random, 0.4, 0.6));
        }

        private static Vec3 randomPosInsideCage(BlockPos pos, RandomSource random) {
            return Vec3.atLowerCornerOf(pos).add(Mth.nextDouble(random, 0.1, 0.9), Mth.nextDouble(random, 0.25, 0.75), Mth.nextDouble(random, 0.1, 0.9));
        }

        private static Vec3 keyholePos(BlockPos pos, Direction facing) {
            return Vec3.atBottomCenterOf(pos).add(facing.getStepX() * 0.5, 1.75, facing.getStepZ() * 0.5);
        }
    }

    public static final class Server {
        private static final int UNLOCKING_DELAY_TICKS = 14;
        private static final int DISPLAY_CYCLE_TICK_RATE = 20;
        private static final int INSERT_FAIL_SOUND_BUFFER_TICKS = 15;

        public static void tick(ServerLevel level, BlockPos pos, BlockState state, VaultConfig config, VaultServerData serverData, VaultSharedData sharedData) {
            VaultState vaultState = state.getValue(VaultBlock.STATE);
            if (shouldCycleDisplayItem(level.getGameTime(), vaultState)) {
                cycleDisplayItemFromLootTable(level, vaultState, config, sharedData, pos);
            }

            BlockState blockState = state;
            if (level.getGameTime() >= serverData.stateUpdatingResumesAt()) {
                blockState = state.setValue(VaultBlock.STATE, vaultState.tickAndGetNext(level, pos, config, serverData, sharedData));
                if (state != blockState) {
                    setVaultState(level, pos, state, blockState, config, sharedData);
                }
            }

            if (serverData.isDirty || sharedData.isDirty) {
                VaultBlockEntity.setChanged(level, pos, state);
                if (sharedData.isDirty) {
                    level.sendBlockUpdated(pos, state, blockState, Block.UPDATE_CLIENTS);
                }

                serverData.isDirty = false;
                sharedData.isDirty = false;
            }
        }

        public static void tryInsertKey(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            VaultConfig config,
            VaultServerData serverData,
            VaultSharedData sharedData,
            Player player,
            ItemStack stack
        ) {
            VaultState vaultState = state.getValue(VaultBlock.STATE);
            if (canEjectReward(config, vaultState)) {
                if (!isValidToInsert(config, stack)) {
                    playInsertFailSound(level, serverData, pos, SoundEvents.VAULT_INSERT_ITEM_FAIL);
                } else if (serverData.hasRewardedPlayer(player)) {
                    playInsertFailSound(level, serverData, pos, SoundEvents.VAULT_REJECT_REWARDED_PLAYER);
                } else {
                    List<ItemStack> list = resolveItemsToEject(level, config, pos, player, stack);
                    if (!list.isEmpty()) {
                        player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
                        stack.consume(config.keyItem().getCount(), player);
                        // CraftBukkit start
                        LootTable sourceLootTable = level.getServer().reloadableRegistries().getLootTable(config.lootTable());
                        org.bukkit.event.block.BlockDispenseLootEvent vaultDispenseLootEvent = org.bukkit.craftbukkit.event.CraftEventFactory.callBlockDispenseLootEvent(level, pos, player, list, sourceLootTable);
                        if (vaultDispenseLootEvent.isCancelled()) return;
                        list = vaultDispenseLootEvent.getDispensedLoot().stream().map(org.bukkit.craftbukkit.inventory.CraftItemStack::asNMSCopy).toList();
                        // CraftBukkit end
                        unlock(level, state, pos, config, serverData, sharedData, list, player); // Paper - Vault API
                        serverData.addToRewardedPlayers(player);
                        sharedData.updateConnectedPlayersWithinRange(level, pos, serverData, config, config.deactivationRange());
                    }
                }
            }
        }

        static void setVaultState(ServerLevel level, BlockPos pos, BlockState oldState, BlockState newState, VaultConfig config, VaultSharedData sharedData) {
        // Paper start - Vault API
            setVaultState(level, pos, oldState, newState, config,sharedData, null);
        }

        static void setVaultState(ServerLevel level, BlockPos pos, BlockState oldState, BlockState newState, VaultConfig config, VaultSharedData sharedData, @Nullable Player associatedPlayer) {
            VaultState vaultState = oldState.getValue(VaultBlock.STATE); final VaultState oldVaultState = vaultState;
            VaultState vaultState1 = newState.getValue(VaultBlock.STATE); final VaultState newVaultState = vaultState1;
            org.bukkit.entity.Player apiAssociatedPlayer = null;
            if (associatedPlayer != null) {
                apiAssociatedPlayer = (org.bukkit.entity.Player) associatedPlayer.getBukkitEntity();
            } else if (newVaultState == VaultState.ACTIVE) {
                final Set<UUID> connectedPlayers = sharedData.getConnectedPlayers();
                if (!connectedPlayers.isEmpty()) { // Used over sharedData#hasConnectedPlayers to ensure belows iterator#next is always okay.
                    apiAssociatedPlayer = level.getCraftServer().getPlayer(connectedPlayers.iterator().next());
                }
            }
            final io.papermc.paper.event.block.VaultChangeStateEvent event = new io.papermc.paper.event.block.VaultChangeStateEvent(
                org.bukkit.craftbukkit.block.CraftBlock.at(level, pos),
                apiAssociatedPlayer,
                org.bukkit.craftbukkit.block.data.CraftBlockData.toBukkit(oldVaultState, org.bukkit.block.data.type.Vault.State.class),
                org.bukkit.craftbukkit.block.data.CraftBlockData.toBukkit(newVaultState, org.bukkit.block.data.type.Vault.State.class)
            );
            if (!event.callEvent()) return;
        // Paper end - Vault API
            level.setBlock(pos, newState, Block.UPDATE_ALL);
            vaultState.onTransition(level, pos, vaultState1, config, sharedData, newState.getValue(VaultBlock.OMINOUS));
        }

        static void cycleDisplayItemFromLootTable(ServerLevel level, VaultState state, VaultConfig config, VaultSharedData sharedData, BlockPos pos) {
            if (!canEjectReward(config, state)) {
                sharedData.setDisplayItem(ItemStack.EMPTY);
            } else {
                ItemStack randomDisplayItemFromLootTable = getRandomDisplayItemFromLootTable(
                    level, pos, config.overrideLootTableToDisplay().orElse(config.lootTable())
                );
                // CraftBukkit start
                org.bukkit.event.block.VaultDisplayItemEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callVaultDisplayItemEvent(level, pos, randomDisplayItemFromLootTable);
                if (event.isCancelled()) return;
                randomDisplayItemFromLootTable = org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(event.getDisplayItem());
                // CraftBukkit end
                sharedData.setDisplayItem(randomDisplayItemFromLootTable);
            }
        }

        private static ItemStack getRandomDisplayItemFromLootTable(ServerLevel level, BlockPos pos, ResourceKey<LootTable> lootTable) {
            LootTable lootTable1 = level.getServer().reloadableRegistries().getLootTable(lootTable);
            LootParams lootParams = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .create(LootContextParamSets.VAULT);
            List<ItemStack> randomItems = lootTable1.getRandomItems(lootParams, level.getRandom());
            return randomItems.isEmpty() ? ItemStack.EMPTY : Util.getRandom(randomItems, level.getRandom());
        }

        private static void unlock(
            ServerLevel level,
            BlockState state,
            BlockPos pos,
            VaultConfig config,
            VaultServerData serverData,
            VaultSharedData sharedData,
            List<ItemStack> itemsToEject
        ) {
        // Paper start - Vault API
            unlock(level, state, pos, config, serverData, sharedData, itemsToEject, null);
        }
        private static void unlock(
            ServerLevel level,
            BlockState state,
            BlockPos pos,
            VaultConfig config,
            VaultServerData serverData,
            VaultSharedData sharedData,
            List<ItemStack> itemsToEject,
            final @Nullable Player associatedPlayer
        ) {
        // Paper end - Vault API
            serverData.setItemsToEject(itemsToEject);
            sharedData.setDisplayItem(serverData.getNextItemToEject());
            serverData.pauseStateUpdatingUntil(level.getGameTime() + 14L);
            setVaultState(level, pos, state, state.setValue(VaultBlock.STATE, VaultState.UNLOCKING), config, sharedData, associatedPlayer); // Paper - Vault API
        }

        private static List<ItemStack> resolveItemsToEject(ServerLevel level, VaultConfig config, BlockPos pos, Player player, ItemStack key) {
            LootTable lootTable = level.getServer().reloadableRegistries().getLootTable(config.lootTable());
            LootParams lootParams = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .withLuck(player.getLuck())
                .withParameter(LootContextParams.THIS_ENTITY, player)
                .withParameter(LootContextParams.TOOL, key)
                .create(LootContextParamSets.VAULT);
            return lootTable.getRandomItems(lootParams);
        }

        private static boolean canEjectReward(VaultConfig config, VaultState state) {
            return !config.keyItem().isEmpty() && state != VaultState.INACTIVE;
        }

        private static boolean isValidToInsert(VaultConfig config, ItemStack stack) {
            return ItemStack.isSameItemSameComponents(stack, config.keyItem()) && stack.getCount() >= config.keyItem().getCount();
        }

        private static boolean shouldCycleDisplayItem(long gameTime, VaultState state) {
            return gameTime % 20L == 0L && state == VaultState.ACTIVE;
        }

        private static void playInsertFailSound(ServerLevel level, VaultServerData serverData, BlockPos pos, SoundEvent sound) {
            if (level.getGameTime() >= serverData.getLastInsertFailTimestamp() + 15L) {
                level.playSound(null, pos, sound, SoundSource.BLOCKS);
                serverData.setLastInsertFailTimestamp(level.getGameTime());
            }
        }
    }
}
