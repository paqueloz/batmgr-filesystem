package com.batmgr.filesystem;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DirCleanerTests {

    private URL  resource;
    private Path testRoot;
    private DirChecker dirChecker;
    private DirCleaner dirCleaner;

    @BeforeEach
    void setUp() throws URISyntaxException {
        resource = getClass().getResource("/sample.txt");
        testRoot = Paths.get(resource.toURI()).resolve("..").normalize();
        dirChecker = new DirChecker();
        dirCleaner = new DirCleaner();
    }

    @Test
    void theMethod_cleanFolder_shouldRemoveOurIndex() throws IOException, NoSuchAlgorithmException, InvalidIndexException {
        // given
        Path testDir = testRoot.resolve("cleaner1");
        Files.createDirectories(testDir);
        dirChecker.indexTree(testDir);
        assertThat(testDir.resolve(DirInfo.IDXFILE)).exists();
        // when
        dirCleaner.cleanFolder(testDir);
        // then
        assertThat(testDir.resolve(DirInfo.IDXFILE)).doesNotExist();
    }

    @Test
    void theMethod_cleanFolder_shouldLeaveOtherIndex() throws IOException {
        // given
        Path testDir = testRoot.resolve("cleaner2");
        Files.createDirectories(testDir);
        Files.writeString(testDir.resolve(DirInfo.IDXFILE), "CLEANER2");
        // when
        dirCleaner.cleanFolder(testDir);
        // then
        assertThat(testDir.resolve(DirInfo.IDXFILE)).exists();
    }

    @Test
    void theMethod_cleanFolder_shouldLeaveOtherIndexDir() throws IOException {
        // given
        Path testDir = testRoot.resolve("cleaner3");
        Path testSubdir = testDir.resolve(DirInfo.IDXFILE);
        Files.createDirectories(testSubdir);
        // when
        dirCleaner.cleanFolder(testDir);
        // then
        assertThat(testDir.resolve(DirInfo.IDXFILE)).exists();
    }

    @Test
    void theMethod_cleanTree_shouldRecurse() throws IOException, NoSuchAlgorithmException, InvalidIndexException {
        // given
        Path testDir = testRoot.resolve("cleaner4");
        Path testSubdir = testDir.resolve("subdir");
        Files.createDirectories(testSubdir);
        dirChecker.indexTree(testDir);
        assertThat(testSubdir.resolve(DirInfo.IDXFILE)).exists();
        // when
        dirCleaner.cleanTree(testDir);
        // then
        assertThat(testDir.resolve(DirInfo.IDXFILE)).doesNotExist();
        assertThat(testSubdir.resolve(DirInfo.IDXFILE)).doesNotExist();
    }
}
