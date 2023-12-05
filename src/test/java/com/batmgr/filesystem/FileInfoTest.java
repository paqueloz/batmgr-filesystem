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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.security.NoSuchAlgorithmException;

import org.junit.jupiter.api.Test;

@SuppressWarnings("nls")
public class FileInfoTest {
    
    @Test
    public void testSampleSha256() throws URISyntaxException, NoSuchAlgorithmException, IOException {
        FileChecker fc = new FileChecker();
        URL resource = getClass().getResource("/sample.txt");
        Path p = Paths.get(resource.toURI());
        assertThat("4F13A4F6083341F66D39024D7B3765387EE1A3437414CECCC774238A62C65BBA").isEqualTo(fc.computeSha256(p));
    }

    @Test
    public void testFileInfoEmpty() {
        FileInfo fi = new FileInfo();
        assertThat(Constantes.OBJECT_NOT_INITIALIZED).isEqualTo(fi.toString());
    }

    @Test
    public void testFileInfoNotInit() {
        assertThatThrownBy(() -> {
            FileInfo fi = new FileInfo();
            fi.getHash();
        }).isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void testFileInfoArrondi() {
        FileInfo fi = new FileInfo("abcd", 10000, FileTime.fromMillis(990387509456L), "h", 0);
        assertThat("h;0000;10000;2001-05-20T19:38:29Z;abcd").isEqualTo(fi.toString());
        assertThat("h;0001;10000;2001-05-20T19:38:29Z;abcd").isEqualTo(new FileInfo("abcd", 10000, FileTime.fromMillis(990387509000L), "h", 1).toString());
        assertThat("h;0002;10000;2001-05-20T19:38:29Z;abcd").isEqualTo(new FileInfo("abcd", 10000, FileTime.fromMillis(990387509999L), "h", 2).toString());
        assertThat("h;0003;10000;2001-05-20T19:38:30Z;abcd").isEqualTo(new FileInfo("abcd", 10000, FileTime.fromMillis(990387510000L), "h", 3).toString());
    }
    
    @Test
    public void testFileInfoParseOK() {
        FileInfo fi = new FileInfo("4F13A4F6083341F66D39024D7B3765387EE1A3437414CECCC774238A62C65B00;001a;10000;2001-05-20T19:38:29Z;abcd");
        assertThat("4F13A4F6083341F66D39024D7B3765387EE1A3437414CECCC774238A62C65B00;001A;10000;2001-05-20T19:38:29Z;abcd").isEqualTo(fi.toString());
    }
    
    @Test
    public void testFileInfoParseIllegalHash() {
        assertThatThrownBy(() -> {
            new FileInfo("h;0000;10000;2001-05-20T19:38:29Z;abcd");
        }).isInstanceOf(IllegalArgumentException.class);
    }
    
    @Test
    public void testFileInfoParseIllegalHash2() {
        assertThatThrownBy(() -> {
            new FileInfo("4F13A4F6083341F66D39024D7B3765387EE1A3437414CECCC774238A62C65B;0000;10000;2001-05-20T19:38:29Z;abcd");
        }).isInstanceOf(IllegalArgumentException.class);
    }
    
    @Test
    public void testFileInfoParseIllegalHash3() {
        assertThatThrownBy(() -> {
            new FileInfo("4F13A4F6083341F66D39024D7B3765387EE1A3437414CECCC774238A62C65B0Z;0000;10000;2001-05-20T19:38:29Z;abcd");
        }).isInstanceOf(IllegalArgumentException.class);
    }
    
    @Test
    public void testFileInfoParseIllegalFlags() {
        assertThatThrownBy(() -> {
            new FileInfo("4F13A4F6083341F66D39024D7B3765387EE1A3437414CECCC774238A62C65B0Z;1;10000;2001-05-20T19:38:29Z;abcd");
        }).isInstanceOf(IllegalArgumentException.class);
    }
    
    @Test
    public void testFileInfoParseIllegalFlags2() {
        assertThatThrownBy(() -> {
            new FileInfo("4F13A4F6083341F66D39024D7B3765387EE1A3437414CECCC774238A62C65B0Z;gggg;10000;2001-05-20T19:38:29Z;abcd");
        }).isInstanceOf(IllegalArgumentException.class);
    }
    
    @Test
    public void testFileInfoParseIllegalSize() {
        assertThatThrownBy(() -> {
            new FileInfo("4F13A4F6083341F66D39024D7B3765387EE1A3437414CECCC774238A62C65B00;0000;12x34;2001-05-20T19:38:29Z;abcd");
        }).isInstanceOf(IllegalArgumentException.class);
    }
    
    @Test
    public void testFileInfoParseIllegalTimeNoTimezone() {
        assertThatThrownBy(() -> {
            new FileInfo("4F13A4F6083341F66D39024D7B3765387EE1A3437414CECCC774238A62C65B00;0000;1234;2001-05-20T19:38:29;abcd");
        }).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testFileInfoParseIllegalTimeNoT() {
        assertThatThrownBy(() -> {
            new FileInfo("4F13A4F6083341F66D39024D7B3765387EE1A3437414CECCC774238A62C65B00;0000;1234;2001-05-20 19:38:29Z;abcd");
        }).isInstanceOf(IllegalArgumentException.class);
    }
    
    @Test
    public void testFileInfoParseIllegalNoName() {
        assertThatThrownBy(() -> {
            new FileInfo("4F13A4F6083341F66D39024D7B3765387EE1A3437414CECCC774238A62C65B00;0000;1234;2001-05-20T19:38:29Z");
        }).isInstanceOf(IllegalArgumentException.class);
    }
    
    @Test
    public void testFileInfoParseIllegalEmptyName() {
        assertThatThrownBy(() -> {
            new FileInfo("4F13A4F6083341F66D39024D7B3765387EE1A3437414CECCC774238A62C65B00;0000;1234;2001-05-20T19:38:29Z;");
        }).isInstanceOf(IllegalArgumentException.class);
    }
    
    @Test
    public void testFileInfoParseNameSpace() {
        FileInfo fi = new FileInfo("4F13A4F6083341F66D39024D7B3765387EE1A3437414CECCC774238A62C65B00;000a;1234;2001-05-20T19:38:29Z; ");
        assertThat(fi.toString()).isEqualTo("4F13A4F6083341F66D39024D7B3765387EE1A3437414CECCC774238A62C65B00;000A;1234;2001-05-20T19:38:29Z; ");
    }

    @Test
    public void testFileInfoParseNameDot() {
        FileInfo fi = new FileInfo("4F13A4F6083341F66D39024D7B3765387EE1A3437414CECCC774238A62C65B00;0000;1234;2001-05-20T19:38:29Z;.");
        assertThat(fi.toString()).isEqualTo("4F13A4F6083341F66D39024D7B3765387EE1A3437414CECCC774238A62C65B00;0000;1234;2001-05-20T19:38:29Z;.");
    }
    
    @Test
    public void test_getFlagsLocation() {
        assertThat(new FileInfo().getFlagsLocation(0x39)).isEqualTo(0x7a);
    }
    
    @Test
    public void test_getFlagsString() {
        FileInfo fi = new FileInfo();
        fi.setRemovedFlag();
        assertThat(fi.getFlagsString()).isEqualTo("0001");
    }
}
