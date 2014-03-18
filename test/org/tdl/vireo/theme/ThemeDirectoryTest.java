package org.tdl.vireo.theme;

import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import play.test.UnitTest;

import java.io.*;

public class ThemeDirectoryTest extends UnitTest{

    private String origPath = null;
    private File tempDir = null;

    @Before public void setup() throws IOException {
        // don't stomp on existing theme dir
        tempDir = java.nio.file.Files.createTempDirectory(null).toFile();
        tempDir.deleteOnExit();
        origPath = ThemeDirectory.swapPath(tempDir.getPath());
    }

    @After public void cleanup() throws IOException {
        // clean temp dir, including test file
        for (File file : tempDir.listFiles()) {
            file.delete();
        }
        tempDir.delete();
        ThemeDirectory.swapPath(origPath);
    }

    @Test public void testPath() {
        // Just for this one test, undo the temp dir thing and
        // check that the reverse URL retrieval works.
        // This test, unlike the others, is a bit implementation and
        // configuration dependent.
        ThemeDirectory.swapPath(null);
        File file = ThemeDirectory.getFile("gotcha");
        assertEquals("conf/theme", file.getParent());
    }

    @Test public void testCheck() {
        // make sure tempDir doesn't exist.
        tempDir.delete();

        // check it without creating
        assertFalse(ThemeDirectory.check(false));
        assertFalse(tempDir.exists());

        // check it with creating
        assertTrue(ThemeDirectory.check(true));
        assertTrue(tempDir.exists());
    }

    @Test public void testFileUrlTranslation() {
        final File file = new File(tempDir.getPath()+File.separatorChar+"foo.txt");
        final String url = ThemeDirectory.URL_PREFIX+"foo.txt";

        assertEquals(file, ThemeDirectory.fileForUrl(url));
        assertEquals(url, ThemeDirectory.urlForFile(file));
    }

    @Test public void testGetFile() throws IOException {
        makeTestFile("foo.txt", "correct");

        // get non-existent file
        File file = ThemeDirectory.getFile("foo.jpg");
        assertFalse(file.exists());

        // get existing file
        File file2 = ThemeDirectory.getFile("foo.txt");
        assertTrue(file2.exists());
        // verify contents
        FileInputStream in = new FileInputStream(file2);
        String contents = IOUtils.toString(in, "UTF-8");
        assertEquals("correct", contents);
    }

    @Test public void testListFiles() throws IOException {
        final File file1 = makeTestFile("file1","stuff");
        final File file2 = makeTestFile("file2","things");
        assertTrue(file1.exists());
        assertTrue(file2.exists());

        File[] files = ThemeDirectory.listFiles(new FileFilter() {
            public boolean accept(File file) {
                // make sure we don't see any unexpected files
                assertTrue(file.compareTo(file1) == 0 || file.compareTo(file2) == 0);
                return true;
            }
        });

        // make sure we got them both
        assertEquals(2, files.length);
    }

    @Test public void testDeleteFile() throws IOException {
        File file = makeTestFile("deleteme", "correct");
        assertTrue(file.exists());

        ThemeDirectory.deleteFile("deleteme");
        assertFalse(file.exists());
    }
    
    // make a known file to test on
    private File makeTestFile(String name, String contents) throws IOException {
        if (!tempDir.exists()) {
            tempDir.mkdir();
        }
        File file = new File(tempDir.getPath()+File.separatorChar+name);
        FileWriter out = null;
        try {
            out = new FileWriter(file, false);
            out.write(contents);
        } finally {
            if (out != null) {
                out.close();
            }
        }
        return file;
    }
}
