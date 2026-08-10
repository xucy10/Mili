package net.minecraft.server.packs;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.util.InclusiveRange;

public record OverlayMetadataSection(List<OverlayMetadataSection.OverlayEntry> overlays) {
    private static final Pattern DIR_VALIDATOR = Pattern.compile("[-_a-zA-Z0-9.]+");
    public static final MetadataSectionType<OverlayMetadataSection> CLIENT_TYPE = new MetadataSectionType<>(
        "overlays", codecForPackType(PackType.CLIENT_RESOURCES)
    );
    public static final MetadataSectionType<OverlayMetadataSection> SERVER_TYPE = new MetadataSectionType<>("overlays", codecForPackType(PackType.SERVER_DATA));

    private static DataResult<String> validateOverlayDir(String directoryName) {
        return !DIR_VALIDATOR.matcher(directoryName).matches()
            ? DataResult.error(() -> directoryName + " is not accepted directory name")
            : DataResult.success(directoryName);
    }

    @VisibleForTesting
    public static Codec<OverlayMetadataSection> codecForPackType(PackType packType) {
        return RecordCodecBuilder.create(
            instance -> instance.group(
                    OverlayMetadataSection.OverlayEntry.listCodecForPackType(packType).fieldOf("entries").forGetter(OverlayMetadataSection::overlays)
                )
                .apply(instance, OverlayMetadataSection::new)
        );
    }

    public static MetadataSectionType<OverlayMetadataSection> forPackType(PackType packType) {
        return switch (packType) {
            case CLIENT_RESOURCES -> CLIENT_TYPE;
            case SERVER_DATA -> SERVER_TYPE;
        };
    }

    public List<String> overlaysForVersion(PackFormat packFormat) {
        return this.overlays.stream().filter(overlay -> overlay.isApplicable(packFormat)).map(OverlayMetadataSection.OverlayEntry::overlay).toList();
    }

    public record OverlayEntry(InclusiveRange<PackFormat> format, String overlay) {
        static Codec<List<OverlayMetadataSection.OverlayEntry>> listCodecForPackType(PackType packType) {
            int i = PackFormat.lastPreMinorVersion(packType);
            return OverlayMetadataSection.OverlayEntry.IntermediateEntry.CODEC
                .listOf()
                .flatXmap(
                    list -> PackFormat.validateHolderList(
                        (List<OverlayMetadataSection.OverlayEntry.IntermediateEntry>)list,
                        i,
                        (intermediateEntry, inclusiveRange) -> new OverlayMetadataSection.OverlayEntry(inclusiveRange, intermediateEntry.overlay())
                    ),
                    list -> DataResult.success(
                        list.stream()
                            .map(
                                overlayEntry -> new OverlayMetadataSection.OverlayEntry.IntermediateEntry(
                                    PackFormat.IntermediaryFormat.fromRange(overlayEntry.format(), i), overlayEntry.overlay()
                                )
                            )
                            .toList()
                    )
                );
        }

        public boolean isApplicable(PackFormat packFormat) {
            return this.format.isValueInRange(packFormat);
        }

        record IntermediateEntry(@Override PackFormat.IntermediaryFormat format, String overlay) implements PackFormat.IntermediaryFormatHolder {
            static final Codec<OverlayMetadataSection.OverlayEntry.IntermediateEntry> CODEC = RecordCodecBuilder.create(
                instance -> instance.group(
                        PackFormat.IntermediaryFormat.OVERLAY_CODEC.forGetter(OverlayMetadataSection.OverlayEntry.IntermediateEntry::format),
                        Codec.STRING
                            .validate(OverlayMetadataSection::validateOverlayDir)
                            .fieldOf("directory")
                            .forGetter(OverlayMetadataSection.OverlayEntry.IntermediateEntry::overlay)
                    )
                    .apply(instance, OverlayMetadataSection.OverlayEntry.IntermediateEntry::new)
            );

            @Override
            public String toString() {
                return this.overlay;
            }
        }
    }
}
