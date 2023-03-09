/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 paqueloz
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.batmgr.filesystem;

import java.awt.Toolkit;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.nio.file.FileVisitResult.CONTINUE;

/**
 * Synchronize a source directory to a target directory
 */
@SuppressWarnings("nls")
public class Synchronize {
    
    private static final Logger LOG = LoggerFactory.getLogger(Synchronize.class);
    
    public static void main(String[] args) {
        try {
            boolean scrape = args.length > 2 && args[2].equals("scrape");
            LOG.info("scrape : " + scrape);
            synchronize(Paths.get(args[0]), Paths.get(args[1]), scrape);
        } catch (Throwable t) {
            LOG.error("program aborted", t);
        }
        Toolkit.getDefaultToolkit().beep();
    }
    
    /**
     * Recursively copy the contents of src to dst.
     * <p>
     * This method is safe: it checks the contents of copied files and aborts if there is a difference.
     * <br>
     * This method is efficient: existing files are not copied.
     * <br>
     * This method is also unsafe: the index could be hacked and some file modifications may be undetected.
     * <br>
     * This method is not optimal: identical files can be copied several times.
     */
    public static void synchronize(Path src, Path dst, boolean scrape) throws NoSuchAlgorithmException, IOException, InvalidIndexException
    {
        LOG.info(String.format("Synchronize %s", src.toString()));
        DirChecker checker = new DirChecker();
        checker.indexFolder(src);
        checker.sweepFolder(src);
        Files.createDirectories(dst);
        checker.indexFolder(dst);
        checker.sweepFolder(dst);
        // TODO faut-il un délai avant d'avoir un index stable ?
        DirInfo srcInfo = new DirInfo(src);
        Map<String, FileInfo> srcNames = srcInfo.getNameIndex();
        DirInfo dstInfo = new DirInfo(dst);
        Map<String, FileInfo> dstNames = dstInfo.getNameIndex();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(src)) {
            for (Path p : stream) { // cannot use stream.forEach because of IOException
                if (Files.isDirectory(p)) {
                    if (checker.isSpecialDir(p)) {
                        continue;
                    }
                    synchronize(p, dst.resolve(p.getFileName().toString()), scrape);
                } else {
                    if (!Files.isRegularFile(p)) {
                        continue;
                    }
                    String name = p.getFileName().toString();
                    FileInfo srcFileInfo = srcNames.get(name);
                    if (srcFileInfo == null) { // file not indexed (special file, index file...)
                        continue;
                    }
                    Path dstPath = dst.resolve(name);
                    FileInfo dstFileInfo = dstNames.get(name);
                    if (dstFileInfo != null && !srcFileInfo.getHash().equals(dstFileInfo.getHash())) {
                        // file is present but different, remove it
                        Files.delete(dstPath);
                        dstInfo.removeFromIndex(dstFileInfo);
                        dstFileInfo = null;
                    }
                    if (dstFileInfo != null) {
                        // file is present and has same contents, skip it
                        continue;
                    }
                    LOG.info(String.format("Copy %s", name));
                    Files.copy(p, dstPath);
                    dstInfo.addIfNeeded(dstPath);
                    dstNames = dstInfo.getNameIndex();
                    dstFileInfo = dstNames.get(name);
                    if (dstFileInfo == null) {
                        throw new RuntimeException(String.format("%s not present in destination", dstPath));
                    }
                    if (!dstFileInfo.getHash().equals(srcFileInfo.getHash())) {
                        throw new RuntimeException(String.format("%s has different content", dstPath));
                    }
                }
            }
        }
        if (scrape) { // scrub ?? // timing issues ??
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dst)) {
                for (Path p : stream) { // cannot use stream.forEach because of IOException
                    if (Files.isDirectory(p)) {
                        if (checker.isSpecialDir(p)) {
                            continue;
                        }
                        if (!Files.exists(src.resolve(p.getFileName().toString()))) {
                            LOG.info(String.format("directory %s present only in dest, trying to delete",
                                p.getFileName()));
                            try {
                                Files.walkFileTree(p, new FileVisitor<Path>() {
                                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                                        throws IOException {
                                        return CONTINUE;
                                    }
                                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                                        throws IOException {
                                        LOG.info(String.format("deleting file %s", file));
                                        Files.delete(file);
                                        return CONTINUE;
                                    }
                                    public FileVisitResult visitFileFailed(Path file, IOException exc)
                                        throws IOException {
                                        throw exc;
                                    }
                                    public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                                        throws IOException {
                                        if (exc != null) {
                                            throw exc;
                                        }
                                        LOG.info(String.format("deleting directory %s", dir));
                                        Files.delete(dir);
                                        return CONTINUE;
                                    }
                                });
                            } catch (Exception e) {
                                LOG.warn(String.format("cannot delete %s", p), e);
                            }
                        }
                    } else {
                        if (!Files.isRegularFile(p)) {
                            continue;
                        }
                        String name = p.getFileName().toString();
                        FileInfo dstFileInfo = dstNames.get(name);
                        if (dstFileInfo == null) { // file not indexed (special file, index file...)
                            continue;
                        }
                        FileInfo srcFileInfo = srcNames.get(name);
                        if (srcFileInfo == null) { // not present in src
                            LOG.info(String.format("deleting file %s", p));
                            Files.delete(p);
                        }
                    }
                }
            }
        }
    }
}
