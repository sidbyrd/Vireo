package org.tdl.vireo.services;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Test;
import play.test.UnitTest;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class UtilitiesTest extends UnitTest {

    // I added these methods, so I'm testing them. The methods that were already in Utilities remain untested.

    @Test public void testGetResourceFile() throws IOException {
        final File textFile = Utilities.getResourceFile("SampleTextDocument.txt");
        FileInputStream in = null;
        try {
            in = new FileInputStream(textFile);
            final String contents = IOUtils.toString(in, "UTF-8");
            assertEquals("UTF-8 ≠ 💩.\n", contents);
        } finally {
            if (in != null) {
                in.close();
            }
            textFile.delete(); // should already be marked deleteOnExit, but let's make sure.
        }
    }

    @Test public void testGetResourceFileWithExtension() throws IOException {
        final File textFile = Utilities.getResourceFileWithExtension("SampleTextDocument.txt", ".pdf");
        assertEquals("pdf", FilenameUtils.getExtension(textFile.getName()));
        textFile.delete(); // should already be marked deleteOnExit, but let's make sure.
    }

    @Test public void testBlankFileWithSize() throws IOException {
        final File zero = Utilities.blankFileWithSize(0, "txt");
        assertEquals("txt", FilenameUtils.getExtension(zero.getName()));
        assertTrue(zero.exists());
        assertEquals(0, zero.length());
        zero.delete(); // should already be marked deleteOnExit, but let's make sure.

        final File clarke = Utilities.blankFileWithSize(2001, "empty");
        assertEquals("empty", FilenameUtils.getExtension(clarke.getName()));
        assertTrue(clarke.exists());
        assertEquals(2001, clarke.length());
        clarke.delete(); // should already be marked deleteOnExit, but let's make sure.
    }
}
