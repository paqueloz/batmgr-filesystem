package com.batmgr.filesystem;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

public class DirCleanerTests {

    private URL        resource;
    private Path       testRoot;
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
    void theMethod_cleanFolder_shouldRemoveOurIndex() throws Exception {
        // given
        Path testDir = testRoot.resolve("cleaner1");
        Files.createDirectories(testDir);
        dirChecker.indexTree(testDir);
        assertThat(testDir.resolve(Shared.INDEX_NAME)).exists();
        // when
        dirCleaner.cleanFolder(testDir);
        // then
        assertThat(testDir.resolve(Shared.INDEX_NAME)).doesNotExist();
    }

    @Test
    void theMethod_cleanFolder_shouldLeaveOtherIndex() throws Exception {
        // given
        Path testDir = testRoot.resolve("cleaner2");
        Files.createDirectories(testDir);
        Files.writeString(testDir.resolve(Shared.INDEX_NAME), "CLEANER2");
        // when
        dirCleaner.cleanFolder(testDir);
        // then
        assertThat(testDir.resolve(Shared.INDEX_NAME)).exists();
    }

    @Test
    void theMethod_cleanFolder_shouldLeaveOtherIndexDir() throws Exception {
        // given
        Path testDir = testRoot.resolve("cleaner3");
        Path testSubdir = testDir.resolve(Shared.INDEX_NAME);
        Files.createDirectories(testSubdir);
        // when
        dirCleaner.cleanFolder(testDir);
        // then
        assertThat(testDir.resolve(Shared.INDEX_NAME)).exists();
    }

    @Test
    void theMethod_cleanTree_shouldRecurse() throws Exception {
        // given
        Path testDir = testRoot.resolve("cleaner4");
        Path testSubdir = testDir.resolve("subdir");
        Files.createDirectories(testSubdir);
        dirChecker.indexTree(testDir);
        assertThat(testSubdir.resolve(Shared.INDEX_NAME)).exists();
        // when
        dirCleaner.cleanTree(testDir);
        // then
        assertThat(testDir.resolve(Shared.INDEX_NAME)).doesNotExist();
        assertThat(testSubdir.resolve(Shared.INDEX_NAME)).doesNotExist();
    }
}
