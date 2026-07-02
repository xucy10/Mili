package org.mili.rust;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class RustOptimizer {
    private static final String PREFIX = "rust-opt:";

    private RustOptimizer() {}

    public static String dedup(String input) {
        return executeRustCommand("dedup", input, RustOptimizer::fallbackDedup);
    }

    public static String hash(String input) {
        return executeRustCommand("hash", input, RustOptimizer::fallbackHash);
    }

    public static long mergePacketCost(List<Long> sizes) {
        String payload = sizes.stream().map(String::valueOf).collect(Collectors.joining(","));
        String result = executeRustCommand("merge-cost", payload, RustOptimizer::fallbackMergeCost);
        if (result.startsWith("merge-cost:")) {
            return Long.parseLong(result.substring("merge-cost:".length()));
        }
        throw new IllegalStateException("Unexpected result from Rust optimizer: " + result);
    }

    public static long packetSize(String input) {
        String result = executeRustCommand("packet-size", input, RustOptimizer::fallbackPacketSize);
        if (result.startsWith("packet-size:")) {
            return Long.parseLong(result.substring("packet-size:".length()));
        }
        throw new IllegalStateException("Unexpected result from Rust optimizer: " + result);
    }

    public static String scheduler(int jobCount) {
        return executeRustCommand("scheduler", String.valueOf(jobCount), input -> "scheduler:1:1:0");
    }

    private static String executeRustCommand(String command, String input, Fallback fallback) {
        if (input == null) {
            input = "";
        }

        Path binary = locateBinary();
        if (binary == null) {
            return fallback.apply(input);
        }

        try {
            Process process = new ProcessBuilder(binary.toString(), command, input)
                .redirectErrorStream(true)
                .start();
            String output;
            try (InputStream stream = process.getInputStream()) {
                output = new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException("Rust optimizer exited with code " + exitCode + ": " + output);
            }
            if (output.startsWith(PREFIX)) {
                return output.substring(PREFIX.length());
            }
            return output;
        } catch (IOException | InterruptedException ex) {
            Thread.currentThread().interrupt();
            return fallback.apply(input);
        }
    }

    private static Path locateBinary() {
        String path = System.getProperty("mili.rust.binary");
        if (path != null && !path.isBlank()) {
            return Path.of(path);
        }

        Path candidate = Path.of("build/rust/optimizer");
        if (Files.exists(candidate)) {
            return candidate;
        }

        candidate = Path.of("build/rust/optimizer.exe");
        if (Files.exists(candidate)) {
            return candidate;
        }

        return null;
    }

    private static String fallbackDedup(String input) {
        return input.chars()
            .distinct()
            .sorted()
            .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
            .toString();
    }

    private static String fallbackHash(String input) {
        long hash = 0xcbf29ce484222325L;
        for (byte b : input.getBytes(StandardCharsets.UTF_8)) {
            hash ^= (b & 0xFF);
            hash *= 0x100000001b3L;
        }
        return String.format("hash:%016x", hash);
    }

    private static String fallbackMergeCost(String input) {
        List<Long> sizes = List.of(input.split("[;,| ]+"))
            .stream()
            .filter(s -> !s.isBlank())
            .map(Long::parseLong)
            .collect(Collectors.toList());

        if (sizes.size() < 2) {
            return "merge-cost:0";
        }

        sizes.sort(Long::compareTo);
        long cost = 0L;
        while (sizes.size() > 1) {
            long first = sizes.remove(0);
            long second = sizes.remove(0);
            long merged = first + second;
            cost += merged;
            int index = 0;
            while (index < sizes.size() && sizes.get(index) < merged) {
                index++;
            }
            sizes.add(index, merged);
        }
        return "merge-cost:" + cost;
    }

    private static String fallbackPacketSize(String input) {
        long total = 0L;
        for (String token : input.split("[;,| ]+")) {
            if (!token.isBlank()) {
                total += Long.parseLong(token);
            }
        }
        return "packet-size:" + total;
    }

    private interface Fallback {
        String apply(String input);
    }
}
