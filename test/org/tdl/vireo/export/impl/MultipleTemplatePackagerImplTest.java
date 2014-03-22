package org.tdl.vireo.export.impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.jdom.JDOMException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.tdl.vireo.export.ExportPackage;
import org.tdl.vireo.model.AttachmentType;
import org.tdl.vireo.model.DegreeLevel;
import org.tdl.vireo.model.Person;
import org.tdl.vireo.model.RoleType;
import org.tdl.vireo.model.Submission;
import org.tdl.vireo.model.jpa.JpaPersonRepositoryImpl;
import org.tdl.vireo.model.jpa.JpaSettingsRepositoryImpl;
import org.tdl.vireo.model.jpa.JpaSubmissionRepositoryImpl;
import org.tdl.vireo.proquest.ProquestVocabularyRepository;
import org.tdl.vireo.security.SecurityContext;

import play.Logger;
import play.modules.spring.Spring;
import play.test.UnitTest;

/**
 * Test the multiple template packager.
 * 
 * Since there are expected to be lots of various formats, this test just runs
 * through the basics. It checks that the files were created and not much more.
 * 
 * @author <a href="http://www.scottphillips.com">Scott Phillips</a>
 * 
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
