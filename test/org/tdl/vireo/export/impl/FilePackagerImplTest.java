package org.tdl.vireo.export.impl;

import org.junit.Test;
import org.tdl.vireo.model.AttachmentType;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import static org.tdl.vireo.export.impl.AbstractPackagerImpl.FILE_NAME;
import static org.tdl.vireo.export.impl.AbstractPackagerImpl.PackageType.dir;
import static org.tdl.vireo.export.impl.AbstractPackagerImpl.PackageType.zip;
import static org.tdl.vireo.services.SubmissionParams.Variable.STUDENT_EMAIL;

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
	@Test public void testGeneratePackage_stock_dir() throws IOException {
		FilePackagerImpl packager = new FilePackagerImpl();
		// packager.setEntryName( -default- );
		// packager.setPackageType( -default==dir- );
		addAttachmentType(packager, AttachmentType.PRIMARY, null, null);
		addAttachmentType(packager, AttachmentType.SUPPLEMENTAL, null, null);
		addAttachmentType(packager, AttachmentType.SOURCE, null, null);
		File exportFile = generateFileAndAssertPackageBasics(packager, null);

		// test all expected filenames and file contents
		Map<String, String> fileMap = getDirectoryFileContents(exportFile);
		assertStandardAttachments(packager, fileMap);
		assertEquals(0, fileMap.size()); // no leftover files
	}

	// Test the standard File Packager in default configuration, but with zip output
	@Test public void testGeneratePackage_stock_zip() throws IOException {
		FilePackagerImpl packager = new FilePackagerImpl();
		// packager.setEntryName( -default- );
		packager.setPackageType(zip);
		addAttachmentType(packager, AttachmentType.PRIMARY, null, null);
		addAttachmentType(packager, AttachmentType.SUPPLEMENTAL, null, null);
		addAttachmentType(packager, AttachmentType.SOURCE, null, null);
		File exportFile = generateFileAndAssertPackageBasics(packager, null);

		// test all expected filenames and file contents
		Map<String, String> fileMap = getZipFileContents(exportFile);
		assertStandardAttachments(packager, fileMap);
		assertEquals(0, fileMap.size()); // no leftover files
	}

	// Test using entryName, custom filenames, and custom dirnames, with both "dir" and "zip" output.
	@Test public void testGeneratePackage_allOptions() throws IOException {
		FilePackagerImpl packager = new FilePackagerImpl();
		// {FILE_NAME} is not valid since entryName isn't a file. It won't even be recognized as a variable, so left alone.
		packager.setEntryName("entry-{" + STUDENT_EMAIL + "}-notvar-{"+ FILE_NAME +'}');
		packager.setPackageType(dir);
		addAttachmentType(packager, AttachmentType.PRIMARY,
				"primaryFile-{" + FILE_NAME + "}-{" + STUDENT_EMAIL + '}', /* FILE_NAME still gets "PRIMARY-DOCUMENT", not "bottle" */
				"primaryDir-{" + STUDENT_EMAIL + '}');
		addAttachmentType(packager, AttachmentType.SUPPLEMENTAL,
				"supplementalFile-{" + FILE_NAME + "}-end", /* FILE_NAME gets "fluff" */
				"supplementalDir-{" + FILE_NAME + '}');
		File exportFile = generateFileAndAssertPackageBasics(packager, "entry-email@email.com-notvar-{"+ FILE_NAME +'}');

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

		assertEquals(0, fileMap.size()); // no leftover files

		////////////////////////////////////////////////////////////////////
		// Use the same setup, but do it again, this time with .zip output
		////////////////////////////////////////////////////////////////////

		pkg.delete();
		assertFalse(pkg.getFile().exists());

		packager.setPackageType(zip);
		exportFile = generateFileAndAssertPackageBasics(packager, "entry-email@email.com-notvar-{"+ FILE_NAME +'}');

		// test all expected directories, filenames and file contents
		fileMap = getZipFileContents(exportFile);
		if (fileMap.containsKey(primaryFileDir)) {
			// explicit directory entries in zip files are optional
			assertEquals(null, fileMap.remove(primaryFileDir));
		}
		assertTrue(fileMap.containsKey(primaryFileName));
		assertEquals("bottle", fileMap.remove(primaryFileName));

		if (fileMap.containsKey(supplementalFileDir)) {
			// explicit directory entries in zip files are optional
			assertEquals(null, fileMap.remove(supplementalFileDir));
		}
		assertTrue(fileMap.containsKey(supplementalFileName));
		assertEquals("fluff", fileMap.remove(supplementalFileName));

		assertEquals(0, fileMap.size()); // no leftover files
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

////////////////////////// helper /////////////////////////////

	/**
	 * Generates the ExportPackage and saves it in field so cleanup() can clean it.
	 * Asserts that the generated package and the File it contains have all the right properties.
	 * Returns the File from the export, which will be either a zip or a dir.
	 * @param packager the packager to test
	 * @param entryName the correct entryName for the generated package
	 * @return the File from the generated package
	 */
	public File generateFileAndAssertPackageBasics(FilePackagerImpl packager, String entryName) {
		pkg = packager.generatePackage(sub);
		assertEquals(entryName, pkg.getEntryName());
		assertNotNull(pkg);
		assertEquals("File System", pkg.getFormat());
		assertNull(pkg.getMimeType());
		return assertExport(packager.packageType);
	}
}
