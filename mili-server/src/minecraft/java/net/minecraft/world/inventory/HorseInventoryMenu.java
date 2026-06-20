package net.minecraft.world.inventory;

import net.minecraft.resources.Identifier;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.entity.player.Inventory;

public class HorseInventoryMenu extends AbstractMountInventoryMenu {
    private static final Identifier SADDLE_SLOT_SPRITE = Identifier.withDefaultNamespace("container/slot/saddle");
    private static final Identifier LLAMA_ARMOR_SLOT_SPRITE = Identifier.withDefaultNamespace("container/slot/llama_armor");
    private static final Identifier ARMOR_SLOT_SPRITE = Identifier.withDefaultNamespace("container/slot/horse_armor");

    public HorseInventoryMenu(int containerId, Inventory inventory, Container horseContainer, final AbstractHorse horse, int columns) {
        super(containerId, inventory, horseContainer, horse);
        Container container = horse.createEquipmentSlotContainer(EquipmentSlot.SADDLE);
        this.addSlot(new ArmorSlot(container, horse, EquipmentSlot.SADDLE, 0, 8, 18, SADDLE_SLOT_SPRITE) {
            @Override
            public boolean isActive() {
                return horse.canUseSlot(EquipmentSlot.SADDLE) && horse.getType().is(EntityTypeTags.CAN_EQUIP_SADDLE);
            }
        });
        final boolean flag = horse instanceof Llama;
        Identifier identifier = flag ? LLAMA_ARMOR_SLOT_SPRITE : ARMOR_SLOT_SPRITE;
        Container container1 = horse.createEquipmentSlotContainer(EquipmentSlot.BODY);
        this.addSlot(new ArmorSlot(container1, horse, EquipmentSlot.BODY, 0, 8, 36, identifier) {
            @Override
            public boolean isActive() {
                return horse.canUseSlot(EquipmentSlot.BODY) && (horse.getType().is(EntityTypeTags.CAN_WEAR_HORSE_ARMOR) || flag);
            }
        });
        if (columns > 0) {
            for (int i = 0; i < 3; i++) {
                for (int i1 = 0; i1 < columns; i1++) {
                    this.addSlot(new Slot(horseContainer, i1 + i * columns, 80 + i1 * 18, 18 + i * 18));
                }
            }
        }

        this.addStandardInventorySlots(inventory, 8, 84);
    }

    @Override
    protected boolean hasInventoryChanged(Container oldInventory) {
        return ((AbstractHorse)this.mount).hasInventoryChanged(oldInventory);
    }
}
