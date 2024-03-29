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

import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.format.DateTimeParseException;

import jakarta.xml.bind.DatatypeConverter;

/**
 * Manage the details of a file. <br>
 * From / to directory index.
 */
@SuppressWarnings("nls")
public class FileInfo {
    
    private static final int FLAG_REMOVED = 1;

    private boolean          initialized  = false;
    private String           name;
    private long             size;
    private FileTime         lastModif;           // Round milliseconds down to 0
    private String           hash;                // SHA-256
    private static final int HASH_BYTES   = 32;   // length of the hash
    private int              flags;               // 4 hex digits (2 bytes)
                                                   
    /**
     * Default constructor : the object exists but is not initialized
     */
    public FileInfo()
    {
    }
    
    public FileInfo(String name, long size, FileTime lastModif, String hash, int flags)
    {
        this.name = name;
        this.size = size;
        this.lastModif = secondFileTime(lastModif);
        this.hash = hash;
        if (flags < 0 || flags > 0xffff) {
            throw new IllegalArgumentException(String.format("flags %X is not between 0 and 0xffff", flags));
        }
        this.flags = flags;
        initialized = true;
    }
    
    public FileTime secondFileTime(FileTime lastModif)
    {
        long millis = lastModif.toMillis();
        long diff = millis % 1000;
        return FileTime.fromMillis(millis - diff);
    }
    
    /**
     * @param line structured as SHA256;0000;size;YYYY-MM-DDTHH:MM:SS;name
     * @throws IllegalArgumentException
     */
    public FileInfo(String line) throws IllegalArgumentException
    {
        if (line == null) {
            throw new IllegalArgumentException("line must not be null");
        }
        String[] fields = line.split(";");
        if (fields.length < 5) {
            throw new IllegalArgumentException(String.format("line has %d fields instead of 5", fields.length));
        }
        byte[] hex = null;
        try {
            hex = DatatypeConverter.parseHexBinary(fields[0]);
        } catch (IllegalArgumentException e) {
            // exception handled below
        }
        if (hex == null || hex.length != HASH_BYTES) {
            throw new IllegalArgumentException(String.format("%s is not a valid SHA-256 signature", fields[0]));
        }
        hash = fields[0];
        int val = -1;
        try {
            val = Integer.parseInt(fields[1], 16);
        } catch (NumberFormatException e) {
            // exception handled below
        }
        if (val == -1 || fields[1].length() != 4) {
            throw new IllegalArgumentException(String.format("%s is not a valid 16 bit status", fields[1]));
        }
        flags = val;
        long len = -1;
        try {
            len = Long.parseLong(fields[2]);
        } catch (NumberFormatException e) {
            // exception handled below
        }
        if (len == -1) {
            throw new IllegalArgumentException(String.format("%s is not a valid size", fields[2]));
        }
        size = len;
        Instant instant = null;
        try {
            instant = Instant.parse(fields[3]);
        } catch (DateTimeParseException e) {
            // exception handled below
        }
        if (instant == null) {
            throw new IllegalArgumentException(String.format("%s is not a valid timestamp", fields[3]));
        }
        lastModif = FileTime.from(instant);
        StringBuffer finLigne = new StringBuffer(fields[4]);
        for (int i = 5; i < fields.length; i++) {
            finLigne.append(";").append(fields[i]);
        }
        if (finLigne.length() == 0) {
            throw new IllegalArgumentException(String.format("file name is empty"));
        }
        name = finLigne.toString();
        initialized = true;
    }

    public String getHash() {
        if (!initialized) {
            throw new IllegalStateException(Constantes.OBJECT_NOT_INITIALIZED);
        }
        return hash;
    }

    @Override
    public String toString() {
        if (!initialized) {
            return Constantes.OBJECT_NOT_INITIALIZED;
        }
        return String.format("%s;%04X;%d;%s;%s", hash, flags, Long.valueOf(size), lastModif.toString(), name);
    }

    public String getName()
    {
        return name;
    }
    
    public long getSize()
    {
        return size;
    }

    public static String getHumanReadableSize(long bytes) {
        int unit = 1024;
        if (bytes < unit) {
            return bytes + " B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = "KMGTPE".charAt(exp - 1) + "i";
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }
    
    public FileTime getLastModif()
    {
        return lastModif;
    }
    
    /**
     * Compute the flags location
     * @param loc beginning of the entry
     * @return loc + appropriate offset
     */
    public long getFlagsLocation(Integer loc) {
        return loc + 2 * HASH_BYTES + 1;
    }
    
    /**
     * Change flags to mark file as removed
     */
    public void setRemovedFlag() {
        flags |= FLAG_REMOVED;
    }
    
    /**
     * @return true if the removed flag is set
     */
    public boolean isRemovedFlagSet() {
        return (flags & FLAG_REMOVED) != 0;
    }

    /**
     * Return the current value of the flags
     * @return 4 hex digits
     */
    public String getFlagsString() {
        return String.format("%04X", flags);
    }

}
