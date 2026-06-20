package net.minecraft.data.structures;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.packs.PackType;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.slf4j.Logger;

public class StructureUpdater implements SnbtToNbt.Filter {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PREFIX = PackType.SERVER_DATA.getDirectory() + "/minecraft/structure/";

    @Override
    public CompoundTag apply(String structureLocationPath, CompoundTag tag) {
        return structureLocationPath.startsWith(PREFIX) ? update(structureLocationPath, tag) : tag;
    }

    public static CompoundTag update(String structureLocationPath, CompoundTag tag) {
        StructureTemplate structureTemplate = new StructureTemplate();
        int dataVersion = NbtUtils.getDataVersion(tag, 500);
        int i = 4650;
        if (dataVersion < 4650) {
            LOGGER.warn("SNBT Too old, do not forget to update: {} < {}: {}", dataVersion, 4650, structureLocationPath);
        }

        CompoundTag compoundTag = ca.spottedleaf.dataconverter.minecraft.MCDataConverter.convertTag(ca.spottedleaf.dataconverter.minecraft.datatypes.MCTypeRegistry.STRUCTURE, tag, dataVersion, ca.spottedleaf.dataconverter.minecraft.util.Version.getCurrentVersion()); // Paper
        structureTemplate.load(BuiltInRegistries.BLOCK, compoundTag);
        return structureTemplate.save(new CompoundTag());
    }
}
