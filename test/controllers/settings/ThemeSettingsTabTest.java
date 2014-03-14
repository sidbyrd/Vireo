package controllers.settings;

import static org.tdl.vireo.constant.AppConfig.*;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.ivy.util.FileUtil;
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
import controllers.AbstractVireoFunctionalTest;
import sun.security.x509.Extension;

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

		List<String> fields = new ArrayList<String>();
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
			Map<String,String> params = new HashMap<String,String>();
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
	 * Test various cases and orderings of uploading and deleting custom images, both the 1x
     * and 2x versions.
	 * @throws IOException 
	 */
	@Test
	public void testUploadingImage() throws IOException {
		LOGIN();

		//Get the url
		final String URL = Router.reverse("settings.ThemeSettingsTab.uploadImage").url;

        // upload new 1x
        // -- upload regular size here --

        // upload a different 1x
		Map<String,String> params = new HashMap<String,String>();
		params.put("submit_upload", "true");
        params.put("name", CIName.TEST_LOGO.toString());
		File file = getResourceFile("SampleFeedbackDocumentSmall.png");
		Map<String,File> files = new HashMap<String,File>();
		files.put("image1x", file);
		Response response = POST(URL,params,files);

        // check result -- should be set for 1x image only
		assertStatus(302,response);
        assertEquals(ThemeSettingsTab.THEME_URL_PREFIX+"test-logo.png", settingRepo.getConfigValue(CIName.TEST_LOGO+AppConfig.CI_URLPATH));
        assertEquals(ThemeSettingsTab.THEME_URL_PREFIX+"test-logo.png",CustomImage.url(CIName.TEST_LOGO, false));
        assertNull(ThemeSettingsTab.THEME_URL_PREFIX+"test-logo.png",CustomImage.url(CIName.TEST_LOGO, true));
        assertEquals("189", settingRepo.getConfigValue(CIName.TEST_LOGO+AppConfig.CI_HEIGHT));
        assertEquals("271", settingRepo.getConfigValue(CIName.TEST_LOGO+AppConfig.CI_WIDTH));
		File logoFile = new File(ThemeSettingsTab.THEME_PATH+"test-logo.png");
		assertTrue(logoFile.exists());
        assertEquals(CustomImage.extension(CIName.TEST_LOGO), "png");
        assertFalse(CustomImage.isDefault(CIName.TEST_LOGO));
        assertTrue(CustomImage.hasCustomFile(CIName.TEST_LOGO, false));
        assertFalse(CustomImage.hasCustomFile(CIName.TEST_LOGO, true));
        assertFalse(CustomImage.is1xScaled(CIName.TEST_LOGO));
        assertTrue(CustomImage.is2xNone(CIName.TEST_LOGO));
        assertFalse(CustomImage.is2xSeparate(CIName.TEST_LOGO));

        // upload invalid image (format PNG but extension GIF)

        // upload invalid 2x (same format, not double the size)

        // upload invalid 2x (different format, double the size)

        // upload valid new 2x (same format, double the size)
		params.clear();
		params.put("submit_upload", "true");
        params.put("name", CIName.TEST_LOGO.toString());
		file = getResourceFile("SampleFeedbackDocument.png");
		files.clear();
		files.put("image2x", file);
		response = POST(URL,params,files);

        // check result -- should be set for separate 1x and 2x
		assertStatus(302,response);
        assertEquals(ThemeSettingsTab.THEME_URL_PREFIX+"test-logo.png", settingRepo.getConfigValue(CIName.TEST_LOGO+AppConfig.CI_URLPATH));
        assertEquals(ThemeSettingsTab.THEME_URL_PREFIX+"test-logo.png",CustomImage.url(CIName.TEST_LOGO, false));
        assertNull(ThemeSettingsTab.THEME_URL_PREFIX+"test-logo@2x.png",CustomImage.url(CIName.TEST_LOGO, true));
        assertEquals("189", settingRepo.getConfigValue(CIName.TEST_LOGO+AppConfig.CI_HEIGHT));
        assertEquals("271", settingRepo.getConfigValue(CIName.TEST_LOGO+AppConfig.CI_WIDTH));
		logoFile = new File(ThemeSettingsTab.THEME_PATH+"test-logo@2x.png");
		assertTrue(logoFile.exists());
        assertFalse(CustomImage.isDefault(CIName.TEST_LOGO));
        assertTrue(CustomImage.hasCustomFile(CIName.TEST_LOGO, false));
        assertTrue(CustomImage.hasCustomFile(CIName.TEST_LOGO, true));
        assertFalse(CustomImage.is1xScaled(CIName.TEST_LOGO));
        assertFalse(CustomImage.is2xNone(CIName.TEST_LOGO));
        assertTrue(CustomImage.is2xSeparate(CIName.TEST_LOGO));

        // delete 1x
        params.clear();
		params.put("delete1x", "true");
        params.put("name", CIName.TEST_LOGO.toString());
		response = POST(URL,params);

        // delete 1x again -- should not cause error

        // check result -- should be set for 2x, scaled down to 1x on low-res devices
		assertStatus(302,response);
        assertEquals(ThemeSettingsTab.THEME_URL_PREFIX+"test-logo@2x.png", settingRepo.getConfigValue(CIName.TEST_LOGO+AppConfig.CI_URLPATH));
        assertEquals(ThemeSettingsTab.THEME_URL_PREFIX+"test-logo@2x.png",CustomImage.url(CIName.TEST_LOGO, false));
        assertNull(ThemeSettingsTab.THEME_URL_PREFIX+"test-logo@2x.png",CustomImage.url(CIName.TEST_LOGO, true));
        assertEquals("189", settingRepo.getConfigValue(CIName.TEST_LOGO+AppConfig.CI_HEIGHT));
        assertEquals("271", settingRepo.getConfigValue(CIName.TEST_LOGO+AppConfig.CI_WIDTH));
		logoFile = new File(ThemeSettingsTab.THEME_PATH+"test-logo.png");
		assertFalse(logoFile.exists());
        assertFalse(CustomImage.isDefault(CIName.TEST_LOGO));
        assertFalse(CustomImage.hasCustomFile(CIName.TEST_LOGO, false));
        assertTrue(CustomImage.hasCustomFile(CIName.TEST_LOGO, true));
        assertTrue(CustomImage.is1xScaled(CIName.TEST_LOGO));
        assertFalse(CustomImage.is2xNone(CIName.TEST_LOGO));
        assertFalse(CustomImage.is2xSeparate(CIName.TEST_LOGO));

        // upload invalid 1x (same extension, not half the size)

        // delete 2x
        params.clear();
		params.put("delete2x", "true");
        params.put("name", CIName.TEST_LOGO.toString());
		response = POST(URL,params);

        // check result -- should be default values
		assertStatus(302,response);
        assertEquals(Configuration.DEFAULTS.get(CIName.TEST_LOGO+AppConfig.CI_URLPATH), settingRepo.getConfigValue(CIName.TEST_LOGO+AppConfig.CI_URLPATH));
        assertEquals(Configuration.DEFAULTS.get(CIName.TEST_LOGO+AppConfig.CI_HEIGHT), settingRepo.getConfigValue(CIName.TEST_LOGO+AppConfig.CI_HEIGHT));
        assertEquals(Configuration.DEFAULTS.get(CIName.TEST_LOGO+AppConfig.CI_WIDTH), settingRepo.getConfigValue(CIName.TEST_LOGO+AppConfig.CI_WIDTH));
		assertFalse(logoFile.exists());

        // delete 2x again -- should not cause error.

        // upload valid 1x and 2x together

        // quickly check result -- changed correctly to be set for separate images

        // delete 2x

        // quickly check result -- changed to be set for 1x only

        // clean up : here or somewhere else? delete files, reset config values.

        // not tested: JPG and GIF formats, deleting and uploading different combinations in the same request, proper HTML interface function
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
        String extension = FilenameUtils.getExtension(filePath);
        return getResourceFileWithExtension(filePath, extension);
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
        File file = File.createTempFile("ingest-import-test", "."+extension);

        // While we're packaged by play we have to ask Play for the inputstream instead of the classloader.
        //InputStream is = DSpaceCSVIngestServiceImplTests.class
        //		.getResourceAsStream(filePath);
        InputStream is = Play.classloader.getResourceAsStream(filePath);
        OutputStream os = new FileOutputStream(file);

        // Copy the file out of the jar into a temporary space.
        byte[] buffer = new byte[1024];
        int len;
        while ((len = is.read(buffer)) > 0) {
            os.write(buffer, 0, len);
        }
        is.close();
        os.close();

        return file;
    }
}
