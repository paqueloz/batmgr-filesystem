/*
 * Copyright(c) - CdC 2015, Tous droits réservés.
 * -----------------------------------------------------------
 * Centrale de Compensation
 * Avenue Edmond-Vaucher, 18
 * Centre Informatique AVS/AI
 * Case Postale 2676
 * 1211 Genève 2
 * -----------------------------------------------------------
 */
package com.batmgr.filesystem;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;

public class DirCheckerTest {
    
    private URL  resource;

    private Path testRoot;

    @Before
    public void setUp() throws Exception {
        resource = getClass().getResource("/sample.txt");
        testRoot = Paths.get(resource.toURI()).resolve("..").normalize();
    }
    
    private void cleanupDir(Path p) throws IOException, InterruptedException
    {
        if (Files.exists(p)) {
            FileUtils.deleteDirectory(p.toFile());
        }
        Thread.sleep(1000);
        Files.createDirectory(p);
    }
    
    @Test
    public void test_indexFolder() throws IOException, InterruptedException, InvalidIndexException, NoSuchAlgorithmException {
        String sampleFile = "smpl.txt";
        Path testPath = testRoot.resolve("tst1");
        cleanupDir(testPath);
        Files.copy(testRoot.resolve("sample.txt"), testPath.resolve(sampleFile));
        DirChecker dirChecker = new DirChecker();
        dirChecker.indexFolder(testPath);
        DirInfo dirInfo = new DirInfo(testPath);
        assertTrue(dirInfo.isHashPresent("4F13A4F6083341F66D39024D7B3765387EE1A3437414CECCC774238A62C65BBA"));
    }
    
}
