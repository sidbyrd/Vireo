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
import org.tdl.vireo.services.StringVariableReplacement;
import org.tdl.vireo.services.Utilities;
import play.Logger;
import play.modules.spring.Spring;
import play.test.UnitTest;

import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import static org.tdl.vireo.export.impl.AbstractPackagerImpl.AttachmentPropertyKey.customName;
import static org.tdl.vireo.export.impl.AbstractPackagerImpl.AttachmentPropertyKey.directory;
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
        props.put(AttachmentType.PRIMARY.name(), new Properties());
        props.put(AttachmentType.SUPPLEMENTAL.name(), new Properties());
        props.put(AttachmentType.SOURCE.name(), new Properties());
        packager.setAttachmentTypeNames(props);

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
        assertTrue(fileMap.containsKey("PRIMARY-DOCUMENT.pdf"));
        assertEquals("bottle", fileMap.remove("PRIMARY-DOCUMENT.pdf"));
        assertTrue(fileMap.containsKey("fluff.jpg"));
        assertEquals("fluff", fileMap.remove("fluff.jpg"));
        assertEquals(0, fileMap.size()); // should be nothing else left
	}

    // Test the standard File Packager in default configuration, but with zip output
	@Test public void testGeneratePackage_default_zip() throws IOException {
        // packager.setEntryName( -default- );
        packager.setPackageType(zip);
        LinkedHashMap<String, Properties> props = new LinkedHashMap<String, Properties>(3);
        props.put(AttachmentType.PRIMARY.name(), new Properties());
        props.put(AttachmentType.SUPPLEMENTAL.name(), new Properties());
        props.put(AttachmentType.SOURCE.name(), new Properties());
        packager.setAttachmentTypeNames(props);

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
        assertTrue(fileMap.containsKey("PRIMARY-DOCUMENT.pdf"));
        assertEquals("bottle", fileMap.remove("PRIMARY-DOCUMENT.pdf"));
        assertTrue(fileMap.containsKey("fluff.jpg"));
        assertEquals("fluff", fileMap.remove("fluff.jpg"));
        assertEquals(0, fileMap.size()); // should be nothing else left
	}

    // Test using entryName, custom filenames, and custom dirnames, with both "dir" and "zip" output.
	@Test public void testGeneratePackage_allOptions() throws IOException {
        packager.setEntryName("entry-{"+STUDENT_EMAIL+"}-notvar-{"+FILE_NAME+FALLBACK+"fallback}"); // FILE_NAME is not valid on anything but files; replaced with "fallback".
        packager.setPackageType(dir);
        LinkedHashMap<String, Properties> props = new LinkedHashMap<String, Properties>(3);
        final Properties primaryProps = new Properties();
        primaryProps.setProperty(customName.name(), "primaryFile-{"+FILE_NAME+"}-{"+STUDENT_EMAIL+'}'); // FILE_NAME still gets "PRIMARY-DOCUMENT", not "bottle".
        primaryProps.setProperty(directory.name(), "primaryDir-{"+STUDENT_EMAIL+'}');
        props.put(AttachmentType.PRIMARY.name(), primaryProps);
        final Properties supplementalProps = new Properties();
        supplementalProps.setProperty(customName.name(), "supplementalFile-{"+FILE_NAME+"}-end"); // FILE_NAME gets "fluff".
        supplementalProps.setProperty(directory.name(), "supplementalDir-{" + FILE_NAME + '}');
        props.put(AttachmentType.SUPPLEMENTAL.name(), supplementalProps);
        packager.setAttachmentTypeNames(props);

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

        final String primaryFileName = primaryFileDir+File.separator+"primaryFile-PRIMARY-DOCUMENT-email@email.com.pdf";
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

	/**
	 * Creates a map of file names to their contents as strings
     * Maybe don't call this on non-test files that aren't short and simple.
	 * 
	 * @param targetDir
	 *            the source directory be parsed
     * @return a map of file names to their string contents. Contents of directories == null,
     * but files in the directory will have separate entries. For example,
     * "foo.txt" => "contents"
     * "myDir/" => null
     * "myDir/bar.txt" => "contents"
     * @throws IOException if something can't be read
	 */
    public Map<String, String> getDirectoryFileContents(File targetDir) throws IOException {
        Logger.debug("list dir: "+targetDir.getPath());
        return getDirectoryFileContents(targetDir, "");
    }
	private Map<String, String> getDirectoryFileContents(File targetDir, String prefix) throws IOException {
		File[] files = targetDir.listFiles();
		Map<String, String> fileContentsMap = new HashMap<String, String>(files.length);
		for (File file : files) {
            String contents = null;
            if (!file.isDirectory()) {
                contents = Utilities.fileToString(file);
            } else {
                fileContentsMap.putAll(getDirectoryFileContents(file, prefix+file.getName()+File.separator));
            }
            Logger.debug("  '"+prefix+file.getName()+"' => '"+(contents==null?"null":contents)+"'");
            fileContentsMap.put(prefix+file.getName(), contents);
		}
		return fileContentsMap;
	}

    /**
     * Creates a map of file names to their contents as strings, reading from a
     * zip file with files inside, potentially in directories.
     * Maybe don't call this on non-test files that aren't short and simple.
     *
     * @param zip the source zip directory be parsed
     * @return a map of file names to their string contents. Contents of directories == null,
     * but files in the directory will have separate entries. For example,
     * "foo.txt" => "contents"
     * "myDir/" => null
     * "myDir/bar.txt" => "contents"
     * @throws IOException if something can't be read
     */
    public Map<String, String> getZipFileContents(File zip) throws IOException {
        Logger.debug("list zip: "+zip.getPath());

        Map<String, String> fileContentsMap = new HashMap<String, String>(2);
        byte[] buffer = new byte[128];

        ZipFile zf = null;
        try {
            zf = new ZipFile(zip);
            final Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                final String filename = entry.getName();

                String contents = null;
                if (!entry.isDirectory()) {
                    // read from entry into string
                    InputStream zis = zf.getInputStream(entry);
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    int len=0;
                    while ((len = zis.read(buffer)) > 0) {
                        out.write(buffer, 0, len);
                    }
                    contents = out.toString("UTF-8");
                }

                // got it
                Logger.debug("  '"+filename+"' => '"+(contents==null?"null":contents)+"'");
                fileContentsMap.put(filename, contents);
            }
        } finally {
            if (zf!=null) { zf.close(); }
        }
        return fileContentsMap;
    }
}
