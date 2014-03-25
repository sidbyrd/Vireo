package org.tdl.vireo.export.impl;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.tdl.vireo.model.AttachmentType;
import org.tdl.vireo.services.Utilities;
import org.w3c.dom.Document;
import play.exceptions.TemplateNotFoundException;

import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static org.tdl.vireo.export.impl.AbstractPackagerImpl.PackageType.dir;
import static org.tdl.vireo.model.AttachmentType.PRIMARY;
import static org.tdl.vireo.services.StringCustomizer.Variable.LAST_NAME;
import static org.tdl.vireo.services.StringCustomizer.Variable.STUDENT_NETID;

/**
 * Creates several TemplatePackagers with known configurations designed to test all the functions of a FilePackagerImpl,
 * generates the output, and asserts that it's all as expected. Also tests a recreation of a representative sample of
 * TemplatePackagers in the default configuration. This double checks that the default works, while allowing
 * customization and addition of new, unexpected TemplatePackager configurations without breaking the test.
 */
public class TemplatePackagerImplTest extends AbstractPackagerTest { // subclass to get test helpers and share setup

    TemplatePackagerImpl packager = null;

    @BeforeClass
    public static void setupClass() throws ParserConfigurationException {
        AbstractPackagerTest.setupClass();

        // configure all the XML namespaces we're going to need to use with XPath
        xpath.setNamespaceContext(new NamespaceContext() {
            public String getNamespaceURI(String prefix) {
                if("dim".equals(prefix)) { return "http://www.dspace.org/xmlns/dspace/dim"; }
                if("mets".equals(prefix)) { return "http://www.loc.gov/METS/"; }
                if("dc".equals(prefix)) { return "http://purl.org/dc/elements/1.1/"; }
                return null;
            }
            public String getPrefix(String namespaceURI) {return null;}
            public Iterator getPrefixes(String namespaceURI) {return null;}
        });
    }

    @Before
    @Override
    public void setup() throws IOException {
        super.setup();

        packager = new TemplatePackagerImpl();
        // pass along the dependencies that Spring injected into me.
        packager.personRepo = personRepo;
        packager.subRepo = subRepo;
        packager.settingRepo = settingRepo;
        packager.proquestRepo = proquestRepo;
    }

    // Configure with test template that uses every available template variable
    // Also test custom entry and manifest names.
    @Test public void testAllTemplateVariables() throws Exception {
        packager.setFormat("testFormat");
        packager.setMimeType("application/xml");
        packager.setEntryName("custom-{"+STUDENT_NETID+ '}');
        packager.setManifestTemplatePath("test/org/tdl/vireo/export/impl/TestSingleTemplate.xml");
        packager.setManifestName("test-{"+LAST_NAME+"}.xml");
        Map<String, Object> templateArgs = new HashMap<String, Object>(1);
        templateArgs.put("customArg", "verified");
        packager.setManifestTemplateArguments(templateArgs);
        addAttachmentType(packager, PRIMARY, null, null);
        final File exportFile = generateFileAndAssertPackageBasics("custom-netid");

        // Test directories and files
        final Map<String, String> fileMap = getDirectoryFileContents(exportFile);
        assertStandardAttachments(packager, fileMap);

        // Test that every template var was available
        final Document manifest = getFileXML(fileMap, "test-last name.xml"); // customized here
        assertEquals(0, fileMap.size()); // no leftover files
        // Test injected vars
        assertEquals(String.valueOf(sub.hashCode()), xpath.evaluate("/test/sub", manifest));
        assertEquals(String.valueOf(subRepo.hashCode()), xpath.evaluate("/test/subRepo", manifest));
        assertEquals(String.valueOf(personRepo.hashCode()), xpath.evaluate("/test/personRepo", manifest));
        assertEquals(String.valueOf(settingRepo.hashCode()), xpath.evaluate("/test/settingRepo", manifest));
        assertEquals(String.valueOf(proquestRepo.hashCode()), xpath.evaluate("/test/proquestRepo", manifest));
        // Test vars from packager
        assertEquals(dir.name(), xpath.evaluate("/test/packageType", manifest));
        assertEquals("testFormat", xpath.evaluate("/test/format", manifest));
        assertEquals("application/xml", xpath.evaluate("/test/mimeType", manifest));
        assertEquals("custom-netid", xpath.evaluate("/test/entryName", manifest));
        assertEquals(PRIMARY.name(), xpath.evaluate("/test/attachmentType[1]", manifest));
        assertEquals("test-{"+LAST_NAME+"}.xml", xpath.evaluate("/test/manifestName", manifest)); // not customized here
        // Test custom template args
        assertEquals("verified", xpath.evaluate("/test/custom", manifest));
    }

    // Test the standard VireoExport packager in default configuration
    // Interesting features: includes all submission fields
    @Test public void test_stock_VireoExport() throws Exception {
        packager.setDisplayName("Vireo Export");
        packager.setFormat("http://purl.org/dc/elements/1.1/");
        packager.setManifestTemplatePath("conf/formats/vireo.xml");
        packager.setManifestName("vireo.xml");
        for (AttachmentType at : AttachmentType.values()) {
            addAttachmentType(packager, at, null, null);
        }
        final File exportFile = generateFileAndAssertPackageBasics(null);

        // test all expected directories, filenames and file contents
        final Map<String, String> fileMap = getDirectoryFileContents(exportFile);
        assertStandardAttachments(packager, fileMap);
        final Document manifest = getFileXML(fileMap, "vireo.xml");
        assertEquals(0, fileMap.size()); // no leftover files

        // This manifest should include everything from the Submission and related Vireo objects, but it doesn't put many
        // demands on the Packager system itself. Just spot check that several important fields made it through.
        assertEquals(String.valueOf(sub.getId()), xpath.evaluate("/submission/@id", manifest));
        assertEquals(sub.getSubmitter().getFirstName(), xpath.evaluate("/submission/submitter/firstName", manifest));
        assertEquals(sub.getDocumentTitle(), xpath.evaluate("/submission/documentTitle", manifest));
        assertEquals(sub.getDocumentAbstract(), xpath.evaluate("/submission/documentAbstract", manifest));
        Iterator<String> subjects = sub.getDocumentSubjects().iterator();
        for (int i=1; subjects.hasNext(); i++) { // xpath uses 1-based indexing. Sigh.
            assertEquals(subjects.next(), xpath.evaluate("/submission/documentSubjects/documentSubject["+i+ ']', manifest).trim());
        }
    }

    // Test the standard DSpaceMETS packager in default configuration
    // Interesting features: includes template args
    @Test public void test_stock_DSpaceMETS() throws Exception {
        packager.setDisplayName("DSpaceMETS");
        packager.setFormat("http://purl.org/net/sword-types/METSDSpaceSIP");
        packager.setManifestTemplatePath("conf/formats/dspace_mets.xml");
        packager.setManifestName("mets.xml");
        Map<String, Object> templateArgs = new HashMap<String, Object>(1);
        templateArgs.put("agent", "Vireo DSpace METS packager");
        packager.setManifestTemplateArguments(templateArgs);
        addAttachmentType(packager, PRIMARY, null, null);
        addAttachmentType(packager, AttachmentType.SUPPLEMENTAL, null, null);
        addAttachmentType(packager, AttachmentType.LICENSE, null, null);
        addAttachmentType(packager, AttachmentType.SOURCE, null, null);
        final File exportFile = generateFileAndAssertPackageBasics(null);

        // test all expected directories, filenames and file contents
        final Map<String, String> fileMap = getDirectoryFileContents(exportFile);
        assertStandardAttachments(packager, fileMap);
        final Document manifest = getFileXML(fileMap, "mets.xml");
        assertEquals(0, fileMap.size()); // no leftover files

        // We had a template argument.
        assertEquals("Vireo DSpace METS packager", xpath.evaluate("/mets:mets/mets:metsHdr/mets:agent/mets:name", manifest).trim());
        // Otherwise, this manifest doesn't put many demands on the Packager system itself. Just spot check.
        assertEquals("vireo-submission-"+sub.getId(), xpath.evaluate("/mets:mets/@OBJID", manifest));
        assertEquals(sub.getDocumentTitle(), xpath.evaluate("/mets:mets/mets:dmdSec/mets:mdWrap/mets:xmlData/dim:dim/dim:field[@mdschema='dc'][@element='title']", manifest).trim());
    }

    // Test the standard GenericQDC packager in default configuration
    // Interesting features: none--standin for all the equally simple default TemplatePackager configs.
    @Test public void test_stock_GenericQDC() throws Exception {
        packager.setDisplayName("Generic Qualified Dublin Core");
        packager.setFormat("http://purl.org/dc/elements/1.1/");
        packager.setManifestTemplatePath("conf/formats/qdc.xml");
        packager.setManifestName("metadata.xml");
        addAttachmentType(packager, PRIMARY, null, null);
        addAttachmentType(packager, AttachmentType.SUPPLEMENTAL, null, null);
        addAttachmentType(packager, AttachmentType.LICENSE, null, null);
        addAttachmentType(packager, AttachmentType.SOURCE, null, null);
        final File exportFile = generateFileAndAssertPackageBasics(null);

        // test all expected directories, filenames and file contents
        final Map<String, String> fileMap = getDirectoryFileContents(exportFile);
        assertStandardAttachments(packager, fileMap);
        final Document manifest = getFileXML(fileMap, "metadata.xml");
        assertEquals(0, fileMap.size()); // no leftover files

        // This manifest doesn't put many demands on the Packager system itself. Just spot check.
        assertEquals(sub.getDocumentTitle(), xpath.evaluate("/metadata/dc:title", manifest).trim());
    }

    // Test the standard Marc21 packager in default configuration
    // Interesting features: no attachments, so it returns one file, not a directory. Not XML.
    @Test public void test_stock_Marc21() throws Exception {
        packager.setDisplayName("MARC21");
        packager.setMimeType("text/plain");
        packager.setFormat("http://www.loc.gov/marc/umb/um11to12.html");
        packager.setManifestTemplatePath("conf/formats/marc21.xml");
        packager.setManifestName("marc21.bib");

        // Since there are no attachments, we expect a single .bib file instead of a directory.
        // Can't use convenience testing methods for this.
        pkg = packager.generatePackage(sub);
        assertEquals(null, pkg.getEntryName());
        assertNotNull(pkg);
        assertEquals(packager.format, pkg.getFormat());
        assertEquals(packager.mimeType, pkg.getMimeType());
        final File exportFile = pkg.getFile();
        assertNotNull(exportFile);
        assertTrue("Package file does not exist", exportFile.exists());
        assertTrue("Package file is not readable", exportFile.canRead());
        assertFalse("Package should not be a directory", exportFile.isDirectory());
        assertTrue("Package file should end in .bib", exportFile.getName().endsWith(".bib"));

        // test expected filename and contents
        final String marc = Utilities.fileToString(exportFile);

        // This manifest doesn't put many demands on the Packager system itself. Just spot check.
        assertTrue(marc.contains(sub.getDocumentTitle()));
    }

    // Test the standard ProQuest packager in default configuration
    // Interesting features: customizes entry name, manifest name, attachment names, and attachment dirs.
    @Test public void test_stock_ProQuest() throws Exception {
        packager.setDisplayName("ProQuest UMI");
        packager.setFormat("http://www.proquest.com/assets/downloads/products/ftp_submissions.pdf");
        packager.setManifestTemplatePath("conf/formats/ProquestUMI.xml");
        packager.setManifestName("{LAST_NAME}_{FIRST_NAME}_DATA.xml");
        packager.setEntryName("upload_{LAST_NAME}_{FIRST_NAME}");
        addAttachmentType(packager, PRIMARY, "{LAST_NAME}_{FIRST_NAME}", null);
        addAttachmentType(packager, AttachmentType.SUPPLEMENTAL, "supp_file_{FILE_NAME}", "{LAST_NAME}_{FIRST_NAME}_media");
        addAttachmentType(packager, AttachmentType.LICENSE, null, "{LAST_NAME}_{FIRST_NAME}_permission");
        final File exportFile = generateFileAndAssertPackageBasics("upload_last name_first name");

        // test all expected directories, filenames and file contents
        final Map<String, String> fileMap = getDirectoryFileContents(exportFile);

        // check files and directories
        final String primaryName = "last name_first name.pdf";
        assertTrue(fileMap.containsKey(primaryName));
        assertEquals("bottle", fileMap.remove(primaryName));

        final String supplementalDir = "last name_first name_media";
        assertTrue(fileMap.containsKey(supplementalDir));
        assertNull(fileMap.remove(supplementalDir));
        final String supplementalName = supplementalDir+File.separator+"supp_file_fluff.jpg";
        assertTrue(fileMap.containsKey(supplementalName));
        assertEquals("fluff", fileMap.remove(supplementalName));

        final String licenseDir = "last name_first name_permission";
        assertTrue(fileMap.containsKey(licenseDir));
        assertNull(fileMap.remove(licenseDir));
        final String licenseName = licenseDir+File.separator+"license.txt";
        assertTrue(fileMap.containsKey(licenseName));
        assertEquals("license", fileMap.remove(licenseName));

        final Document manifest = getFileXML(fileMap, "last name_first name_DATA.xml");
        assertEquals(0, fileMap.size()); // no leftover files

        // Check the manifest
        assertEquals(sub.getStudentFirstName(), xpath.evaluate("/DISS_submission/DISS_authorship/DISS_author/DISS_name/DISS_fname", manifest).trim());
        assertEquals(sub.getDocumentTitle(), xpath.evaluate("/DISS_submission/DISS_description/DISS_title", manifest).trim());
        assertEquals("0285", xpath.evaluate("/DISS_submission/DISS_description/DISS_categorization/DISS_category[1]/DISS_cat_code", manifest).trim());
        assertEquals("last name_first name.pdf", xpath.evaluate("/DISS_submission/DISS_content/DISS_binary", manifest).trim());
    }

    // The single template packagers takes configuration of manifest name and manifest template path separately, and
    // it joins them together before passing them to MultipleTemplatePackagerImpl in one call. Test that.
    @Test public void testSetManifestNameAndPath() {
        // test default
        assertEquals(1, packager.templates.size());
        assertTrue(packager.templates.containsKey("mets.xml")); // default manifest name
        assertNull(packager.templates.get("mets.xml"));

        // set path
        packager.setManifestTemplatePath("conf/formats/vireo.xml");
        assertEquals(1, packager.templates.size());
        assertTrue(packager.templates.containsKey("mets.xml"));
        assertEquals("vireo.xml", packager.templates.get("mets.xml").toString());

        // change path
        packager.setManifestTemplatePath("conf/formats/qdc.xml");
        assertEquals(1, packager.templates.size());
        assertTrue(packager.templates.containsKey("mets.xml"));
        assertEquals("qdc.xml", packager.templates.get("mets.xml").toString());

        // change name
        packager.setManifestName("name2");
        assertEquals(1, packager.templates.size());
        assertTrue(packager.templates.containsKey("name2"));
        assertEquals("qdc.xml", packager.templates.get("name2").toString());

        // error - change path to non-existent file
        try {
            packager.setManifestTemplatePath("conf/formats/nope.xml");
            fail("Should not accept non-existent template file");
        } catch (TemplateNotFoundException e) { /**/ }
        // nothing changed
        assertEquals(1, packager.templates.size());
        assertTrue(packager.templates.containsKey("name2"));
        assertEquals("qdc.xml", packager.templates.get("name2").toString());
    }

    // Test that only exactly one template is accepted.
    @Test public void testSetTemplatePaths() {
        final Map<String, String> templatePaths = new HashMap<String, String>(2);
        // This method is left over from superclass, but it's there and overridden to behave in single-template context.

        // zero is error
        try {
            packager.setTemplatePaths(templatePaths);
            fail("Should not accept zero template paths");
        } catch (IllegalArgumentException e) { /**/ }
        // test default
        assertEquals(1, packager.templates.size());
        assertTrue(packager.templates.containsKey("mets.xml")); // default manifest name
        assertNull(packager.templates.get("mets.xml"));

        // single works
        templatePaths.put("name1", "conf/formats/vireo.xml");
        packager.setTemplatePaths(templatePaths);
        assertEquals(1, packager.templates.size());
        assertTrue(packager.templates.containsKey("name1"));
        assertEquals("vireo.xml", packager.templates.get("name1").toString());

        // double is error
        templatePaths.put("name2", "conf/formats/qdc.xml");
        try {
            packager.setTemplatePaths(templatePaths);
            fail("Should not accept multiple template paths");
        } catch (IllegalArgumentException e) { /**/ }
    }

    // Test error conditions unique to TemplatePackagerImpl.
    @Test public void testGeneratePackageErrors() {
        // error - no manifest template file set.
        try {
            packager.generatePackage(sub);
            fail("Should not package with no template path");
        } catch (IllegalStateException e) {
            assertEquals("Unable to generate package because no manifest template path has been defined.", e.getMessage());
        }

        // error - no manifest name file set.
        // it's set from the beginning, but we can unset it.
        packager.setManifestName(null);
        try {
            packager.generatePackage(sub);
            fail("Should not package with no manifest name");
        } catch (IllegalStateException e) {
            assertEquals("Unable to generate package because no manifest name has been defined.", e.getMessage());
        }
        // additional errors tested in MultipleTemplatePackagerTest
    }


///////////////////////////// helpers ///////////////////////////////

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
        return assertExport(pkg, packager.packageType);
    }
}
