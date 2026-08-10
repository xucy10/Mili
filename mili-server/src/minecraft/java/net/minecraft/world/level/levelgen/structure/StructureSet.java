package net.minecraft.world.level.levelgen.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

public record StructureSet(List<StructureSet.StructureSelectionEntry> structures, StructurePlacement placement) {
    public static final Codec<StructureSet> DIRECT_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                StructureSet.StructureSelectionEntry.CODEC.listOf().fieldOf("structures").forGetter(StructureSet::structures),
                StructurePlacement.CODEC.fieldOf("placement").forGetter(StructureSet::placement)
            )
            .apply(instance, StructureSet::new)
    );
    public static final Codec<Holder<StructureSet>> CODEC = RegistryFileCodec.create(Registries.STRUCTURE_SET, DIRECT_CODEC);

    public StructureSet(Holder<Structure> structure, StructurePlacement placement) {
        this(List.of(new StructureSet.StructureSelectionEntry(structure, 1)), placement);
    }

    public static StructureSet.StructureSelectionEntry entry(Holder<Structure> structure, int weight) {
        return new StructureSet.StructureSelectionEntry(structure, weight);
    }

    public static StructureSet.StructureSelectionEntry entry(Holder<Structure> structure) {
        return new StructureSet.StructureSelectionEntry(structure, 1);
    }

    public record StructureSelectionEntry(Holder<Structure> structure, int weight) {
        public static final Codec<StructureSet.StructureSelectionEntry> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Structure.CODEC.fieldOf("structure").forGetter(StructureSet.StructureSelectionEntry::structure),
                    ExtraCodecs.POSITIVE_INT.fieldOf("weight").forGetter(StructureSet.StructureSelectionEntry::weight)
                )
                .apply(instance, StructureSet.StructureSelectionEntry::new)
        );
    }
}
