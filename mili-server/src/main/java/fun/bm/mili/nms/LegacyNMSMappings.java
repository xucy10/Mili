package fun.bm.mili.nms;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.framework.qual.DefaultQualifier;

/**
 * Maintains a small lookup table of legacy NMS class names that were renamed
 * across Minecraft major versions and therefore are missing from the official
 * spigot→mojang mapping consumed by {@code io.papermc.paper.util.ObfHelper}.
 *
 * <p>Paper's {@code PaperReflection#mapClassName} consults
 * {@code ObfHelper} which only knows about the current spigot (obfuscated) →
 * mojang (public) name pairs. When a plugin still uses an older spigot name
 * that has been renamed in a newer Minecraft version, reflection lookups for
 * the legacy name fail with {@link ClassNotFoundException} because the obf
 * name no longer exists in the live server jar.</p>
 *
 * <p>This class is consulted by the Mili-injected shim in
 * {@code PaperReflection#mapClassName} (see
 * {@code mili-server/paper-patches/features/0XXX-Mili-Add-legacy-NMS-class-remapping.patch}).
 * Adding entries here makes the rewriter transparently redirect the old name
 * to its current mojang equivalent before falling back to the original
 * Paper lookup path.</p>
 *
 * <h2>Limitations</h2>
 * <ul>
 *     <li>This only handles <em>reflective</em> lookups (i.e. {@code Class.forName}
 *         calls rewritten by {@code io.papermc:reflection-rewriter}). Direct
 *         class symbol references baked into a plugin's bytecode (for example
 *         a {@code V1_21_11} implementation class that hard-codes an
 *         {@code extends DataWatcherObject} or similar) cannot be remapped
 *         here &mdash; those are resolved by the JVM at class link time and
 *         the rewriter proxy never sees them. Affected plugins must be
 *         updated by their authors.</li>
 *     <li>Map keys must use the exact fully qualified dotted class name
 *         passed to {@code Class.forName} by the plugin (the "legacy
 *         spigot / pre-rename" form). Values must be the current mojang
 *         public name as it exists in the live jar.</li>
 * </ul>
 */
@DefaultQualifier(NonNull.class)
public final class LegacyNMSMappings {

    private LegacyNMSMappings() {
    }

    /**
     * Hard-coded legacy → current NMS class name pairs.
     *
     * <p>Entries are intentionally limited to a small curated set of common
     * renames that have been observed in the wild and which the upstream
     * Paper reflection-rewriter does not already cover. When adding new
     * entries, prefer the {@code net.minecraft.server.XXXServer} (server-side)
     * and {@code net.minecraft.network.syncher.XXX} (network syncher)
     * families first, since those are the namespaces most likely to be
     * reached by reflection from third-party plugins.</p>
     */
    private static final Map<String, String> DEFAULT_MAPPINGS = buildDefaults();

    private static Map<String, String> buildDefaults() {
        final Map<String, String> map = new HashMap<>();
        // CustomCrops 3.6.x and other plugins still resolve the pre-1.20.5
        // spigot name for the scoreboard server-side class.
        // 1.20.5+ uses the mojang public name ServerScoreboard.
        map.put("net.minecraft.server.ScoreboardServer", "net.minecraft.server.ServerScoreboard");
        return Collections.unmodifiableMap(map);
    }

    /**
     * Returns the unmodifiable view of the legacy → current name map.
     * The returned map is shared across threads; do not mutate.
     */
    public static Map<String, String> getMappings() {
        return DEFAULT_MAPPINGS;
    }

    /**
     * Resolves a legacy class name to its current equivalent, or returns
     * {@code name} unchanged if no entry is present.
     */
    public static String mapClassName(final String name) {
        final String mapped = DEFAULT_MAPPINGS.get(name);
        return mapped != null ? mapped : name;
    }
}
