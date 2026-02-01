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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DirInfoTests {

    private URL  resource;

    private Path testRoot;

    @BeforeEach
    void setUp() throws Exception {
        resource = getClass().getResource("/sample.txt");
        testRoot = Paths.get(resource.toURI()).resolve("..").normalize();
    }

    @Test
    void constructor_shouldWorkOnEmptyDirectory() throws Exception {
        Path testPath = testRoot.resolve("tst1");
        DirUtil.cleanupDir(testPath);
        new DirInfo(testPath);
    }

    @Test
    void constructor_shouldIncrementOnEmptyIndex() throws Exception {
        Path testPath = testRoot.resolve("tst1");
        DirUtil.cleanupDir(testPath);
        Files.copy(testRoot.resolve("empty.txt"), testPath.resolve(Shared.INDEX_NAME));
        new DirInfo(testPath);
        assertThat(Files.exists(testPath.resolve(Shared.INDEX_NAME + "-a"))).isTrue();
    }

    @Test
    void constructor_shouldWorkOnGoodIndex() throws Exception {
        Path testPath = testRoot.resolve("tst1");
        DirUtil.cleanupDir(testPath);
        Files.copy(testRoot.resolve("index-good.txt"), testPath.resolve(Shared.INDEX_NAME));
        DirInfo dirInfo = new DirInfo(testPath);
        assertThat(dirInfo.getLocation("abcd")).isEqualTo(57);
        assertThat(dirInfo.getLocation("efgh")).isEqualTo(0x10b); // location found with hex editor
        assertThat(dirInfo.getLocation("not present")).isEqualTo(-1);
    }
    
    @Test
    void indexLength_shouldBeConstant() throws IOException {
        assertThat("στην".getBytes(DirInfo.IDXCHARSET).length).isEqualTo(8); // 4 greek characters = 8 bytes
        assertThat(Files.size(testRoot.resolve("index-good.txt"))).isEqualTo(370);
    }

    @Test
    void constructor_shouldIncrementOnBadIndexSignature() throws Exception{
        Path testPath = testRoot.resolve("tst1");
        DirUtil.cleanupDir(testPath);
        Files.copy(testRoot.resolve("index-bad-signature.txt"), testPath.resolve(Shared.INDEX_NAME));
        new DirInfo(testPath);
        assertThat(Files.exists(testPath.resolve(Shared.INDEX_NAME + "-a"))).isTrue();
    }

    @Test
    void constructor_shouldFailOnBadIndexContent() {
        assertThatThrownBy(() -> {
            Path testPath = testRoot.resolve("tst1");
            DirUtil.cleanupDir(testPath);
            Files.copy(testRoot.resolve("index-bad-content.txt"), testPath.resolve(Shared.INDEX_NAME));
            new DirInfo(testPath);
        }).isInstanceOf(InvalidIndexException.class);
    }

    @Test
    void theMethod_addIfNeeded_shouldUpdateIndexWhenFileAddedAndModified() throws Exception {
        String sampleFile = "smpl.txt";
        Path testPath = testRoot.resolve("tst1");
        DirUtil.cleanupDir(testPath);
        Files.copy(testRoot.resolve("index-good.txt"), testPath.resolve(Shared.INDEX_NAME));
        DirInfo dirInfo = new DirInfo(testPath);
        Files.copy(testRoot.resolve("sample.txt"), testPath.resolve(sampleFile));
        dirInfo.addIfNeeded(testPath.resolve(sampleFile));
        assertThat(dirInfo.isHashPresent("4F13A4F6083341F66D39024D7B3765387EE1A3437414CECCC774238A62C65BBA")).isTrue();
        assertThat(Files.size(testPath.resolve(Shared.INDEX_NAME))).isEqualTo(476);
        try { Thread.sleep(2000); } catch (InterruptedException e) {}
        // now modify sampleFile
        try (FileChannel fc = FileChannel.open(testPath.resolve(sampleFile), StandardOpenOption.WRITE)) {
            ByteBuffer byteBuffer = ByteBuffer.wrap("**********".getBytes(DirInfo.IDXCHARSET));
            fc.write(byteBuffer);
        }
        // add to index again
        dirInfo.addIfNeeded(testPath.resolve(sampleFile));
        assertThat(dirInfo.isHashPresent("4F13A4F6083341F66D39024D7B3765387EE1A3437414CECCC774238A62C65BBA")).isFalse();
        assertThat(Files.size(testPath.resolve(Shared.INDEX_NAME))).isEqualTo(582);
        try (FileChannel fc = FileChannel.open(testPath.resolve(Shared.INDEX_NAME), StandardOpenOption.READ)) {
            fc.position(new FileInfo().getFlagsLocation(370));
            ByteBuffer buf = ByteBuffer.allocate(4); // 4 chars
            assertThat(fc.read(buf)).isEqualTo(4);
            assertThat(buf.array()[3]).isEqualTo((byte)'1');
        }
    }

}