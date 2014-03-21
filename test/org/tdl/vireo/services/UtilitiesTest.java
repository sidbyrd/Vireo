package org.tdl.vireo.services;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Test;
import play.test.UnitTest;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class UtilitiesTest extends UnitTest {

    // I added these methods, so I'm testing them. The methods that were already in Utilities remain untested.

    @Test public void testGetResourceFile_and_fileToString() throws IOException {
        final File file = Utilities.getResourceFile("SampleTextDocument.txt");
        assertEquals("UTF-8 ≠ 💩.\n", Utilities.fileToString(file));
        FileUtils.deleteQuietly(file); // should already be marked deleteOnExit, but why wait?
    }

    @Test public void testGetResourceFileWithExtension() throws IOException {
        final File textFile = Utilities.getResourceFileWithExtension("SampleTextDocument.txt", ".pdf");
        assertEquals("pdf", FilenameUtils.getExtension(textFile.getName()));
        FileUtils.deleteQuietly(textFile); // should already be marked deleteOnExit, but let's make sure.
    }

    @Test public void testBlankFileWithSize() throws IOException {
        final File zero = Utilities.blankFileWithSize(0, "txt");
        assertEquals("txt", FilenameUtils.getExtension(zero.getName()));
        assertTrue(zero.exists());
        assertEquals(0, zero.length());
        FileUtils.deleteQuietly(zero); // should already be marked deleteOnExit, but let's make sure.

        final File clarke = Utilities.blankFileWithSize(2001, "empty");
        assertEquals("empty", FilenameUtils.getExtension(clarke.getName()));
        assertTrue(clarke.exists());
        assertEquals(2001, clarke.length());
        FileUtils.deleteQuietly(clarke); // should already be marked deleteOnExit, but let's make sure.
    }

    @Test public void testFileWithNameAndContents() throws IOException {
        final File file = Utilities.fileWithNameAndContents("testing.txt", "you can see my insides!");
        assertEquals("testing.txt", file.getName());
        assertEquals("you can see my insides!", Utilities.fileToString(file));
        FileUtils.deleteQuietly(file); // should already be marked deleteOnExit, but why wait?
    }
}
