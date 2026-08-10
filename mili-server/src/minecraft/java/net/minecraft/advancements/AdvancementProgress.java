package net.minecraft.advancements;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public class AdvancementProgress implements Comparable<AdvancementProgress> {
    private static final DateTimeFormatter OBTAINED_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT);
    private static final Codec<Instant> OBTAINED_TIME_CODEC = ExtraCodecs.temporalCodec(OBTAINED_TIME_FORMAT)
        .xmap(Instant::from, instant -> instant.atZone(ZoneId.systemDefault()));
    private static final Codec<Map<String, CriterionProgress>> CRITERIA_CODEC = Codec.unboundedMap(Codec.STRING, OBTAINED_TIME_CODEC)
        .xmap(
            map -> Util.mapValues((Map<String, Instant>)map, CriterionProgress::new),
            map -> map.entrySet()
                .stream()
                .filter(entry -> entry.getValue().isDone())
                .collect(Collectors.toMap(Entry::getKey, entry -> Objects.requireNonNull(entry.getValue().getObtained())))
        );
    public static final Codec<AdvancementProgress> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                CRITERIA_CODEC.optionalFieldOf("criteria", Map.of()).forGetter(advancementProgress -> advancementProgress.criteria),
                Codec.BOOL.fieldOf("done").orElse(true).forGetter(AdvancementProgress::isDone)
            )
            .apply(instance, (map, _boolean) -> new AdvancementProgress(new HashMap<>(map)))
    );
    private final Map<String, CriterionProgress> criteria;
    private AdvancementRequirements requirements = AdvancementRequirements.EMPTY;

    private AdvancementProgress(Map<String, CriterionProgress> criteria) {
        this.criteria = criteria;
    }

    public AdvancementProgress() {
        this.criteria = Maps.newHashMap();
    }

    public void update(AdvancementRequirements requirements) {
        Set<String> set = requirements.names();
        this.criteria.entrySet().removeIf(entry -> !set.contains(entry.getKey()));

        for (String string : set) {
            this.criteria.putIfAbsent(string, new CriterionProgress());
        }

        this.requirements = requirements;
    }

    public boolean isDone() {
        return this.requirements.test(this::isCriterionDone);
    }

    public boolean hasProgress() {
        for (CriterionProgress criterionProgress : this.criteria.values()) {
            if (criterionProgress.isDone()) {
                return true;
            }
        }

        return false;
    }

    public boolean grantProgress(String criterionName) {
        CriterionProgress criterionProgress = this.criteria.get(criterionName);
        if (criterionProgress != null && !criterionProgress.isDone()) {
            criterionProgress.grant();
            return true;
        } else {
            return false;
        }
    }

    public boolean revokeProgress(String criterionName) {
        CriterionProgress criterionProgress = this.criteria.get(criterionName);
        if (criterionProgress != null && criterionProgress.isDone()) {
            criterionProgress.revoke();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return "AdvancementProgress{criteria=" + this.criteria + ", requirements=" + this.requirements + "}";
    }

    public void serializeToNetwork(FriendlyByteBuf buffer) {
        buffer.writeMap(this.criteria, FriendlyByteBuf::writeUtf, (buffer1, value) -> value.serializeToNetwork(buffer1));
    }

    public static AdvancementProgress fromNetwork(FriendlyByteBuf buffer) {
        Map<String, CriterionProgress> map = buffer.readMap(FriendlyByteBuf::readUtf, CriterionProgress::fromNetwork);
        return new AdvancementProgress(map);
    }

    public @Nullable CriterionProgress getCriterion(String criterionName) {
        return this.criteria.get(criterionName);
    }

    private boolean isCriterionDone(String criterionName) {
        CriterionProgress criterion = this.getCriterion(criterionName);
        return criterion != null && criterion.isDone();
    }

    public float getPercent() {
        if (this.criteria.isEmpty()) {
            return 0.0F;
        } else {
            float f = this.requirements.size();
            float f1 = this.countCompletedRequirements();
            return f1 / f;
        }
    }

    public @Nullable Component getProgressText() {
        if (this.criteria.isEmpty()) {
            return null;
        } else {
            int size = this.requirements.size();
            if (size <= 1) {
                return null;
            } else {
                int i = this.countCompletedRequirements();
                return Component.translatable("advancements.progress", i, size);
            }
        }
    }

    private int countCompletedRequirements() {
        return this.requirements.count(this::isCriterionDone);
    }

    public Iterable<String> getRemainingCriteria() {
        List<String> list = Lists.newArrayList();

        for (Entry<String, CriterionProgress> entry : this.criteria.entrySet()) {
            if (!entry.getValue().isDone()) {
                list.add(entry.getKey());
            }
        }

        return list;
    }

    public Iterable<String> getCompletedCriteria() {
        List<String> list = Lists.newArrayList();

        for (Entry<String, CriterionProgress> entry : this.criteria.entrySet()) {
            if (entry.getValue().isDone()) {
                list.add(entry.getKey());
            }
        }

        return list;
    }

    public @Nullable Instant getFirstProgressDate() {
        return this.criteria.values().stream().map(CriterionProgress::getObtained).filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
    }

    @Override
    public int compareTo(AdvancementProgress other) {
        Instant firstProgressDate = this.getFirstProgressDate();
        Instant firstProgressDate1 = other.getFirstProgressDate();
        if (firstProgressDate == null && firstProgressDate1 != null) {
            return 1;
        } else if (firstProgressDate != null && firstProgressDate1 == null) {
            return -1;
        } else {
            return firstProgressDate == null && firstProgressDate1 == null ? 0 : firstProgressDate.compareTo(firstProgressDate1);
        }
    }
}
