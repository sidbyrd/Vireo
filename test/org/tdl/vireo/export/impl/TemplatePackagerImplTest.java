package org.tdl.vireo.export.impl;

import org.jdom.Document;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.junit.Before;
import org.junit.Test;
import org.tdl.vireo.model.AttachmentType;
import org.tdl.vireo.model.PersonRepository;
import org.tdl.vireo.model.SettingsRepository;
import org.tdl.vireo.model.SubmissionRepository;
import org.tdl.vireo.proquest.ProquestVocabularyRepository;
import org.tdl.vireo.services.Utilities;
import play.Logger;
import play.Play;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.util.Enumeration;
import java.util.Map;

/**
 * Test the generic template package.
 * 
 * Creates several TemplatePackagers with known configurations designed to test all the functions of a FilePackagerImpl,
 * generates the output, and asserts that it's all as expected. Also tests a recreation of each important TemplatePackager
 * in the default configuration. This double checks that they all work, while allowing customization and addition of new,
 * unexpected TemplatePackager configurations without breaking the test.
 */
public class TemplatePackagerImplTest extends AbstractPackagerTest { // subclass to get test helpers and share setup

    TemplatePackagerImpl packager = null;

    @Before
    @Override
    public void setup() throws IOException {
        super.setup();

        // pass along the dependencies that Spring injected into me.
        packager = new TemplatePackagerImpl();
        packager.personRepo = personRepo;
        packager.subRepo = subRepo;
        packager.settingRepo = settingRepo;
        packager.proquestRepo = proquestRepo;
    }

    // Test the standard VireoExport packager in default configuration
    @Test public void test_stock_VireoExport() throws IOException, JDOMException {
        packager.setDisplayName("Vireo Export");
        packager.setFormat("http://purl.org/dc/elements/1.1/");
        packager.setManifestTemplatePath("conf/formats/vireo.xml");
        packager.setManifestName("vireo.xml");
        for (AttachmentType at : AttachmentType.values()) {
            addAttachmentType(packager, at, null, null);
        }
        pkg = packager.generatePackage(sub);

        // test package properties
        assertNotNull(pkg);
        assertEquals("http://purl.org/dc/elements/1.1/", pkg.getFormat());
        assertNull(pkg.getMimeType());
        assertNull(pkg.getEntryName());

        // test main package directory file
        File exportFile = pkg.getFile();
        assertNotNull(exportFile);
        assertTrue("Package file does not exist", exportFile.exists());
        assertTrue("Package file is not readable", exportFile.canRead());
        assertTrue("Package should be a directory", exportFile.isDirectory());

        // test all expected directories, filenames and file contents
        Map<String, String> fileMap = beyondStandardContents(exportFile, AbstractPackagerImpl.PackageType.dir);

        // Load up the manifest and make sure it's valid XML.
        assertTrue(fileMap.containsKey("vireo.xml"));
        final String manifest = fileMap.remove("vireo.xml");
        SAXBuilder builder = new SAXBuilder();
        Document doc = builder.build(new StringReader(manifest));

        // Check that the manifest contains important data
        assertTrue(manifest.contains(sub.getStudentFirstName()));
        assertTrue(manifest.contains(sub.getStudentLastName()));
        assertTrue(manifest.contains(sub.getDocumentTitle()));
        assertTrue(manifest.contains(sub.getDocumentAbstract()));

        assertEquals(0, fileMap.size()); // correct number of leftover files == 0
    }
}
