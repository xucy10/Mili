package net.minecraft.world.level.block.entity;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.IdentifierException;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringUtil;
import net.minecraft.util.Util;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StructureBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.StructureMode;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockRotProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class StructureBlockEntity extends BlockEntity implements BoundingBoxRenderable {
    private static final int SCAN_CORNER_BLOCKS_RANGE = 5;
    public static final int MAX_OFFSET_PER_AXIS = 48;
    public static final int MAX_SIZE_PER_AXIS = 48;
    public static final String AUTHOR_TAG = "author";
    private static final String DEFAULT_AUTHOR = "";
    private static final String DEFAULT_METADATA = "";
    private static final BlockPos DEFAULT_POS = new BlockPos(0, 1, 0);
    private static final Vec3i DEFAULT_SIZE = Vec3i.ZERO;
    private static final Rotation DEFAULT_ROTATION = Rotation.NONE;
    private static final Mirror DEFAULT_MIRROR = Mirror.NONE;
    private static final boolean DEFAULT_IGNORE_ENTITIES = true;
    private static final boolean DEFAULT_STRICT = false;
    private static final boolean DEFAULT_POWERED = false;
    private static final boolean DEFAULT_SHOW_AIR = false;
    private static final boolean DEFAULT_SHOW_BOUNDING_BOX = true;
    private static final float DEFAULT_INTEGRITY = 1.0F;
    private static final long DEFAULT_SEED = 0L;
    private @Nullable Identifier structureName;
    public String author = "";
    public String metaData = "";
    public BlockPos structurePos = DEFAULT_POS;
    public Vec3i structureSize = DEFAULT_SIZE;
    public Mirror mirror = Mirror.NONE;
    public Rotation rotation = Rotation.NONE;
    public StructureMode mode;
    public boolean ignoreEntities = true;
    private boolean strict = false;
    private boolean powered = false;
    public boolean showAir = false;
    public boolean showBoundingBox = true;
    public float integrity = 1.0F;
    public long seed = 0L;

    public StructureBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityType.STRUCTURE_BLOCK, pos, blockState);
        this.mode = blockState.getValue(StructureBlock.MODE);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("name", this.getStructureName());
        output.putString("author", this.author);
        output.putString("metadata", this.metaData);
        output.putInt("posX", this.structurePos.getX());
        output.putInt("posY", this.structurePos.getY());
        output.putInt("posZ", this.structurePos.getZ());
        output.putInt("sizeX", this.structureSize.getX());
        output.putInt("sizeY", this.structureSize.getY());
        output.putInt("sizeZ", this.structureSize.getZ());
        output.store("rotation", Rotation.LEGACY_CODEC, this.rotation);
        output.store("mirror", Mirror.LEGACY_CODEC, this.mirror);
        output.store("mode", StructureMode.LEGACY_CODEC, this.mode);
        output.putBoolean("ignoreEntities", this.ignoreEntities);
        output.putBoolean("strict", this.strict);
        output.putBoolean("powered", this.powered);
        output.putBoolean("showair", this.showAir);
        output.putBoolean("showboundingbox", this.showBoundingBox);
        output.putFloat("integrity", this.integrity);
        output.putLong("seed", this.seed);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.setStructureName(input.getStringOr("name", ""));
        this.author = input.getStringOr("author", "");
        this.metaData = input.getStringOr("metadata", "");
        int i = Mth.clamp(input.getIntOr("posX", DEFAULT_POS.getX()), -48, 48);
        int i1 = Mth.clamp(input.getIntOr("posY", DEFAULT_POS.getY()), -48, 48);
        int i2 = Mth.clamp(input.getIntOr("posZ", DEFAULT_POS.getZ()), -48, 48);
        this.structurePos = new BlockPos(i, i1, i2);
        int i3 = Mth.clamp(input.getIntOr("sizeX", DEFAULT_SIZE.getX()), 0, 48);
        int i4 = Mth.clamp(input.getIntOr("sizeY", DEFAULT_SIZE.getY()), 0, 48);
        int i5 = Mth.clamp(input.getIntOr("sizeZ", DEFAULT_SIZE.getZ()), 0, 48);
        this.structureSize = new Vec3i(i3, i4, i5);
        this.rotation = input.read("rotation", Rotation.LEGACY_CODEC).orElse(DEFAULT_ROTATION);
        this.mirror = input.read("mirror", Mirror.LEGACY_CODEC).orElse(DEFAULT_MIRROR);
        this.mode = input.read("mode", StructureMode.LEGACY_CODEC).orElse(StructureMode.DATA);
        this.ignoreEntities = input.getBooleanOr("ignoreEntities", true);
        this.strict = input.getBooleanOr("strict", false);
        this.powered = input.getBooleanOr("powered", false);
        this.showAir = input.getBooleanOr("showair", false);
        this.showBoundingBox = input.getBooleanOr("showboundingbox", true);
        this.integrity = input.getFloatOr("integrity", 1.0F);
        this.seed = input.getLongOr("seed", 0L);
        this.updateBlockState();
    }

    private void updateBlockState() {
        if (this.level != null) {
            BlockPos blockPos = this.getBlockPos();
            BlockState blockState = this.level.getBlockState(blockPos);
            if (blockState.is(Blocks.STRUCTURE_BLOCK)) {
                this.level.setBlock(blockPos, blockState.setValue(StructureBlock.MODE, this.mode), Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    public boolean usedBy(Player player) {
        if (!player.canUseGameMasterBlocks()) {
            return false;
        } else {
            if (player.level().isClientSide()) {
                player.openStructureBlock(this);
            }

            return true;
        }
    }

    public String getStructureName() {
        return this.structureName == null ? "" : this.structureName.toString();
    }

    public boolean hasStructureName() {
        return this.structureName != null;
    }

    public void setStructureName(@Nullable String structureName) {
        this.setStructureName(StringUtil.isNullOrEmpty(structureName) ? null : Identifier.tryParse(structureName));
    }

    public void setStructureName(@Nullable Identifier structureName) {
        this.structureName = structureName;
    }

    public void createdBy(LivingEntity author) {
        this.author = author.getPlainTextName();
    }

    public BlockPos getStructurePos() {
        return this.structurePos;
    }

    public void setStructurePos(BlockPos structurePos) {
        this.structurePos = structurePos;
    }

    public Vec3i getStructureSize() {
        return this.structureSize;
    }

    public void setStructureSize(Vec3i structureSize) {
        this.structureSize = structureSize;
    }

    public Mirror getMirror() {
        return this.mirror;
    }

    public void setMirror(Mirror mirror) {
        this.mirror = mirror;
    }

    public Rotation getRotation() {
        return this.rotation;
    }

    public void setRotation(Rotation rotation) {
        this.rotation = rotation;
    }

    public String getMetaData() {
        return this.metaData;
    }

    public void setMetaData(String metaData) {
        this.metaData = metaData;
    }

    public StructureMode getMode() {
        return this.mode;
    }

    public void setMode(StructureMode mode) {
        this.mode = mode;
        BlockState blockState = this.level.getBlockState(this.getBlockPos());
        if (blockState.is(Blocks.STRUCTURE_BLOCK)) {
            this.level.setBlock(this.getBlockPos(), blockState.setValue(StructureBlock.MODE, mode), Block.UPDATE_CLIENTS);
        }
    }

    public boolean isIgnoreEntities() {
        return this.ignoreEntities;
    }

    public boolean isStrict() {
        return this.strict;
    }

    public void setIgnoreEntities(boolean ignoreEntities) {
        this.ignoreEntities = ignoreEntities;
    }

    public void setStrict(boolean strict) {
        this.strict = strict;
    }

    public float getIntegrity() {
        return this.integrity;
    }

    public void setIntegrity(float integrity) {
        this.integrity = integrity;
    }

    public long getSeed() {
        return this.seed;
    }

    public void setSeed(long seed) {
        this.seed = seed;
    }

    public boolean detectSize() {
        if (this.mode != StructureMode.SAVE) {
            return false;
        } else {
            BlockPos blockPos = this.getBlockPos();
            int i = 80;
            BlockPos blockPos1 = new BlockPos(blockPos.getX() - 80, this.level.getMinY(), blockPos.getZ() - 80);
            BlockPos blockPos2 = new BlockPos(blockPos.getX() + 80, this.level.getMaxY(), blockPos.getZ() + 80);
            Stream<BlockPos> relatedCorners = this.getRelatedCorners(blockPos1, blockPos2);
            return calculateEnclosingBoundingBox(blockPos, relatedCorners)
                .filter(
                    boundingBox -> {
                        int i1 = boundingBox.maxX() - boundingBox.minX();
                        int i2 = boundingBox.maxY() - boundingBox.minY();
                        int i3 = boundingBox.maxZ() - boundingBox.minZ();
                        if (i1 > 1 && i2 > 1 && i3 > 1) {
                            this.structurePos = new BlockPos(
                                boundingBox.minX() - blockPos.getX() + 1, boundingBox.minY() - blockPos.getY() + 1, boundingBox.minZ() - blockPos.getZ() + 1
                            );
                            this.structureSize = new Vec3i(i1 - 1, i2 - 1, i3 - 1);
                            this.setChanged();
                            BlockState blockState = this.level.getBlockState(blockPos);
                            this.level.sendBlockUpdated(blockPos, blockState, blockState, Block.UPDATE_ALL);
                            return true;
                        } else {
                            return false;
                        }
                    }
                )
                .isPresent();
        }
    }

    private Stream<BlockPos> getRelatedCorners(BlockPos minPos, BlockPos maxPos) {
        return BlockPos.betweenClosedStream(minPos, maxPos)
            .filter(pos -> this.level.getBlockState(pos).is(Blocks.STRUCTURE_BLOCK))
            .map(this.level::getBlockEntity)
            .filter(blockEntity -> blockEntity instanceof StructureBlockEntity)
            .map(blockEntity -> (StructureBlockEntity)blockEntity)
            .filter(blockEntity -> blockEntity.mode == StructureMode.CORNER && Objects.equals(this.structureName, blockEntity.structureName))
            .map(BlockEntity::getBlockPos);
    }

    private static Optional<BoundingBox> calculateEnclosingBoundingBox(BlockPos pos, Stream<BlockPos> relatedCorners) {
        Iterator<BlockPos> iterator = relatedCorners.iterator();
        if (!iterator.hasNext()) {
            return Optional.empty();
        } else {
            BlockPos blockPos = iterator.next();
            BoundingBox boundingBox = new BoundingBox(blockPos);
            if (iterator.hasNext()) {
                iterator.forEachRemaining(boundingBox::encapsulate);
            } else {
                boundingBox.encapsulate(pos);
            }

            return Optional.of(boundingBox);
        }
    }

    public boolean saveStructure() {
        return this.mode == StructureMode.SAVE && this.saveStructure(true);
    }

    public boolean saveStructure(boolean writeToDisk) {
        if (this.structureName != null && this.level instanceof ServerLevel serverLevel) {
            BlockPos var4 = this.getBlockPos().offset(this.structurePos);
            return saveStructure(serverLevel, this.structureName, var4, this.structureSize, this.ignoreEntities, this.author, writeToDisk, List.of());
        } else {
            return false;
        }
    }

    public static boolean saveStructure(
        ServerLevel level,
        Identifier structureName,
        BlockPos pos,
        Vec3i size,
        boolean ignoreEntities,
        String author,
        boolean writeToDisk,
        List<Block> ignoredBlocks
    ) {
        StructureTemplateManager structureManager = level.getStructureManager();

        StructureTemplate structureTemplate;
        try {
            structureTemplate = structureManager.getOrCreate(structureName);
        } catch (IdentifierException var12) {
            return false;
        }

        structureTemplate.fillFromWorld(level, pos, size, !ignoreEntities, Stream.concat(ignoredBlocks.stream(), Stream.of(Blocks.STRUCTURE_VOID)).toList());
        structureTemplate.setAuthor(author);
        if (writeToDisk) {
            try {
                return structureManager.save(structureName);
            } catch (IdentifierException var11) {
                return false;
            }
        } else {
            return true;
        }
    }

    public static RandomSource createRandom(long seed) {
        return seed == 0L ? RandomSource.create(Util.getMillis()) : RandomSource.create(seed);
    }

    public boolean placeStructureIfSameSize(ServerLevel level) {
        if (this.mode == StructureMode.LOAD && this.structureName != null) {
            StructureTemplate structureTemplate = level.getStructureManager().get(this.structureName).orElse(null);
            if (structureTemplate == null) {
                return false;
            } else if (structureTemplate.getSize().equals(this.structureSize)) {
                this.placeStructure(level, structureTemplate);
                return true;
            } else {
                this.loadStructureInfo(structureTemplate);
                return false;
            }
        } else {
            return false;
        }
    }

    public boolean loadStructureInfo(ServerLevel level) {
        StructureTemplate structureTemplate = this.getStructureTemplate(level);
        if (structureTemplate == null) {
            return false;
        } else {
            this.loadStructureInfo(structureTemplate);
            return true;
        }
    }

    private void loadStructureInfo(StructureTemplate structureTemplate) {
        this.author = !StringUtil.isNullOrEmpty(structureTemplate.getAuthor()) ? structureTemplate.getAuthor() : "";
        this.structureSize = structureTemplate.getSize();
        this.setChanged();
    }

    public void placeStructure(ServerLevel level) {
        StructureTemplate structureTemplate = this.getStructureTemplate(level);
        if (structureTemplate != null) {
            this.placeStructure(level, structureTemplate);
        }
    }

    private @Nullable StructureTemplate getStructureTemplate(ServerLevel level) {
        return this.structureName == null ? null : level.getStructureManager().get(this.structureName).orElse(null);
    }

    private void placeStructure(ServerLevel level, StructureTemplate structureTemplate) {
        this.loadStructureInfo(structureTemplate);
        StructurePlaceSettings structurePlaceSettings = new StructurePlaceSettings()
            .setMirror(this.mirror)
            .setRotation(this.rotation)
            .setIgnoreEntities(this.ignoreEntities)
            .setKnownShape(this.strict);
        if (this.integrity < 1.0F) {
            structurePlaceSettings.clearProcessors()
                .addProcessor(new BlockRotProcessor(Mth.clamp(this.integrity, 0.0F, 1.0F)))
                .setRandom(createRandom(this.seed));
        }

        BlockPos blockPos = this.getBlockPos().offset(this.structurePos);
        if (SharedConstants.DEBUG_STRUCTURE_EDIT_MODE) {
            BlockPos.betweenClosed(blockPos, blockPos.offset(this.structureSize))
                .forEach(blockPos1 -> level.setBlock(blockPos1, Blocks.STRUCTURE_VOID.defaultBlockState(), Block.UPDATE_CLIENTS));
        }

        structureTemplate.placeInWorld(
            level,
            blockPos,
            blockPos,
            structurePlaceSettings,
            createRandom(this.seed),
            Block.UPDATE_CLIENTS | (this.strict ? Block.UPDATE_SKIP_ALL_SIDEEFFECTS : 0)
        );
    }

    public void unloadStructure() {
        if (this.structureName != null) {
            ServerLevel serverLevel = (ServerLevel)this.level;
            StructureTemplateManager structureManager = serverLevel.getStructureManager();
            structureManager.remove(this.structureName);
        }
    }

    public boolean isStructureLoadable() {
        if (this.mode == StructureMode.LOAD && !this.level.isClientSide() && this.structureName != null) {
            ServerLevel serverLevel = (ServerLevel)this.level;
            StructureTemplateManager structureManager = serverLevel.getStructureManager();

            try {
                return structureManager.get(this.structureName).isPresent();
            } catch (IdentifierException var4) {
                return false;
            }
        } else {
            return false;
        }
    }

    public boolean isPowered() {
        return this.powered;
    }

    public void setPowered(boolean powered) {
        this.powered = powered;
    }

    public boolean getShowAir() {
        return this.showAir;
    }

    public void setShowAir(boolean showAir) {
        this.showAir = showAir;
    }

    public boolean getShowBoundingBox() {
        return this.showBoundingBox;
    }

    public void setShowBoundingBox(boolean showBoundingBox) {
        this.showBoundingBox = showBoundingBox;
    }

    @Override
    public BoundingBoxRenderable.Mode renderMode() {
        if (this.mode != StructureMode.SAVE && this.mode != StructureMode.LOAD) {
            return BoundingBoxRenderable.Mode.NONE;
        } else if (this.mode == StructureMode.SAVE && this.showAir) {
            return BoundingBoxRenderable.Mode.BOX_AND_INVISIBLE_BLOCKS;
        } else {
            return this.mode != StructureMode.SAVE && !this.showBoundingBox ? BoundingBoxRenderable.Mode.NONE : BoundingBoxRenderable.Mode.BOX;
        }
    }

    @Override
    public BoundingBoxRenderable.RenderableBox getRenderableBox() {
        BlockPos structurePos = this.getStructurePos();
        Vec3i structureSize = this.getStructureSize();
        int x = structurePos.getX();
        int z = structurePos.getZ();
        int y = structurePos.getY();
        int i = y + structureSize.getY();
        int x1;
        int i1;
        switch (this.mirror) {
            case LEFT_RIGHT:
                x1 = structureSize.getX();
                i1 = -structureSize.getZ();
                break;
            case FRONT_BACK:
                x1 = -structureSize.getX();
                i1 = structureSize.getZ();
                break;
            default:
                x1 = structureSize.getX();
                i1 = structureSize.getZ();
        }

        int i2;
        int i3;
        int i4;
        int i5;
        switch (this.rotation) {
            case CLOCKWISE_90:
                i2 = i1 < 0 ? x : x + 1;
                i3 = x1 < 0 ? z + 1 : z;
                i4 = i2 - i1;
                i5 = i3 + x1;
                break;
            case CLOCKWISE_180:
                i2 = x1 < 0 ? x : x + 1;
                i3 = i1 < 0 ? z : z + 1;
                i4 = i2 - x1;
                i5 = i3 - i1;
                break;
            case COUNTERCLOCKWISE_90:
                i2 = i1 < 0 ? x + 1 : x;
                i3 = x1 < 0 ? z : z + 1;
                i4 = i2 + i1;
                i5 = i3 - x1;
                break;
            default:
                i2 = x1 < 0 ? x + 1 : x;
                i3 = i1 < 0 ? z + 1 : z;
                i4 = i2 + x1;
                i5 = i3 + i1;
        }

        return BoundingBoxRenderable.RenderableBox.fromCorners(i2, y, i3, i4, i, i5);
    }

    public static enum UpdateType {
        UPDATE_DATA,
        SAVE_AREA,
        LOAD_AREA,
        SCAN_AREA;
    }
}
