package org.tdl.vireo.export.impl;

import org.junit.Test;
import org.tdl.vireo.model.AttachmentType;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import static org.tdl.vireo.export.impl.AbstractPackagerImpl.PackageType.dir;
import static org.tdl.vireo.export.impl.AbstractPackagerImpl.PackageType.zip;
import static org.tdl.vireo.services.StringVariableReplacement.FALLBACK;
import static org.tdl.vireo.services.StringVariableReplacement.Variable.FILE_NAME;
import static org.tdl.vireo.services.StringVariableReplacement.Variable.STUDENT_EMAIL;

/**
 * Test the generic template package.
 *
 * Originally, the idea was to test whatever beans were configured in Spring. But then the test always tested
 * that what came out matched what the default configuration should produce. So in essence, we were testing
 * that the config was still default, not that the packer worked in all cases.
 *
 * Now we manually create packagers and test their output thoroughly, including a packager deliberately set up
 * to mirror the default configuration, but also including others designed to make sure every option gets
 * tested.
 */
public class FilePackagerImplTest extends AbstractPackagerTest {

    // Test the standard File Packager in default configuration
	@Test public void testGeneratePackage_default_dir() throws IOException {
        FilePackagerImpl packager = new FilePackagerImpl();
        // packager.setEntryName( -default- );
        // packager.setPackageType( -default==dir- );
        addAttachmentType(packager, AttachmentType.PRIMARY, null, null);
        addAttachmentType(packager, AttachmentType.SUPPLEMENTAL, null, null);
        addAttachmentType(packager, AttachmentType.SOURCE, null, null);
        pkg = packager.generatePackage(sub);

        // test package properties
        assertNotNull(pkg);
        assertEquals("File System",pkg.getFormat());
        assertNull(pkg.getMimeType());

        // test main package directory file
        File exportFile = pkg.getFile();
        assertNotNull(exportFile);
        assertTrue("Package file does not exist", exportFile.exists());
        assertTrue("Package file is not readable", exportFile.canRead());
        assertTrue("Package should be a directory", exportFile.isDirectory());

        // test all expected filenames and file contents
        Map<String, String> fileMap = getDirectoryFileContents(exportFile);
        assertTrue(fileMap.containsKey(primaryDocName+".pdf"));
        assertEquals("bottle", fileMap.remove(primaryDocName+".pdf"));
        assertTrue(fileMap.containsKey("fluff.jpg"));
        assertEquals("fluff", fileMap.remove("fluff.jpg"));
        assertEquals(0, fileMap.size()); // should be nothing else left
	}

    // Test the standard File Packager in default configuration, but with zip output
	@Test public void testGeneratePackage_default_zip() throws IOException {
        FilePackagerImpl packager = new FilePackagerImpl();
        // packager.setEntryName( -default- );
        packager.setPackageType(zip);
        addAttachmentType(packager, AttachmentType.PRIMARY, null, null);
        addAttachmentType(packager, AttachmentType.SUPPLEMENTAL, null, null);
        addAttachmentType(packager, AttachmentType.SOURCE, null, null);
        pkg = packager.generatePackage(sub);

        // test package properties
        assertNotNull(pkg);
        assertEquals("File System",pkg.getFormat());
        assertNull(pkg.getMimeType());

        // test main package zip file
        File exportFile = pkg.getFile();
        assertNotNull(exportFile);
        assertTrue("Package file does not exist", exportFile.exists());
        assertTrue("Package file is not readable", exportFile.canRead());
        assertFalse("Package should not be a directory", exportFile.isDirectory());
        assertTrue("Package file should end in .zip", exportFile.getName().endsWith(".zip"));

        // test all expected filenames and file contents
        Map<String, String> fileMap = getZipFileContents(exportFile);
        assertTrue(fileMap.containsKey(primaryDocName+".pdf"));
        assertEquals("bottle", fileMap.remove(primaryDocName+".pdf"));
        assertTrue(fileMap.containsKey("fluff.jpg"));
        assertEquals("fluff", fileMap.remove("fluff.jpg"));
        assertEquals(0, fileMap.size()); // should be nothing else left
	}

    // Test using entryName, custom filenames, and custom dirnames, with both "dir" and "zip" output.
	@Test public void testGeneratePackage_allOptions() throws IOException {
        FilePackagerImpl packager = new FilePackagerImpl();
        packager.setEntryName("entry-{" + STUDENT_EMAIL + "}-notvar-{" + FILE_NAME + FALLBACK + "fallback}"); // FILE_NAME is not valid on anything but files; replaced with "fallback".
        packager.setPackageType(dir);
        addAttachmentType(packager, AttachmentType.PRIMARY,
                "primaryFile-{" + FILE_NAME + "}-{" + STUDENT_EMAIL + '}', /* FILE_NAME still gets "PRIMARY-DOCUMENT", not "bottle" */
                "primaryDir-{" + STUDENT_EMAIL + '}');
        addAttachmentType(packager, AttachmentType.SUPPLEMENTAL,
                "supplementalFile-{" + FILE_NAME + "}-end", /* FILE_NAME gets "fluff" */
                "supplementalDir-{" + FILE_NAME + '}');
        pkg = packager.generatePackage(sub);

        // test package properties
        assertNotNull(pkg);
        assertEquals("File System", pkg.getFormat());
        assertNull(pkg.getMimeType());
        assertEquals("entry-email@email.com-notvar-fallback", pkg.getEntryName());

        // test main package directory file
        File exportFile = pkg.getFile();
        assertNotNull(exportFile);
        assertTrue("Package file does not exist", exportFile.exists());
        assertTrue("Package file is not readable", exportFile.canRead());
        assertTrue("Package should be a directory", exportFile.isDirectory());
        assertTrue("Package file should end in .dir", exportFile.getName().endsWith(".dir"));

        // test all expected directories, filenames and file contents
        Map<String, String> fileMap = getDirectoryFileContents(exportFile);
        final String primaryFileDir = "primaryDir-email@email.com";
        assertTrue(fileMap.containsKey(primaryFileDir));
        assertEquals(null, fileMap.remove(primaryFileDir));

        final String primaryFileName = primaryFileDir+File.separator+"primaryFile-"+primaryDocName+"-email@email.com.pdf";
        assertTrue(fileMap.containsKey(primaryFileName));
        assertEquals("bottle", fileMap.remove(primaryFileName));

        final String supplementalFileDir = "supplementalDir-fluff";
        assertTrue(fileMap.containsKey(supplementalFileDir));
        assertEquals(null, fileMap.remove(supplementalFileDir));

        final String supplementalFileName = supplementalFileDir+File.separator+"supplementalFile-fluff-end.jpg";
        assertTrue(fileMap.containsKey(supplementalFileName));
        assertEquals("fluff", fileMap.remove(supplementalFileName));

        assertEquals(0, fileMap.size()); // should be nothing else left

        ////////////////////////////////////////////////////////////////////
        // Use the same setup, but do it again, this time with .zip output
        ////////////////////////////////////////////////////////////////////

        pkg.delete();
        packager.setPackageType(zip);
        pkg = packager.generatePackage(sub);

        // test package properties
        assertNotNull(pkg);
        assertEquals("File System", pkg.getFormat());
        assertNull(pkg.getMimeType());
        assertEquals("entry-email@email.com-notvar-fallback", pkg.getEntryName());

        // test main package zip file
        exportFile = pkg.getFile();
        assertNotNull(exportFile);
        assertTrue("Package file does not exist", exportFile.exists());
        assertTrue("Package file is not readable", exportFile.canRead());
        assertFalse("Package should not be a directory", exportFile.isDirectory());
        assertTrue("Package file should end in .zip", exportFile.getName().endsWith(".zip"));

        // test all expected directories, filenames and file contents
        fileMap = getZipFileContents(exportFile);
        if (fileMap.containsKey(primaryFileDir)); {
            // explicit directory entries in zip files are optional
            assertEquals(null, fileMap.remove(primaryFileDir));
        }

        assertTrue(fileMap.containsKey(primaryFileName));
        assertEquals("bottle", fileMap.remove(primaryFileName));

        if (fileMap.containsKey(supplementalFileDir)); {
            // explicit directory entries in zip files are optional
            assertEquals(null, fileMap.remove(supplementalFileDir));
        }

        assertTrue(fileMap.containsKey(supplementalFileName));
        assertEquals("fluff", fileMap.remove(supplementalFileName));

        assertEquals(0, fileMap.size()); // should be nothing else left
	}

    @Test public void testGeneratePackage_failures() {
        FilePackagerImpl packager = new FilePackagerImpl();
        // try with no configured attachment types
        try {
            pkg = packager.generatePackage(sub);
            fail("Should not allow package generation with no attachment types set.");
        } catch (IllegalArgumentException e) {
            assertTrue("Wrong error: "+e.getMessage(), e.getMessage().contains("because no attachment types"));
        }

        // try with invalid submission
        LinkedHashMap<String, Properties> props = new LinkedHashMap<String, Properties>(3);
        props.put(AttachmentType.PRIMARY.name(), new Properties());
        packager.setAttachmentTypeNames(props);
        try {
            pkg = packager.generatePackage(null);
            fail("Should not allow package generation invalid submission.");
        } catch (IllegalArgumentException e) {
            assertTrue("Wrong error: "+e.getMessage(), e.getMessage().contains("because the submission"));
        }
    }
}
