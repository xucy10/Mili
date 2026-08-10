package net.minecraft.world.level;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.chunk.StructureAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheck;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.jspecify.annotations.Nullable;

public class StructureManager {
    public final LevelAccessor level;
    private final WorldOptions worldOptions;
    private final StructureCheck structureCheck;

    public StructureManager(LevelAccessor level, WorldOptions worldOptions, StructureCheck structureCheck) {
        this.level = level;
        this.worldOptions = worldOptions;
        this.structureCheck = structureCheck;
    }

    public StructureManager forWorldGenRegion(WorldGenRegion region) {
        if (region.getLevel() != this.level) {
            throw new IllegalStateException("Using invalid structure manager (source level: " + region.getLevel() + ", region: " + region);
        } else {
            return new StructureManager(region, this.worldOptions, this.structureCheck);
        }
    }

    public List<StructureStart> startsForStructure(ChunkPos chunkPos, Predicate<Structure> structurePredicate) {
        Map<Structure, LongSet> allReferences = this.level.getChunk(chunkPos.x, chunkPos.z, ChunkStatus.STRUCTURE_REFERENCES).getAllReferences(); // Folia - region threading
        // Paper end - Fix swamp hut cat generation deadlock
        Builder<StructureStart> builder = ImmutableList.builder();

        for (Entry<Structure, LongSet> entry : allReferences.entrySet()) {
            Structure structure = entry.getKey();
            if (structurePredicate.test(structure)) {
                this.fillStartsForStructure(structure, entry.getValue(), builder::add);
            }
        }

        return builder.build();
    }

    public List<StructureStart> startsForStructure(SectionPos sectionPos, Structure structure) {
        LongSet referencesForStructure = this.level
            .getChunk(sectionPos.x(), sectionPos.z(), ChunkStatus.STRUCTURE_REFERENCES)
            .getReferencesForStructure(structure);
        Builder<StructureStart> builder = ImmutableList.builder();
        this.fillStartsForStructure(structure, referencesForStructure, builder::add);
        return builder.build();
    }

    public void fillStartsForStructure(Structure structure, LongSet structureRefs, Consumer<StructureStart> startConsumer) {
        for (long l : structureRefs) {
            SectionPos sectionPos = SectionPos.of(new ChunkPos(l), this.level.getMinSectionY());
            StructureStart startForStructure = this.getStartForStructure(
                sectionPos, structure, this.level.getChunk(sectionPos.x(), sectionPos.z(), ChunkStatus.STRUCTURE_STARTS)
            );
            if (startForStructure != null && startForStructure.isValid()) {
                startConsumer.accept(startForStructure);
            }
        }
    }

    public @Nullable StructureStart getStartForStructure(SectionPos sectionPos, Structure structure, StructureAccess structureAccess) {
        return structureAccess.getStartForStructure(structure);
    }

    public void setStartForStructure(SectionPos sectionPos, Structure structure, StructureStart structureStart, StructureAccess structureAccess) {
        structureAccess.setStartForStructure(structure, structureStart);
    }

    public void addReferenceForStructure(SectionPos sectionPos, Structure structure, long reference, StructureAccess structureAccess) {
        structureAccess.addReferenceForStructure(structure, reference);
    }

    public boolean shouldGenerateStructures() {
        return this.worldOptions.generateStructures();
    }

    public StructureStart getStructureAt(BlockPos pos, Structure structure) {
        for (StructureStart structureStart : this.startsForStructure(SectionPos.of(pos), structure)) {
            if (structureStart.getBoundingBox().isInside(pos)) {
                return structureStart;
            }
        }

        return StructureStart.INVALID_START;
    }

    public StructureStart getStructureWithPieceAt(BlockPos pos, TagKey<Structure> structureTag) {
        return this.getStructureWithPieceAt(pos, holder -> holder.is(structureTag));
    }

    public StructureStart getStructureWithPieceAt(BlockPos pos, HolderSet<Structure> structures) {
        return this.getStructureWithPieceAt(pos, structures::contains); // Folia - region threading
    }

    public StructureStart getStructureWithPieceAt(BlockPos pos, Predicate<Holder<Structure>> predicate) {
        // Folia - region threading
        // Paper end - Fix swamp hut cat generation deadlock
        Registry<Structure> registry = this.registryAccess().lookupOrThrow(Registries.STRUCTURE);

        for (StructureStart structureStart : this.startsForStructure(
            new ChunkPos(pos), structure -> registry.get(registry.getId(structure)).map(predicate::test).orElse(false) // Paper - Fix swamp hut cat generation deadlock // Folia - region threading
        )) {
            if (this.structureHasPieceAt(pos, structureStart)) {
                return structureStart;
            }
        }

        return StructureStart.INVALID_START;
    }

    public StructureStart getStructureWithPieceAt(BlockPos pos, Structure structure) {
        for (StructureStart structureStart : this.startsForStructure(SectionPos.of(pos), structure)) {
            if (this.structureHasPieceAt(pos, structureStart)) {
                return structureStart;
            }
        }

        return StructureStart.INVALID_START;
    }

    public boolean structureHasPieceAt(BlockPos pos, StructureStart structureStart) {
        for (StructurePiece structurePiece : structureStart.getPieces()) {
            if (structurePiece.getBoundingBox().isInside(pos)) {
                return true;
            }
        }

        return false;
    }

    public boolean hasAnyStructureAt(BlockPos pos) {
        SectionPos sectionPos = SectionPos.of(pos);
        return this.level.getChunk(sectionPos.x(), sectionPos.z(), ChunkStatus.STRUCTURE_REFERENCES).hasAnyStructureReferences();
    }

    public Map<Structure, LongSet> getAllStructuresAt(BlockPos pos) {
        SectionPos sectionPos = SectionPos.of(pos);
        return this.level.getChunk(sectionPos.x(), sectionPos.z(), ChunkStatus.STRUCTURE_REFERENCES).getAllReferences();
    }

    public StructureCheckResult checkStructurePresence(ChunkPos chunkPos, Structure structure, StructurePlacement placement, boolean skipKnownStructures) {
        return this.structureCheck.checkStart(chunkPos, structure, placement, skipKnownStructures);
    }

    public void addReference(StructureStart structureStart) {
        //structureStart.addReference(); // Folia - region threading - move to caller
        this.structureCheck.incrementReference(structureStart.getChunkPos(), structureStart.getStructure());
    }

    public RegistryAccess registryAccess() {
        return this.level.registryAccess();
    }
}
