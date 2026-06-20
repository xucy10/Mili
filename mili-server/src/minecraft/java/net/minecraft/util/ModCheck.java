package net.minecraft.util;

import java.util.function.Supplier;
import org.apache.commons.lang3.ObjectUtils;

public record ModCheck(ModCheck.Confidence confidence, String description) {
    public static ModCheck identify(String vanillaBrandName, Supplier<String> brandNameGetter, String side, Class<?> signingClass) {
        String string = brandNameGetter.get();
        if (!vanillaBrandName.equals(string)) {
            return new ModCheck(ModCheck.Confidence.DEFINITELY, side + " brand changed to '" + string + "'");
        } else {
            return signingClass.getSigners() == null
                ? new ModCheck(ModCheck.Confidence.VERY_LIKELY, side + " jar signature invalidated")
                : new ModCheck(ModCheck.Confidence.PROBABLY_NOT, side + " jar signature and brand is untouched");
        }
    }

    public boolean shouldReportAsModified() {
        return this.confidence.shouldReportAsModified;
    }

    public ModCheck merge(ModCheck other) {
        return new ModCheck(ObjectUtils.max(this.confidence, other.confidence), this.description + "; " + other.description);
    }

    public String fullDescription() {
        return this.confidence.description + " " + this.description;
    }

    public static enum Confidence {
        PROBABLY_NOT("Probably not.", false),
        VERY_LIKELY("Very likely;", true),
        DEFINITELY("Definitely;", true);

        final String description;
        final boolean shouldReportAsModified;

        private Confidence(final String description, final boolean shouldReportAsModified) {
            this.description = description;
            this.shouldReportAsModified = shouldReportAsModified;
        }
    }
}
