package net.minecraft.server.packs.linkfs;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.ReadOnlyFileSystemException;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchEvent.Modifier;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

class LinkFSPath implements Path {
    private static final BasicFileAttributes DIRECTORY_ATTRIBUTES = new DummyFileAttributes() {
        @Override
        public boolean isRegularFile() {
            return false;
        }

        @Override
        public boolean isDirectory() {
            return true;
        }
    };
    private static final BasicFileAttributes FILE_ATTRIBUTES = new DummyFileAttributes() {
        @Override
        public boolean isRegularFile() {
            return true;
        }

        @Override
        public boolean isDirectory() {
            return false;
        }
    };
    private static final Comparator<LinkFSPath> PATH_COMPARATOR = Comparator.comparing(LinkFSPath::pathToString);
    private final String name;
    private final LinkFileSystem fileSystem;
    private final @Nullable LinkFSPath parent;
    private @Nullable List<String> pathToRoot;
    private @Nullable String pathString;
    private final PathContents pathContents;

    public LinkFSPath(LinkFileSystem fileSystem, String name, @Nullable LinkFSPath parent, PathContents pathContents) {
        this.fileSystem = fileSystem;
        this.name = name;
        this.parent = parent;
        this.pathContents = pathContents;
    }

    private LinkFSPath createRelativePath(@Nullable LinkFSPath parent, String name) {
        return new LinkFSPath(this.fileSystem, name, parent, PathContents.RELATIVE);
    }

    @Override
    public LinkFileSystem getFileSystem() {
        return this.fileSystem;
    }

    @Override
    public boolean isAbsolute() {
        return this.pathContents != PathContents.RELATIVE;
    }

    @Override
    public File toFile() {
        if (this.pathContents instanceof PathContents.FileContents fileContents) {
            return fileContents.contents().toFile();
        } else {
            throw new UnsupportedOperationException("Path " + this.pathToString() + " does not represent file");
        }
    }

    @Override
    public @Nullable LinkFSPath getRoot() {
        return this.isAbsolute() ? this.fileSystem.rootPath() : null;
    }

    @Override
    public LinkFSPath getFileName() {
        return this.createRelativePath(null, this.name);
    }

    @Override
    public @Nullable LinkFSPath getParent() {
        return this.parent;
    }

    @Override
    public int getNameCount() {
        return this.pathToRoot().size();
    }

    private List<String> pathToRoot() {
        if (this.name.isEmpty()) {
            return List.of();
        } else {
            if (this.pathToRoot == null) {
                Builder<String> builder = ImmutableList.builder();
                if (this.parent != null) {
                    builder.addAll(this.parent.pathToRoot());
                }

                builder.add(this.name);
                this.pathToRoot = builder.build();
            }

            return this.pathToRoot;
        }
    }

    @Override
    public LinkFSPath getName(int index) {
        List<String> list = this.pathToRoot();
        if (index >= 0 && index < list.size()) {
            return this.createRelativePath(null, list.get(index));
        } else {
            throw new IllegalArgumentException("Invalid index: " + index);
        }
    }

    @Override
    public LinkFSPath subpath(int start, int end) {
        List<String> list = this.pathToRoot();
        if (start >= 0 && end <= list.size() && start < end) {
            LinkFSPath linkFsPath = null;

            for (int i = start; i < end; i++) {
                linkFsPath = this.createRelativePath(linkFsPath, list.get(i));
            }

            return linkFsPath;
        } else {
            throw new IllegalArgumentException();
        }
    }

    @Override
    public boolean startsWith(Path path) {
        if (path.isAbsolute() != this.isAbsolute()) {
            return false;
        } else if (path instanceof LinkFSPath linkFsPath) {
            if (linkFsPath.fileSystem != this.fileSystem) {
                return false;
            } else {
                List<String> list = this.pathToRoot();
                List<String> list1 = linkFsPath.pathToRoot();
                int size = list1.size();
                if (size > list.size()) {
                    return false;
                } else {
                    for (int i = 0; i < size; i++) {
                        if (!list1.get(i).equals(list.get(i))) {
                            return false;
                        }
                    }

                    return true;
                }
            }
        } else {
            return false;
        }
    }

    @Override
    public boolean endsWith(Path path) {
        if (path.isAbsolute() && !this.isAbsolute()) {
            return false;
        } else if (path instanceof LinkFSPath linkFsPath) {
            if (linkFsPath.fileSystem != this.fileSystem) {
                return false;
            } else {
                List<String> list = this.pathToRoot();
                List<String> list1 = linkFsPath.pathToRoot();
                int size = list1.size();
                int i = list.size() - size;
                if (i < 0) {
                    return false;
                } else {
                    for (int i1 = size - 1; i1 >= 0; i1--) {
                        if (!list1.get(i1).equals(list.get(i + i1))) {
                            return false;
                        }
                    }

                    return true;
                }
            }
        } else {
            return false;
        }
    }

    @Override
    public LinkFSPath normalize() {
        return this;
    }

    @Override
    public LinkFSPath resolve(Path path) {
        LinkFSPath linkFsPath = this.toLinkPath(path);
        return path.isAbsolute() ? linkFsPath : this.resolve(linkFsPath.pathToRoot());
    }

    private LinkFSPath resolve(List<String> names) {
        LinkFSPath linkFsPath = this;

        for (String string : names) {
            linkFsPath = linkFsPath.resolveName(string);
        }

        return linkFsPath;
    }

    LinkFSPath resolveName(String name) {
        if (isRelativeOrMissing(this.pathContents)) {
            return new LinkFSPath(this.fileSystem, name, this, this.pathContents);
        } else if (this.pathContents instanceof PathContents.DirectoryContents directoryContents) {
            LinkFSPath linkFsPath = directoryContents.children().get(name);
            return linkFsPath != null ? linkFsPath : new LinkFSPath(this.fileSystem, name, this, PathContents.MISSING);
        } else if (this.pathContents instanceof PathContents.FileContents) {
            return new LinkFSPath(this.fileSystem, name, this, PathContents.MISSING);
        } else {
            throw new AssertionError("All content types should be already handled");
        }
    }

    private static boolean isRelativeOrMissing(PathContents pathContents) {
        return pathContents == PathContents.MISSING || pathContents == PathContents.RELATIVE;
    }

    @Override
    public LinkFSPath relativize(Path path) {
        LinkFSPath linkFsPath = this.toLinkPath(path);
        if (this.isAbsolute() != linkFsPath.isAbsolute()) {
            throw new IllegalArgumentException("absolute mismatch");
        } else {
            List<String> list = this.pathToRoot();
            List<String> list1 = linkFsPath.pathToRoot();
            if (list.size() >= list1.size()) {
                throw new IllegalArgumentException();
            } else {
                for (int i = 0; i < list.size(); i++) {
                    if (!list.get(i).equals(list1.get(i))) {
                        throw new IllegalArgumentException();
                    }
                }

                return linkFsPath.subpath(list.size(), list1.size());
            }
        }
    }

    @Override
    public URI toUri() {
        try {
            return new URI("x-mc-link", this.fileSystem.store().name(), this.pathToString(), null);
        } catch (URISyntaxException var2) {
            throw new AssertionError("Failed to create URI", var2);
        }
    }

    @Override
    public LinkFSPath toAbsolutePath() {
        return this.isAbsolute() ? this : this.fileSystem.rootPath().resolve(this);
    }

    @Override
    public LinkFSPath toRealPath(LinkOption... options) {
        return this.toAbsolutePath();
    }

    @Override
    public WatchKey register(WatchService watcher, Kind<?>[] events, Modifier... modifiers) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int compareTo(Path other) {
        LinkFSPath linkFsPath = this.toLinkPath(other);
        return PATH_COMPARATOR.compare(this, linkFsPath);
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        } else if (other instanceof LinkFSPath linkFsPath) {
            if (this.fileSystem != linkFsPath.fileSystem) {
                return false;
            } else {
                boolean hasRealContents = this.hasRealContents();
                if (hasRealContents != linkFsPath.hasRealContents()) {
                    return false;
                } else {
                    return hasRealContents
                        ? this.pathContents == linkFsPath.pathContents
                        : Objects.equals(this.parent, linkFsPath.parent) && Objects.equals(this.name, linkFsPath.name);
                }
            }
        } else {
            return false;
        }
    }

    private boolean hasRealContents() {
        return !isRelativeOrMissing(this.pathContents);
    }

    @Override
    public int hashCode() {
        return this.hasRealContents() ? this.pathContents.hashCode() : this.name.hashCode();
    }

    @Override
    public String toString() {
        return this.pathToString();
    }

    private String pathToString() {
        if (this.pathString == null) {
            StringBuilder stringBuilder = new StringBuilder();
            if (this.isAbsolute()) {
                stringBuilder.append("/");
            }

            Joiner.on("/").appendTo(stringBuilder, this.pathToRoot());
            this.pathString = stringBuilder.toString();
        }

        return this.pathString;
    }

    private LinkFSPath toLinkPath(@Nullable Path path) {
        if (path == null) {
            throw new NullPointerException();
        } else if (path instanceof LinkFSPath linkFsPath && linkFsPath.fileSystem == this.fileSystem) {
            return linkFsPath;
        } else {
            throw new ProviderMismatchException();
        }
    }

    public boolean exists() {
        return this.hasRealContents();
    }

    public @Nullable Path getTargetPath() {
        return this.pathContents instanceof PathContents.FileContents fileContents ? fileContents.contents() : null;
    }

    public PathContents.@Nullable DirectoryContents getDirectoryContents() {
        return this.pathContents instanceof PathContents.DirectoryContents directoryContents ? directoryContents : null;
    }

    public BasicFileAttributeView getBasicAttributeView() {
        return new BasicFileAttributeView() {
            @Override
            public String name() {
                return "basic";
            }

            @Override
            public BasicFileAttributes readAttributes() throws IOException {
                return LinkFSPath.this.getBasicAttributes();
            }

            @Override
            public void setTimes(FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime) {
                throw new ReadOnlyFileSystemException();
            }
        };
    }

    public BasicFileAttributes getBasicAttributes() throws IOException {
        if (this.pathContents instanceof PathContents.DirectoryContents) {
            return DIRECTORY_ATTRIBUTES;
        } else if (this.pathContents instanceof PathContents.FileContents) {
            return FILE_ATTRIBUTES;
        } else {
            throw new NoSuchFileException(this.pathToString());
        }
    }
}
