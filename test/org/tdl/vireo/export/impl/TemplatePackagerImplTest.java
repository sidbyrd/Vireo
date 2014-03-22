package org.tdl.vireo.export.impl;

import org.apache.commons.io.FileUtils;
import org.jdom.Document;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.tdl.vireo.export.ExportPackage;
import org.tdl.vireo.model.DegreeLevel;
import org.tdl.vireo.proquest.ProquestVocabularyRepository;
import org.tdl.vireo.services.StringVariableReplacement;
import play.modules.spring.Spring;

import java.io.*;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.tdl.vireo.services.Utilities.fileToString;

/**
 * Test the generic template package.
 * 
 * Creates several TemplatePackagers with known configurations designed to test all the functions of a FilePackagerImpl,
 * generates the output, and asserts that it's all as expected. Also tests a recreation of each important TemplatePackager
 * in the default configuration. This double checks that they all work, while allowing customization and addition of new,
 * unexpected TemplatePackager configurations without breaking the test.
 *
 * @author <a href="http://www.scottphillips.com">Scott Phillips</a>
 * 
 */
public class TemplatePackagerImplTest extends AbstractPackagerTest { // subclass to get test helpers and share setup

    // stock configuration test: VireoExport
    @Test public void test_stock_VireoExport() {
        
    }
	
    // delete me!
	@Test
	public void testConfigs() throws IOException, JDOMException {

		// Test all the template packagers
		Map<String,TemplatePackagerImpl> packagers = Spring.getBeansOfType(TemplatePackagerImpl.class);
		
		for (TemplatePackagerImpl packager : packagers.values()) {
			
			ExportPackage pkg = packager.generatePackage(sub);
			
			//Since the manifest name can be customized, create a temporary manifest name to compare with.
			Map<String, String> parameters = StringVariableReplacement.setParameters(sub);
			String manifestName = StringVariableReplacement.applyParameterSubstitution(packager.manifestName, parameters);
			
			assertNotNull(pkg);
			assertEquals(packager.format,pkg.getFormat());
			assertEquals(packager.mimeType,pkg.getMimeType());
			
			
			File exportFile = pkg.getFile();
			assertNotNull(exportFile);
			assertTrue("Package file does not exist", exportFile.exists());
			assertTrue("Package file is not readable", exportFile.canRead());
			
			if (exportFile.isDirectory()) {
			
				// The export is a directory of multiple files
				Map<String, String> fileMap = getDirectoryFileContents(exportFile);
				
				// There should be three files
				assertTrue(fileMap.containsKey(manifestName));
				assertEquals(3, fileMap.size());
				//TODO Test for custom file names.
				//assertTrue(fileMap.containsKey("LASTNAME-SELECTEDDOCUMENTTYPE-2002.pdf"));
				//assertTrue(fileMap.containsKey("fluff.jpg"));
				
				// Load up the manifest and make sure it's valid XML.
				SAXBuilder builder = new SAXBuilder();
				Document doc = builder.build(new StringReader(fileMap.get(manifestName)));
				
				// Check that the manifest contains important data
				String manifest = fileMap.get(manifestName);
				assertTrue(manifest.contains(sub.getStudentFirstName()));
				assertTrue(manifest.contains(sub.getStudentLastName()));
				assertTrue(manifest.contains(sub.getDocumentTitle()));
				assertTrue(manifest.contains(sub.getDocumentAbstract()));
			} else if(".zip".equals(exportFile.getName().substring(exportFile.getName().lastIndexOf('.')))){
				
				byte[] buffer = new byte[1024];
				
				File tempFolder = new File("tempFolder");
				tempFolder.mkdir();
				
				ZipInputStream zis = new ZipInputStream(new FileInputStream(exportFile));
				ZipEntry ze = zis.getNextEntry();
				
				while(ze!=null) {
					
					String fileName = ze.getName();
					File newFile = new File(tempFolder.getPath() + File.separator + fileName);
					
					new File(newFile.getParent()).mkdirs();
					
					FileOutputStream fos = new FileOutputStream(newFile);
					
					int len;
					while ((len = zis.read(buffer)) > 0) {
						fos.write(buffer, 0, len);
					}
					
					fos.close();
					ze = zis.getNextEntry();
				}
				
				zis.closeEntry();
				zis.close();
				
				// The export is a directory of multiple files
				Map<String, String> fileMap = getDirectoryFileContents(tempFolder);
				
				// There should be three files
				assertTrue(fileMap.containsKey(manifestName));
				assertEquals(4, fileMap.size());
				//TODO Test for custom file names.
				//assertTrue(fileMap.containsKey("LASTNAME-SELECTEDDOCUMENTTYPE-2002.pdf"));
				//assertTrue(fileMap.containsKey("fluff.jpg"));
				
				// Load up the manifest and make sure it's valid XML.
				SAXBuilder builder = new SAXBuilder();
				Document doc = builder.build(new StringReader(fileMap.get(manifestName)));
				
				// Check that the manifest contains important data
				String manifest = fileMap.get(manifestName);
				assertTrue(manifest.contains(sub.getStudentFirstName()));
				assertTrue(manifest.contains(sub.getStudentLastName()));
				assertTrue(manifest.contains(sub.getDocumentTitle()));
				assertTrue(manifest.contains(sub.getDocumentAbstract()));
				
				FileUtils.deleteDirectory(tempFolder);
				
			} else {
				
				if(".xml".equals(exportFile.getName().substring(exportFile.getName().lastIndexOf('.')))){
					// The export is a single file, try and load it as xml.					
					SAXBuilder builder = new SAXBuilder();
					Document doc = builder.build(exportFile);
				}	
				
				// Check that the export contains important data
				String manifest = fileToString(exportFile);
				assertTrue(manifest.contains(sub.getStudentFirstName()));
				assertTrue(manifest.contains(sub.getStudentLastName()));
				assertTrue(manifest.contains(sub.getDocumentTitle()));
				assertTrue(manifest.contains(sub.getDocumentAbstract()));
				
			}
			
			// Cleanup
			pkg.delete();
			assertFalse(exportFile.exists());
		}
    }
}
