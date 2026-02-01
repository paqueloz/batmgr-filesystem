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

import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

/**
 * Manage the indexation of one directory:
 * <ul>
 *     <li>Constructor: ensure an index file is present and loaded</li>
 *     <li>addIfNeeded(Path): ensure the file is in the index (in memory and on disk)</li>
 *     <li>removeFromIndex(FileInfo): mark the file as removed in the index (in memory and on disk)</li>
 *     <li>isHashPresent(String): check if a file with the given hash is in the index (and in the directory)</li>
 *     <li>getNameIndex(): returns a shallow copy of the index, a quick way to obtain contents of directory</li>
 *     <li>deleteIndex(): deletes index file and makes the instance unusable</li>
 * </ul>
 */
@Slf4j
public class DirInfo {

    private static final String STANDARD_INDEX_NAME = ".index";

    public static final String IDXCHARSET   = "UTF-8";
    public static final String IDXSIGNATURE = "DIRECTORY INDEX - NO REAL DATA IN THIS FILE - VERSION 1";

    /**
     * key is file name (unique)
     */
    private Map<String, FileInfo>  nameIndex;
    /**
     * key is content hash (not unique)
     */
    private Map<String, FileInfo>  hashIndex;
    /**
     * locations in index file
     */
    private Map<FileInfo, Integer> locations;

    private Path    path;
    private String  idxName;
    private Path    idxPath;
    private boolean initialized;

    /**
     * Load the index in this directory
     */
    public DirInfo(Path path) throws IOException, InvalidIndexException,
        NotIndexableException {

        this.path = path;
        nameIndex = new HashMap<>();
        hashIndex = new HashMap<>();
        locations = new HashMap<>();
        initIndexFile();
        readIndexFile();
        initialized = true;
    }

    /**
     * Delete index file and make the instance unusable
     */
    public void deleteIndexFile() throws IOException {
        assertInitialized();
        Files.delete(idxPath);
        log.debug("{} deleted", idxPath);
        initialized = false;
    }

    /**
     * Adds file to the index if needed. <br>
     * Computing hash is expensive. <br>
     * Check name, size and last update first. <br>
     * Only if needed compute hash and update index. <br>
     *
     * @param p
     * @throws IOException
     * @throws NoSuchAlgorithmException
     */
    public void addIfNeeded(Path p) throws IOException, NoSuchAlgorithmException {
        assertInitialized();
        // always skip idxName
        if (p.getFileName().toString().equals(idxName)) {
            return;
        }
        // compute properties
        String name = p.getFileName().toString();
        long size = Files.size(p);
        FileTime lastModif = Files.getLastModifiedTime(p);
        // search by filename
        FileInfo fileInfo = nameIndex.get(name);
        // the next statement makes the index system efficient
        // BUT also risky because lastModif could be inaccurate,
        // or writes could happen in the file during the second
        // elapsed after the fileTime was stored in the index,
        // or the index could be hacked
        if (fileInfo != null
            && fileInfo.getSize() == size
            && fileInfo.getLastModif().compareTo(fileInfo.secondFileTime(lastModif)) == 0) {
            return; // same file
        }
        // remove obsolete entry
        if (fileInfo != null) {
            removeFromIndex(fileInfo);
        }
        // reindex
        String hash = new FileChecker().computeSha256(p);
        fileInfo = new FileInfo(name, size, lastModif, hash, 0);
        appendToIndex(fileInfo);
    }

    /**
     * Append one line to the index. <br>
     * If the file was already present, flag the old entry. <br>
     *
     * @param fileInfo the file to append
     * @throws IOException
     * @throws UnsupportedEncodingException
     */
    public void appendToIndex(FileInfo fileInfo) throws UnsupportedEncodingException, IOException {
        assertInitialized();
        int location = (int) Files.size(idxPath);
        Files.write(idxPath,
            String.format("%s\r\n", fileInfo).getBytes(IDXCHARSET),
            StandardOpenOption.APPEND);
        // update indexes if write is successful
        nameIndex.put(fileInfo.getName(), fileInfo);
        hashIndex.put(fileInfo.getHash(), fileInfo);
        locations.put(fileInfo, location);
    }

    /**
     * The entry is obsolete, update the flags but leave the file.
     *
     * @param fileInfo entry to remove
     * @throws IOException
     */
    public void removeFromIndex(FileInfo fileInfo) throws IOException {
        assertInitialized();
        Integer loc = locations.get(fileInfo);
        if (loc == null) {
            throw new IllegalArgumentException("file location is unknown");
        }
        try (FileChannel fc = FileChannel.open(idxPath, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            fc.position(fileInfo.getFlagsLocation(loc));
            fileInfo.setRemovedFlag();
            ByteBuffer byteBuffer = ByteBuffer.wrap(fileInfo.getFlagsString().getBytes(IDXCHARSET));
            fc.write(byteBuffer);
        }
        nameIndex.remove(fileInfo.getName());
        hashIndex.remove(fileInfo.getHash());
        locations.remove(fileInfo);
    }

    /**
     * Return the location of the file, -1 if unknown
     *
     * @param fileName the target file name
     * @return the location of the file or -1 if unknown
     */
    public int getLocation(String fileName) {
        assertInitialized();
        int result = -1;
        FileInfo fi = nameIndex.get(fileName);
        if (fi == null) {
            return result;
        }
        return locations.get(fi).intValue();
    }

    /**
     * Look for file by signature
     *
     * @param hash the target signature
     * @return true if a file with the given signature is in the directory
     */
    public boolean isHashPresent(String hash) {
        assertInitialized();
        return hashIndex.containsKey(hash);
    }

    /**
     * Return a Map with file names as keys and FileInfo as values.
     *
     * @return a shallow copy of the index, sharing the same FileInfo objects
     */
    public Map<String, FileInfo> getNameIndex() {
        assertInitialized();
        return new HashMap<>(nameIndex);
    }

    private void assertInitialized() {
        if (!initialized) {
            throw new IllegalStateException("DirInfo is not initialized");
        }
    }

    /**
     * Read the index, one line per file
     *
     * @throws IOException           if the index doesn't exist or cannot be read
     * @throws InvalidIndexException if the index is corrupted
     */
    private void readIndexFile() throws IOException, InvalidIndexException {
        try (InputStream in = Files.newInputStream(idxPath);
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, IDXCHARSET))) {

            String line;
            int start = 0; // position in file (bytes)
            while ((line = reader.readLine()) != null) {
                // require signature on 1st line
                if (start == 0) {
                    if (!line.equals(IDXSIGNATURE)) {
                        throw new InvalidIndexException(idxPath + ": has an invalid signature");
                    }
                    start += IDXSIGNATURE.length() + 2;
                    continue;
                }
                indexLine(line, idxPath, start);
                start += line.getBytes(IDXCHARSET).length + 2;
            }
            if (start == 0) {
                throw new InvalidIndexException(idxPath + ": is empty");
            }
        }
    }

    /**
     * Parse one line and add it to the index (unless it has flag removed).
     *
     * @param line      the input
     * @param indexFile the file for logging
     * @throws InvalidIndexException
     */
    private void indexLine(String line, Path indexFile, int start) throws InvalidIndexException {
        FileInfo fi;
        try {
            fi = new FileInfo(line);
        } catch (IllegalArgumentException e) {
            throw new InvalidIndexException(String.format("%s: %s", indexFile, e.getMessage()));
        }
        if (fi.isRemovedFlagSet()) {
            return;
        }
        if (nameIndex.containsKey(fi.getName())) {
            throw new InvalidIndexException(String.format("multiple occurrences of name %s in index", fi.getName()));
        }
        // several files can have the same hash
        nameIndex.put(fi.getName(), fi);
        hashIndex.put(fi.getHash(), fi); // TODO use list
        locations.put(fi, start);
    }

    /**
     * Computing file name for the index is tricky because of possible collisions
     * with an actual file or directory.
     * <p>
     * The second difficulty is that it must be done early, in the constructor.
     * <p>
     * If the method succeeds, the index file exists and has a valid signature.
     */
    private void initIndexFile() throws NotIndexableException {
        idxName = STANDARD_INDEX_NAME;
        for (int suffix = 'a'; suffix <= 'z'; suffix++) {
            idxPath = path.resolve(idxName);
            if (isSuitableIndexPath()) {
                if (!STANDARD_INDEX_NAME.equals(idxName)) {
                    log.warn("Using {} as index file name in {}", idxName, path);
                }
                return;
            }
            idxName = STANDARD_INDEX_NAME + "-" + (char) suffix;
        }
        throw new NotIndexableException("Cannot create index file in " + path);
    }

    private boolean isSuitableIndexPath() {
        try {
            // file doesn't exist, create it
            if (!Files.exists(idxPath)) {
                try (BufferedWriter writer = Files.newBufferedWriter(idxPath, Charset.forName(IDXCHARSET))) {
                    writer.write(String.format("%s\r\n", IDXSIGNATURE));
                }
                return true;
            }
            // directory, not suitable
            if (Files.isDirectory(idxPath)) {
                return false;
            }
            // file exists, check signature
            try (InputStream in = Files.newInputStream(idxPath);
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, IDXCHARSET))) {

                String firstLine = reader.readLine();
                return IDXSIGNATURE.equals(firstLine);
            }
        } catch (IOException e) {
            return false;
        }
    }

}
