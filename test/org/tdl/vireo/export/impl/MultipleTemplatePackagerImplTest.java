package org.tdl.vireo.export.impl;

import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.tdl.vireo.model.AttachmentType.*;

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
        final File exportFile = generateFileAndAssertPackageBasics(packager, null);

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
        assertTrue("contents="+contents, contents.contains("LASTNAME-SELECTEDDOCUMENTTYPE-2002.pdf\tbundle:CONTENT\tprimary:true"));
        assertTrue("contents="+contents, contents.contains("fluff.jpg\tbundle:CONTENT"));
        assertTrue("contents="+contents, contents.contains("source.pdf\tbundle:CONTENT"));
        assertTrue("contents="+contents, contents.contains("license.txt\tbundle:LICENSE"));

        assertEquals(0, fileMap.size()); // no leftover files
    }

    // mimetype
    // different template paths
    // custom template arguments
/*
        templateBinding.put("packageType", packageType.name());
        templateBinding.put("format", format);
        templateBinding.put("entryName", customEntryName);
        templateBinding.put("attachmentTypes", attachmentTypes);
        templateBinding.put("template", customTemplateName);
        templateBinding.put("templates", templates);
     */
    // zip format
    // error - submission == null
    // error - templates empty
    // error - no format
    // custom entry
    // single-template, type dir
    // double template, type dir
    // single template, type zip

//////////////////////////////// helpers /////////////////////////////
    
    /**
     * Generates the ExportPackage and saves it in field so cleanup() can clean it.
     * Asserts that the generated package and the File it contains have all the right properties.
     * Returns the File from the export, which will be either a zip or a dir.
     * @param packager the packager to test
     * @param entryName the correct entryName for the generated package
     * @return the File from the generated package
     */
    public File generateFileAndAssertPackageBasics(MultipleTemplatePackagerImpl packager, String entryName) {
        pkg = packager.generatePackage(sub);
        assertEquals(entryName, pkg.getEntryName());
        assertNotNull(pkg);
        assertEquals(packager.format, pkg.getFormat());
        assertEquals(packager.mimeType, pkg.getMimeType());
        return assertExport(pkg, packager.packageType);
    }
}
