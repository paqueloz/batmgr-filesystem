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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.NoSuchAlgorithmException;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;

public class DirInfoTest {

    private URL  resource;

    private Path testRoot;

    @Before
    public void setUp() throws URISyntaxException {
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
    public void testEmptyDir() throws IOException, InvalidIndexException, InterruptedException {
        Path testPath = testRoot.resolve("tst1");
        cleanupDir(testPath);
        DirInfo dirInfo = new DirInfo(testPath);
    }

    @Test(expected = InvalidIndexException.class)
    public void testEmptyIndex() throws IOException, InterruptedException, InvalidIndexException {
        Path testPath = testRoot.resolve("tst1");
        cleanupDir(testPath);
        Files.copy(testRoot.resolve("empty.txt"), testPath.resolve(DirInfo.IDXFILE));
        DirInfo dirInfo = new DirInfo(testPath);
    }

    @Test
    public void testGoodIndex() throws IOException, InterruptedException, InvalidIndexException {
        Path testPath = testRoot.resolve("tst1");
        cleanupDir(testPath);
        Files.copy(testRoot.resolve("index-good.txt"), testPath.resolve(DirInfo.IDXFILE));
        DirInfo dirInfo = new DirInfo(testPath);
        assertEquals(57, dirInfo.getLocation("abcd"));
        assertEquals(0x10b, dirInfo.getLocation("efgh")); // location found with hex editor
        assertEquals(-1, dirInfo.getLocation("not present"));
    }
    
    @Test
    public void testGoodIndexLength() throws IOException {
        assertEquals(8, "στην".getBytes(DirInfo.IDXCHARSET).length); // 4 greek characters = 8 bytes
        assertEquals(370, Files.size(testRoot.resolve("index-good.txt")));
    }

    @Test(expected = InvalidIndexException.class)
    public void testBadIndex() throws IOException, InterruptedException, InvalidIndexException {
        Path testPath = testRoot.resolve("tst1");
        cleanupDir(testPath);
        Files.copy(testRoot.resolve("index-bad-signature.txt"), testPath.resolve(DirInfo.IDXFILE));
        DirInfo dirInfo = new DirInfo(testPath);
    }

    @Test(expected = InvalidIndexException.class)
    public void testBadContent() throws IOException, InterruptedException, InvalidIndexException {
        Path testPath = testRoot.resolve("tst1");
        cleanupDir(testPath);
        Files.copy(testRoot.resolve("index-bad-content.txt"), testPath.resolve(DirInfo.IDXFILE));
        DirInfo dirInfo = new DirInfo(testPath);
    }

    @Test
    public void test_addAndRemoveFromIndex() throws IOException, InterruptedException, InvalidIndexException, NoSuchAlgorithmException {
        String sampleFile = "smpl.txt";
        Path testPath = testRoot.resolve("tst1");
        cleanupDir(testPath);
        Files.copy(testRoot.resolve("index-good.txt"), testPath.resolve(DirInfo.IDXFILE));
        DirInfo dirInfo = new DirInfo(testPath);
        Files.copy(testRoot.resolve("sample.txt"), testPath.resolve(sampleFile));
        dirInfo.addIfNeeded(testPath.resolve(sampleFile));
        assertTrue(dirInfo.isHashPresent("4F13A4F6083341F66D39024D7B3765387EE1A3437414CECCC774238A62C65BBA"));
        assertEquals(476, Files.size(testPath.resolve(DirInfo.IDXFILE)));
        // now modify sampleFile
        try (FileChannel fc = FileChannel.open(testPath.resolve(sampleFile), StandardOpenOption.WRITE)) {
            ByteBuffer byteBuffer = ByteBuffer.wrap("**********".getBytes(DirInfo.IDXCHARSET));
            fc.write(byteBuffer);
        }
        // add to index again
        dirInfo.addIfNeeded(testPath.resolve(sampleFile));
        assertFalse(dirInfo.isHashPresent("4F13A4F6083341F66D39024D7B3765387EE1A3437414CECCC774238A62C65BBA"));
        assertEquals(582, Files.size(testPath.resolve(DirInfo.IDXFILE)));
        try (FileChannel fc = FileChannel.open(testPath.resolve(DirInfo.IDXFILE), StandardOpenOption.READ)) {
            fc.position(new FileInfo().getFlagsLocation(370));
            ByteBuffer buf = ByteBuffer.allocate(4); // 4 chars
            assertEquals(4, fc.read(buf));
            assertEquals('1', buf.array()[3]);
        }
    }

}