package net.minecraft.util;

import com.mojang.serialization.DataResult;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.SharedConstants;
import org.apache.commons.io.FilenameUtils;

public class FileUtil {
    private static final Pattern COPY_COUNTER_PATTERN = Pattern.compile("(<name>.*) \\((<count>\\d*)\\)", 66);
    private static final int MAX_FILE_NAME = 255;
    private static final Pattern RESERVED_WINDOWS_FILENAMES = Pattern.compile(".*\\.|(?:COM|CLOCK\\$|CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?", 2);
    private static final Pattern STRICT_PATH_SEGMENT_CHECK = Pattern.compile("[-._a-z0-9]+");

    public static String sanitizeName(String name) {
        for (char c : SharedConstants.ILLEGAL_FILE_CHARACTERS) {
            name = name.replace(c, '_');
        }

        return name.replaceAll("[./\"]", "_");
    }

    public static String findAvailableName(Path dirPath, String fileName, String fileFormat) throws IOException {
        fileName = sanitizeName(fileName);
        if (!isPathPartPortable(fileName)) {
            fileName = "_" + fileName + "_";
        }

        Matcher matcher = COPY_COUNTER_PATTERN.matcher(fileName);
        int i = 0;
        if (matcher.matches()) {
            fileName = matcher.group("name");
            i = Integer.parseInt(matcher.group("count"));
        }

        if (fileName.length() > 255 - fileFormat.length()) {
            fileName = fileName.substring(0, 255 - fileFormat.length());
        }

        while (true) {
            String string = fileName;
            if (i != 0) {
                String string1 = " (" + i + ")";
                int i1 = 255 - string1.length();
                if (fileName.length() > i1) {
                    string = fileName.substring(0, i1);
                }

                string = string + string1;
            }

            string = string + fileFormat;
            Path path = dirPath.resolve(string);

            try {
                Path path1 = Files.createDirectory(path);
                Files.deleteIfExists(path1);
                return dirPath.relativize(path1).toString();
            } catch (FileAlreadyExistsException var8) {
                i++;
            }
        }
    }

    public static boolean isPathNormalized(Path path) {
        Path path1 = path.normalize();
        return path1.equals(path);
    }

    public static boolean isPathPortable(Path path) {
        for (Path path1 : path) {
            if (!isPathPartPortable(path1.toString())) {
                return false;
            }
        }

        return true;
    }

    public static boolean isPathPartPortable(String path) {
        return !RESERVED_WINDOWS_FILENAMES.matcher(path).matches();
    }

    public static Path createPathToResource(Path dirPath, String locationPath, String fileFormat) {
        String string = locationPath + fileFormat;
        Path path = Paths.get(string);
        if (path.endsWith(fileFormat)) {
            throw new InvalidPathException(string, "empty resource name");
        } else {
            return dirPath.resolve(path);
        }
    }

    public static String getFullResourcePath(String path) {
        return FilenameUtils.getFullPath(path).replace(File.separator, "/");
    }

    public static String normalizeResourcePath(String path) {
        return FilenameUtils.normalize(path).replace(File.separator, "/");
    }

    public static DataResult<List<String>> decomposePath(String path) {
        int index = path.indexOf(47);
        if (index == -1) {
            return switch (path) {
                case "", ".", ".." -> DataResult.error(() -> "Invalid path '" + path + "'");
                default -> !containsAllowedCharactersOnly(path) ? DataResult.error(() -> "Invalid path '" + path + "'") : DataResult.success(List.of(path));
            };
        } else {
            List<String> list = new ArrayList<>();
            int i = 0;
            boolean flag = false;

            while (true) {
                String sub = path.substring(i, index);
                switch (sub) {
                    case "":
                    case ".":
                    case "..":
                        return DataResult.error(() -> "Invalid segment '" + sub + "' in path '" + path + "'");
                }

                if (!containsAllowedCharactersOnly(sub)) {
                    return DataResult.error(() -> "Invalid segment '" + sub + "' in path '" + path + "'");
                }

                list.add(sub);
                if (flag) {
                    return DataResult.success(list);
                }

                i = index + 1;
                index = path.indexOf(47, i);
                if (index == -1) {
                    index = path.length();
                    flag = true;
                }
            }
        }
    }

    public static Path resolvePath(Path path, List<String> subdirectories) {
        int size = subdirectories.size();

        return switch (size) {
            case 0 -> path;
            case 1 -> path.resolve(subdirectories.get(0));
            default -> {
                String[] strings = new String[size - 1];

                for (int i = 1; i < size; i++) {
                    strings[i - 1] = subdirectories.get(i);
                }

                yield path.resolve(path.getFileSystem().getPath(subdirectories.get(0), strings));
            }
        };
    }

    private static boolean containsAllowedCharactersOnly(String segment) {
        return STRICT_PATH_SEGMENT_CHECK.matcher(segment).matches();
    }

    public static boolean isValidPathSegment(String segment) {
        return !segment.equals("..") && !segment.equals(".") && containsAllowedCharactersOnly(segment);
    }

    public static void validatePath(String... elements) {
        if (elements.length == 0) {
            throw new IllegalArgumentException("Path must have at least one element");
        } else {
            for (String string : elements) {
                if (!isValidPathSegment(string)) {
                    throw new IllegalArgumentException("Illegal segment " + string + " in path " + Arrays.toString((Object[])elements));
                }
            }
        }
    }

    public static void createDirectoriesSafe(Path path) throws IOException {
        Files.createDirectories(Files.exists(path) ? path.toRealPath() : path);
    }
}
