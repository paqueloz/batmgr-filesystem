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
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Synchronize a source directory to a target directory
 */
@SuppressWarnings("nls")
public class Synchronize {
    
    private static final Logger LOG = LoggerFactory.getLogger(Synchronize.class);
    
    public static void main(String[] args) {
        try {
            synchronize(Paths.get(args[0]), Paths.get(args[1]));
        } catch (Throwable t) {
            LOG.error("program aborted", t);
        }
        Toolkit.getDefaultToolkit().beep();
    }
    
    /**
     * Recursively copy the contents of src to dst.
     * <p>
     * This method is safe: it checks the contents of copied files and issues a warning if there is a difference.
     * <br>
     * This method is efficient: existing files are not copied.
     * <br>
     * This method is not optimal: identical files can be copied several times.
     */
    public static void synchronize(Path src, Path dst) throws NoSuchAlgorithmException, IOException, InvalidIndexException
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
                    synchronize(p, dst.resolve(p.getFileName().toString()));
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
                        LOG.warn(String.format("%s not present in destination", dstPath));
                        continue;
                    }
                    if (!dstFileInfo.getHash().equals(srcFileInfo.getHash())) {
                        LOG.warn(String.format("%s has different content", dstPath));
                    }
                }
            }
        }
    }
}
