package org.tdl.vireo.export.impl;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.tdl.vireo.export.ExportPackage;
import org.tdl.vireo.model.*;
import org.tdl.vireo.model.jpa.JpaPersonRepositoryImpl;
import org.tdl.vireo.model.jpa.JpaSettingsRepositoryImpl;
import org.tdl.vireo.model.jpa.JpaSubmissionRepositoryImpl;
import org.tdl.vireo.proquest.ProquestVocabularyRepository;
import org.tdl.vireo.security.SecurityContext;
import org.tdl.vireo.services.Utilities;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;
import play.Logger;
import play.modules.spring.Spring;
import play.test.UnitTest;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.tdl.vireo.export.impl.AbstractPackagerImpl.PackageType.dir;
import static org.tdl.vireo.export.impl.AbstractPackagerImpl.PackageType.zip;

/**
 * Contains things that any tester of an AbstractPackagerImpl would want to have. Does not actually contain tests.
 */
public abstract class AbstractPackagerTest extends UnitTest {
	// All the repositories
	public static SecurityContext context = Spring.getBeanOfType(SecurityContext.class);
	public static JpaPersonRepositoryImpl personRepo = Spring.getBeanOfType(JpaPersonRepositoryImpl.class);
	public static JpaSubmissionRepositoryImpl subRepo = Spring.getBeanOfType(JpaSubmissionRepositoryImpl.class);
	public static JpaSettingsRepositoryImpl settingRepo = Spring.getBeanOfType(JpaSettingsRepositoryImpl.class);
    public static ProquestVocabularyRepository proquestRepo = Spring.getBeanOfType(ProquestVocabularyRepository.class);

	public Person person;
	public Submission sub;
    public ExportPackage pkg;
    public String primaryDocName = null;

    // XML stuff
    static DocumentBuilder builder = null;
    static XPath xpath = null;

    @BeforeClass
    public static void setupClass() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        builder = factory.newDocumentBuilder();
        xpath = XPathFactory.newInstance().newXPath();
    }

	/**
	 * Set up a submission so we can test packaging it up.
	 */
	@Before
	public void setup() throws IOException {
		context.turnOffAuthorization();
		person = personRepo.createPerson("netid", "email@email.com", "first", "last", RoleType.NONE).save();
		sub = subRepo.createSubmission(person);
        // fill out the submission more for packagers to draw on
        sub.setStudentLastName("last name");
        sub.setStudentFirstName("first name");
		sub.setStudentMiddleName("middle name");
		sub.setStudentBirthYear(2002);
		sub.setDocumentTitle("document title");
		sub.setDocumentAbstract("document abstract");
		sub.setDocumentKeywords("document keywords");
		sub.addDocumentSubject(proquestRepo.findAllSubjects().get(0).getDescription());
		sub.addDocumentSubject(proquestRepo.findAllSubjects().get(1).getDescription());
		sub.addDocumentSubject(proquestRepo.findAllSubjects().get(2).getDescription());
		sub.setDegree("selected degree");
		sub.setDegreeLevel(DegreeLevel.UNDERGRADUATE);
		sub.setDepartment("selected department");
		sub.setCollege("selected college");
		sub.setMajor("selected major");
		sub.setDocumentType("selected document type");
		sub.setGraduationMonth(0);
		sub.setGraduationYear(2002);
		sub.setDepositId("depositId");
        sub.setDocumentLanguage("en");

		// Create some attachments
		File bottle_pdf = Utilities.fileWithNameAndContents("bottle.pdf", "bottle");
        File fluff_jpg = Utilities.fileWithNameAndContents("fluff.jpg", "fluff");
        File license_txt = Utilities.fileWithNameAndContents("license.txt", "license");
        File source_pdf = Utilities.fileWithNameAndContents("source.pdf", "source");

		sub.addAttachment(bottle_pdf, AttachmentType.PRIMARY);
		sub.addAttachment(fluff_jpg, AttachmentType.SUPPLEMENTAL);
        sub.addAttachment(license_txt, AttachmentType.LICENSE);
        sub.addAttachment(source_pdf, AttachmentType.SOURCE);

        EmbargoType embargo = settingRepo.findAllEmbargoTypes().get(1);
        sub.setEmbargoType(embargo);

		sub.save();

        FileUtils.deleteQuietly(bottle_pdf);
        FileUtils.deleteQuietly(fluff_jpg);
        FileUtils.deleteQuietly(license_txt);
        FileUtils.deleteQuietly(source_pdf);

        // shared test subjects
        pkg = null;
        primaryDocName = "LASTNAME-SELECTEDDOCUMENTTYPE-2002"; // based on the info from above
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
            assertFalse(pkg.getFile().exists());
        }
	}
    
//////////////////////////////// helpers /////////////////////////////////

    /**
     * Convenience method to add one attachmentType with custom properties to a packager
     * @param packager packager to configure
     * @param attachmentType attachment type to add to the packager
     * @param customName the customName property, or null for none
     * @param directory the directory property, or null for none
     */
    public static void addAttachmentType(AbstractPackagerImpl packager, AttachmentType attachmentType, String customName, String directory) {
        Properties props = new Properties();
        if (customName != null) {
            props.setProperty(AbstractPackagerImpl.AttachmentPropertyKey.customName.name(), customName);
        }
        if (directory != null) {
            props.setProperty(AbstractPackagerImpl.AttachmentPropertyKey.directory.name(), directory);
        }
        LinkedHashMap<String, Properties> old = packager.attachmentAttributes;
        old.put(attachmentType.name(), props);
        packager.setAttachmentTypeNames(old);
    }

    /**
     * Asserts that a proper export was produced
     * @param pkg package to check
     * @param packageType dir or zip
     * @return the export's file
     */
    public static File assertExport(ExportPackage pkg, AbstractPackagerImpl.PackageType packageType) {
        final File exportFile = pkg.getFile();
        assertNotNull(exportFile);
        assertTrue("Package file does not exist", exportFile.exists());
        assertTrue("Package file is not readable", exportFile.canRead());
        if (packageType==dir) {
            assertTrue("Package should be a directory", exportFile.isDirectory());
            assertTrue("Package file should end in .dir", exportFile.getName().endsWith(".dir"));
        } else if (packageType==zip) {
            assertFalse("Package should not be a directory", exportFile.isDirectory());
            assertTrue("Package file should end in .zip", exportFile.getName().endsWith(".zip"));
        } else {
            fail("Unsupported package type");
        }
        return exportFile;
    }

    /**
     * Assert that package directories, files, and file contents are as expected for stock setup(), and packager
     * including PRIMARY and SUPPLEMENTAL with no Properties customization.
     * @param fileMap map of filenames to file contents
     * @throws IOException if can't read
     */
    public void assertStandardAttachments(AbstractPackagerImpl packager, Map<String, String> fileMap) throws IOException {
        if (packager.attachmentTypes.contains(AttachmentType.LICENSE)) {
            assertTrue(fileMap.containsKey("license.txt"));
            assertEquals("license", fileMap.remove("license.txt"));
        }
        if (packager.attachmentTypes.contains(AttachmentType.ADMINISTRATIVE)) {
        }
        if (packager.attachmentTypes.contains(AttachmentType.ARCHIVED)) {
        }
        if (packager.attachmentTypes.contains(AttachmentType.FEEDBACK)) {
        }
        if (packager.attachmentTypes.contains(AttachmentType.PRIMARY)) {
            assertTrue(fileMap.containsKey(primaryDocName+".pdf"));
            assertEquals("bottle", fileMap.remove(primaryDocName+".pdf"));
        }
        if (packager.attachmentTypes.contains(AttachmentType.SOURCE)) {
            assertTrue(fileMap.containsKey("source.pdf"));
            assertEquals("source", fileMap.remove("source.pdf"));
        }
        if (packager.attachmentTypes.contains(AttachmentType.SUPPLEMENTAL)) {
            assertTrue(fileMap.containsKey("fluff.jpg"));
            assertEquals("fluff", fileMap.remove("fluff.jpg"));
        }
        if (packager.attachmentTypes.contains(AttachmentType.UNKNOWN)) {
        }
    }

	/**
	 * Creates a map of file names to their contents as strings, traversing a starting
     * directory hierarchy.
     * Maybe don't call this on non-test files that aren't short and simple--it
     * doesn't try to be efficient with its String reading, for example.
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
    public static Map<String, String> getDirectoryFileContents(File targetDir) throws IOException {
        Logger.info("list dir: " + targetDir.getPath());
        return getDirectoryFileContents(targetDir, "");
    }
	private static Map<String, String> getDirectoryFileContents(File targetDir, String prefix) throws IOException {
		File[] files = targetDir.listFiles();
		Map<String, String> fileContentsMap = new HashMap<String, String>(files.length);
		for (File file : files) {
            String contents = null;
            if (!file.isDirectory()) {
                contents = Utilities.fileToString(file);
            } else {
                fileContentsMap.putAll(getDirectoryFileContents(file, prefix+file.getName()+File.separator));
            }
            Logger.info("  '" + prefix + file.getName() + "' => '" + (contents == null ? "null" : contents) + "'");
            fileContentsMap.put(prefix+file.getName(), contents);
		}
		return fileContentsMap;
	}

    /**
     * Creates a map of file names to their contents as strings, reading from a
     * zip file with files inside, potentially in directories.
     * Maybe don't call this on non-test files that aren't short and simple--it
     * doesn't try to be efficient with its String reading, for example.
     *
     * @param zip the source zip directory be parsed
     * @return a map of file names to their string contents. Contents of directories == null,
     * but files in the directory will have separate entries. For example,
     * "foo.txt" => "contents"
     * "myDir/" => null
     * "myDir/bar.txt" => "contents"
     * @throws IOException if something can't be read
     */
    public static Map<String, String> getZipFileContents(File zip) throws IOException {
        Logger.info("list zip: " + zip.getPath());

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
                Logger.info("  '" + filename + "' => '" + (contents == null ? "null" : contents) + "'");
                fileContentsMap.put(filename, contents);
            }
        } finally {
            if (zf!=null) { zf.close(); }
        }
        return fileContentsMap;
    }

    /**
     * Asserts the existence, name, and XML validity of the named XML file included in the export.
     * Removes the file contents from the map and returns its XML Document contents
     * for further examination.
     * @param fileMap map of filename to file contents
     * @param manifestName name of file in the map
     * @return parsed XML contents of the file
     * @throws IOException if can't read file
     * @throws org.xml.sax.SAXException if not valid XML
     */
    public static Document getFileXML(Map<String, String> fileMap, String manifestName) throws IOException, SAXException {
        assertTrue(fileMap.containsKey(manifestName));
        final String manifest = fileMap.remove(manifestName);
        return builder.parse(new ByteArrayInputStream(manifest.getBytes("UTF-8")));
    }
}
