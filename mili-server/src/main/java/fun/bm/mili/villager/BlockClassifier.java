package fun.bm.mili.villager;

import org.bukkit.Material;

import java.util.EnumSet;

/**
 * Classifies blocks for villager movement checks.
 */
public final class BlockClassifier {
    private final EnumSet<Material> impassableRegular;
    private final EnumSet<Material> impassableTall;
    private final EnumSet<Material> impassableAll;
    private final EnumSet<Material> doorBlocks;
    private final EnumSet<Material> cropBlocks;
    private final EnumSet<Material> professionBlocks;

    private BlockClassifier(EnumSet<Material> impassableRegular,
                            EnumSet<Material> impassableTall,
                            EnumSet<Material> impassableAll,
                            EnumSet<Material> doorBlocks,
                            EnumSet<Material> cropBlocks,
                            EnumSet<Material> professionBlocks) {
        this.impassableRegular = impassableRegular;
        this.impassableTall = impassableTall;
        this.impassableAll = impassableAll;
        this.doorBlocks = doorBlocks;
        this.cropBlocks = cropBlocks;
        this.professionBlocks = professionBlocks;
    }

    public static BlockClassifier fromServerRegistry() {
        EnumSet<Material> impassableRegular = EnumSet.noneOf(Material.class);
        EnumSet<Material> impassableTall = EnumSet.noneOf(Material.class);
        EnumSet<Material> impassableAll = EnumSet.noneOf(Material.class);
        EnumSet<Material> doorBlocks = EnumSet.noneOf(Material.class);
        EnumSet<Material> cropBlocks = EnumSet.noneOf(Material.class);
        EnumSet<Material> professionBlocks = EnumSet.noneOf(Material.class);

        for (Material mat : Material.values()) {
            String name = mat.name();
            if (name.endsWith("_DOOR") || name.endsWith("_TRAPDOOR") || name.endsWith("_FENCE_GATE")) {
                doorBlocks.add(mat);
            }
            if (name.endsWith("_CROP") || name.endsWith("_STEM") || name.equals("BEETROOTS") || name.equals("CARROTS") || name.equals("POTATOES") || name.equals("WHEAT")) {
                cropBlocks.add(mat);
            }
            if (name.endsWith("_BED")) {
                impassableRegular.add(mat);
            }
        }

        // Profession blocks
        professionBlocks.add(Material.BLAST_FURNACE);
        professionBlocks.add(Material.SMOKER);
        professionBlocks.add(Material.CARTOGRAPHY_TABLE);
        professionBlocks.add(Material.BREWING_STAND);
        professionBlocks.add(Material.COMPOSTER);
        professionBlocks.add(Material.BARREL);
        professionBlocks.add(Material.FLETCHING_TABLE);
        professionBlocks.add(Material.CAULDRON);
        professionBlocks.add(Material.LECTERN);
        professionBlocks.add(Material.STONECUTTER);
        professionBlocks.add(Material.LOOM);
        professionBlocks.add(Material.SMITHING_TABLE);
        professionBlocks.add(Material.GRINDSTONE);

        // Tall impassable blocks
        impassableTall.add(Material.AIR);
        impassableTall.add(Material.CAVE_AIR);
        impassableTall.add(Material.VOID_AIR);

        // All impassable
        impassableAll.addAll(impassableRegular);
        impassableAll.addAll(impassableTall);

        return new BlockClassifier(impassableRegular, impassableTall, impassableAll, doorBlocks, cropBlocks, professionBlocks);
    }

    public EnumSet<Material> impassableRegular() { return impassableRegular; }
    public EnumSet<Material> impassableTall() { return impassableTall; }
    public EnumSet<Material> impassableAll() { return impassableAll; }
    public EnumSet<Material> doorBlocks() { return doorBlocks; }
    public EnumSet<Material> cropBlocks() { return cropBlocks; }
    public EnumSet<Material> professionBlocks() { return professionBlocks; }
}