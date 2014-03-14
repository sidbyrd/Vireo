package controllers.settings;

import controllers.AbstractVireoFunctionalTest;
import org.apache.commons.io.FilenameUtils;
import org.apache.tools.ant.util.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.tdl.vireo.constant.AppConfig;
import org.tdl.vireo.constant.CustomImage;
import org.tdl.vireo.model.Configuration;
import org.tdl.vireo.model.PersonRepository;
import org.tdl.vireo.model.SettingsRepository;
import org.tdl.vireo.security.SecurityContext;
import play.Play;
import play.db.jpa.JPA;
import play.modules.spring.Spring;
import play.mvc.Http.Response;
import play.mvc.Router;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.tdl.vireo.constant.AppConfig.*;

/**
 * Test for the theme setting tab. 
 * 
 * @author <a href="http://www.scottphillips.com">Scott Phillips</a>
 */
public class ThemeSettingsTabTest extends AbstractVireoFunctionalTest {

	public static SecurityContext context = Spring.getBeanOfType(SecurityContext.class);
	public static PersonRepository personRepo = Spring.getBeanOfType(PersonRepository.class);
	public static SettingsRepository settingRepo = Spring.getBeanOfType(SettingsRepository.class);
	
	@Before
	public void setup() {
		context.turnOffAuthorization();
		JPA.em().getTransaction().commit();
		JPA.em().clear();
		JPA.em().getTransaction().begin();
	}
	
	@After
	public void cleanup() {
		context.restoreAuthorization();
		JPA.em().getTransaction().commit();
		JPA.em().clear();
		JPA.em().getTransaction().begin();
	}
	
	/**
	 * Just test that the page is displayed without error.
	 */
	@Test
	public void testDisplayOfTHemeSettingsTab() {
		
		LOGIN();
		
		final String URL = Router.reverse("settings.ThemeSettingsTab.themeSettings").url;

		Response response = GET(URL);
		assertIsOk(response);
	}
	
	/**
	 * Test changing the text and input areas
	 */
	@Test
	public void testUpdatingThemeSettings() {
		
		LOGIN();
		
		// Get our urls and a list of fields.
		final String URL = Router.reverse("settings.ThemeSettingsTab.updateThemeSettingsJSON").url;

		Collection<String> fields = new ArrayList<String>(10);
		fields.add(FRONT_PAGE_INSTRUCTIONS);
		fields.add(SUBMIT_INSTRUCTIONS);
		fields.add(CORRECTION_INSTRUCTIONS);
		fields.add(CUSTOM_CSS);
		fields.add(BACKGROUND_MAIN_COLOR);
		fields.add(BACKGROUND_HIGHLIGHT_COLOR);
		fields.add(BUTTON_MAIN_COLOR_ON);
		fields.add(BUTTON_HIGHLIGHT_COLOR_ON);
		fields.add(BUTTON_MAIN_COLOR_OFF);
		fields.add(BUTTON_HIGHLIGHT_COLOR_OFF);
		
		for (String field : fields) {
		
			Configuration originalValue = settingRepo.findConfigurationByName(field);
			
			// change the current semester
			Map<String,String> params = new HashMap<String,String>(2);
			params.put("field", field);
			params.put("value","changed \"by test\"");
			Response response = POST(URL,params);
			assertContentMatch("\"success\": \"true\"", response);
		
			
			// Check that all the fields are set.
			JPA.em().getTransaction().commit();
			JPA.em().clear();
			JPA.em().getTransaction().begin();
			assertNotNull(settingRepo.findConfigurationByName(field));
			assertEquals("changed \"by test\"",settingRepo.findConfigurationByName(field).getValue());
			
			JPA.em().clear();
			if (originalValue == null) {
				settingRepo.findConfigurationByName(field).delete();
			} else {
				Configuration value = settingRepo.findConfigurationByName(field);
				value.setValue(originalValue.getValue());
				value.save();
			}
		
		}
	}

    /**
     * Helper for testUploadingImage() that checks pretty much everything about the state of a custom image.
     * for code2x: 0==none, 1==separate, 2==same.
     */
    private static void checkUploadedImage(String nameBase, String name1x, String name2x,
                                           String width, String height,
                                           File custom1x, File custom2x, int code2x) throws IOException {
        // check image metadata
        assertEquals(ThemeSettingsTab.THEME_URL_PREFIX+nameBase, settingRepo.getConfigValue(CIName.TEST_LOGO+AppConfig.CI_URLPATH));
        assertEquals((name1x==null)?null:ThemeSettingsTab.THEME_URL_PREFIX + name1x, CustomImage.url(CIName.TEST_LOGO, false));
        assertEquals((name2x==null)?null:ThemeSettingsTab.THEME_URL_PREFIX+name2x, CustomImage.url(CIName.TEST_LOGO, true));
        assertEquals(width, settingRepo.getConfigValue(CIName.TEST_LOGO + AppConfig.CI_WIDTH));
        assertEquals(height, settingRepo.getConfigValue(CIName.TEST_LOGO + AppConfig.CI_HEIGHT));
        assertEquals(!(custom1x!=null || custom2x!=null), CustomImage.isDefault(CIName.TEST_LOGO));
        assertEquals(custom1x != null, CustomImage.hasCustomFile(CIName.TEST_LOGO, false));
        assertEquals(custom2x != null, CustomImage.hasCustomFile(CIName.TEST_LOGO, true));
        assertEquals(code2x == 0, CustomImage.is2xNone(CIName.TEST_LOGO));
        assertEquals(code2x == 1, CustomImage.is2xSeparate(CIName.TEST_LOGO));
        assertEquals(code2x == 2, CustomImage.is1xScaled(CIName.TEST_LOGO));

        // check custom file existence and contents
        final String ext = FilenameUtils.getExtension(nameBase);
        assertEquals(ext, CustomImage.extension(CIName.TEST_LOGO));
        File file = new File(ThemeSettingsTab.THEME_PATH+CustomImage.standardFilename(CIName.TEST_LOGO, false, ext));
        assertEquals(custom1x!=null, file.exists());
        if (custom1x!=null) {
            assertTrue(org.apache.commons.io.FileUtils.contentEquals(custom1x, file));
        }
        file = new File(ThemeSettingsTab.THEME_PATH+CustomImage.standardFilename(CIName.TEST_LOGO, true, ext));
        assertEquals(custom2x!=null, file.exists());
        if (custom2x!=null) {
            assertTrue(org.apache.commons.io.FileUtils.contentEquals(custom2x, file));
        }
    }

	/**
	 * Test various cases and orderings of uploading and deleting custom images, both the 1x
     * and 2x versions.
	 * @throws IOException 
	 */
	@Test
	public void testUploadingImage() throws IOException {
        final String png1x="test-logo.png";
        final String png2x="test-logo@2x.png";
        final String default1x="public"+File.separatorChar+"images"+File.separatorChar+"vireo-sm.png";
        final String default2x="public"+File.separatorChar+"images"+File.separatorChar+"vireo-sm@2x.png";
        final Map<String,String> params = new HashMap<String,String>(4);
        final Map<String,File> files = new HashMap<String,File>(2);
        final File pngSmall = getResourceFile("SampleFeedbackDocumentSmall.png");
        final File pngLarge = getResourceFile("SampleFeedbackDocument.png");
        final File jpgLarge = getResourceFile("SampleFeedbackDocument.jpg");

        LOGIN();

		//Get the url
		final String URL = Router.reverse("settings.ThemeSettingsTab.uploadImage").url;

        // upload 1x onto default
        params.put("submit_upload", "true");
        params.put("name", CIName.TEST_LOGO.toString());
        files.put("image1x", pngLarge);
        // should be accepted and set as standalone 1x image with no 2x
        Response response = POST(URL,params,files);
        assertStatus(302, response);
        checkUploadedImage(png1x, png1x, null, "541", "378", pngLarge, null, 0);

        // upload a different (smaller) 1x onto existing 1x
        files.clear();
		files.put("image1x", pngSmall);
		response = POST(URL,params,files);
        // should be accepted and replace the previous 1x
		assertStatus(302, response);
        checkUploadedImage(png1x, png1x, null, "271", "189", pngSmall, null, 0);

        // upload invalid image (format PNG but extension GIF)
        files.clear();
		files.put("image1x", getResourceFileWithExtension("SampleFeedbackDocumentSmall.png", "gif"));
		response = POST(URL,params,files);
        // should be rejected, and image data should stay the same
		assertStatus(200,response); // in test, a page describing the error appears
        checkUploadedImage(png1x, png1x, null, "271", "189", pngSmall, null, 0);

        // upload invalid 2x (same format as 1x, not double the size)
        files.clear();
		files.put("image2x", pngSmall);
		response = POST(URL,params,files);
        // should be rejected, and image data should stay the same
		assertStatus(200,response);
        checkUploadedImage(png1x, png1x, null, "271", "189", pngSmall, null, 0);

        // upload invalid 2x (different format from 2x, double the size)
        files.clear();
		files.put("image2x", jpgLarge);
		response = POST(URL,params,files);
        // should be rejected, and image data should stay the same
		assertStatus(200,response);
        checkUploadedImage(png1x, png1x, null, "271", "189", pngSmall, null, 0);

        // upload valid new 2x (same format as 1x, double the size)
		files.clear();
		files.put("image2x", pngLarge);
		response = POST(URL,params,files);
        // should be accepted and image data set for separate 1x and 2x
		assertStatus(302,response);
        checkUploadedImage(png1x, png1x, png2x, "271", "189", pngSmall, pngLarge, 1);

        // delete 1x, leaving 2x in place
        params.clear();
		params.put("delete1x", "true");
        params.put("name", CIName.TEST_LOGO.toString());
        files.clear();
        response = POST(URL,params,files);
        // should be accepted and image data set for remaining image to be standalone 2x
		assertStatus(302,response);
        checkUploadedImage(png2x, null, png2x, "271", "189", null, pngLarge, 2);

        // upload invalid 1x (same extension as 2x, not half the size)
        params.clear();
        params.put("submit_upload", "true");
        params.put("name", CIName.TEST_LOGO.toString());
        files.clear();
        files.put("image1x", pngLarge);
        response = POST(URL,params,files);
        // should be accepted and image data set for remaining image to be standalone 2x
		assertStatus(302,response);
        checkUploadedImage(png2x, null, png2x, "271", "189", null, pngLarge, 2);

        // upload 1x onto existing 2x
        files.clear();
        files.put("image1x", pngSmall);
        response = POST(URL,params,files);
        // should be accepted and image data set for separate 1x and 2x
		assertStatus(302,response);
        checkUploadedImage(png1x, png1x, png2x, "271", "189", pngSmall, pngLarge, 1);

        // delete 2x, leaving 1x in place
        params.clear();
		params.put("delete2x", "true");
        params.put("name", CIName.TEST_LOGO.toString());
        files.clear();
        response = POST(URL,params,files);
        // should be accepted and data set for standalone 1x image
        assertStatus(302,response);
        checkUploadedImage(png1x, png1x, null, "271", "189", pngSmall, null, 0);

        // delete 1x with no existing 2x, resulting in default state
        params.clear();
		params.put("delete1x", "true");
        params.put("name", CIName.TEST_LOGO.toString());
        files.clear();
        response = POST(URL,params,files);
        // should be accepted and image data set for remaining image to be standalone 2x
		assertStatus(302,response);
        checkUploadedImage(default1x, default1x, default2x, "150", "50", null, null, 1);

        // upload 1x, upload 2x (valid combination)
        params.clear();
        params.put("submit_upload", "true");
        params.put("name", CIName.TEST_LOGO.toString());
        files.clear();
        files.put("image1x", pngSmall);
        files.put("image2x", pngLarge);
        response = POST(URL,params,files);
        // should be accepted and data set for separate 1x and 2x
        assertStatus(302,response);
        checkUploadedImage(png1x, png1x, png2x, "271", "189", pngSmall, pngLarge, 1);

        // delete2x, upload different 1x (JPEG), upload different 2x that's an invalid combination
        params.clear();
        params.put("delete2x", "true");
        params.put("submit_upload", "true");
        params.put("name", CIName.TEST_LOGO.toString());
        files.clear();
        files.put("image1x", pngLarge);
        files.put("image2x", pngSmall);
        response = POST(URL,params,files);
        // should be rejected ultimately, but not until 2x deleted, and 1x updated and data set for standalone 1x
        assertStatus(200,response);
        checkUploadedImage(png1x, png1x, null, "271", "189", jpgLarge, null, 0);

        // clean up : here or somewhere else? delete files, reset config values.
        params.clear();
		params.put("delete1x", "true");
        params.put("name", CIName.TEST_LOGO.toString());
        files.clear();
        POST(URL,params,files);

        // not tested: GIF format acceptance, all possible combinations of upload+delete in same request, proper HTML interface function
	}

	/**
     * Extract the file from the jar and place it in a temporary location for the test to operate from.
     *
     * @param filePath The path, relative to the classpath, of the file to reference.
     * @return A Java File object reference.
     * @throws IOException
     */
    protected static File getResourceFile(String filePath) throws IOException {
        // use the original file's correct extension
        return getResourceFileWithExtension(filePath, FilenameUtils.getExtension(filePath));
    }
	
    /**
     * Extract the file from the jar and place it in a temporary location for the test to operate from.
     * Manually override the file extension that the temp file will get.
     *
     * @param filePath The path, relative to the classpath, of the file to reference.
     * @param extension the file extension for the created temp file
     * @return A Java File object reference.
     * @throws IOException
     */
    protected static File getResourceFileWithExtension(String filePath, String extension) throws IOException {
        File file = File.createTempFile("ingest-import-test", '.'+extension);

        // While we're packaged by play we have to ask Play for the inputstream instead of the classloader.
        //InputStream is = DSpaceCSVIngestServiceImplTests.class
        //		.getResourceAsStream(filePath);
        InputStream is = Play.classloader.getResourceAsStream(filePath);
        OutputStream os = new FileOutputStream(file);
        try {
            // Copy the file out of the jar into a temporary space.
            byte[] buffer = new byte[1024];
            int len;
            while ((len = is.read(buffer)) > 0) {
                os.write(buffer, 0, len);
            }
        } finally {
            is.close();
            os.close();
        }

        return file;
    }
}
