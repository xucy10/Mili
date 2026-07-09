@file:JvmName("BotRemoveEventKt")
package org.leavesmc.leaves.event.bot

import net.kyori.adventure.text.Component
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.HandlerList
import org.leavesmc.leaves.event.BukkitEvent

class BotRemoveEvent(
    val bot: Player,
    val save: Boolean
) : BukkitEvent(), Cancellable {

    enum class RemoveReason { INTERNAL, DEATH, PLUGIN }

    var reason: RemoveReason = RemoveReason.PLUGIN
        private set
    var remover: CommandSender? = null
        private set
    var resume = false
        private set
    var removeMessage: Component? = null
    var async = false
        private set

    constructor(botName: String, save: Boolean) : this(
        object : Player {
            override fun getName() = botName
            override fun getServer() = throw UnsupportedOperationException()
            override fun spigot() = throw UnsupportedOperationException()
            override fun getUniqueId() = java.util.UUID.randomUUID()
            override fun sendMessage(message: String) {}
            override fun sendMessage(vararg messages: String) {}
            override fun sendMessage(identity: net.kyori.identity.Identity?, message: Component, type: net.kyori.adventure.audience.MessageType) {}
            override fun getEquipment() = null!!
            override fun getInventory() = null!!
            override fun getEnderChest() = null!!
            override fun getOpenInventory() = null!!
            override fun openInventory(org.bukkit.inventory.Inventory) = null!!
            override fun openWorkbench(org.bukkit.Location?, Boolean) = null!!
            override fun openEnchanting(org.bukkit.Location?, Boolean) = null!!
            override fun openAnvil(org.bukkit.Location?, Boolean) = null!!
            override fun openCartographyTable(org.bukkit.Location?, Boolean) = null!!
            override fun openGrindstone(org.bukkit.Location?, Boolean) = null!!
            override fun openLoom(org.bukkit.Location?, Boolean) = null!!
            override fun openSmithingTable(org.bukkit.Location?, Boolean) = null!!
            override fun openStonecutter(org.bukkit.Location?, Boolean) = null!!
            override fun closeInventory() {}
            override fun getItemInHand() = null!!
            override fun getItemOnCursor() = null!!
            override fun setItemOnCursor(org.bukkit.inventory.ItemStack?) = null!!
            override fun hasCooldown(org.bukkit.Material) = false
            override fun getCooldown(org.bukkit.Material) = 0
            override fun setCooldown(org.bukkit.Material, Int) {}
            override fun getSleepTicks() = 0
            override fun sleep(org.bukkit.Location?, Boolean) = false
            override fun wakeup(Boolean) {}
            override fun getBedLocation() = null!!
            override fun getRespawnLocation() = null!!
            override fun setRespawnLocation(org.bukkit.Location?) {}
            override fun setRespawnLocation(org.bukkit.Location?, Boolean) {}
            override fun sendBlockChange(org.bukkit.Location?, org.bukkit.Material?, Byte) {}
            override fun sendBlockChanges(Collection<org.bukkit.block.data.BlockData>, Boolean) {}
            override fun sendBlockDamage(org.bukkit.Location?, Float) {}
            override fun sendBlockDamage(org.bukkit.Location?, Float, Int) {}
            override fun sendBlockDamage(org.bukkit.Location?, Float, org.bukkit.entity.Entity?) {}
            override fun sendBlockDamage(org.bukkit.Location?, Float, org.bukkit.entity.Entity?, Boolean) {}
            override fun sendEquipmentChange(org.bukkit.entity.LivingEntity?, org.bukkit.inventory.EquipmentSlot?, org.bukkit.inventory.ItemStack?) {}
            override fun sendMultiBlockChange(MutableMap<org.bukkit.Location, org.bukkit.block.data.BlockData>, Boolean) {}
            override fun sendSignChange(org.bukkit.Location?, @Nullable List<net.kyori.adventure.text.Component>?, org.bukkit.DyeColor?) {}
            override fun sendSignChange(org.bukkit.Location?, @Nullable List<net.kyori.adventure.text.Component>?, org.bukkit.DyeColor?, Boolean) {}
            override fun sendMap(org.bukkit.map.MapView?) {}
            override fun sendMap(org.bukkit.map.MapView?, Boolean) {}
            override fun setTexturePack(url: String) {}
            override fun setTexturePack(url: String, hash: ByteArray?) {}
            override fun updateInventory() {}
            override fun setPlayerTime(time: Long, relative: Boolean) {}
            override fun getPlayerTime() = 0L
            override fun getPlayerTimeOffset() = 0L
            override fun isPlayerTimeRelative() = false
            override fun resetPlayerTime() {}
            override fun setPlayerWeather(type: org.bukkit.WeatherType) {}
            override fun getPlayerWeather() = null!!
            override fun resetPlayerWeather() {}
            override fun giveExp(amount: Int) {}
            override fun giveExp(exp: org.bukkit.entity.ExperienceOrb) {}
            override fun giveExpLevels(levels: Int) {}
            override fun getExp() = 0f
            override fun setExp(exp: Float) {}
            override fun getExpTarget() = 0
            override fun setExpTarget(points: Int) {}
            override fun getLevel() = 0
            override fun setLevel(level: Int) {}
            override fun getTotalExperience() = 0
            override fun setTotalExperience(exp: Int) {}
            override fun sendExperienceChange(progress: Float) {}
            override fun sendExperienceChange(progress: Float, level: Int) {}
            override fun getAllowFlight() = false
            override fun setAllowFlight(Boolean) {}
            override fun hideEntity(org.bukkit.entity.Player?, org.bukkit.entity.Entity?) {}
            override fun showEntity(org.bukkit.entity.Player?, org.bukkit.entity.Entity?) {}
            override fun canSee(entity: org.bukkit.entity.Entity?) = false
            override fun canSee(entity: org.bukkit.entity.Player?) = false
            override fun hideEntity(plugin: org.bukkit.plugin.Plugin?, entity: org.bukkit.entity.Entity?) {}
            override fun showEntity(plugin: org.bukkit.plugin.Plugin?, entity: org.bukkit.entity.Entity?) {}
            override fun canSee(plugin: org.bukkit.plugin.Plugin?, entity: org.bukkit.entity.Entity?) = false
            override fun canSee(plugin: org.bukkit.plugin.Plugin?, entity: org.bukkit.entity.Player?) = false
            override fun getPreviouslyHashedClient() = null!!
            override fun sendPluginMessage(plugin: org.bukkit.plugin.Plugin?, channel: String, message: ByteArray?) {}
            override fun getListeningPluginChannels() = emptySet<String>()
            override fun hidePlayer(player: org.bukkit.entity.Player?) {}
            override fun hidePlayer(plugin: org.bukkit.plugin.Plugin?, player: org.bukkit.entity.Player?) {}
            override fun showPlayer(player: org.bukkit.entity.Player?) {}
            override fun showPlayer(plugin: org.bukkit.plugin.Plugin?, player: org.bukkit.entity.Player?) {}
            override fun canSee(player: org.bukkit.entity.Player?) = false
            override fun isOnGround() = false
            override fun isSneaking() = false
            override fun setSneaking(Boolean) {}
            override fun isSprinting() = false
            override fun setSprinting(Boolean) {}
            override fun saveData() {}
            override fun loadData() {}
            override fun setSleepingIgnored(Boolean) {}
            override fun isSleepingIgnored() = false
            override fun isDeeplySleeping() = false
            override fun getSleepingPlayer() = null!!
            override fun getPlayerProfile() = org.bukkit.profile.PlayerProfile(null, null)
            override fun getClientBrandName() = null!!
            override fun awardAdvancement(advancement: org.bukkit.advancement.Advancement?) {}
            override fun revokeAdvancement(advancement: org.bukkit.advancement.Advancement?) {}
            override fun getAdvancementProgress(advancement: org.bukkit.advancement.Advancement?) = null!!
            override fun getAdvancementProgress(silent: Boolean) = null!!
            override fun incrementStatistic(statistic: org.bukkit.Statistic) {}
            override fun incrementStatistic(statistic: org.bukkit.Statistic, amount: Int) {}
            override fun incrementStatistic(statistic: org.bukkit.Statistic, material: org.bukkit.Material) {}
            override fun incrementStatistic(statistic: org.bukkit.Statistic, material: org.bukkit.Material, amount: Int) {}
            override fun incrementStatistic(statistic: org.bukkit.Statistic, entityType: org.bukkit.entity.EntityType) {}
            override fun incrementStatistic(statistic: org.bukkit.Statistic, entityType: org.bukkit.entity.EntityType, amount: Int) {}
            override fun decrementStatistic(statistic: org.bukkit.Statistic) {}
            override fun decrementStatistic(statistic: org.bukkit.Statistic, amount: Int) {}
            override fun decrementStatistic(statistic: org.bukkit.Statistic, material: org.bukkit.Material) {}
            override fun decrementStatistic(statistic: org.bukkit.Statistic, entityType: org.bukkit.entity.EntityType) {}
            override fun decrementStatistic(statistic: org.bukkit.Statistic, entityType: org.bukkit.entity.EntityType, amount: Int) {}
            override fun getStatistic(statistic: org.bukkit.Statistic) = 0
            override fun getStatistic(statistic: org.bukkit.Statistic, material: org.bukkit.Material) = 0
            override fun getStatistic(statistic: org.bukkit.Statistic, entityType: org.bukkit.entity.EntityType) = 0
            override fun setStatistic(statistic: org.bukkit.Statistic, material: org.bukkit.Material, newValue: Int) {}
            override fun setStatistic(statistic: org.bukkit.Statistic, entityType: org.bukkit.entity.EntityType, newValue: Int) {}
            override fun setStatistic(statistic: org.bukkit.Statistic, newValue: Int) {}
            override fun hasPlayedBefore() = false
            override fun getFirstPlayed() = 0L
            override fun getLastPlayed() = 0L
            override fun getLastSeen() = 0L
            override fun hasSeenWinScreen() = false
            override fun setHasSeenWinScreen(Boolean) {}
            override fun isConversing() = false
            override fun acceptConversationInput(input: String) {}
            override fun beginConversation(conversation: org.bukkit.conversations.Conversation?) = false
            override fun abandonConversation(conversation: org.bukkit.conversations.Conversation?) {}
            override fun abandonConversation(conversation: org.bukkit.conversations.Conversation?, details: org.bukkit.conversations.ConversationAbandonedEvent?) {}
            override fun sendRawMessage(message: String) {}
            override fun sendRawMessage(identity: net.kyori.identity.Identity?, message: String) {}
            override fun kickPlayer(message: String?) {}
            override fun kick(message: Component?) {}
            override fun kick(message: Component?, cause: org.bukkit.event.player.PlayerKickEvent.Cause?) {}
            override fun chat(msg: String) {}
            override fun getDisplayName() = Component.text(botName)
            override fun displayName() = Component.text(botName)
            override fun setDisplayName(name: Component?) {}
            override fun displayName(name: Component?) {}
            override fun getPlayerListName() = Component.text(botName)
            override fun playerListName() = Component.text(botName)
            override fun setPlayerListName(name: Component?) {}
            override fun playerListName(name: Component?) {}
            override fun getPlayerListHeader() = null!!
            override fun playerListHeader() = null!!
            override fun getPlayerListFooter() = null!!
            override fun playerListFooter() = null!!
            override fun setPlayerListHeader(header: Component?) {}
            override fun setPlayerListFooter(footer: Component?) {}
            override fun setPlayerListHeaderFooter(header: Component?, footer: Component?) {}
            override fun sendPlayerListHeaderAndFooter(header: Component?, footer: Component?) {}
            override fun sendPlayerListHeader(header: Component?) {}
            override fun sendPlayerListFooter(footer: Component?) {}
            override fun getCompassTarget() = null!!
            override fun setCompassTarget(location: org.bukkit.Location?) {}
            override fun getAddress() = null!!
            override fun sendTitle(title: Component?, subtitle: Component?) {}
            override fun sendTitle(title: Component?, subtitle: Component?, fadeIn: Int, stay: Int, fadeOut: Int) {}
            override fun sendTitle(title: org.bukkit.entity.Player?, subtitle: org.bukkit.entity.Player?, fadeIn: Int, stay: Int, fadeOut: Int) {}
            override fun sendTitlePart(part: org.bukkit.entity.Player.TitlePart?, component: Component?) {}
            override fun sendTitlePart(part: org.bukkit.entity.Player.TitlePart?, component: Component?, fadeIn: Int, stay: Int, fadeOut: Int) {}
            override fun resetTitle() {}
            override fun spawnParticle(particle: org.bukkit.Particle?, location: org.bukkit.Location?, count: Int) {}
            override fun spawnParticle(particle: org.bukkit.Particle?, x: Double, y: Double, z: Double, count: Int) {}
            override fun <T : Any> spawnParticle(particle: org.bukkit.Particle?, location: org.bukkit.Location?, count: Int, data: T?) {}
            override fun <T : Any> spawnParticle(particle: org.bukkit.Particle?, x: Double, y: Double, z: Double, count: Int, data: T?) {}
            override fun spawnParticle(particle: org.bukkit.Particle?, location: org.bukkit.Location?, count: Int, offsetX: Double, offsetY: Double, offsetZ: Double) {}
            override fun spawnParticle(particle: org.bukkit.Particle?, x: Double, y: Double, z: Double, count: Int, offsetX: Double, offsetY: Double, offsetZ: Double) {}
            override fun <T : Any> spawnParticle(particle: org.bukkit.Particle?, location: org.bukkit.Location?, count: Int, offsetX: Double, offsetY: Double, offsetZ: Double, data: T?) {}
            override fun <T : Any> spawnParticle(particle: org.bukkit.Particle?, x: Double, y: Double, z: Double, count: Int, offsetX: Double, offsetY: Double, offsetZ: Double, data: T?) {}
            override fun spawnParticle(particle: org.bukkit.Particle?, location: org.bukkit.Location?, count: Int, offsetX: Double, offsetY: Double, offsetZ: Double, extra: Double) {}
            override fun spawnParticle(particle: org.bukkit.Particle?, x: Double, y: Double, z: Double, count: Int, offsetX: Double, offsetY: Double, offsetZ: Double, extra: Double) {}
            override fun <T : Any> spawnParticle(particle: org.bukkit.Particle?, location: org.bukkit.Location?, count: Int, offsetX: Double, offsetY: Double, offsetZ: Double, extra: Double, data: T?) {}
            override fun <T : Any> spawnParticle(particle: org.bukkit.Particle?, x: Double, y: Double, z: Double, count: Int, offsetX: Double, offsetY: Double, offsetZ: Double, extra: Double, data: T?) {}
            override fun getWorld() = null!!
            override fun getLocation() = null!!
            override fun getLocation(loc: org.bukkit.Location?) = null!!
            override fun getEyeLocation() = null!!
            override fun getVelocity() = org.bukkit.util.Vector()
            override fun setVelocity(velocity: org.bukkit.util.Vector?) {}
            override fun getHeight() = 0.0
            override fun getWidth() = 0.0
            override fun getBoundingBox() = null!!
            override fun isInWater() = false
            override fun getWorldBorder() = null!!
            override fun isInVisiblePlace() = false
            override fun setRotation(yaw: Float, pitch: Float) {}
            override fun teleport(location: org.bukkit.Location?) = false
            override fun teleport(location: org.bukkit.Location?, cause: org.bukkit.event.player.PlayerTeleportEvent.TeleportCause?) = false
            override fun teleport(destination: org.bukkit.entity.Entity?) = false
            override fun teleport(destination: org.bukkit.entity.Entity?, cause: org.bukkit.event.player.PlayerTeleportEvent.TeleportCause?) = false
            override fun teleportAsync(location: org.bukkit.Location?, cause: org.bukkit.event.player.PlayerTeleportEvent.TeleportCause?, teleportFlags: org.leavesmc.leaves.util.AsyncTeleport?) = false
            override fun getNearbyEntities(x: Double, y: Double, z: Double) = emptyList<org.bukkit.entity.Entity>()
            override fun getEntityId() = 0
            override fun getFireTicks() = 0
            override fun getMaxFireTicks() = 0
            override fun setFireTicks(ticks: Int) {}
            override fun setVisualFire(vis: Boolean) {}
            override fun isVisualFire() = false
            override fun getFreezeTicks() = 0
            override fun getMaxFreezeTicks() = 0
            override fun setFreezeTicks(ticks: Int) {}
            override fun isFrozen() = false
            override fun remove() {}
            override fun isDead() = false
            override fun isValid() = false
            override fun isPersistent() = false
            override fun setPersistent(Boolean) {}
            override fun getPassenger() = null!!
            override fun setPassenger(passenger: org.bukkit.entity.Entity?) = false
            override fun getPassengers() = emptyList<org.bukkit.entity.Entity>()
            override fun addPassenger(passenger: org.bukkit.entity.Entity?) = false
            override fun removePassenger(passenger: org.bukkit.entity.Entity?) = false
            override fun isEmpty() = false
            override fun eject() = false
            override fun leaveVehicle() = false
            override fun getVehicle() = null!!
            override fun getScoreboardTags() = emptySet<String>()
            override fun addScoreboardTag(tag: String) = false
            override fun removeScoreboardTag(tag: String) = false
            override fun getCustomChatCompletions() = emptySet<String>()
            override fun addCustomChatCompletion(key: String) {}
            override fun removeCustomChatCompletion(key: String) {}
            override fun getPistonMoveBehaviour() = org.bukkit.PistonMoveMaterial.MOVE
            override fun getEntitySpawnReason() = org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM
            override fun isOp() = false
            override fun setOp(value: Boolean) {}
            override fun getPlayer() = this
            override fun getType() = org.bukkit.entity.EntityType.PLAYER
            override fun getTicksLived() = 0
            override fun setTicksLived(value: Int) {}
            override fun getClosest(entity: org.bukkit.entity.Entity?) = null!!
            override fun isInLava() = false
            override fun getFallDistance() = 0f
            override fun setFallDistance(distance: Float) {}
            override fun setLastDamageCause(event: org.bukkit.event.entity.EntityDamageEvent?) {}
            override fun getLastDamageCause() = null!!
            override fun getNoDamageTicks() = 0
            override fun setNoDamageTicks(ticks: Int) {}
            override fun getNoActionTicks() = 0
            override fun setNoActionTicks(ticks: Int) {}
            override fun getMaximumNoDamageTicks() = 20
            override fun setMaximumNoDamageTicks(ticks: Int) {}
            override fun getMaximumLavaHurtTicks() = 20
            override fun setMaximumLavaHurtTicks(ticks: Int) {}
            override fun getRemainingAir() = 0
            override fun setRemainingAir(ticks: Int) {}
            override fun getMaximumAir() = 0
            override fun setMaximumAir(ticks: Int) {}
            override fun getArrowCooldown() = 0
            override fun setArrowCooldown(ticks: Int) {}
            override fun getArrowsInBody() = 0
            override fun setArrowsInBody(count: Int) {}
            override fun getBeeStingerCooldown() = 0
            override fun setBeeStingerCooldown(ticks: Int) {}
            override fun getBeeStingersInBody() = 0
            override fun setBeeStingersInBody(count: Int) {}
            override fun getMaximumArrowCooldown() = 0
            override fun setMaximumArrowCooldown(ticks: Int) {}
            override fun getLastHurtDirection() = org.bukkit.util.Vector()
            override fun setLastHurtDirection(direction: org.bukkit.util.Vector?) {}
            override fun getCurrentHurtDirection() = org.bukkit.util.Vector()
            override fun setCurrentHurtDirection(direction: org.bukkit.util.Vector?) {}
            override fun getHurtDirectionTimestamp() = 0L
            override fun setHurtDirectionTimestamp(timestamp: Long) {}
            override fun damage(amount: Double) {}
            override fun damage(amount: Double, source: org.bukkit.entity.Entity?) {}
            override fun getHealth() = 20.0
            override fun setHealth(health: Double) {}
            override fun getAbsorptionAmount() = 0.0
            override fun setAbsorptionAmount(amount: Double) {}
            override fun getMaxHealth() = 20.0
            override fun setMaxHealth(health: Double) {}
            override fun resetMaxHealth() {}
            override fun getHandRaised() = 0f
            override fun setHandRaised(raised: Float) {}
            override fun getHandRaisedTime() = 0
            override fun setHandRaisedTime(time: Int) {}
            override fun isHandRaised() = false
            override fun getPose() = org.bukkit.entity.Pose.STANDING
            override fun getLocationOffset() = org.bukkit.util.Vector()
            override fun getAttackable() = false
            override fun setAttackable(Boolean) {}
            override fun <T : org.bukkit.projectiles.Projectile> launchProjectile(projectile: Class<out T>?) = null!!
            override fun <T : org.bukkit.projectiles.Projectile> launchProjectile(projectile: Class<out T>?, velocity: org.bukkit.util.Vector?) = null!!
            override fun getMetadata(metadataKey: String?) = emptyList<org.bukkit.metadata.MetadataValue>()
            override fun hasMetadata(metadataKey: String?) = false
            override fun setMetadata(metadataKey: String?, newMetadataValue: org.bukkit.metadata.MetadataValue?) {}
            override fun removeMetadata(metadataKey: String?, owningPlugin: org.bukkit.plugin.Plugin?) {}
            override fun isPermissionSet(name: String?) = false
            override fun isPermissionSet(perm: org.bukkit.permissions.Permission?) = false
            override fun hasPermission(name: String?) = false
            override fun hasPermission(perm: org.bukkit.permissions.Permission?) = false
            override fun addAttachment(plugin: org.bukkit.plugin.Plugin?, name: String?, value: Boolean?) = null!!
            override fun addAttachment(plugin: org.bukkit.plugin.Plugin?) = null!!
            override fun addAttachment(plugin: org.bukkit.plugin.Plugin?, name: String?, value: Boolean?, ticks: Int) = null!!
            override fun addAttachment(plugin: org.bukkit.plugin.Plugin?, ticks: Int) = null!!
            override fun removeAttachment(attachment: org.bukkit.permissions.PermissionAttachment?) {}
            override fun recalculatePermissions() {}
            override fun getEffectivePermissions() = emptySet<org.bukkit.permissions.PermissionAttachmentInfo>()
            override fun isOnline() = false
            override fun isConnected() = false
            override fun getLastLogin() = 0L
            override fun getLastSeen() = 0L
            override fun getLastDeathLocation() = null!!
            override fun getLastDeathWorld() = null!!
            override fun getLastDeathPlayerKiller() = null!!
            override fun setLastDeathPlayerKiller(killer: org.bukkit.entity.Player?) {}
            override fun isInvisible() = false
            override fun isGlowing() = false
            override fun setGlowing(Boolean) {}
            override fun isGliding() = false
            override fun setGliding(Boolean) {}
            override fun isSwimming() = false
            override fun setSwimming(Boolean) {}
            override fun isRiptiding() = false
            override fun isClimbing() = false
            override fun getGameMode() = null!!
            override fun setGameMode(mode: org.bukkit.GameMode?) {}
            override fun isBlocking() = false
            override fun getActiveItem() = null!!
            override fun getItemInUse() = null!!
            override fun getActiveItemUsedTime() = 0
            override fun hasLineOfSight(other: org.bukkit.entity.Entity?) = false
            override fun isCharged() = false
            override fun hasCollided() = false
            override fun getCollidableExemptions() = emptySet<org.bukkit.entity.Entity>()
            override fun addCollidableExemption(entity: org.bukkit.entity.Entity?) {}
            override fun removeCollidableExemption(entity: org.bukkit.entity.Entity?) {}
            override fun getLastLeashHolder() = null!!
            override fun setLastLeashHolder(entity: org.bukkit.entity.Entity?) {}
            override fun getPitch() = 0f
            override fun getYaw() = 0f
            override fun getEyeHeight() = 0.0
            override fun getEyeHeight(ignorePose: Boolean) = 0.0
            override fun getTargetBlock(exact: Boolean) = null!!
            override fun getTargetBlock(maxDistance: Int, ignoreBlocks: org.bukkit.FluidCollisionMode?) = null!!
            override fun hasLineOfSight(other: org.bukkit.Location?) = false
            override fun getTargetBlockInfo(exact: Boolean, ignoredBlocks: Set<org.bukkit.block.data.BlockData>?, maxDistance: Int) = null!!
            override fun getLastTwoTargetBlocks(exact: Boolean) = emptyList<org.bukkit.block.Block>()
            override fun getLastTwoTargetBlocks(ignoredBlocks: Set<org.bukkit.block.data.BlockData>?, maxDistance: Int) = emptyList<org.bukkit.block.Block>()
            override fun getTargetEntity(maxDistance: Int) = null!!
            override fun getTargetEntity(maxDistance: Int, ignoreBlocks: Boolean) = null!!
            override fun getTarget(targetEntities: Int, maxDistance: Int, exact: Boolean) = null!!
            override fun getTargetEntities(maxDistance: Int, exact: Boolean, ignoreBlocks: Boolean) = emptyList<org.bukkit.entity.Entity>()
            override fun getLocale() = ""
            override fun getAffectsSpawning() = true
            override fun getTrackedBy() = null!!
            override fun setTrackedBy(player: org.bukkit.entity.Player?) {}
            override fun getViewDistance() = 0
            override fun getSimulationDistance() = 0
            override fun getNoPhysicsViewDistance() = 0
            override fun getSendViewDistance() = 0
            override fun getEntityInteractionRange() = 0.0
            override fun getBlockInteractionRange() = 0.0
            override fun setViewDistance(viewDistance: Int) {}
            override fun setSimulationDistance(simulationDistance: Int) {}
            override fun setNoPhysicsViewDistance(viewDistance: Int) {}
            override fun setSendViewDistance(viewDistance: Int) {}
            override fun setEntityInteractionRange(range: Double) {}
            override fun setBlockInteractionRange(range: Double) {}
            override fun getPortalCooldown() = 0
            override fun setPortalCooldown(cooldown: Int) {}
            override fun getAllowServerSideViewDistance() = true
            override fun getLastPortalDirection() = null!!
            override fun getBiome() = null!!
            override fun setBiome(biome: net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>?) {}
            override fun getEnchantmentSeed() = 0
            override fun setEnchantmentSeed(seed: Int) {}
            override fun isValidLocation(loc: org.bukkit.Location?) = false
            override fun getNoPhysicsBlocks() = emptySet<org.bukkit.block.data.BlockData>()
            override fun setNoPhysicsBlocks(blocks: Set<org.bukkit.block.data.BlockData>?) {}
            override fun getClientViewDistance() = 0
            override fun getClientSimulationDistance() = 0
            override fun getClientViewRadiusByte() = 0.toByte()
            override fun getClientSimulationRadiusByte() = 0.toByte()
            override fun setClientViewDistance(viewDistance: Int) {}
            override fun setClientSimulationDistance(simulationDistance: Int) {}
            override fun getPlayerTime() = 0L
            override fun getPlayerTimeOffset() = 0L
            override fun isPlayerTimeRelative() = false
            override fun resetPlayerTime() {}
            override fun setPlayerTime(time: Long, relative: Boolean) {}
            override fun getExpCooldown() = 0
            override fun setExpCooldown(ticks: Int) {}
            override fun getAbsorptionAmount() = 0.0
            override fun setAbsorptionAmount(amount: Double) {}
        },
        save
    )

    constructor(bot: Player, reason: RemoveReason, remover: CommandSender?, save: Boolean, resume: Boolean)
        : this(bot, save) {
        this.reason = reason
        this.remover = remover
        this.resume = resume
    }

    constructor(bot: Player, reason: RemoveReason, remover: CommandSender?, save: Boolean, resume: Boolean, async: Boolean)
        : this(bot, reason, remover, save, resume) {
        this.async = async
    }

    constructor(bot: Player, remover: CommandSender?, reason: RemoveReason, save: Boolean, resume: Boolean)
        : this(bot, reason, remover, save, resume)

    constructor(bot: Player, reason: RemoveReason, remover: CommandSender?, removeMessage: Component?, save: Boolean)
        : this(bot, save) {
        this.reason = reason
        this.remover = remover
        this.removeMessage = removeMessage
    }

    fun getReason() = reason
    fun shouldSave() = save
    fun shouldResume() = resume

    private var _cancelled = false
    override fun isCancelled() = _cancelled
    override fun setCancelled(cancel: Boolean) { _cancelled = cancel }

    companion object {
        @JvmStatic val HANDLERS = HandlerList()
        @JvmStatic fun getHandlerList() = HANDLERS
    }
    override fun getHandlers() = HANDLERS
}
