package org.tdl.vireo.export.impl;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.tdl.vireo.export.ExportPackage;
import org.tdl.vireo.model.AttachmentType;
import org.tdl.vireo.model.Person;
import org.tdl.vireo.model.RoleType;
import org.tdl.vireo.model.Submission;
import org.tdl.vireo.model.jpa.JpaPersonRepositoryImpl;
import org.tdl.vireo.model.jpa.JpaSettingsRepositoryImpl;
import org.tdl.vireo.model.jpa.JpaSubmissionRepositoryImpl;
import org.tdl.vireo.security.SecurityContext;
import org.tdl.vireo.services.Utilities;
import play.modules.spring.Spring;
import play.test.UnitTest;

import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import static org.tdl.vireo.services.StringVariableReplacement.Variable.FILE_NAME;
import static org.tdl.vireo.services.StringVariableReplacement.Variable.STUDENT_EMAIL;

/**
 * Test the generic template package.
 *
 * Originally, the idea was to test whatever beans were configured in Spring. But then the test always tested
 * that what came out matched what the default configuration should produce. So in essence, we were testing
 * that the config was still default, not that the packer worked in all cases.
 *
 * Now we just manually create one packager that matches the default config. It still isn't a thorough
 * unit testing, but it's as good as before, plus proper configuration independence is restored.
 *
 * If someone would like to add more tests to thoroughly test all the function of FilePackagerImpl, not just
 * some of what is used by the default config, that would be welcome.
 *
 * @author <a href="http://www.scottphillips.com">Scott Phillips</a>
 * 
 */
public class FilePackagerImplTest extends UnitTest {

	// All the repositories
	public static SecurityContext context = Spring.getBeanOfType(SecurityContext.class);
	public static JpaPersonRepositoryImpl personRepo = Spring.getBeanOfType(JpaPersonRepositoryImpl.class);
	public static JpaSubmissionRepositoryImpl subRepo = Spring.getBeanOfType(JpaSubmissionRepositoryImpl.class);
	public static JpaSettingsRepositoryImpl settingRepo = Spring.getBeanOfType(JpaSettingsRepositoryImpl.class);

	private Person person;
	private Submission sub;
    private FilePackagerImpl packager;
    private ExportPackage pkg;
	
	/**
	 * Set up a submission so we can test packaging it up.
	 */
	@Before
	public void setup() throws IOException {
		context.turnOffAuthorization();
		person = personRepo.createPerson("netid", "email@email.com", "first", "last", RoleType.NONE).save();
		sub = subRepo.createSubmission(person);
				
		// Create some attachments
		File bottle_pdf = Utilities.fileWithNameAndContents("bottle.pdf", "bottle");
        File fluff_jpg = Utilities.fileWithNameAndContents("fluff.jpg", "fluff");

		sub.addAttachment(bottle_pdf, AttachmentType.PRIMARY);
		sub.addAttachment(fluff_jpg, AttachmentType.SUPPLEMENTAL);

		sub.save();

        FileUtils.deleteQuietly(bottle_pdf);
        FileUtils.deleteQuietly(fluff_jpg);

        // shared test subjects
        packager = new FilePackagerImpl();
        pkg = null;
	}
	
	/**
	 * Clean up our submission.
	 */
	@After
	public void cleanup() {		
		sub.delete();
		person.delete();
		context.restoreAuthorization();

        if (pkg!=null) {
            pkg.delete();
        }
	}

    // Test the standard File Packager in default configuration
	@Test public void testGeneratePackage_default_dir() throws IOException {
        // packager.setEntryName( -default- );
        // packager.setPackageType( -default==dir- );
        LinkedHashMap<String, Properties> props = new LinkedHashMap<String, Properties>(3);
        props.put("PRIMARY", new Properties());
        props.put("SUPPLEMENTAL", new Properties());
        props.put("SOURCE", new Properties());
        packager.setAttachmentTypeNames(props);

        pkg = packager.generatePackage(sub);

        assertNotNull(pkg);
        assertEquals("File System",pkg.getFormat());
        assertNull(pkg.getMimeType());

        File exportFile = pkg.getFile();
        assertNotNull(exportFile);
        assertTrue("Package file does not exist", exportFile.exists());
        assertTrue("Package file is not readable", exportFile.canRead());
        assertTrue("Package should be a directory", exportFile.isDirectory());

        // The export is a directory of multiple files
        Map<String, String> fileMap = getDirectoryFileContents(exportFile);

        // There should be two files with the correct filenames and contents
        assertTrue(fileMap.containsKey("PRIMARY-DOCUMENT.pdf"));
        assertEquals("bottle", fileMap.get("PRIMARY-DOCUMENT.pdf"));
        assertTrue(fileMap.containsKey("fluff.jpg"));
        assertEquals("fluff", fileMap.get("fluff.jpg"));
	}

    // Test the standard File Packager in default configuration, but with zip output
	@Test public void testGeneratePackage_default_zip() throws IOException {
        // packager.setEntryName( -default- );
        packager.setPackageType(FilePackagerImpl.PackageType.zip);
        LinkedHashMap<String, Properties> props = new LinkedHashMap<String, Properties>(3);
        props.put("PRIMARY", new Properties());
        props.put("SUPPLEMENTAL", new Properties());
        props.put("SOURCE", new Properties());
        packager.setAttachmentTypeNames(props);

        pkg = packager.generatePackage(sub);

        assertNotNull(pkg);
        assertEquals("File System",pkg.getFormat());
        assertNull(pkg.getMimeType());

        File exportFile = pkg.getFile();
        assertNotNull(exportFile);
        assertTrue("Package file does not exist", exportFile.exists());
        assertTrue("Package file is not readable", exportFile.canRead());
        assertFalse("Package should not be a directory", exportFile.isDirectory());
        assertTrue("Package file should end in .zip", exportFile.getName().endsWith(".zip"));

        // The export is a zipped directory of multiple files
        Map<String, String> fileMap = getZipFileContents(exportFile);

        // There should be two files with the correct filenames and contents
        assertTrue(fileMap.containsKey("PRIMARY-DOCUMENT.pdf"));
        assertEquals("bottle", fileMap.get("PRIMARY-DOCUMENT.pdf"));
        assertTrue(fileMap.containsKey("fluff.jpg"));
        assertEquals("fluff", fileMap.get("fluff.jpg"));
	}

    // Test using entryName, custom filenames, and custom dirnames, with zipped output
	@Test public void testGeneratePackage_custom_zip() throws IOException {
        packager.setEntryName("entry-{"+STUDENT_EMAIL+"}-notvar-{"+FILE_NAME+'}');
        packager.setPackageType(FilePackagerImpl.PackageType.zip);
        LinkedHashMap<String, Properties> props = new LinkedHashMap<String, Properties>(3);
        Properties primaryProps = new Properties();
//primaryProps.setProperty("");
        props.put("PRIMARY", new Properties());
        props.put("SUPPLEMENTAL", new Properties());
        props.put("SOURCE", new Properties());
        packager.setAttachmentTypeNames(props);

        pkg = packager.generatePackage(sub);

        assertNotNull(pkg);
        assertEquals("File System", pkg.getFormat());
        assertNull(pkg.getMimeType());
        assertEquals("entry-email@email.com-notvar-", pkg.getEntryName());

        File exportFile = pkg.getFile();
        assertNotNull(exportFile);
        assertTrue("Package file does not exist", exportFile.exists());
        assertTrue("Package file is not readable", exportFile.canRead());
        assertFalse("Package should not be a directory", exportFile.isDirectory());
        assertTrue("Package file should end in .zip", exportFile.getName().endsWith(".zip"));

        // The export is a zipped directory of multiple files
        Map<String, String> fileMap = getZipFileContents(exportFile);

        // There should be two files with the correct filenames and contents
        assertTrue(fileMap.containsKey("PRIMARY-DOCUMENT.pdf"));
        assertEquals("bottle", fileMap.get("PRIMARY-DOCUMENT.pdf"));
        assertTrue(fileMap.containsKey("fluff.jpg"));
        assertEquals("fluff", fileMap.get("fluff.jpg"));
	}

	/**
	 * Creates a map of file names to their contents as strings
     * Maybe don't call this on non-test files that aren't short and simple.
	 * 
	 * @param targetDir
	 *            the source directory be parsed
	 * @return a map of file names to their string contents
     * @throws IOException if something can't be read
	 */
	public Map<String, String> getDirectoryFileContents(File targetDir) throws IOException {
		File[] contents = targetDir.listFiles();
		Map<String, String> fileContentsMap = new HashMap<String, String>(contents.length);
		for (File file : contents) {
			fileContentsMap.put(file.getName(), Utilities.fileToString(file));
		}
		return fileContentsMap;
	}

    /**
     * Creates a map of file names to their contents as strings, reading from a
     * zip file with several files inside.
     * Maybe don't call this on non-test files that aren't short and simple.
     *
     * @param zip the source zip directory be parsed
     * @return a map of file names to their string contents
     * @throws IOException if something can't be read
     */
    public Map<String, String> getZipFileContents(File zip) throws IOException {
        Map<String, String> fileContentsMap = new HashMap<String, String>(2);
        byte[] buffer = new byte[128];

        ZipFile zf = null;
        try {
            zf = new ZipFile(zip);
            final Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                final String filename = entry.getName();

                // read from entry into string
                InputStream zis = zf.getInputStream(entry);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                int len=0;
                while ((len = zis.read(buffer)) > 0) {
                    out.write(buffer, 0, len);
                }
                final String contents = out.toString("UTF-8");

                // got it
                fileContentsMap.put(filename, contents);
            }
        } finally {
            if (zf!=null) { zf.close(); }
        }
        return fileContentsMap;
    }

    // slower, for pre-Java 1.7
    public Map<String, String> getZipFileContentsOld(File zip) throws IOException {
        Map<String, String> fileContentsMap = new HashMap<String, String>(2);
        ZipInputStream zis = null;
        byte[] buffer = new byte[128];
        try {
            zis = new ZipInputStream(new FileInputStream(zip));
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                final String filename = entry.getName();
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                int len=0;
                while ((len = zis.read(buffer)) > 0) {
                    out.write(buffer, 0, len);
                }
                final String contents = out.toString("UTF-8");
                fileContentsMap.put(filename, contents);
            }
        } finally {
            if (zis!=null) { zis.close(); }
        }
        return fileContentsMap;
    }
}
