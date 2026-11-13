package com.itriguero.homeclock;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UpdateDownloaderTest {

    // SHA-256 de "hello"
    private static final String HELLO_SHA =
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";

    @Test
    public void computesSha256() throws Exception {
        File file = tempFile("hello");
        try {
            assertEquals(HELLO_SHA, UpdateDownloader.sha256(file));
        } finally {
            file.delete();
        }
    }

    @Test
    public void verifiesMatchingHashIgnoringCase() throws Exception {
        File file = tempFile("hello");
        try {
            assertTrue(UpdateDownloader.verify(file, HELLO_SHA));
            assertTrue(UpdateDownloader.verify(file, HELLO_SHA.toUpperCase()));
        } finally {
            file.delete();
        }
    }

    @Test
    public void rejectsDifferentHash() throws Exception {
        File file = tempFile("bye");
        try {
            assertFalse(UpdateDownloader.verify(file, HELLO_SHA));
        } finally {
            file.delete();
        }
    }

    @Test
    public void rejectsMissingFile() throws Exception {
        File missing = new File(System.getProperty("java.io.tmpdir"),
                "homeclock-missing-" + System.nanoTime() + ".apk");

        assertFalse(UpdateDownloader.verify(missing, HELLO_SHA));
    }

    private static File tempFile(String content) throws Exception {
        File file = Files.createTempFile("homeclock", ".apk").toFile();
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return file;
    }
}
