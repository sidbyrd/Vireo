package org.tdl.vireo.export.impl;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.tdl.vireo.model.AttachmentType;
import org.tdl.vireo.services.Utilities;
import org.w3c.dom.Document;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.tdl.vireo.export.impl.AbstractPackagerImpl.PackageType.dir;
import static org.tdl.vireo.export.impl.AbstractPackagerImpl.PackageType.zip;
import static org.tdl.vireo.export.impl.AbstractPackagerImpl.FILE_NAME;
import static org.tdl.vireo.model.AttachmentType.*;
import static org.tdl.vireo.services.StringVariableReplacement.TEMPLATE_MODE;
import static org.tdl.vireo.services.SubmissionParams.Variable.*;

/**
 * Test the multiple template packager, including one that recreates the default DSpaceSimpleArchive packager.
 */
public class MultipleTemplatePackagerImplTest extends AbstractPackagerTest {

	MultipleTemplatePackagerImpl packager = null;

	@Before
	@Override
	public void setup() throws IOException {
		super.setup();

		packager = new MultipleTemplatePackagerImpl();
		// pass along the dependencies that Spring injected into me.
		packager.personRepo = personRepo;
		packager.subRepo = subRepo;
		packager.settingRepo = settingRepo;
		packager.proquestRepo = proquestRepo;
	}

	// Configure with test template that uses every available template variable
	// Also test custom entry and template names.
	@Test public void testAllTemplateVariables() throws Exception {
		packager.setFormat("testFormat");
		packager.setEntryName("custom-{" + STUDENT_NETID + '}');
		Map<String, String> templatePaths = new HashMap<String, String>(4);
		templatePaths.put("test-{"+LAST_NAME+"}.xml", "test/org/tdl/vireo/export/impl/TestMultipleTemplate.xml".replace('/', File.separatorChar));
		templatePaths.put("qdc-{"+LAST_NAME+"}.xml", "conf/formats/qdc.xml".replace('/', File.separatorChar));
		packager.setTemplatePaths(templatePaths);
		Map<String, Object> templateArgs = new HashMap<String, Object>(1);
		templateArgs.put("customArg", "verified");
		packager.setTemplateArguments(templateArgs);
		addAttachmentType(packager, PRIMARY, null, null);
		final File exportFile = generateFileAndAssertPackageBasics("custom-netid");

		// Test files and directories in the export
		final Map<String, String> fileMap = getDirectoryFileContents(exportFile);
		assertStandardAttachments(packager, fileMap);

		// Briefly check the QDC file, to prove that having multiple different templates works and customized template names work
		Document template = getFileXML(fileMap, "qdc-last name.xml"); // customized in filename
		assertNotNull(xpath.evaluate("/metadata", template).trim());

		// Test that every template var was available
		template = getFileXML(fileMap, "test-last name.xml"); // customized in filename
		assertEquals(0, fileMap.size()); // no leftover files
		// Test injected vars
		assertEquals(String.valueOf(sub.hashCode()), xpath.evaluate("/test/sub", template));
		assertEquals(String.valueOf(subRepo.hashCode()), xpath.evaluate("/test/subRepo", template));
		assertEquals(String.valueOf(personRepo.hashCode()), xpath.evaluate("/test/personRepo", template));
		assertEquals(String.valueOf(settingRepo.hashCode()), xpath.evaluate("/test/settingRepo", template));
		assertEquals(String.valueOf(proquestRepo.hashCode()), xpath.evaluate("/test/proquestRepo", template));
		// Test vars from packager
		assertEquals(dir.name(), xpath.evaluate("/test/packageType", template));
		assertEquals("testFormat", xpath.evaluate("/test/format", template));
		assertEquals("custom-netid", xpath.evaluate("/test/entryName", template));
		assertEquals(PRIMARY.name(), xpath.evaluate("/test/attachmentType[1]", template));
		assertEquals("test-{"+LAST_NAME+"}.xml", xpath.evaluate("/test/currentTemplate", template)); // not customized as 'template'
		assertEquals("test-{"+LAST_NAME+"}.xml", xpath.evaluate("/test/template[@current='true']", template)); // not customized as key in 'templates'
		assertEquals("qdc-{"+LAST_NAME+"}.xml", xpath.evaluate("/test/template[@current='false']", template)); // not customized as key in 'templates'
		// Test custom template args
		assertEquals("verified", xpath.evaluate("/test/custom", template));
	}

	// Test the standard DSpaceSimpleArchive packager in default configuration
	@Test public void test_stock_DSpaceSimpleArchive() throws Exception {
		packager.setDisplayName("DSpace Simple Archive");
		packager.setFormat("http://www.dspace.org/xmlns/dspace");
		Map<String, String> templatePaths = new HashMap<String, String>(4);
		templatePaths.put("dublin_core.xml", "conf/formats/dspace_simple.xml");
		templatePaths.put("metadata_thesis.xml", "conf/formats/dspace_simple.xml");
		templatePaths.put("metadata_local.xml", "conf/formats/dspace_simple.xml");
		templatePaths.put("contents", "conf/formats/dspace_simple.xml");
		packager.setTemplatePaths(templatePaths);
		addAttachmentType(packager, PRIMARY, null, null);
		addAttachmentType(packager, SUPPLEMENTAL, null, null);
		addAttachmentType(packager, LICENSE, null, null);
		addAttachmentType(packager, SOURCE, null, null);
		final File exportFile = generateFileAndAssertPackageBasics(null);

		// test all expected directories, filenames and file contents
		final Map<String, String> fileMap = getDirectoryFileContents(exportFile);
		assertStandardAttachments(packager, fileMap);

		// test each templated metadata file
		Document doc = getFileXML(fileMap, "dublin_core.xml");
		assertEquals(sub.getDocumentTitle(), xpath.evaluate("/dublin_core/dcvalue[@element='title']", doc).trim());
		assertEquals("application/pdf", xpath.evaluate("/dublin_core/dcvalue[@element='format'][@qualifier='mimetype']", doc).trim());
		assertEquals("en", xpath.evaluate("/dublin_core/dcvalue[@element='language'][@qualifier='iso']", doc).trim());

		doc = getFileXML(fileMap, "metadata_thesis.xml");
		assertEquals(sub.getDegree(), xpath.evaluate("/dublin_core[@schema='degree']/dcvalue[@element='degree'][@qualifier='name']", doc).trim());

		doc = getFileXML(fileMap, "metadata_local.xml");
		assertNotNull("Should have local.embargo.terms", xpath.evaluate("/dublin_core[@schema='local']/dcvalue[@element='embargo'][@qualifier='terms']", doc).trim());

		// test contents file
		assertTrue(fileMap.containsKey("contents"));
		String contents = fileMap.remove("contents");
		assertTrue("contents=" + contents, contents.contains("LASTNAME-SELECTEDDOCUMENTTYPE-2002.pdf\tbundle:CONTENT\tprimary:true"));
		assertTrue("contents=" + contents, contents.contains("fluff.jpg\tbundle:CONTENT"));
		assertTrue("contents=" + contents, contents.contains("source.pdf\tbundle:CONTENT"));
		assertTrue("contents=" + contents, contents.contains("license.txt\tbundle:LICENSE"));

		assertEquals(0, fileMap.size()); // no leftover files
	}

	// Test that using the {FILE_NAME} replacement in the directory name of an attachment type
	// can put multiple attachments of the same AttachmentType into different directories.
	@Test public void testSameAttachmentTypeDifferentDirs() throws Exception {
		packager.setFormat("test");
		Map<String, String> templatePaths = new HashMap<String, String>(1);
		templatePaths.put("test.xml", "test/org/tdl/vireo/export/impl/TestMultipleTemplate.xml".replace('/', File.separatorChar));
		packager.setTemplatePaths(templatePaths);
		addAttachmentType(packager, SUPPLEMENTAL, "file-{" + FILE_NAME + "}-custom", "dir-{" + FILE_NAME + "}-custom");
		File suppl2 = Utilities.fileWithNameAndContents("second.gif", "second");
		sub.addAttachment(suppl2, AttachmentType.SUPPLEMENTAL);
		sub.save();
		FileUtils.deleteQuietly(suppl2);
		final File exportFile = generateFileAndAssertPackageBasics(null);

		// test all expected directories, filenames and file contents
		final Map<String, String> fileMap = getDirectoryFileContents(exportFile);

		final String dir1="dir-fluff-custom";
		assertTrue(fileMap.containsKey(dir1));
		assertNull(fileMap.remove(dir1));
		final String file1=dir1+File.separator+"file-fluff-custom.jpg";
		assertTrue(fileMap.containsKey(file1));
		assertEquals("fluff", fileMap.remove(file1));

		final String dir2="dir-second-custom";
		assertTrue(fileMap.containsKey(dir2));
		assertNull(fileMap.remove(dir2));
		final String file2=dir2+File.separator+"file-second-custom.gif";
		assertTrue(fileMap.containsKey(file2));
		assertEquals("second", fileMap.remove(file2));

		// don't actually care about template file
		getFileXML(fileMap, "test.xml");
		assertEquals(0, fileMap.size()); // no leftover files
	}

	// Test renaming of colliding template and attachment filenames
	@Test public void testFilenameCollision() throws Exception {

		// do for both package types (dir and zip)
		for(AbstractPackagerImpl.PackageType packageType : AbstractPackagerImpl.PackageType.values()) {

			packager.setPackageType(packageType);
			packager.setFormat("test");
			Map<String, String> templatePaths = new HashMap<String, String>(2);
			// template names must be unique to start, but these will be the same after customization
			templatePaths.put("filename-collision-last name.pdf", "test/org/tdl/vireo/export/impl/TestMultipleTemplate.xml".replace('/', File.separatorChar));
			templatePaths.put("filename-collision-{"+LAST_NAME+"}.pdf", "test/org/tdl/vireo/export/impl/TestMultipleTemplate.xml".replace('/', File.separatorChar));
			packager.setTemplatePaths(templatePaths);
			addAttachmentType(packager, PRIMARY, "filename-collision-{"+LAST_NAME+'}', null); // is a .pdf
			addAttachmentType(packager, SOURCE,  "filename-collision-{"+LAST_NAME+'}', null); // is a .pdf
			File exportFile = generateFileAndAssertPackageBasics(null);

			// We should have a four-way filename collision of two templates and two attachments.
			Map<String, String> fileMap = getExportPackageFileContents(exportFile, packageType);

			// Get templates
			String file0="filename-collision-last name.pdf";
			Document doc0 = getFileXML(fileMap, file0);
			String templateArg0 = xpath.evaluate("/test/currentTemplate".replace('/', File.separatorChar), doc0);
			String file1="filename-collision-last name_1.pdf";
			Document doc1 = getFileXML(fileMap, file1);
			String templateArg1 = xpath.evaluate("/test/currentTemplate".replace('/', File.separatorChar), doc1);

			// It isn't defined which template will be which, but they should be the first two.
			// Each template should get its original un-customized, un-de-duped template name in the template arg 'template'.
			assertTrue(templateArg0.equals("filename-collision-last name.pdf") && templateArg1.equals("filename-collision-{" + LAST_NAME + "}.pdf") ||
					   templateArg1.equals("filename-collision-last name.pdf") && templateArg0.equals("filename-collision-{" + LAST_NAME + "}.pdf"));

			// Get attachments
			String file2="filename-collision-last name_2.pdf";
			assertTrue(fileMap.containsKey(file2));
			String file2contents=fileMap.remove(file2);
			assertNotNull(file2contents);
			String file3="filename-collision-last name_3.pdf";
			assertTrue(fileMap.containsKey(file3));
			String file3contents=fileMap.remove(file3);
			assertNotNull(file3contents);
			assertEquals(0, fileMap.size()); // no leftover files

			// It isn't defined which attachment will be which, but they should be the second pair.
			assertTrue( file2contents.equals("bottle") && file3contents.equals("source") ||
						file3contents.equals("bottle") && file2contents.equals("source"));
		}
	}

	/**
	 * There are four customizable (i.e. string parameter replaced) fields where, once customization
	 * is complete, if they have a / in the output, it must be replaced with -. That's a nice
	 * runtime workaround that's beats failing, but it is still undesirable. Those fields should
	 * not be knowingly initialized with something that will result in / .
	 * @throws IOException
	 */
	@Test public void testFileSeparators_configError() throws IOException {
		Map<String, String> templatePaths = new HashMap<String, String>(1);

		try {
			packager.setEntryName("no"+File.separator+"slash");
			fail("Should not be able to set entry with known "+File.separator+" in it.");
		} catch (IllegalArgumentException e) { /**/ }
		try {
			templatePaths.put("no"+File.separator+"slash", "test/org/tdl/vireo/export/impl/TestMultipleTemplate.xml".replace('/', File.separatorChar));
			packager.setTemplatePaths(templatePaths);
			fail("Should not be able to set template name with known "+File.separator+" in it.");
		} catch (IllegalArgumentException e) { /**/ }
		try {
			addAttachmentType(packager, PRIMARY, "no"+File.separator+"slash", null);
			fail("Should not be able to custom attachment name with known "+File.separator+" in it.");
		} catch (IllegalArgumentException e) { /**/ }
		try {
			addAttachmentType(packager, PRIMARY, null, "no"+File.separator+"slash");
			fail("Should not be able to set attachment directory with known "+File.separator+" in it.");
		} catch (IllegalArgumentException e) { /**/ }

		// But it should be legal to do those things if the input uses template mode for
		// customization, because templates can contain / without outputting / .
		packager.setEntryName(TEMPLATE_MODE+"no"+File.separator+"slash");
		templatePaths.clear();
		templatePaths.put(TEMPLATE_MODE + "no" + File.separator + "slash", "test/org/tdl/vireo/export/impl/TestMultipleTemplate.xml".replace('/', File.separatorChar));
		packager.setTemplatePaths(templatePaths);
		addAttachmentType(packager, PRIMARY, TEMPLATE_MODE+"no"+File.separator+"slash", TEMPLATE_MODE+"no"+File.separator+"slash");
	}

	// If a File.separator sneaks in somewhere it shouldn't be as a result of customization when generatePackage is
	// called (not at config time), make sure it gets handled elegantly without failing (by replacing / with -).
	@Test public void testFileSeparators_customizationReplacement() throws IOException {
		sub.setStudentFirstName("Annoying"+File.separator+"Name"); // at least it beats Robert'); DROP TABLE Students; -- a.k.a. Bobby Tables.
		sub.save();

		packager.setFormat("testFormat");
		packager.setEntryName("entry-{"+FIRST_NAME+'}');
		Map<String, String> templatePaths = new HashMap<String, String>(4);
		templatePaths.put("template-{" + FIRST_NAME + "}.xml", "test/org/tdl/vireo/export/impl/TestMultipleTemplate.xml".replace('/', File.separatorChar));
		packager.setTemplatePaths(templatePaths);
		addAttachmentType(packager, PRIMARY, "file-{"+FIRST_NAME+'}', "dir-{"+FIRST_NAME+'}');

		// fixed entry name
		final File exportFile = generateFileAndAssertPackageBasics("entry-Annoying-Name");

		// fixed attachment dir
		final Map<String, String> fileMap = getDirectoryFileContents(exportFile);
		assertTrue(fileMap.containsKey("dir-Annoying-Name"));
		assertNull(fileMap.remove("dir-Annoying-Name"));

		// fixed attachment filename
		assertNotNull(fileMap.remove("dir-Annoying-Name/file-Annoying-Name.pdf".replace('/', File.separatorChar)));

		// fixed template name
		assertNotNull(fileMap.remove("template-Annoying-Name.xml"));
		assertEquals(0, fileMap.size()); // no leftover files
	}

	@Test public void testGeneratePackageErrors() {
		// submission = null
		try {
			packager.generatePackage(null);
			fail("Should not generate package with no submission");
		} catch (IllegalArgumentException e) {
			assertTrue(e.getMessage().contains("submission is null"));
		}

		// templates is empty
		try {
			packager.generatePackage(sub);
			fail("Should not generate package with no templates");
		} catch (IllegalStateException e) {
			assertTrue(e.getMessage().contains("no template file"));
		}

		// no format
		Map<String, String> templatePaths = new HashMap<String, String>(4);
		templatePaths.put("contents", "conf/formats/dspace_simple.xml".replace('/', File.separatorChar));
		packager.setTemplatePaths(templatePaths);
		try {
			packager.generatePackage(sub);
			fail("Should not generate package with no format name");
		} catch (IllegalStateException e) {
			assertTrue(e.getMessage().contains("no package format name"));
		}
	}

	// Test that a configuration with a single template and no attachments doesn't bother making a directory for
	// exports with package type dir, but that a container zip archive is still made for package type zip.
	@Test public void testSingleTemplateContainer() throws Exception {
		packager.setFormat("test");
		Map<String, String> templatePaths = new HashMap<String, String>(4);
		templatePaths.put("test.xml", "test/org/tdl/vireo/export/impl/TestMultipleTemplate.xml".replace('/', File.separatorChar));
		packager.setTemplatePaths(templatePaths);

		// Since there are no attachments, we expect a single XML file instead of a directory.
		pkg = packager.generatePackage(sub);
		assertEquals(null, pkg.getEntryName());
		assertNotNull(pkg);
		assertEquals(packager.format, pkg.getFormat());
		File exportFile = pkg.getFile();
		assertNotNull(exportFile);
		assertTrue("Package file does not exist", exportFile.exists());
		assertTrue("Package file is not readable", exportFile.canRead());
		assertFalse("Package should not be a directory", exportFile.isDirectory());
		assertTrue("Package file should end in .xml", exportFile.getName().endsWith(".xml"));

		// test expected filename and contents
		Document doc = builder.parse(exportFile);
		assertEquals(dir.name(), xpath.evaluate("/test/packageType", doc));

		// now test the same setup with the zip package format
		packager.setPackageType(zip);
		exportFile = generateFileAndAssertPackageBasics(null);

		final Map<String, String> fileMap = getZipFileContents(exportFile);
		doc = getFileXML(fileMap, "test.xml");
		assertEquals(zip.name(), xpath.evaluate("/test/packageType", doc));
		assertEquals(0, fileMap.size()); // no leftover files
	}

//////////////////////////////// helpers /////////////////////////////

	/**
	 * Generates the ExportPackage and saves it in field so cleanup() can clean it.
	 * Asserts that the generated package and the File it contains have all the right properties.
	 * Returns the File from the export, which will be either a zip or a dir.
	 * @param entryName the correct entryName for the generated package
	 * @return the File from the generated package
	 */
	public File generateFileAndAssertPackageBasics(String entryName) {
		pkg = packager.generatePackage(sub);
		assertEquals(entryName, pkg.getEntryName());
		assertNotNull(pkg);
		assertEquals(packager.format, pkg.getFormat());
		assertEquals(packager.mimeType, pkg.getMimeType());
		return assertExport(packager.packageType);
	}
}
