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
