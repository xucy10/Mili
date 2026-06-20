package net.minecraft.util;

import com.google.common.hash.Funnels;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Map;
import java.util.OptionalLong;
import org.apache.commons.io.IOUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class HttpUtil {
    private static final Logger LOGGER = LogUtils.getLogger();

    private HttpUtil() {
    }

    public static Path downloadFile(
        Path saveFile,
        URL url,
        Map<String, String> requestProperties,
        HashFunction hashFunction,
        @Nullable HashCode hash,
        int maxSize,
        Proxy proxy,
        HttpUtil.DownloadProgressListener progressListener
    ) {
        HttpURLConnection httpUrlConnection = null;
        InputStream inputStream = null;
        progressListener.requestStart();
        Path path;
        if (hash != null) {
            path = cachedFilePath(saveFile, hash);

            try {
                if (checkExistingFile(path, hashFunction, hash)) {
                    LOGGER.info("Returning cached file since actual hash matches requested");
                    progressListener.requestFinished(true);
                    updateModificationTime(path);
                    return path;
                }
            } catch (IOException var35) {
                LOGGER.warn("Failed to check cached file {}", path, var35);
            }

            try {
                LOGGER.warn("Existing file {} not found or had mismatched hash", path);
                Files.deleteIfExists(path);
            } catch (IOException var34) {
                progressListener.requestFinished(false);
                throw new UncheckedIOException("Failed to remove existing file " + path, var34);
            }
        } else {
            path = null;
        }

        Path hashCode1;
        try {
            httpUrlConnection = (HttpURLConnection)url.openConnection(proxy);
            httpUrlConnection.setInstanceFollowRedirects(true);
            requestProperties.forEach(httpUrlConnection::setRequestProperty);
            inputStream = httpUrlConnection.getInputStream();
            long contentLengthLong = httpUrlConnection.getContentLengthLong();
            OptionalLong optionalLong = contentLengthLong != -1L ? OptionalLong.of(contentLengthLong) : OptionalLong.empty();
            FileUtil.createDirectoriesSafe(saveFile);
            progressListener.downloadStart(optionalLong);
            if (optionalLong.isPresent() && optionalLong.getAsLong() > maxSize) {
                throw new IOException("Filesize is bigger than maximum allowed (file is " + optionalLong + ", limit is " + maxSize + ")");
            }

            if (path == null) {
                Path path1 = Files.createTempFile(saveFile, "download", ".tmp");

                try {
                    HashCode hashCode1x = downloadAndHash(hashFunction, maxSize, progressListener, inputStream, path1);
                    Path path2 = cachedFilePath(saveFile, hashCode1x);
                    if (!checkExistingFile(path2, hashFunction, hashCode1x)) {
                        Files.move(path1, path2, StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        updateModificationTime(path2);
                    }

                    progressListener.requestFinished(true);
                    return path2;
                } finally {
                    Files.deleteIfExists(path1);
                }
            }

            HashCode hashCode = downloadAndHash(hashFunction, maxSize, progressListener, inputStream, path);
            if (!hashCode.equals(hash)) {
                throw new IOException("Hash of downloaded file (" + hashCode + ") did not match requested (" + hash + ")");
            }

            progressListener.requestFinished(true);
            hashCode1 = path;
        } catch (Throwable var36) {
            if (httpUrlConnection != null) {
                InputStream errorStream = httpUrlConnection.getErrorStream();
                if (errorStream != null) {
                    try {
                        LOGGER.error("HTTP response error: {}", IOUtils.toString(errorStream, StandardCharsets.UTF_8));
                    } catch (Exception var32) {
                        LOGGER.error("Failed to read response from server");
                    }
                }
            }

            progressListener.requestFinished(false);
            throw new IllegalStateException("Failed to download file " + url, var36);
        } finally {
            IOUtils.closeQuietly(inputStream);
        }

        return hashCode1;
    }

    private static void updateModificationTime(Path path) {
        try {
            Files.setLastModifiedTime(path, FileTime.from(Instant.now()));
        } catch (IOException var2) {
            LOGGER.warn("Failed to update modification time of {}", path, var2);
        }
    }

    private static HashCode hashFile(Path path, HashFunction hashFunction) throws IOException {
        Hasher hasher = hashFunction.newHasher();

        try (
            OutputStream outputStream = Funnels.asOutputStream(hasher);
            InputStream inputStream = Files.newInputStream(path);
        ) {
            inputStream.transferTo(outputStream);
        }

        return hasher.hash();
    }

    private static boolean checkExistingFile(Path path, HashFunction hashFunction, HashCode expectedHash) throws IOException {
        if (Files.exists(path)) {
            HashCode hashCode = hashFile(path, hashFunction);
            if (hashCode.equals(expectedHash)) {
                return true;
            }

            LOGGER.warn("Mismatched hash of file {}, expected {} but found {}", path, expectedHash, hashCode);
        }

        return false;
    }

    private static Path cachedFilePath(Path path, HashCode hash) {
        return path.resolve(hash.toString());
    }

    private static HashCode downloadAndHash(
        HashFunction hashFunction, int maxSize, HttpUtil.DownloadProgressListener progressListener, InputStream stream, Path outputPath
    ) throws IOException {
        HashCode var11;
        try (OutputStream outputStream = Files.newOutputStream(outputPath, StandardOpenOption.CREATE)) {
            Hasher hasher = hashFunction.newHasher();
            byte[] bytes = new byte[8196];
            long l = 0L;

            int i;
            while ((i = stream.read(bytes)) >= 0) {
                l += i;
                progressListener.downloadedBytes(l);
                if (l > maxSize) {
                    throw new IOException("Filesize was bigger than maximum allowed (got >= " + l + ", limit was " + maxSize + ")");
                }

                if (Thread.interrupted()) {
                    LOGGER.error("INTERRUPTED");
                    throw new IOException("Download interrupted");
                }

                outputStream.write(bytes, 0, i);
                hasher.putBytes(bytes, 0, i);
            }

            var11 = hasher.hash();
        }

        return var11;
    }

    public static int getAvailablePort() {
        try {
            int var1;
            try (ServerSocket serverSocket = new ServerSocket(0)) {
                var1 = serverSocket.getLocalPort();
            }

            return var1;
        } catch (IOException var5) {
            return 25564;
        }
    }

    public static boolean isPortAvailable(int port) {
        if (port >= 0 && port <= 65535) {
            try {
                boolean var2;
                try (ServerSocket serverSocket = new ServerSocket(port)) {
                    var2 = serverSocket.getLocalPort() == port;
                }

                return var2;
            } catch (IOException var6) {
                return false;
            }
        } else {
            return false;
        }
    }

    public interface DownloadProgressListener {
        void requestStart();

        void downloadStart(OptionalLong totalSize);

        void downloadedBytes(long progress);

        void requestFinished(boolean success);
    }
}
