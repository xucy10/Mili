package net.minecraft.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class DirectoryLock implements AutoCloseable {
    public static final String LOCK_FILE = "session.lock";
    private final FileChannel lockFile;
    private final FileLock lock;
    private static final ByteBuffer DUMMY;

    public static DirectoryLock create(Path path) throws IOException {
        Path path1 = path.resolve("session.lock");
        FileUtil.createDirectoriesSafe(path);
        FileChannel fileChannel = FileChannel.open(path1, StandardOpenOption.CREATE, StandardOpenOption.WRITE);

        try {
            fileChannel.write(DUMMY.duplicate());
            fileChannel.force(true);
            FileLock fileLock = fileChannel.tryLock();
            if (fileLock == null) {
                throw DirectoryLock.LockException.alreadyLocked(path1);
            } else {
                return new DirectoryLock(fileChannel, fileLock);
            }
        } catch (IOException var6) {
            try {
                fileChannel.close();
            } catch (IOException var5) {
                var6.addSuppressed(var5);
            }

            throw var6;
        }
    }

    private DirectoryLock(FileChannel lockFile, FileLock lock) {
        this.lockFile = lockFile;
        this.lock = lock;
    }

    @Override
    public void close() throws IOException {
        try {
            if (this.lock.isValid()) {
                this.lock.release();
            }
        } finally {
            if (this.lockFile.isOpen()) {
                this.lockFile.close();
            }
        }
    }

    public boolean isValid() {
        return this.lock.isValid();
    }

    public static boolean isLocked(Path path) throws IOException {
        Path path1 = path.resolve("session.lock");

        try {
            boolean var4;
            try (
                FileChannel fileChannel = FileChannel.open(path1, StandardOpenOption.WRITE);
                FileLock fileLock = fileChannel.tryLock();
            ) {
                var4 = fileLock == null;
            }

            return var4;
        } catch (AccessDeniedException var10) {
            return true;
        } catch (NoSuchFileException var11) {
            return false;
        }
    }

    static {
        byte[] bytes = "☃".getBytes(StandardCharsets.UTF_8);
        DUMMY = ByteBuffer.allocateDirect(bytes.length);
        DUMMY.put(bytes);
        DUMMY.flip();
    }

    public static class LockException extends IOException {
        private LockException(Path path, String message) {
            super(path.toAbsolutePath() + ": " + message);
        }

        public static DirectoryLock.LockException alreadyLocked(Path path) {
            return new DirectoryLock.LockException(path, "already locked (possibly by other Minecraft instance?)");
        }
    }
}
