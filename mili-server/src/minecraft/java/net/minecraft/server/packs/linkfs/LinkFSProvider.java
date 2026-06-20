package net.minecraft.server.packs.linkfs;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.ReadOnlyFileSystemException;
import java.nio.file.StandardOpenOption;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

class LinkFSProvider extends FileSystemProvider {
    public static final String SCHEME = "x-mc-link";

    @Override
    public String getScheme() {
        return "x-mc-link";
    }

    @Override
    public FileSystem newFileSystem(URI uri, Map<String, ?> environment) {
        throw new UnsupportedOperationException();
    }

    @Override
    public FileSystem getFileSystem(URI uri) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Path getPath(URI uri) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attributes) throws IOException {
        if (!options.contains(StandardOpenOption.CREATE_NEW)
            && !options.contains(StandardOpenOption.CREATE)
            && !options.contains(StandardOpenOption.APPEND)
            && !options.contains(StandardOpenOption.WRITE)) {
            Path targetPath = toLinkPath(path).toAbsolutePath().getTargetPath();
            if (targetPath == null) {
                throw new NoSuchFileException(path.toString());
            } else {
                return Files.newByteChannel(targetPath, options, attributes);
            }
        } else {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path directory, final Filter<? super Path> filter) throws IOException {
        final PathContents.DirectoryContents directoryContents = toLinkPath(directory).toAbsolutePath().getDirectoryContents();
        if (directoryContents == null) {
            throw new NotDirectoryException(directory.toString());
        } else {
            return new DirectoryStream<Path>() {
                @Override
                public Iterator<Path> iterator() {
                    return directoryContents.children().values().stream().filter(path -> {
                        try {
                            return filter.accept(path);
                        } catch (IOException var3) {
                            throw new DirectoryIteratorException(var3);
                        }
                    }).map(path -> (Path)path).iterator();
                }

                @Override
                public void close() {
                }
            };
        }
    }

    @Override
    public void createDirectory(Path path, FileAttribute<?>... attributes) {
        throw new ReadOnlyFileSystemException();
    }

    @Override
    public void delete(Path path) {
        throw new ReadOnlyFileSystemException();
    }

    @Override
    public void copy(Path source, Path target, CopyOption... options) {
        throw new ReadOnlyFileSystemException();
    }

    @Override
    public void move(Path source, Path target, CopyOption... options) {
        throw new ReadOnlyFileSystemException();
    }

    @Override
    public boolean isSameFile(Path path, Path path2) {
        return path instanceof LinkFSPath && path2 instanceof LinkFSPath && path.equals(path2);
    }

    @Override
    public boolean isHidden(Path path) {
        return false;
    }

    @Override
    public FileStore getFileStore(Path path) {
        return toLinkPath(path).getFileSystem().store();
    }

    @Override
    public void checkAccess(Path path, AccessMode... modes) throws IOException {
        if (modes.length == 0 && !toLinkPath(path).exists()) {
            throw new NoSuchFileException(path.toString());
        } else {
            AccessMode[] var3 = modes;
            int var4 = modes.length;
            int var5 = 0;

            while (var5 < var4) {
                AccessMode accessMode = var3[var5];
                switch (accessMode) {
                    case READ:
                        if (!toLinkPath(path).exists()) {
                            throw new NoSuchFileException(path.toString());
                        }
                    default:
                        var5++;
                        break;
                    case EXECUTE:
                    case WRITE:
                        throw new AccessDeniedException(accessMode.toString());
                }
            }
        }
    }

    @Override
    public <V extends FileAttributeView> @Nullable V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
        LinkFSPath linkFsPath = toLinkPath(path);
        return (V)(type == BasicFileAttributeView.class ? linkFsPath.getBasicAttributeView() : null);
    }

    @Override
    public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options) throws IOException {
        LinkFSPath linkFsPath = toLinkPath(path).toAbsolutePath();
        if (type == BasicFileAttributes.class) {
            return (A)linkFsPath.getBasicAttributes();
        } else {
            throw new UnsupportedOperationException("Attributes of type " + type.getName() + " not supported");
        }
    }

    @Override
    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setAttribute(Path path, String attribute, Object value, LinkOption... options) {
        throw new ReadOnlyFileSystemException();
    }

    private static LinkFSPath toLinkPath(@Nullable Path path) {
        if (path == null) {
            throw new NullPointerException();
        } else if (path instanceof LinkFSPath linkFsPath) {
            return linkFsPath;
        } else {
            throw new ProviderMismatchException();
        }
    }
}
