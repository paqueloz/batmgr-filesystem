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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manage the indexation of one directory
 */
public class DirInfo {
    
    private static final Logger    LOG          = LoggerFactory.getLogger(DirInfo.class);

    public static final String     IDXFILE      = ".index";
    public static final String     IDXCHARSET   = "UTF-8";
    private static final String    IDXSIGNATURE = "DIRECTORY INDEX - NO REAL DATA IN THIS FILE - VERSION 1";

    private Map<String, FileInfo>  nameIndex;                                                               // key is file name (unique)
    private Map<String, FileInfo>  hashIndex;                                                               // key is content hash (not unique)
    private Map<FileInfo, Integer> locations;                                                               // locations in index file
                                                                                                             
    private Path                   path;
    private Path                   indexFile;
    
    /**
     * Load the index in this directory
     * @param path
     * @throws IOException
     * @throws InvalidIndexException
     */
    public DirInfo(Path path) throws IOException, InvalidIndexException {
        this.path = path;
        indexFile = path.resolve(IDXFILE);
        nameIndex = new HashMap<>();
        hashIndex = new HashMap<>();
        locations = new HashMap<>();
        createIndexIfNeeded();
        readIndex();
    }

    public void createIndexIfNeeded() throws IOException
    {
        if (Files.exists(indexFile)) {
            return;
        }
        try (BufferedWriter writer = Files.newBufferedWriter(indexFile, Charset.forName(IDXCHARSET))) {
            writer.write(String.format("%s\r\n", IDXSIGNATURE));
        }
    }

    /**
     * Read the index, one line per file
     * @throws IOException if the index doesn't exist or cannot be read
     * @throws InvalidIndexException if the index is corrupted
     */
    public void readIndex() throws IOException, InvalidIndexException {
        try (InputStream in = Files.newInputStream(indexFile);
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, IDXCHARSET))) {
            String line;
            int start = 0; // position in file (bytes)
            while ((line = reader.readLine()) != null) {
                // require signature on 1st line
                if (start == 0) {
                    if (!line.equals(IDXSIGNATURE)) {
                        throw new InvalidIndexException(indexFile + ": has an invalid signature");
                    }
                    start += IDXSIGNATURE.length() + 2;
                    continue;
                }
                indexLine(line, indexFile, start);
                start += line.getBytes(IDXCHARSET).length + 2;
            }
            if (start == 0) {
                throw new InvalidIndexException(indexFile + ": is empty");
            }
        }
    }

    /**
     * Parse one line and add it to the index (unless it has flag removed).
     * @param line the input
     * @param indexFile the file for logging
     * @throws InvalidIndexException
     */
    public void indexLine(String line, Path indexFile, int start) throws InvalidIndexException
    {
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
        locations.put(fi, new Integer(start));
    }
    
    /**
     * Adds file to the index if needed. <br>
     * Computing hash is expensive. <br>
     * Check name, size and last update first. <br>
     * Only if needed compute hash and update index. <br>
     * @param p
     * @throws IOException
     * @throws NoSuchAlgorithmException
     */
    public void addIfNeeded(Path p) throws IOException, NoSuchAlgorithmException
    {
        // always skip IDXFILE
        if (p.getFileName().toString().equals(IDXFILE)) {
            return;
        }
        // compute properties
        String name = p.getFileName().toString();
        long size = Files.size(p);
        FileTime lastModif = Files.getLastModifiedTime(p);
        // search by filename
        FileInfo fileInfo = nameIndex.get(name);
        if (fileInfo != null
            && fileInfo.getSize() == size
            && fileInfo.getLastModif().compareTo(fileInfo.secondFileTime(lastModif)) == 0)
        {
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
     * @param fileInfo the file to append
     * @throws IOException
     * @throws UnsupportedEncodingException
     */
    public void appendToIndex(FileInfo fileInfo) throws UnsupportedEncodingException, IOException
    {
        int location = (int) Files.size(indexFile);
        Files.write(indexFile, String.format("%s\r\n", fileInfo).getBytes(IDXCHARSET), StandardOpenOption.APPEND);
        // update indexes if write is successful
        nameIndex.put(fileInfo.getName(), fileInfo);
        hashIndex.put(fileInfo.getHash(), fileInfo);
        locations.put(fileInfo, new Integer(location));
    }

    /**
     * The entry is obsolete, update the flags but leave the file.
     * @param fileInfo entry to remove
     * @throws IOException
     */
    public void removeFromIndex(FileInfo fileInfo) throws IOException
    {
        Integer loc = locations.get(fileInfo);
        if (loc == null) {
            throw new IllegalArgumentException("file location is unknown");
        }
        try (FileChannel fc = FileChannel.open(indexFile, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
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
     * @param fileName the target file name
     * @return the location of the file or -1 if unknown
     */
    public int getLocation(String fileName)
    {
        int result = -1;
        FileInfo fi = nameIndex.get(fileName);
        if (fi == null) {
            return result;
        }
        return locations.get(fi).intValue();
    }

    /**
     * Look for file by signature
     * @param hash the target signature
     * @return true if a file with the given signature is in the directory
     */
    public boolean isHashPresent(String hash)
    {
        return hashIndex.containsKey(hash);
    }

    /**
     * Return a Set with FileInfo
     * @return a shallow copy of the index, sharing the same FileInfo objects
     */
    public Set<FileInfo> getFiles()
    {
        return new HashSet<FileInfo>(locations.keySet());
    }
    
    /**
     * Return a Map with file names as keys and FileInfo as values.
     * <p>
     * @return a shallow copy of the index, sharing the same FileInfo objects
     */
    public Map<String, FileInfo> getNameIndex() {
        return new HashMap<String, FileInfo>(nameIndex);
    }

}
