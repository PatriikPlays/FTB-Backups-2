package net.creeperhost.ftbbackups.utils;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import net.creeperhost.ftbbackups.FTBBackups;
import net.creeperhost.ftbbackups.config.Config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class FileUtils {

    public static final long KB = 1024L;
    public static final long MB = KB * 1024L;
    public static final long GB = MB * 1024L;
    public static final long TB = GB * 1024L;

    public static final double KB_D = 1024D;
    public static final double MB_D = KB_D * 1024D;
    public static final double GB_D = MB_D * 1024D;
    public static final double TB_D = GB_D * 1024D;

    public static void copy(Path outputDirectory, Path serverRoot, Iterable<Path> sourcePaths) throws IOException {
        Path dir = Files.createDirectory(outputDirectory);

        for (Path sourcePath : sourcePaths) {
            try (Stream<Path> pathStream = Files.walk(sourcePath)) {
                for (Path path : (Iterable<Path>) pathStream::iterator) {
                    if (Files.isDirectory(path)) continue;
                    Path relFile = serverRoot.relativize(path);
                    if (excludeFile(relFile)) continue;
                    Path destFile = dir.resolve(relFile);
                    Files.createDirectories(destFile.getParent());
                    Files.copy(path, destFile);
                }
            }
        }
    }

    public static void zip(Path zipFilePath, Path serverRoot, Iterable<Path> sourcePaths) throws IOException {
        Path p = Files.createFile(zipFilePath);
        try (ZipOutputStream zs = new ZipOutputStream(Files.newOutputStream(p))) {
            for (Path sourcePath : sourcePaths) {
                try (Stream<Path> pathStream = Files.walk(sourcePath)) {
                    for (Path path : (Iterable<Path>) pathStream::iterator) {
                        if (Files.isDirectory(path)) continue;
                        Path relFile = serverRoot.relativize(path);
                        if (excludeFile(relFile)) continue;
                        packIntoZip(zs, serverRoot, path);
                    }
                }
            }
        }
    }

    private static void packIntoZip(ZipOutputStream zos, Path rootDir, Path file) throws IOException {
        // Don't pack session.lock files
        if (file.getFileName().toString().equals("session.lock")) return;
        // Don't try and copy a file that does not exist
        if (!file.toFile().exists()) return;
        // Ensure files are readable
        if (!Files.isReadable(file)) return;

        ZipEntry zipEntry = new ZipEntry(rootDir.relativize(file).toString());
        zos.putNextEntry(zipEntry);
        updateZipEntry(zipEntry, file);
        try {
            Files.copy(file, zos);
        } catch (Exception ignored) { }
        zos.closeEntry();
    }

    public static void updateZipEntry(ZipEntry zipEntry, Path path) {
        try {
            BasicFileAttributes basicFileAttributes = Files.readAttributes(path, BasicFileAttributes.class);
            zipEntry.setLastModifiedTime(basicFileAttributes.lastModifiedTime());
            zipEntry.setCreationTime(basicFileAttributes.creationTime());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean isChildOf(Path path, Path parent) {
        if (path == null) return false;
        if (path.equals(parent)) return true;

        return isChildOf(path.getParent(), parent);
    }

    public static String getFileSha1(Path path) {
        try {
            HashCode sha1HashCode = com.google.common.io.Files.asByteSource(path.toFile()).hash(Hashing.sha1());
            return sha1HashCode.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return "";
    }

    public static String getDirectorySha1(Path directory) {
        try {
            Hasher hasher = Hashing.sha1().newHasher();
            try (Stream<Path> pathStream = Files.walk(directory)) {
                for (Path path : (Iterable<Path>) pathStream::iterator) {
                    if (Files.isDirectory(path)) continue;
                    HashCode hash = com.google.common.io.Files.asByteSource(path.toFile()).hash(Hashing.sha1());
                    hasher.putBytes(hash.asBytes());
                }
            }
            return hasher.hash().toString();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return "";
    }

    public static long getFolderSize(File folder) {
        long length = 0;
        File[] files = folder.listFiles();
        if (files == null) {
            FTBBackups.LOGGER.warn("Attempt to get folder size for invalid folder: {}", folder.getAbsolutePath());
            if (folder.isFile()) {
                return folder.length();
            } else {
                return 0;
            }
        }

        int count = files.length;

        for (int i = 0; i < count; i++) {
            if (files[i].isFile()) {
                length += files[i].length();
            } else {
                length += getFolderSize(files[i]);
            }
        }
        return length;
    }

//    public static void createTarGzipFolder(Path source, Path out) throws IOException
//    {
//        GzipParameters gzipParameters = new GzipParameters();
//        gzipParameters.setCompressionLevel(9);
//
//        try (OutputStream fOut = Files.newOutputStream(out); BufferedOutputStream buffOut = new BufferedOutputStream(fOut);
//             GzipCompressorOutputStream gzOut = new GzipCompressorOutputStream(buffOut, gzipParameters); TarArchiveOutputStream tOut = new TarArchiveOutputStream(gzOut))
//        {
//            Files.walkFileTree(source, new SimpleFileVisitor<>()
//            {
//                @Override
//                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
//                {
//                    // only copy files, no symbolic links
//                    if (attributes.isSymbolicLink())
//                    {
//                        return FileVisitResult.CONTINUE;
//                    }
//                    Path targetFile = source.relativize(file);
//                    try
//                    {
//                        if(!file.toFile().getName().equals("session.lock") && file.toFile().canRead())
//                        {
//                            TarArchiveEntry tarEntry = new TarArchiveEntry(file.toFile(), targetFile.toString());
//                            tOut.putArchiveEntry(tarEntry);
//                            Files.copy(file, tOut);
//                            tOut.closeArchiveEntry();
//                        }
//                    } catch (IOException e)
//                    {
//                        e.printStackTrace();
//                    }
//                    return FileVisitResult.CONTINUE;
//                }
//
//                @Override
//                public FileVisitResult visitFileFailed(Path file, IOException exc) {
//                    System.err.printf("Unable to tar.gz : %s%n%s%n", file, exc);
//                    return FileVisitResult.CONTINUE;
//                }
//            });
//            tOut.finish();
//        }
//    }

    public static String getSizeString(double b) {
        if (b >= TB_D) {
            return String.format("%.1fTB", b / TB_D);
        } else if (b >= GB_D) {
            return String.format("%.1fGB", b / GB_D);
        } else if (b >= MB_D) {
            return String.format("%.1fMB", b / MB_D);
        } else if (b >= KB_D) {
            return String.format("%.1fKB", b / KB_D);
        }

        return ((long) b) + "B";
    }

    public static String getSizeString(Path path) {
        return getSizeString(getSize(path.toFile()));
    }

    public static String getSizeString(File file) {
        return getSizeString(getSize(file));
    }

    public static long getSize(File file) {
        if (!file.exists()) {
            return 0L;
        } else if (file.isFile()) {
            return file.length();
        } else if (file.isDirectory()) {
            long length = 0L;
            File[] f1 = file.listFiles();
            if (f1 != null && f1.length > 0) {
                for (File aF1 : f1) {
                    length += getSize(aF1);
                }
            }
            return length;
        }
        return 0L;
    }

    public static boolean excludeFile(Path relPath) {
        for (String exclude : Config.cached().excluded) {
            exclude = exclude.replaceAll("\\\\", "/");

            boolean sw = exclude.startsWith("*");
            if (sw) exclude = exclude.substring(1);

            boolean ew = exclude.endsWith("*");
            if (ew) exclude = exclude.substring(0, exclude.length() - 1);

            boolean wildCard = sw || ew;

            boolean path = exclude.contains("/");
            //Relative paths do not have a leading /
            if (exclude.startsWith("/") && !sw) exclude = exclude.substring(1);

            //Is File Exclusion (e.g. fileName.txt)
            if (!path && !wildCard) {
                if (relPath.getFileName().toString().equals(exclude)) {
                    return true;
                }
            }
            //Is Path Exclusion (e.g. world/region/fileName.txt)
            else if (path && !wildCard) {
                if (relPath.toString().equals(exclude)) {
                    return true;
                }
            }
            // (e.g. *directory/file*)
            else if (sw && ew) {
                if (relPath.toString().contains(exclude)) {
                    return true;
                }
            }
            // (e.g. *directory/fileName.txt)
            else if (sw) {
                if (relPath.toString().endsWith(exclude)) {
                    return true;
                }
            }
            // (e.g. directory/file*)
            else {
                if (relPath.toString().startsWith(exclude)) {
                    return true;
                }
            }
        }

        return false;
    }
}
