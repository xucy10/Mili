package net.minecraft.stats;

import net.minecraft.world.inventory.RecipeBookType;

public class RecipeBook {
    protected final RecipeBookSettings bookSettings = new RecipeBookSettings();

    public boolean isOpen(RecipeBookType bookType) {
        return this.bookSettings.isOpen(bookType);
    }

    public void setOpen(RecipeBookType bookType, boolean _open) {
        this.bookSettings.setOpen(bookType, _open);
    }

    public boolean isFiltering(RecipeBookType bookType) {
        return this.bookSettings.isFiltering(bookType);
    }

    public void setFiltering(RecipeBookType bookType, boolean filtering) {
        this.bookSettings.setFiltering(bookType, filtering);
    }

    public void setBookSettings(RecipeBookSettings settings) {
        this.bookSettings.replaceFrom(settings);
    }

    public RecipeBookSettings getBookSettings() {
        return this.bookSettings;
    }

    public void setBookSetting(RecipeBookType bookType, boolean _open, boolean filtering) {
        this.bookSettings.setOpen(bookType, _open);
        this.bookSettings.setFiltering(bookType, filtering);
    }
}
