package net.minecraft.advancements;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class AdvancementTree {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Map<Identifier, AdvancementNode> nodes = new Object2ObjectOpenHashMap<>();
    private final Set<AdvancementNode> roots = new ObjectLinkedOpenHashSet<>();
    private final Set<AdvancementNode> tasks = new ObjectLinkedOpenHashSet<>();
    private AdvancementTree.@Nullable Listener listener;

    private void remove(AdvancementNode node) {
        for (AdvancementNode advancementNode : node.children()) {
            this.remove(advancementNode);
        }

        LOGGER.debug("Forgot about advancement {}", node.holder()); // Paper - Improve logging and errors
        this.nodes.remove(node.holder().id());
        if (node.parent() == null) {
            this.roots.remove(node);
            if (this.listener != null) {
                this.listener.onRemoveAdvancementRoot(node);
            }
        } else {
            this.tasks.remove(node);
            if (this.listener != null) {
                this.listener.onRemoveAdvancementTask(node);
            }
        }
    }

    public void remove(Set<Identifier> advancements) {
        for (Identifier identifier : advancements) {
            AdvancementNode advancementNode = this.nodes.get(identifier);
            if (advancementNode == null) {
                LOGGER.warn("Told to remove advancement {} but I don't know what that is", identifier);
            } else {
                this.remove(advancementNode);
            }
        }
    }

    public void addAll(Collection<AdvancementHolder> advancements) {
        List<AdvancementHolder> list = new ArrayList<>(advancements);

        while (!list.isEmpty()) {
            if (!list.removeIf(this::tryInsert)) {
                LOGGER.error("Couldn't load advancements: {}", list);
                break;
            }
        }

        // LOGGER.info("Loaded {} advancements", this.nodes.size()); // CraftBukkit - moved to AdvancementDataWorld#reload // Paper - Improve logging and errors; you say it was moved... but it wasn't :) it should be moved however, since this is called when the API creates an advancement
    }

    private boolean tryInsert(AdvancementHolder advancement) {
        Optional<Identifier> optional = advancement.value().parent();
        AdvancementNode advancementNode = optional.map(this.nodes::get).orElse(null);
        if (advancementNode == null && optional.isPresent()) {
            return false;
        } else {
            AdvancementNode advancementNode1 = new AdvancementNode(advancement, advancementNode);
            if (advancementNode != null) {
                advancementNode.addChild(advancementNode1);
            }

            this.nodes.put(advancement.id(), advancementNode1);
            if (advancementNode == null) {
                this.roots.add(advancementNode1);
                if (this.listener != null) {
                    this.listener.onAddAdvancementRoot(advancementNode1);
                }
            } else {
                this.tasks.add(advancementNode1);
                if (this.listener != null) {
                    this.listener.onAddAdvancementTask(advancementNode1);
                }
            }

            return true;
        }
    }

    public void clear() {
        this.nodes.clear();
        this.roots.clear();
        this.tasks.clear();
        if (this.listener != null) {
            this.listener.onAdvancementsCleared();
        }
    }

    public Iterable<AdvancementNode> roots() {
        return this.roots;
    }

    public Collection<AdvancementNode> nodes() {
        return this.nodes.values();
    }

    public @Nullable AdvancementNode get(Identifier id) {
        return this.nodes.get(id);
    }

    public @Nullable AdvancementNode get(AdvancementHolder advancement) {
        return this.nodes.get(advancement.id());
    }

    public void setListener(AdvancementTree.@Nullable Listener listener) {
        this.listener = listener;
        if (listener != null) {
            for (AdvancementNode advancementNode : this.roots) {
                listener.onAddAdvancementRoot(advancementNode);
            }

            for (AdvancementNode advancementNode : this.tasks) {
                listener.onAddAdvancementTask(advancementNode);
            }
        }
    }

    public interface Listener {
        void onAddAdvancementRoot(AdvancementNode advancement);

        void onRemoveAdvancementRoot(AdvancementNode advancement);

        void onAddAdvancementTask(AdvancementNode advancement);

        void onRemoveAdvancementTask(AdvancementNode advancement);

        void onAdvancementsCleared();
    }
}
