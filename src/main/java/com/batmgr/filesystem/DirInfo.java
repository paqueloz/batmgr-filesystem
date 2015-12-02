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
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manage the indexation of one directory
 */
public class DirInfo {
    
    private static final Logger   LOG          = LoggerFactory.getLogger(DirInfo.class);

    private static final String   IDXFILE      = ".index";
    private static final String   IDXCHARSET   = "UTF-8";
    private static final String   IDXSIGNATURE = "DIRECTORY INDEX - NO REAL DATA IN THIS FILE - VERSION 1";

    private Map<String, FileInfo> nameIndex;                                                               // key is file name
    private Map<String, FileInfo> hashIndex;                                                               // key is content hash

    private Path                  path;
    
    /**
     * Load the index in this directory
     * @param path
     * @throws IOException
     * @throws InvalidIndexException
     */
    public DirInfo(Path path) throws IOException, InvalidIndexException {
        this.path = path;
        nameIndex = new HashMap<String, FileInfo>();
        hashIndex = new HashMap<String, FileInfo>();
        createIndexIfNeeded();
        readIndex();
    }

    public void createIndexIfNeeded() throws IOException
    {
        Path indexFile = path.resolve(IDXFILE);
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
        Path indexFile = path.resolve(IDXFILE);
        try (InputStream in = Files.newInputStream(indexFile);
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, IDXCHARSET))) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                // require signature on 1st line
                if (firstLine) {
                    firstLine = false;
                    if (!line.equals(IDXSIGNATURE)) {
                        throw new InvalidIndexException(indexFile + ": has an invalid signature");
                    }
                    continue;
                }
                FileInfo fi;
                try {
                    fi = new FileInfo(line);
                } catch (IllegalArgumentException e) {
                    throw new InvalidIndexException(String.format("%s: %s", indexFile, e.getMessage()));
                }
                if (nameIndex.containsKey(fi.getName())) {
                    throw new InvalidIndexException(String.format("multiple occurences of name %s in index", fi.getName()));
                }
                nameIndex.put(fi.getName(), fi);
                if (hashIndex.containsKey(fi.getHash())) {
                    throw new InvalidIndexException(String.format("multiple occurences of hash %s in index", fi.getHash()));
                }
                hashIndex.put(fi.getHash(), fi);
            }
            if (firstLine) {
                throw new InvalidIndexException(indexFile + ": is empty");
            }
        }
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
        if (p.getFileName().equals(IDXFILE)) {
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
            && fileInfo.getLastModif().compareTo(lastModif) == 0)
        {
            return; // same file
        }
        // reindex
        String hash = new FileChecker().computeSha256(p);
        fileInfo = new FileInfo(name, size, lastModif, hash, 0);
        // appendToIndex(fileInfo);
    }
    
}
