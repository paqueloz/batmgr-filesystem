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

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DirChecker {
    
    /**
     * Index a directory : for each file, ask DirInfo object to check
     * registration
     * @param path directory to index
     * @throws IOException if a disk error occurs
     * @throws InvalidIndexException
     * @throws NoSuchAlgorithmException
     */
    public void indexFolder(Path path) throws IOException, InvalidIndexException, NoSuchAlgorithmException
    {
        DirInfo index = new DirInfo(path);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
            for (Path p : stream) { // cannot use stream.forEach because of IOException
                if (Files.isRegularFile(p)) {
                    index.addIfNeeded(p);
                }
            }
        }
        log.debug(String.format("Folder %s indexed", path));
    }

    /**
     * Index a directory and all its subdirectories
     * @param path directory to index
     * @throws InvalidIndexException
     * @throws IOException
     * @throws NoSuchAlgorithmException
     */
    public void indexTree(Path path) throws NoSuchAlgorithmException, IOException, InvalidIndexException
    {
        indexFolder(path);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
            for (Path p : stream) { // cannot use stream.forEach because of IOException
                if (Files.isDirectory(p)
                    && !Constantes.SPECIAL_DIRS.contains(p.getFileName().toString()))
                {
                    indexTree(p);
                }
            }
        }
    }
    
    /**
     * Find and log duplicates
     *
     * @param path folder to analyse
     * @param threshold : can be used to skip small files
     * @throws IOException
     * @throws InvalidIndexException
     */
    public void listDuplicates(Path path, long threshold) throws IOException, InvalidIndexException
    {
        HashMap<String, ArrayList<Object>> everything = new HashMap<>();
        log.info("counting folders");
        int n = countFolders(path, 0);
        log.info(String.format("%d folders found", n));
        findEverything(path, everything, n, 0, threshold);
        logDuplicates(everything);
        // sortLogDuplicates(everything);
    }
    
    /**
     * Recursively count the folders in a location.
     *
     * @param path folder to search
     * @param current used to track progress
     * @return number of folders and sub-folders (at least 1)
     * @throws IOException
     */
    public int countFolders(Path path, int current) throws IOException
    {
        int result = 1;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
            for (Path p : stream) { // cannot use stream.forEach because of IOException
                if (Files.isDirectory(p)
                    && !Constantes.SPECIAL_DIRS.contains(p.getFileName().toString()))
                {
                    int progress = current + result;
                    if (progress % 10 == 0) {
                        log.info(String.format("%d folders", progress));
                    }
                    result += countFolders(p, progress);
                }
            }
        }
        return result;
    }

    /**
     * Record a whole hierarchy in a map, using their hash, e.g. to find duplicates.
     * <p>
     * Uses recursion.
     *
     * @param path : directory to search
     * @param everything : map where files are recorded by their hashes
     * @param total : number of directories in hierarchy
     * @param current : number of directories processed (tracks progress)
     * @param threshold : can be used to skip small files
     * @return number of directories processed (tracks progress)
     * @throws IOException
     * @throws InvalidIndexException
     */
    public int findEverything(Path path, HashMap<String, ArrayList<Object>> everything, int total,
        int current, long threshold) throws IOException, InvalidIndexException
    {
        int result = 1;
        DirInfo index = new DirInfo(path);
        recordDir(path, index, everything, threshold);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
            for (Path p : stream) { // cannot use stream.forEach because of IOException
                if (Files.isDirectory(p)
                    && !Constantes.SPECIAL_DIRS.contains(p.getFileName().toString()))
                {
                    int progress = current + result;
                    if (progress % 100 == 0) {
                        log.info(String.format("progress %d/%d", progress, total));
                    }
                    result += findEverything(p, everything, total, progress, threshold);
                }
            }
        }
        return result;
    }
    
    /**
     * Record a list of files in a map, using their hash, e.g. to find duplicates
     * @param path : directory where the files are located
     * @param index : list of files
     * @param everything : map where files are recorded by their hashes
     * @param threshold : can be used to skip small files
     */
    private void recordDir(Path path, DirInfo index, HashMap<String, ArrayList<Object>> everything,
        long threshold)
    {
        for (FileInfo current : index.getFiles()) {
            if (current.getSize() < threshold) {
                continue;
            }
            String fullPath = String.format("%s", path.resolve(current.getName()).normalize());
            String hash = current.getHash();
            ArrayList<Object> matches = everything.get(hash);
            if (matches == null) {
                ArrayList<Object> newList = new ArrayList<>();
                newList.add(current.getSize());
                newList.add(fullPath);
                everything.put(hash, newList);
            } else {
                matches.add(fullPath);
            }
        }
    }
    
    /**
     * logDuplicates : groups repeated files. Quite good but additional sorting
     * by size would help.
     * @param everything
     */
    private void logDuplicates(HashMap<String, ArrayList<Object>> everything)
    {
        Collection<ArrayList<Object>> duplicates = everything.values().stream()
            .filter(l -> {
                return l.size() > 2;
            })
            .sorted((l1, l2) -> {
                Long a = (Long) l1.get(0);
                Long b = (Long) l2.get(0);
                return a.intValue() - b.intValue();
            })
            .collect(Collectors.toList());
        for (ArrayList<Object> list : duplicates) {
            log.info(String.format("[%s] %s has %d duplicates:",
                FileInfo.getHumanReadableSize(((Long) list.get(0)).longValue()), list.get(1), list.size() - 2));
            for (int i = 2; i < list.size(); i++) {
                log.info(String.format("    %s", list.get(i)));
            }
        }
    }

    /**
     * Sort by filename, not easy to see duplication
     * @param everything
     */
    private void sortLogDuplicates(HashMap<String, ArrayList<String>> everything)
    {
        int unique = 0;
        int extra = 0;
        TreeSet<String> sortedSet = new TreeSet<>();
        for (ArrayList<String> list : everything.values()) {
            unique++;
            if (list.size() > 1) {
                extra--;
                for (String s : list) {
                    sortedSet.add(s);
                    extra++;
                }
            }
        }
        for (String s : sortedSet) {
            log.info(s);
        }
        
        log.info(String.format("Unique %d, duplicates %d", unique, extra));
    }

    /**
     * Sweep directory : check that only files present in the directory are
     * indexed. Doesn't check that the index is up to date.
     * @param path directory to index
     * @throws IOException if a disk error occurs
     * @throws InvalidIndexException
     * @throws NoSuchAlgorithmException
     */
    public void sweepFolder(Path path) throws IOException, InvalidIndexException, NoSuchAlgorithmException
    {
        DirInfo index = new DirInfo(path);
        Map<String, FileInfo> nameIndex = index.getNameIndex();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
            for (Path p : stream) { // cannot use stream.forEach because of IOException
                if (Files.isRegularFile(p)) {
                    nameIndex.remove(p.getFileName().toString());
                }
            }
        }
        for (FileInfo fileInfo : nameIndex.values()) {
            log.debug(String.format("File %s removed from index", fileInfo.getName()));
            index.removeFromIndex(fileInfo);
            // TODO ensure consistency if we try to remove something
            // just before adding it !(?)
        }
        log.debug(String.format("Folder %s swept", path));
    }

}
