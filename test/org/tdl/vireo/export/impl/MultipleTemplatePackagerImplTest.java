package org.tdl.vireo.export.impl;

import org.jdom.JDOMException;
import org.junit.Test;
import org.tdl.vireo.export.ExportPackage;
import play.modules.spring.Spring;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * Test the multiple template packager.
 */
public class MultipleTemplatePackagerImplTest extends AbstractPackagerTest {

	/**
	 * Test each packager handling of the submission.
	 */
	@Test
	public void testPackager() throws IOException, JDOMException {
		// Test all the template packagers
		MultipleTemplatePackagerImpl packager = (MultipleTemplatePackagerImpl)Spring.getBean("DSpaceSimpleArchive");
		
        ExportPackage pkg = packager.generatePackage(sub);
        assertNotNull(pkg);
        assertEquals(packager.format,pkg.getFormat());

        File exportFile = pkg.getFile();
        assertNotNull(exportFile);
        assertTrue("Package file does not exist", exportFile.exists());
        assertTrue("Package file is not readable", exportFile.canRead());
        assertTrue("Package should be a directory: "+exportFile.getPath(), exportFile.isDirectory());

        Map<String, String> fileMap = getDirectoryFileContents(exportFile);

        // There should be three files
        assertTrue(fileMap.containsKey("LASTNAME-SELECTEDDOCUMENTTYPE-2002.pdf"));
        assertTrue(fileMap.containsKey("fluff.jpg"));

        // Check that each of the templates exist
        for (String name : packager.templates.keySet()) {
            assertTrue(fileMap.containsKey(name));
        }

        // Cleanup
        pkg.delete();
        assertFalse(exportFile.exists());
	}
}
