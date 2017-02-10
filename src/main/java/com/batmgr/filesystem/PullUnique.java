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
import java.nio.file.StandardCopyOption;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedList;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pull all files from a source directory and its descendants to a target
 * directory. <br>
 * Keep a single copy of each file based on content: duplicates are removed and
 * unique names are created if needed.
 */
@SuppressWarnings("nls")
public class PullUnique {
    
    private static final Logger LOG = LoggerFactory.getLogger(PullUnique.class);
    
    public static void main(String[] args) {
        try {
            pullUnique(Paths.get(args[0]), Paths.get(args[1]));
        } catch (Throwable t) {
            LOG.error("program aborted", t);
        }
        Toolkit.getDefaultToolkit().beep();
    }
    
    /**
     * Scan src and all sub-directories, copy all files to dst, each file is
     * copied only once, based on content. <br>
     * Keep a single copy of each file based on content: duplicates are removed and
     * unique names are created if needed.
     * 
     * @param src the source directory
     * @param dst the target directory
     * @throws NoSuchAlgorithmException
     * @throws IOException
     * @throws InvalidIndexException
     */
    public static void pullUnique(Path src, Path dst) throws NoSuchAlgorithmException, IOException, InvalidIndexException
    {
        LOG.info("pullUnique {}", src.toString());
        LinkedList<Path> dirsQueue = new LinkedList<Path>(); // FIFO
        dirsQueue.add(src);
        DirChecker checker = new DirChecker();
        Files.createDirectories(dst);
        checker.indexFolder(dst);
        checker.sweepFolder(dst);
        // TODO add some delay to make sure index is stable ?
        DirInfo dstInfo = new DirInfo(dst);
        while (dirsQueue.size() > 0) {
            pullOneDir(dirsQueue.remove(), dst, dstInfo, dirsQueue);
        }
    }

    /**
     * Scan src, add all directories to dirsToProcess, copy all files to dst
     * only once, based on content
     * @param src
     * @param dst
     * @param dstInfo
     * @param dirsQueue
     * @throws IOException
     * @throws NoSuchAlgorithmException
     * @throws InvalidIndexException
     */
    public static void pullOneDir(Path src, Path dst, DirInfo dstInfo, LinkedList<Path> dirsQueue) throws IOException, NoSuchAlgorithmException,
        InvalidIndexException
    {
        LOG.info("pullOneDir {}", src.toString());
        DirChecker checker = new DirChecker();
        checker.indexFolder(src);
        checker.sweepFolder(src);
        DirInfo srcInfo = new DirInfo(src);
        Map<String, FileInfo> srcNames = srcInfo.getNameIndex();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(src)) {
            for (Path p : stream) { // cannot use stream.forEach because of IOException
                if (Files.isDirectory(p)) {
                    if (checker.isSpecialDir(p)) {
                        continue;
                    }
                    dirsQueue.add(p);
                    continue;
                }
                if (!Files.isRegularFile(p)) {
                    continue;
                }
                String name = p.getFileName().toString();
                FileInfo srcFileInfo = srcNames.get(name);
                if (srcFileInfo == null) { // file not indexed (special file, index file...)
                    continue;
                }
                if (dstInfo.isHashPresent(srcFileInfo.getHash())) { // file already present
                    LOG.debug("Skip duplicate {}", name);
                    continue;
                }
                Path dstPath = dst.resolve(name);
                int i = 1;
                while (Files.exists(dstPath)) {
                    dstPath = dst.resolve(String.format("%s (%d)", name, i++));
                }
                LOG.info("Copy {}", name);
                Files.copy(p, dstPath, StandardCopyOption.COPY_ATTRIBUTES);
                dstInfo.addIfNeeded(dstPath);
                Map<String, FileInfo> dstNames = dstInfo.getNameIndex();
                FileInfo dstFileInfo = dstNames.get(dstPath.getFileName().toString());
                if (dstFileInfo == null) {
                    LOG.warn("{} not present in destination", dstPath);
                    continue;
                }
                if (!dstFileInfo.getHash().equals(srcFileInfo.getHash())) {
                    LOG.warn("{} has different content", dstPath);
                }
            }
        }
    }
}
