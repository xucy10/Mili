package net.minecraft.world.level.block.entity;

import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.util.Mth;
import net.minecraft.world.Clearable;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.LecternMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class LecternBlockEntity extends BlockEntity implements Clearable, MenuProvider {
    public static final int DATA_PAGE = 0;
    public static final int NUM_DATA = 1;
    public static final int SLOT_BOOK = 0;
    public static final int NUM_SLOTS = 1;
    // CraftBukkit start - add fields and methods
    public final Container bookAccess = new LecternInventory();
    public class LecternInventory implements Container {
        public java.util.List<org.bukkit.entity.HumanEntity> transaction = new java.util.ArrayList<>();
        private int maxStack = 1;

        @Override
        public java.util.List<net.minecraft.world.item.ItemStack> getContents() {
            return java.util.List.of(LecternBlockEntity.this.book);
        }

        @Override
        public void onOpen(org.bukkit.craftbukkit.entity.CraftHumanEntity player) {
            this.transaction.add(player);
        }

        @Override
        public void onClose(org.bukkit.craftbukkit.entity.CraftHumanEntity player) {
            this.transaction.remove(player);
        }

        @Override
        public java.util.List<org.bukkit.entity.HumanEntity> getViewers() {
            return this.transaction;
        }

        @Override
        public void setMaxStackSize(int size) {
            this.maxStack = size;
        }

        @Override
        public org.bukkit.@Nullable Location getLocation() {
            if (LecternBlockEntity.this.level == null) return null;
            return org.bukkit.craftbukkit.util.CraftLocation.toBukkit(LecternBlockEntity.this.worldPosition, LecternBlockEntity.this.level);
        }

        @Override
        public org.bukkit.inventory.InventoryHolder getOwner() {
            return LecternBlockEntity.this.getOwner();
        }

        public LecternBlockEntity getLectern() {
            return LecternBlockEntity.this;
        }
        // CraftBukkit end

        @Override
        public int getContainerSize() {
            return 1;
        }

        @Override
        public boolean isEmpty() {
            return LecternBlockEntity.this.book.isEmpty();
        }

        @Override
        public ItemStack getItem(int slot) {
            return slot == 0 ? LecternBlockEntity.this.book : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            if (slot == 0) {
                ItemStack itemStack = LecternBlockEntity.this.book.split(amount);
                if (LecternBlockEntity.this.book.isEmpty()) {
                    LecternBlockEntity.this.onBookItemRemove();
                }

                return itemStack;
            } else {
                return ItemStack.EMPTY;
            }
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            if (slot == 0) {
                ItemStack itemStack = LecternBlockEntity.this.book;
                LecternBlockEntity.this.book = ItemStack.EMPTY;
                LecternBlockEntity.this.onBookItemRemove();
                return itemStack;
            } else {
                return ItemStack.EMPTY;
            }
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            // CraftBukkit start
            if (slot == 0) {
                LecternBlockEntity.this.setBook(stack);
                if (LecternBlockEntity.this.getLevel() != null) {
                    LecternBlock.resetBookState(null, LecternBlockEntity.this.getLevel(), LecternBlockEntity.this.getBlockPos(), LecternBlockEntity.this.getBlockState(), LecternBlockEntity.this.hasBook());
                }
            }
            // CraftBukkit end
        }

        @Override
        public int getMaxStackSize() {
            return this.maxStack; // CraftBukkit
        }

        @Override
        public void setChanged() {
            LecternBlockEntity.this.setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return Container.stillValidBlockEntity(LecternBlockEntity.this, player) && LecternBlockEntity.this.hasBook();
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return false;
        }

        @Override
        public void clearContent() {
        }
    };
    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return index == 0 ? LecternBlockEntity.this.page : 0;
        }

        @Override
        public void set(int index, int value) {
            if (index == 0) {
                LecternBlockEntity.this.setPage(value);
            }
        }

        @Override
        public int getCount() {
            return 1;
        }
    };
    ItemStack book = ItemStack.EMPTY;
    int page;
    private int pageCount;

    public LecternBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityType.LECTERN, pos, blockState);
    }

    public ItemStack getBook() {
        return this.book;
    }

    public boolean hasBook() {
        return this.book.has(DataComponents.WRITABLE_BOOK_CONTENT) || this.book.has(DataComponents.WRITTEN_BOOK_CONTENT);
    }

    public void setBook(ItemStack stack) {
        this.setBook(stack, null);
    }

    void onBookItemRemove() {
        this.page = 0;
        this.pageCount = 0;
        LecternBlock.resetBookState(null, this.getLevel(), this.getBlockPos(), this.getBlockState(), false);
    }

    public void setBook(ItemStack stack, @Nullable Player player) {
        this.book = this.resolveBook(stack, player);
        this.page = 0;
        this.pageCount = getPageCount(this.book);
        this.setChanged();
    }

    public void setPage(int page) {
        int i = Mth.clamp(page, 0, this.pageCount - 1);
        if (i != this.page) {
            this.page = i;
            this.setChanged();
            if (this.level != null) LecternBlock.signalPageChange(this.getLevel(), this.getBlockPos(), this.getBlockState()); // CraftBukkit
        }
    }

    public int getPage() {
        return this.page;
    }

    public int getRedstoneSignal() {
        float f = this.pageCount > 1 ? this.getPage() / (this.pageCount - 1.0F) : 1.0F;
        return Mth.floor(f * 14.0F) + (this.hasBook() ? 1 : 0);
    }

    private ItemStack resolveBook(ItemStack stack, @Nullable Player player) {
        if (this.level instanceof ServerLevel serverLevel) {
            WrittenBookContent.resolveForItem(stack, this.createCommandSourceStack(player, serverLevel), player);
        }

        return stack;
    }

    // CraftBukkit start
    private final CommandSource commandSource = new CommandSource() {

        @Override
        public void sendSystemMessage(Component message) {
        }

        @Override
        public org.bukkit.command.CommandSender getBukkitSender(CommandSourceStack commandSourceStack) {
            return commandSourceStack.getEntity() != null
                ? commandSourceStack.getEntity().getBukkitEntity()
                : new org.bukkit.craftbukkit.command.CraftBlockCommandSender(commandSourceStack, LecternBlockEntity.this);
        }

        @Override
        public boolean acceptsSuccess() {
            return false;
        }

        @Override
        public boolean acceptsFailure() {
            return false;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }
    };
    // CraftBukkit end
    private CommandSourceStack createCommandSourceStack(@Nullable Player player, ServerLevel level) {
        String string;
        Component component;
        if (player == null) {
            string = "Lectern";
            component = Component.literal("Lectern");
        } else {
            string = player.getPlainTextName();
            component = player.getDisplayName();
        }

        Vec3 vec3 = Vec3.atCenterOf(this.worldPosition);
        return new CommandSourceStack(
            this.commandSource, vec3, Vec2.ZERO, level, LevelBasedPermissionSet.GAMEMASTER, string, component, level.getServer(), player // CraftBukkit - commandSource
        );
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.book = input.read("Book", ItemStack.CODEC).map(itemStack -> this.resolveBook(itemStack, null)).orElse(ItemStack.EMPTY);
        this.pageCount = getPageCount(this.book);
        this.page = Mth.clamp(input.getIntOr("Page", 0), 0, this.pageCount - 1);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!this.getBook().isEmpty()) {
            output.store("Book", ItemStack.CODEC, this.getBook());
            output.putInt("Page", this.page);
        }
    }

    @Override
    public void clearContent() {
        this.setBook(ItemStack.EMPTY);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (state.getValue(LecternBlock.HAS_BOOK) && this.level != null) {
            Direction direction = state.getValue(LecternBlock.FACING);
            ItemStack itemStack = this.getBook().copy();
            float f = 0.25F * direction.getStepX();
            float f1 = 0.25F * direction.getStepZ();
            ItemEntity itemEntity = new ItemEntity(this.level, pos.getX() + 0.5 + f, pos.getY() + 1, pos.getZ() + 0.5 + f1, itemStack);
            itemEntity.setDefaultPickUpDelay();
            this.level.addFreshEntity(itemEntity);
        }
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new LecternMenu(containerId, this.bookAccess, this.dataAccess, playerInventory); // CraftBukkit
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.lectern");
    }

    private static int getPageCount(ItemStack stack) {
        WrittenBookContent writtenBookContent = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
        if (writtenBookContent != null) {
            return writtenBookContent.pages().size();
        } else {
            WritableBookContent writableBookContent = stack.get(DataComponents.WRITABLE_BOOK_CONTENT);
            return writableBookContent != null ? writableBookContent.pages().size() : 0;
        }
    }
}
