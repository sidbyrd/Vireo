package controllers.settings;

import controllers.AbstractVireoFunctionalTest;
import org.apache.commons.lang.StringUtils;
import org.junit.*;
import org.tdl.vireo.constant.AppConfig;
import org.tdl.vireo.model.Configuration;
import org.tdl.vireo.model.MockSettingsRepository;
import org.tdl.vireo.model.PersonRepository;
import org.tdl.vireo.model.SettingsRepository;
import org.tdl.vireo.security.SecurityContext;
import org.tdl.vireo.services.Utilities;
import org.tdl.vireo.theme.CustomImage;
import org.tdl.vireo.theme.CustomImageTest;
import org.tdl.vireo.theme.ThemeDirectory;
import play.db.jpa.JPA;
import play.modules.spring.Spring;
import play.mvc.Http;
import play.mvc.Http.Response;
import play.mvc.Router;

import java.io.File;
import java.io.IOException;
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

    /** stubs to avoid stomping real data during test */
    private static String origPath = null;
    private static File tempDir = null;
    private static SettingsRepository origRepo = null;

    /** stuff common to many tests */
    private static final Map<String,String> params = new HashMap<String,String>(4);
    private static final Map<String,File> files = new HashMap<String,File>(2);
    private static final String urlUploadImage = Router.reverse("settings.ThemeSettingsTab.uploadImage").url;

    @BeforeClass
    public static void setupClass() throws IOException{
        // don't stomp on existing theme dir
        tempDir = java.nio.file.Files.createTempDirectory(null).toFile();
        tempDir.deleteOnExit();
        origPath = ThemeDirectory.swapPath(tempDir.getPath());
        // don't stomp on existing Configuration values
        MockSettingsRepository mockRepo = new MockSettingsRepository();
        origRepo = CustomImage.swapSettingsRepo(mockRepo);

        // if it somehow wasn't already, reset to default.
        CustomImage.reset(AppConfig.CIName.LEFT_LOGO);
    }

	@Before
	public void setup() throws IOException {
		context.turnOffAuthorization();
        CustomImage.reset(CIName.LEFT_LOGO);
		JPA.em().getTransaction().commit();
		JPA.em().clear();
		JPA.em().getTransaction().begin();

        params.clear();
        files.clear();
	}
	
	@After
	public void cleanup() throws IOException{
        CustomImage.reset(CIName.LEFT_LOGO);
        context.restoreAuthorization();
        JPA.em().getTransaction().commit();
		JPA.em().clear();
		JPA.em().getTransaction().begin();
	}

    @AfterClass
    public static void cleanupClass() throws IOException {
        for (File file : tempDir.listFiles()) {
            file.delete();
        }
        tempDir.delete();
        ThemeDirectory.swapPath(origPath);
        CustomImage.swapSettingsRepo(origRepo);
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
     * Asserts that the "flash" cookie contains the given error message, or if null, asserts that
     * there is no error.
     * @param response the HTTP Response in which to check
     * @param errorText the error text to check for, or null to assert no error
     */
    private static void assertErrorMessage(Response response, String errorText) {
        final String flash = response.cookies.get("PLAY_FLASH").value;
        if (errorText != null) {
            errorText = errorText.replace(' ','+');
            assertTrue("flash should contain '+fragment+' but was "+flash+'\'', flash.contains(errorText));
        } else {
            assertTrue("flash should be blank but was '"+flash+'\'', StringUtils.isBlank(flash));
        }
    }

    // Make sure deleting a default image doesn't actually do anything
    @Test public void testUploadImage_deleteDefault() {
        LOGIN();
        params.put("name", AppConfig.CIName.LEFT_LOGO.toString());
        params.put("delete1x", "true");
        params.put("delete2x", "true");
		Http.Response response = POST(urlUploadImage,params);

        // no error, and image data should stay default
		assertStatus(302,response);
        assertErrorMessage(response, null);
        assertTrue(CustomImage.isDefault(CIName.LEFT_LOGO));
    }

    // Test that no uploads happen if the submit_upload param is missing, but deletes still work.
    @Test public void testUploadImage_submit_upload_param() throws IOException {
        // start with existing custom 1x
        CustomImage.replaceFile(CIName.LEFT_LOGO, false, Utilities.getResourceFile("SampleLogo-single.png"));

        LOGIN();
        params.put("name", AppConfig.CIName.LEFT_LOGO.toString());
        params.put("delete1x", "true");
		files.put("image2x", Utilities.getResourceFile("SampleLogo-double.png")); // would work if submit_upload were present
		Http.Response response = POST(urlUploadImage,params,files);

        // deleted 1x and failed to add 2x, so we should be back at default
		assertStatus(302,response);
        assertErrorMessage(response, null);
        assertTrue(CustomImage.isDefault(CIName.LEFT_LOGO));
    }

    // Upload a 1x and 2x successfully
    @Test public void testUploadImage_add() throws IOException {
        LOGIN();
        params.put("name", AppConfig.CIName.LEFT_LOGO.toString());
        params.put("submit_upload", "true");
		files.put("image1x", Utilities.getResourceFile("SampleLogo-single.png"));
        files.put("image2x", Utilities.getResourceFile("SampleLogo-double.png"));
		Http.Response response = POST(urlUploadImage,params,files);

        // should have both images and no errors
		assertStatus(302,response);
        assertErrorMessage(response, null);
        assertFalse(CustomImage.isDefault(CIName.LEFT_LOGO));
        assertTrue(CustomImage.is2xSeparate(CIName.LEFT_LOGO));
    }

    // Delete a 1x and 2x successfully
    @Test public void testUploadImage_delete() throws IOException {
        // start with existing custom 1x & 2x
        CustomImage.replaceFile(CIName.LEFT_LOGO, false, Utilities.getResourceFile("SampleLogo-single.png"));
        CustomImage.replaceFile(CIName.LEFT_LOGO, true, Utilities.getResourceFile("SampleLogo-double.png"));

        LOGIN();
        params.put("name", AppConfig.CIName.LEFT_LOGO.toString());
        params.put("delete1x", "true");
        params.put("delete2x", "true");
		Http.Response response = POST(urlUploadImage,params,files);

        // back at default
		assertStatus(302,response);
        assertErrorMessage(response, null);
        assertTrue(CustomImage.isDefault(CIName.LEFT_LOGO));
    }

    // Delete and replace in the same request
    @Test public void testUploadImage_deleteAndReplace() throws IOException {
        // start with existing custom 1x & 2x
        CustomImage.replaceFile(CIName.LEFT_LOGO, false, Utilities.getResourceFile("SampleLogo-single.png"));
        CustomImage.replaceFile(CIName.LEFT_LOGO, true, Utilities.getResourceFile("SampleLogo-double.png"));

        LOGIN();
        // delete 1x and replace 2x
        params.put("name", AppConfig.CIName.LEFT_LOGO.toString());
        params.put("delete1x", "true");
        params.put("submit_upload", "true");
		files.put("image2x", Utilities.getResourceFile("SampleLogo-double.jpg"));
		Http.Response response = POST(urlUploadImage,params,files);

        // should have 2x only, and it should be the new JPG instead of the old PNG
		assertStatus(302,response);
        assertErrorMessage(response, null);
        assertTrue(CustomImage.is1xScaled(CIName.LEFT_LOGO));
        assertEquals("jpg", StringUtils.substringAfterLast(CustomImage.url(CIName.LEFT_LOGO, true), "."));
    }

    // Upload an image with 0 filesize
    @Test public void testUploadImage_errorFilesizeMinimum() throws IOException {
        LOGIN();
        params.put("name", AppConfig.CIName.LEFT_LOGO.toString());
        params.put("submit_upload", "true");
		files.put("image1x", Utilities.blankFileWithSize(0, "jpg"));
		Http.Response response = POST(urlUploadImage,params,files);

        // 0-length file probably won't even get delivered, but at least image data should stay default
		assertStatus(302,response);
        assertTrue(CustomImage.isDefault(CIName.LEFT_LOGO));
    }

    // Upload an image with filesize 1 byte over the max limit enforced by ThemeSettingsTab controller
    @Test public void testUploadImage_errorFilesizeMax() throws IOException {
        LOGIN();
        params.put("name", AppConfig.CIName.LEFT_LOGO.toString());
        params.put("submit_upload", "true");
		files.put("image1x", Utilities.blankFileWithSize(ThemeSettingsTab.MAX_UPLOAD_SIZE+1, "jpg"));
		Http.Response response = POST(urlUploadImage,params,files);

        // should be rejected, and image data should stay default
		assertStatus(302,response);
        assertErrorMessage(response, "maximum image size");
        assertTrue(CustomImage.isDefault(CIName.LEFT_LOGO));
    }

    // Test that errors external to ThemeSettingsTab, returned from CustomImage operations, are displayed
    @Test public void testUploadImage_displayError() throws IOException {
        LOGIN();
        // try to upload a 2x with odd dimensions
        params.put("name", AppConfig.CIName.LEFT_LOGO.toString());
        params.put("submit_upload", "true");
		files.put("image2x", Utilities.getResourceFile("SampleFeedbackDocument.png"));
		Http.Response response = POST(urlUploadImage,params,files);

        // should be rejected, and image data should stay default
		assertStatus(302,response);
        assertErrorMessage(response, "must be even");

       assertTrue(CustomImage.isDefault(CIName.LEFT_LOGO));
    }
}