package net.minecraft.world.level.saveddata.maps;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.MapDecorations;
import net.minecraft.world.item.component.MapItemColor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class MapItemSavedData extends SavedData {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAP_SIZE = 128;
    private static final int HALF_MAP_SIZE = 64;
    public static final int MAX_SCALE = 4;
    public static final int TRACKED_DECORATION_LIMIT = 256;
    private static final String FRAME_PREFIX = "frame-";
    public static final Codec<MapItemSavedData> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                createUUIDBackedDimensionKeyCodec().forGetter(MapItemSavedData::packUUIDBackedDimension), // Paper - store target world by uuid in addition to dimension
                Codec.INT.fieldOf("xCenter").forGetter(mapItemSavedData -> mapItemSavedData.centerX),
                Codec.INT.fieldOf("zCenter").forGetter(mapItemSavedData -> mapItemSavedData.centerZ),
                Codec.BYTE.optionalFieldOf("scale", (byte)0).forGetter(mapItemSavedData -> mapItemSavedData.scale),
                Codec.BYTE_BUFFER.fieldOf("colors").forGetter(mapItemSavedData -> ByteBuffer.wrap(mapItemSavedData.colors)),
                Codec.BOOL.optionalFieldOf("trackingPosition", true).forGetter(mapItemSavedData -> mapItemSavedData.trackingPosition),
                Codec.BOOL.optionalFieldOf("unlimitedTracking", false).forGetter(mapItemSavedData -> mapItemSavedData.unlimitedTracking),
                Codec.BOOL.optionalFieldOf("locked", false).forGetter(mapItemSavedData -> mapItemSavedData.locked),
                MapBanner.CODEC
                    .listOf()
                    .optionalFieldOf("banners", List.of())
                    .forGetter(mapItemSavedData -> List.copyOf(mapItemSavedData.bannerMarkers.values())),
                MapFrame.CODEC.listOf().optionalFieldOf("frames", List.of()).forGetter(mapItemSavedData -> List.copyOf(mapItemSavedData.frameMarkers.values()))
            )
            .apply(instance, MapItemSavedData::new)
    );
    public int centerX;
    public int centerZ;
    public ResourceKey<Level> dimension;
    public boolean trackingPosition;
    public boolean unlimitedTracking;
    public byte scale;
    public byte[] colors = new byte[16384];
    public boolean locked;
    private final org.bukkit.craftbukkit.map.RenderData vanillaRender = new org.bukkit.craftbukkit.map.RenderData(); // Paper - Use Vanilla map renderer when possible
    public final List<MapItemSavedData.HoldingPlayer> carriedBy = Lists.newArrayList();
    public final Map<Player, MapItemSavedData.HoldingPlayer> carriedByPlayers = Maps.newHashMap();
    private final Map<String, MapBanner> bannerMarkers = Maps.newHashMap();
    public final Map<String, MapDecoration> decorations = Maps.newLinkedHashMap();
    private final Map<String, MapFrame> frameMarkers = Maps.newHashMap();
    private int trackedDecorationCount;

    // CraftBukkit start
    public final org.bukkit.craftbukkit.map.CraftMapView mapView;
    private final org.bukkit.craftbukkit.CraftServer server;
    public java.util.UUID uniqueId;
    public MapId id;
    // CraftBukkit end

    public static SavedDataType<MapItemSavedData> type(MapId mapId) {
        return new SavedDataType<>(mapId.key(), () -> {
            throw new IllegalStateException("Should never create an empty map saved data");
        }, CODEC, DataFixTypes.SAVED_DATA_MAP_DATA);
    }

    private MapItemSavedData(
        int centerX, int centerZ, byte scale, boolean trackingPosition, boolean unlimitedTracking, boolean locked, ResourceKey<Level> dimension
    ) {
        this.scale = scale;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.dimension = dimension;
        this.trackingPosition = trackingPosition;
        this.unlimitedTracking = unlimitedTracking;
        this.locked = locked;
        // CraftBukkit start
        this.mapView = new org.bukkit.craftbukkit.map.CraftMapView(this);
        this.server = (org.bukkit.craftbukkit.CraftServer) org.bukkit.Bukkit.getServer();
        this.vanillaRender.buffer = colors; // Paper - Use Vanilla map renderer when possible
        // CraftBukkit end
    }

    // Paper start - store target world by uuid in addition to dimension
    private MapItemSavedData(
        UUIDBackedDimension dimension,
        int x,
        int z,
        byte scale,
        ByteBuffer colors,
        boolean trackingPosition,
        boolean unlimitedTracking,
        boolean locked,
        List<MapBanner> banners,
        List<MapFrame> frames
    ) {
        this(dimension.resolveOrThrow(), x, z, scale, colors, trackingPosition, unlimitedTracking, locked, banners, frames);
    }
    // Paper end - store target world by uuid in addition to dimension

    private MapItemSavedData(
        ResourceKey<Level> dimension,
        int centerX,
        int centerZ,
        byte scale,
        ByteBuffer colors,
        boolean trackingPosition,
        boolean unlimitedTracking,
        boolean locked,
        List<MapBanner> banners,
        List<MapFrame> frames
    ) {
        this(centerX, centerZ, (byte)Mth.clamp(scale, 0, 4), trackingPosition, unlimitedTracking, locked, dimension);
        if (colors.array().length == 16384) {
            this.colors = colors.array();
        }

        for (MapBanner mapBanner : banners) {
            this.bannerMarkers.put(mapBanner.getId(), mapBanner);
            this.addDecoration(
                mapBanner.getDecoration(), null, mapBanner.getId(), mapBanner.pos().getX(), mapBanner.pos().getZ(), 180.0, mapBanner.name().orElse(null)
            );
        }

        for (MapFrame mapFrame : frames) {
            this.frameMarkers.put(mapFrame.getId(), mapFrame);
            this.addDecoration(
                MapDecorationTypes.FRAME, null, getFrameKey(mapFrame.entityId()), mapFrame.pos().getX(), mapFrame.pos().getZ(), mapFrame.rotation(), null
            );
        }

        this.vanillaRender.buffer = colors.array(); // Paper - Use Vanilla map renderer when possible
    }

    public static MapItemSavedData createFresh(
        double x, double z, byte scale, boolean trackingPosition, boolean unlimitedTracking, ResourceKey<Level> dimension
    ) {
        int i = 128 * (1 << scale);
        int floor = Mth.floor((x + 64.0) / i);
        int floor1 = Mth.floor((z + 64.0) / i);
        int i1 = floor * i + i / 2 - 64;
        int i2 = floor1 * i + i / 2 - 64;
        return new MapItemSavedData(i1, i2, scale, trackingPosition, unlimitedTracking, false, dimension);
    }

    public static MapItemSavedData createForClient(byte scale, boolean locked, ResourceKey<Level> dimension) {
        return new MapItemSavedData(0, 0, scale, false, false, locked, dimension);
    }

    public synchronized MapItemSavedData locked() { // Folia - make map data thread-safe
        MapItemSavedData mapItemSavedData = new MapItemSavedData(
            this.centerX, this.centerZ, this.scale, this.trackingPosition, this.unlimitedTracking, true, this.dimension
        );
        mapItemSavedData.bannerMarkers.putAll(this.bannerMarkers);
        mapItemSavedData.decorations.putAll(this.decorations);
        mapItemSavedData.trackedDecorationCount = this.trackedDecorationCount;
        System.arraycopy(this.colors, 0, mapItemSavedData.colors, 0, this.colors.length);
        return mapItemSavedData;
    }

    public synchronized MapItemSavedData scaled() { // Folia - make map data thread-safe
        return createFresh(this.centerX, this.centerZ, (byte)Mth.clamp(this.scale + 1, 0, 4), this.trackingPosition, this.unlimitedTracking, this.dimension);
    }

    private static Predicate<ItemStack> mapMatcher(ItemStack stack) {
        MapId mapId = stack.get(DataComponents.MAP_ID);
        return itemStack -> itemStack == stack || itemStack.is(stack.getItem()) && Objects.equals(mapId, itemStack.get(DataComponents.MAP_ID));
    }

    public synchronized void tickCarriedBy(Player player, ItemStack mapStack) { // Folia - make map data thread-safe
        ca.spottedleaf.moonrise.common.util.TickThread.ensureTickThread(player, "Ticking map player in incorrect region"); // Folia - region threading
        if (!this.carriedByPlayers.containsKey(player)) {
            MapItemSavedData.HoldingPlayer holdingPlayer = new MapItemSavedData.HoldingPlayer(player);
            this.carriedByPlayers.put(player, holdingPlayer);
            this.carriedBy.add(holdingPlayer);
        }

        Predicate<ItemStack> predicate = mapMatcher(mapStack);
        if (!player.getInventory().contains(predicate)) {
            this.removeDecoration(player.getPlainTextName());
        }

        for (int i = 0; i < this.carriedBy.size(); i++) {
            MapItemSavedData.HoldingPlayer holdingPlayer1 = this.carriedBy.get(i);
            Player player1 = holdingPlayer1.player;
            String plainTextName = player1.getPlainTextName();
            if (!player1.isRemoved() && (player1.getInventory().contains(predicate) || mapStack.isFramed())) {
                if (!mapStack.isFramed() && player1.level().dimension() == this.dimension && this.trackingPosition) {
                    this.addDecoration(MapDecorationTypes.PLAYER, player1.level(), plainTextName, player1.getX(), player1.getZ(), player1.getYRot(), null);
                }
            } else {
                this.carriedByPlayers.remove(player1);
                this.carriedBy.remove(holdingPlayer1);
                this.removeDecoration(plainTextName);
            }

            if (!player1.equals(player) && hasMapInvisibilityItemEquipped(player1)) {
                this.removeDecoration(plainTextName);
            }
        }

        if (mapStack.isFramed() && this.trackingPosition) {
            ItemFrame frame = mapStack.getFrame();
            BlockPos pos = frame.getPos();
            MapFrame mapFrame = this.frameMarkers.get(MapFrame.frameId(pos));
            if (mapFrame != null && frame.getId() != mapFrame.entityId() && this.frameMarkers.containsKey(mapFrame.getId())) {
                this.removeDecoration(getFrameKey(mapFrame.entityId()));
            }

            MapFrame mapFrame1 = new MapFrame(pos, frame.getDirection().get2DDataValue() * 90, frame.getId());
            if (this.decorations.size() < player.level().paperConfig().maps.itemFrameCursorLimit) { // Paper - Limit item frame cursors on maps
            this.addDecoration(
                MapDecorationTypes.FRAME, player.level(), getFrameKey(frame.getId()), pos.getX(), pos.getZ(), frame.getDirection().get2DDataValue() * 90, null
            );
            MapFrame mapFrame2 = this.frameMarkers.put(mapFrame1.getId(), mapFrame1);
            if (!mapFrame1.equals(mapFrame2)) {
                this.setDirty();
            }
            } // Paper - Limit item frame cursors on maps
        }

        MapDecorations mapDecorations = mapStack.getOrDefault(DataComponents.MAP_DECORATIONS, MapDecorations.EMPTY);
        if (!this.decorations.keySet().containsAll(mapDecorations.decorations().keySet())) {
            mapDecorations.decorations().forEach((string, entry) -> {
                if (!this.decorations.containsKey(string)) {
                    this.addDecoration(entry.type(), player.level(), string, entry.x(), entry.z(), entry.rotation(), null);
                }
            });
        }
    }

    private static boolean hasMapInvisibilityItemEquipped(Player player) {
        for (EquipmentSlot equipmentSlot : EquipmentSlot.values()) {
            if (equipmentSlot != EquipmentSlot.MAINHAND
                && equipmentSlot != EquipmentSlot.OFFHAND
                && player.getItemBySlot(equipmentSlot).is(ItemTags.MAP_INVISIBILITY_EQUIPMENT)) {
                return true;
            }
        }

        return false;
    }

    private void removeDecoration(String identifier) {
        MapDecoration mapDecoration = this.decorations.remove(identifier);
        if (mapDecoration != null && mapDecoration.type().value().trackCount()) {
            this.trackedDecorationCount--;
        }

        if (mapDecoration != null) this.setDecorationsDirty(); // Paper - only mark dirty if a change occurs
    }

    public static void addTargetDecoration(ItemStack stack, BlockPos pos, String type, Holder<MapDecorationType> mapDecorationType) {
        MapDecorations.Entry entry = new MapDecorations.Entry(mapDecorationType, pos.getX(), pos.getZ(), 180.0F);
        stack.update(DataComponents.MAP_DECORATIONS, MapDecorations.EMPTY, mapDecorations -> mapDecorations.withDecoration(type, entry));
        if (mapDecorationType.value().hasMapColor()) {
            stack.set(DataComponents.MAP_COLOR, new MapItemColor(mapDecorationType.value().mapColor()));
        }
    }

    private void addDecoration(
        Holder<MapDecorationType> decorationType, @Nullable LevelAccessor level, String id, double x, double z, double yRot, @Nullable Component displayName
    ) {
        int i = 1 << this.scale;
        float f = (float)(x - this.centerX) / i;
        float f1 = (float)(z - this.centerZ) / i;
        MapItemSavedData.MapDecorationLocation mapDecorationLocation = this.calculateDecorationLocationAndType(decorationType, level, yRot, f, f1);
        if (mapDecorationLocation == null) {
            this.removeDecoration(id);
        } else {
            MapDecoration mapDecoration = new MapDecoration(
                mapDecorationLocation.type(),
                mapDecorationLocation.x(),
                mapDecorationLocation.y(),
                mapDecorationLocation.rot(),
                Optional.ofNullable(displayName)
            );
            MapDecoration mapDecoration1 = this.decorations.put(id, mapDecoration);
            if (!mapDecoration.equals(mapDecoration1)) {
                if (mapDecoration1 != null && mapDecoration1.type().value().trackCount()) {
                    this.trackedDecorationCount--;
                }

                if (mapDecorationLocation.type().value().trackCount()) {
                    this.trackedDecorationCount++;
                }

                this.setDecorationsDirty();
            }
        }
    }

    private MapItemSavedData.@Nullable MapDecorationLocation calculateDecorationLocationAndType(
        Holder<MapDecorationType> decorationType, @Nullable LevelAccessor level, double yRot, float x, float z
    ) {
        byte b = clampMapCoordinate(x);
        byte b1 = clampMapCoordinate(z);
        if (decorationType.is(MapDecorationTypes.PLAYER)) {
            Pair<Holder<MapDecorationType>, Byte> pair = this.playerDecorationTypeAndRotation(decorationType, level, yRot, x, z);
            return pair == null ? null : new MapItemSavedData.MapDecorationLocation(pair.getFirst(), b, b1, pair.getSecond());
        } else {
            return !isInsideMap(x, z) && !this.unlimitedTracking
                ? null
                : new MapItemSavedData.MapDecorationLocation(decorationType, b, b1, this.calculateRotation(level, yRot));
        }
    }

    private @Nullable Pair<Holder<MapDecorationType>, Byte> playerDecorationTypeAndRotation(
        Holder<MapDecorationType> decorationType, @Nullable LevelAccessor level, double yRot, float x, float z
    ) {
        if (isInsideMap(x, z)) {
            return Pair.of(decorationType, this.calculateRotation(level, yRot));
        } else {
            Holder<MapDecorationType> holder = this.decorationTypeForPlayerOutsideMap(x, z);
            return holder == null ? null : Pair.of(holder, (byte)0);
        }
    }

    private byte calculateRotation(@Nullable LevelAccessor level, double yRot) {
        if (this.dimension == Level.NETHER && level != null) {
            int i = (int)(level.getGameTime() / 10L);
            return (byte)(i * i * 34187121 + i * 121 >> 15 & 15);
        } else {
            double d = yRot < 0.0 ? yRot - 8.0 : yRot + 8.0;
            return (byte)(d * 16.0 / 360.0);
        }
    }

    private static boolean isInsideMap(float x, float z) {
        int i = 63;
        return x >= -63.0F && z >= -63.0F && x <= 63.0F && z <= 63.0F;
    }

    private @Nullable Holder<MapDecorationType> decorationTypeForPlayerOutsideMap(float x, float z) {
        int i = 320;
        boolean flag = Math.abs(x) < 320.0F && Math.abs(z) < 320.0F;
        if (flag) {
            return MapDecorationTypes.PLAYER_OFF_MAP;
        } else {
            return this.unlimitedTracking ? MapDecorationTypes.PLAYER_OFF_LIMITS : null;
        }
    }

    private static byte clampMapCoordinate(float coord) {
        int i = 63;
        if (coord <= -63.0F) {
            return -128;
        } else {
            return coord >= 63.0F ? 127 : (byte)(coord * 2.0F + 0.5);
        }
    }

    public synchronized @Nullable Packet<?> getUpdatePacket(MapId mapId, Player player) { // Folia - make map data thread-safe
        MapItemSavedData.HoldingPlayer holdingPlayer = this.carriedByPlayers.get(player);
        return holdingPlayer == null ? null : holdingPlayer.nextUpdatePacket(mapId);
    }

    public void setColorsDirty(int x, int z) {
    // Paper start - Fix unnecessary map data saves
        this.setColorsDirty(x, z, true);
    }
    public synchronized void setColorsDirty(int x, int z, boolean markFileDirty) { // Folia - make map data thread-safe
        //if (markFileDirty) this.setDirty(); // Folia - make map data thread-safe - move down
    // Paper end - Fix unnecessary map data saves

        for (MapItemSavedData.HoldingPlayer holdingPlayer : this.carriedBy) {
            holdingPlayer.markColorsDirty(x, z);
        }
        if (markFileDirty) this.setDirty(); // Folia - make map data thread-safe - moved down
    }

    public synchronized void setDecorationsDirty() { // Folia - make map data thread-safe
        this.carriedBy.forEach(MapItemSavedData.HoldingPlayer::markDecorationsDirty);
    }

    public synchronized MapItemSavedData.HoldingPlayer getHoldingPlayer(Player player) { // Folia - make map data thread-safe
        MapItemSavedData.HoldingPlayer holdingPlayer = this.carriedByPlayers.get(player);
        if (holdingPlayer == null) {
            holdingPlayer = new MapItemSavedData.HoldingPlayer(player);
            this.carriedByPlayers.put(player, holdingPlayer);
            this.carriedBy.add(holdingPlayer);
        }

        return holdingPlayer;
    }

    public synchronized boolean toggleBanner(LevelAccessor level, BlockPos pos) { // Folia - make map data thread-safe
        double d = pos.getX() + 0.5;
        double d1 = pos.getZ() + 0.5;
        int i = 1 << this.scale;
        double d2 = (d - this.centerX) / i;
        double d3 = (d1 - this.centerZ) / i;
        int i1 = 63;
        if (d2 >= -63.0 && d3 >= -63.0 && d2 <= 63.0 && d3 <= 63.0) {
            MapBanner mapBanner = level.getChunkIfLoadedImmediately(pos.getX() >> 4, pos.getZ() >> 4) == null || !ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(level.getMinecraftWorld(), pos) ? null : MapBanner.fromWorld(level, pos); // Folia - make map data thread-safe - don't sync load or read data we do not own
            if (mapBanner == null) {
                return false;
            }

            if (this.bannerMarkers.remove(mapBanner.getId(), mapBanner)) {
                this.removeDecoration(mapBanner.getId());
                this.setDirty();
                return true;
            }

            if (!this.isTrackedCountOverLimit(((Level) level).paperConfig().maps.itemFrameCursorLimit)) { // Paper - Limit item frame cursors on maps
                this.bannerMarkers.put(mapBanner.getId(), mapBanner);
                this.addDecoration(mapBanner.getDecoration(), level, mapBanner.getId(), d, d1, 180.0, mapBanner.name().orElse(null));
                this.setDirty();
                return true;
            }
        }

        return false;
    }

    public synchronized void checkBanners(BlockGetter level, int x, int z) { // Folia - make map data thread-safe
        Iterator<MapBanner> iterator = this.bannerMarkers.values().iterator();

        while (iterator.hasNext()) {
            MapBanner mapBanner = iterator.next();
            if (mapBanner.pos().getX() == x && mapBanner.pos().getZ() == z) {
                MapBanner mapBanner1 = MapBanner.fromWorld(level, mapBanner.pos());
                if (!mapBanner.equals(mapBanner1)) {
                    iterator.remove();
                    this.removeDecoration(mapBanner.getId());
                    this.setDirty();
                }
            }
        }
    }

    public Collection<MapBanner> getBanners() {
        return this.bannerMarkers.values();
    }

    public synchronized void removedFromFrame(BlockPos pos, int entityId) { // Folia - make map data thread-safe
        this.removeDecoration(getFrameKey(entityId));
        this.frameMarkers.remove(MapFrame.frameId(pos));
        this.setDirty();
    }

    public synchronized boolean updateColor(int x, int z, byte color) { // Folia - make map data thread-safe
        byte b = this.colors[x + z * 128];
        if (b != color) {
            this.setColor(x, z, color);
            return true;
        } else {
            return false;
        }
    }

    public synchronized void setColor(int x, int z, byte color) { // Folia - make map data thread-safe
        this.colors[x + z * 128] = color;
        this.setColorsDirty(x, z);
    }

    public synchronized boolean isExplorationMap() { // Folia - make map data thread-safe
        for (MapDecoration mapDecoration : this.decorations.values()) {
            if (mapDecoration.type().value().explorationMapElement()) {
                return true;
            }
        }

        return false;
    }

    public synchronized void addClientSideDecorations(List<MapDecoration> decorations) { // Folia - make map data thread-safe
        this.decorations.clear();
        this.trackedDecorationCount = 0;

        for (int i = 0; i < decorations.size(); i++) {
            MapDecoration mapDecoration = decorations.get(i);
            this.decorations.put("icon-" + i, mapDecoration);
            if (mapDecoration.type().value().trackCount()) {
                this.trackedDecorationCount++;
            }
        }
    }

    public Iterable<MapDecoration> getDecorations() {
        return this.decorations.values();
    }

    public synchronized boolean isTrackedCountOverLimit(int trackedCount) { // Folia - make map data thread-safe
        return this.trackedDecorationCount >= trackedCount;
    }

    private static String getFrameKey(int entityId) {
        return "frame-" + entityId;
    }

    public class HoldingPlayer {
        public final Player player;
        private boolean dirtyData = true;
        private int minDirtyX;
        private int minDirtyY;
        private int maxDirtyX = 127;
        private int maxDirtyY = 127;
        private boolean dirtyDecorations = true;
        private int tick;
        public int step;

        HoldingPlayer(final Player player) {
            this.player = player;
        }

        private MapItemSavedData.MapPatch createPatch(byte[] buffer) { // CraftBukkit
            int i = this.minDirtyX;
            int i1 = this.minDirtyY;
            int i2 = this.maxDirtyX + 1 - this.minDirtyX;
            int i3 = this.maxDirtyY + 1 - this.minDirtyY;
            byte[] bytes = new byte[i2 * i3];

            for (int i4 = 0; i4 < i2; i4++) {
                for (int i5 = 0; i5 < i3; i5++) {
                    bytes[i4 + i5 * i2] = buffer[i + i4 + (i1 + i5) * 128]; // CraftBukkit
                }
            }

            return new MapItemSavedData.MapPatch(i, i1, i2, i3, bytes);
        }

        @Nullable Packet<?> nextUpdatePacket(MapId mapId) {
            MapItemSavedData.MapPatch mapPatch;
            // Paper start
            if (!this.dirtyData && this.tick % 5 != 0) {
                // this won't end up sending, so don't render it!
                this.tick++;
                return null;
            }

            final boolean vanillaMaps = this.shouldUseVanillaMap();
            // Use Vanilla map renderer when possible - much simpler/faster than the CB rendering
            org.bukkit.craftbukkit.map.RenderData render = !vanillaMaps ? MapItemSavedData.this.mapView.render((org.bukkit.craftbukkit.entity.CraftPlayer) this.player.getBukkitEntity()) : MapItemSavedData.this.vanillaRender;
            // Paper end
            if (this.dirtyData) {
                this.dirtyData = false;
                mapPatch = this.createPatch(render.buffer); // CraftBukkit
            } else {
                mapPatch = null;
            }

            Collection<MapDecoration> collection;
            if ((!vanillaMaps || this.dirtyDecorations) && this.tick++ % 5 == 0) { // Paper - bypass dirtyDecorations for custom maps
                this.dirtyDecorations = false;
                // CraftBukkit start
                Collection<MapDecoration> icons = new java.util.ArrayList<>();
                if (vanillaMaps) this.addSeenPlayers(icons); // Paper

                for (org.bukkit.map.MapCursor cursor : render.cursors) {
                    if (cursor.isVisible()) {
                        icons.add(new MapDecoration(org.bukkit.craftbukkit.map.CraftMapCursor.CraftType.bukkitToMinecraftHolder(cursor.getType()), cursor.getX(), cursor.getY(), cursor.getDirection(), Optional.ofNullable(io.papermc.paper.adventure.PaperAdventure.asVanilla(cursor.caption()))));
                    }
                }
                collection = icons;
                // CraftBukkit end
            } else {
                collection = null;
            }

            return collection == null && mapPatch == null
                ? null
                : new ClientboundMapItemDataPacket(mapId, MapItemSavedData.this.scale, MapItemSavedData.this.locked, collection, mapPatch);
        }

        void markColorsDirty(int x, int z) {
            if (this.dirtyData) {
                this.minDirtyX = Math.min(this.minDirtyX, x);
                this.minDirtyY = Math.min(this.minDirtyY, z);
                this.maxDirtyX = Math.max(this.maxDirtyX, x);
                this.maxDirtyY = Math.max(this.maxDirtyY, z);
            } else {
                this.dirtyData = true;
                this.minDirtyX = x;
                this.minDirtyY = z;
                this.maxDirtyX = x;
                this.maxDirtyY = z;
            }
        }

        private void markDecorationsDirty() {
            this.dirtyDecorations = true;
        }

        // Paper start
        private void addSeenPlayers(java.util.Collection<MapDecoration> icons) {
            org.bukkit.entity.Player player = (org.bukkit.entity.Player) this.player.getBukkitEntity();
            MapItemSavedData.this.decorations.forEach((name, mapIcon) -> {
                // If this cursor is for a player check visibility with vanish system
                org.bukkit.entity.Player other = org.bukkit.Bukkit.getPlayerExact(name); // Spigot
                if (other == null || player.canSee(other)) {
                    icons.add(mapIcon);
                }
            });
        }

        private boolean shouldUseVanillaMap() {
            return mapView.getRenderers().size() == 1 && mapView.getRenderers().getFirst().getClass() == org.bukkit.craftbukkit.map.CraftMapRenderer.class;
        }
        // Paper end
    }

    record MapDecorationLocation(Holder<MapDecorationType> type, byte x, byte y, byte rot) {
    }

    public record MapPatch(int startX, int startY, int width, int height, byte[] mapColors) {
        public static final StreamCodec<ByteBuf, Optional<MapItemSavedData.MapPatch>> STREAM_CODEC = StreamCodec.of(
            MapItemSavedData.MapPatch::write, MapItemSavedData.MapPatch::read
        );

        private static void write(ByteBuf buffer, Optional<MapItemSavedData.MapPatch> mapPatch) {
            if (mapPatch.isPresent()) {
                MapItemSavedData.MapPatch mapPatch1 = mapPatch.get();
                buffer.writeByte(mapPatch1.width);
                buffer.writeByte(mapPatch1.height);
                buffer.writeByte(mapPatch1.startX);
                buffer.writeByte(mapPatch1.startY);
                FriendlyByteBuf.writeByteArray(buffer, mapPatch1.mapColors);
            } else {
                buffer.writeByte(0);
            }
        }

        private static Optional<MapItemSavedData.MapPatch> read(ByteBuf buffer) {
            int unsignedByte = buffer.readUnsignedByte();
            if (unsignedByte > 0) {
                int unsignedByte1 = buffer.readUnsignedByte();
                int unsignedByte2 = buffer.readUnsignedByte();
                int unsignedByte3 = buffer.readUnsignedByte();
                byte[] byteArray = FriendlyByteBuf.readByteArray(buffer);
                return Optional.of(new MapItemSavedData.MapPatch(unsignedByte2, unsignedByte3, unsignedByte, unsignedByte1, byteArray));
            } else {
                return Optional.empty();
            }
        }

        public void applyToMap(MapItemSavedData savedData) {
            synchronized (savedData) { // Folia - make map data thread-safe
            for (int i = 0; i < this.width; i++) {
                for (int i1 = 0; i1 < this.height; i1++) {
                    savedData.setColor(this.startX + i, this.startY + i1, this.mapColors[i + i1 * this.width]);
                }
            }
            } // Folia - make map data thread-safe
        }
    }

    // Paper start - store target world by uuid in addition to dimension
    record UUIDAndError(java.util.UUID uuid, String faultyDimension) {

    }
    record UUIDBackedDimension(@Nullable ResourceKey<Level> resourceKey, @Nullable UUIDAndError uuid) {
        public UUIDBackedDimension(final @org.jetbrains.annotations.NotNull ResourceKey<Level> resourceKey) {
            this(resourceKey, null);
        }
        public UUIDBackedDimension {
            com.google.common.base.Preconditions.checkArgument(resourceKey != null || uuid != null, "Created uuid backed dimension with null level and uuid. This is a bug");
        }

        public @org.jetbrains.annotations.NotNull ResourceKey<Level> resolveOrThrow() {
            if (resourceKey != null) return resourceKey;

            final org.bukkit.World worldByUUID = org.bukkit.Bukkit.getWorld(uuid.uuid());
            if (worldByUUID != null) return ((org.bukkit.craftbukkit.CraftWorld) worldByUUID).getHandle().dimension();

            throw new IllegalArgumentException("Invalid dimension " + uuid.faultyDimension() + " and unknown world uuid " + uuid.uuid);
        }
    }

    private UUIDBackedDimension packUUIDBackedDimension() {
        final net.minecraft.server.level.ServerLevel mappedLevel = net.minecraft.server.MinecraftServer.getServer().getLevel(this.dimension);
        return new UUIDBackedDimension(this.dimension, mappedLevel == null ? null : new UUIDAndError(mappedLevel.uuid, ""));
    }

    private static com.mojang.serialization.MapCodec<UUIDBackedDimension> createUUIDBackedDimensionKeyCodec() {
        return new com.mojang.serialization.MapCodec<>() {
            @Override
            public <T> java.util.stream.Stream<T> keys(final com.mojang.serialization.DynamicOps<T> ops) {
                return java.util.stream.Stream.of("dimension", "UUIDLeast", "UUIDMost").map(ops::createString);
            }

            @Override
            public <T> com.mojang.serialization.DataResult<UUIDBackedDimension> decode(final com.mojang.serialization.DynamicOps<T> ops,
                                                                                       final com.mojang.serialization.MapLike<T> input) {
                final com.mojang.serialization.DataResult<UUIDBackedDimension> foundDimension = Level.RESOURCE_KEY_CODEC.decode(ops, input.get("dimension"))
                    .map(Pair::getFirst)
                    .map(UUIDBackedDimension::new); // Do not pack uuid when reading as the level itself might reference an unloaded world. UUID lookup would be faulty + should be re-generated when written.
                if (foundDimension.isSuccess()) return foundDimension;

                // Fallback attempt at parsing the uuid
                final com.mojang.serialization.DataResult<UUIDBackedDimension> fromUUID = Codec.LONG.decode(ops, input.get("UUIDMost")).map(Pair::getFirst).apply2(
                    java.util.UUID::new,
                    Codec.LONG.decode(ops, input.get("UUIDLeast")).map(Pair::getFirst)
                ).map(uuid -> new UUIDBackedDimension(null, new UUIDAndError(uuid, String.valueOf(input.get("dimension")))));
                if (fromUUID.isSuccess()) return fromUUID;

                return foundDimension; // Return the found dimension instead, it's error is more "accurate" over the bukkit added uuids.
            }

            @Override
            public <T> com.mojang.serialization.RecordBuilder<T> encode(final UUIDBackedDimension input,
                                                                        final com.mojang.serialization.DynamicOps<T> ops,
                                                                        final com.mojang.serialization.RecordBuilder<T> prefix) {
                prefix.add("dimension", input.resourceKey(), Level.RESOURCE_KEY_CODEC);
                if (input.uuid != null) {
                    prefix.add("UUIDMost", input.uuid.uuid().getMostSignificantBits(), Codec.LONG);
                    prefix.add("UUIDLeast", input.uuid.uuid().getLeastSignificantBits(), Codec.LONG);
                }
                return prefix;
            }
        };
    }
    // Paper end - store target world by uuid in addition to dimension
}
