package controllers.settings;

import controllers.AbstractVireoFunctionalTest;
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

    // Private helper. Passes through to CustomImageTest.assertCustomImageState (see docs there) after doing the
    // db stuff needed to view results of what the ThemeSettingsTab controller did in its separate db context.
    private static void assertUploadedImageState(String urlName1x, String urlName2x, String width, String height, File custom1xFile, File custom2xFile, int code2x) throws IOException {
        JPA.em().getTransaction().rollback(); // throw out my own previous context. I haven't been making important changes here anyway.
		JPA.em().getTransaction().begin(); // reload saved context, which includes changes just saved by what was tested.
        CustomImageTest.assertCustomImageState(urlName1x, urlName2x, width, height, custom1xFile, custom2xFile, code2x);
    }

    // try to upload an image with 0 filesize
    public void testUploadImage_filesize_minimum() throws IOException {
        final Map<String,String> params = new HashMap<String,String>(4);
        final Map<String,File> files = new HashMap<String,File>(2);
        final String URL = Router.reverse("settings.ThemeSettingsTab.uploadImage").url;
        LOGIN();

        params.put("name", AppConfig.CIName.LEFT_LOGO.toString());
        params.put("submit_upload", "true");
		files.put("image1x", Utilities.blankFileWithSize(0));
		Http.Response response = POST(URL,params,files);
        // should be rejected, and image data should stay default
		assertStatus(302,response);
        assertTrue(response.cookies.get("PLAY_FLASH").value.contains("image+was+empty"));
        //assertUploadedImageState(CustomImageTest.urlDefault1x, CustomImageTest.urlDefault1x, "150", "50", null, null, 1);
    }

    // try to upload an image with filesize 1 byte over the max limit enforced by ThemeSettingsTab controller
    public void testUploadImage_filesize_maximum() throws IOException {
        final Map<String,String> params = new HashMap<String,String>(4);
        final Map<String,File> files = new HashMap<String,File>(2);
        final String URL = Router.reverse("settings.ThemeSettingsTab.uploadImage").url;
        LOGIN();

        params.put("name", AppConfig.CIName.LEFT_LOGO.toString());
        params.put("submit_upload", "true");
		files.put("image1x", Utilities.blankFileWithSize(ThemeSettingsTab.MAX_UPLOAD_SIZE+1));
		Http.Response response = POST(URL,params,files);
        // should be rejected, and image data should stay default
		assertStatus(302,response);
        assertTrue(response.cookies.get("PLAY_FLASH").value.contains("maximum+image+size"));
        //assertUploadedImageState(CustomImageTest.urlDefault1x, CustomImageTest.urlDefault1x, "150", "50", null, null, 1);
    }

    //TODO
    // split up the giant testUploadingImage() test
    // focus here on testing interface bits, not CI function.

	/**
	 * Test various cases and orderings of uploading and deleting custom images, both the 1x
     * and 2x versions.
	 * @throws IOException
	 */
	@Test
	public void testUploadingImage() throws IOException {
        final Map<String,String> params = new HashMap<String,String>(4);
        final Map<String,File> files = new HashMap<String,File>(2);

        final String default1x = CustomImage.url(AppConfig.CIName.LEFT_LOGO, false);
        final String default2x = CustomImage.url(AppConfig.CIName.LEFT_LOGO, true);
        final String defaultWidth = CustomImage.displayWidth(AppConfig.CIName.LEFT_LOGO);
        final String defaultHeight = CustomImage.displayHeight(AppConfig.CIName.LEFT_LOGO);
        final String png1x="theme/left-logo.png";
        final String png2x="theme/left-logo@2x.png";
        final String jpg1x="theme/left-logo.jpg";
        final String gif1x="theme/left-logo.gif";
        final File pngSmall = Utilities.getResourceFile("SampleLogo-single.png"); // single: 150*84
        final File pngLarge = Utilities.getResourceFile("SampleLogo-double.png"); // double: 300*168
        final File pngOdd = Utilities.getResourceFile("SampleFeedbackDocument.png"); // odd: 541x378
        final File jpgLarge = Utilities.getResourceFile("SampleLogo-double.jpg"); // any jpg
        final File gifLarge = Utilities.getResourceFile("SampleLogo-double.gif"); // any gif

        LOGIN();

		//Get the url
		final String URL = Router.reverse("settings.ThemeSettingsTab.uploadImage").url;

        // upload invalid 2x image onto default (non-even height/width)
        params.put("submit_upload", "true");
        params.put("name", AppConfig.CIName.LEFT_LOGO.toString());
		files.put("image2x", pngOdd);
		Http.Response response = POST(URL,params,files);
        // should be rejected, and image data should stay default
		assertStatus(302,response);
        assertTrue(response.cookies.get("PLAY_FLASH").value.contains("must+be+even"));
        assertUploadedImageState(default1x, default2x, defaultWidth, defaultHeight, null, null, 1);

        // upload 1x onto default (with odd dimensions)
        files.clear();
        files.put("image1x", pngOdd);
        // should be accepted and set as standalone 1x image with no 2x
        response = POST(URL,params,files);
        assertStatus(302, response);
        assertUploadedImageState(png1x, null, "541", "378", pngOdd, null, 0);

        // upload a different (smaller) 1x onto existing 1x
        files.clear();
		files.put("image1x", pngSmall);
		response = POST(URL,params,files);
        // should be accepted and replace the previous 1x
		assertStatus(302, response);
        assertUploadedImageState(png1x, null, "150", "84", pngSmall, null, 0);

        // upload invalid 1x image (true format is PNG but extension says GIF)
        files.clear();
		files.put("image1x", Utilities.getResourceFileWithExtension("SampleFeedbackDocument.png", "gif"));
		response = POST(URL,params,files);
        // should be rejected, and image data should stay the same
		assertStatus(302,response);
        assertTrue(response.cookies.get("PLAY_FLASH").value.contains("format+not+recognized"));
        assertUploadedImageState(png1x, null, "150", "84", pngSmall, null, 0);

        // upload incompatible 2x image (same format as 1x, not double the size)
        files.clear();
		files.put("image2x", pngSmall);
		response = POST(URL,params,files);
        // should be rejected, and image data should stay the same
		assertStatus(302,response);
        assertTrue(response.cookies.get("PLAY_FLASH").value.contains("dimensions+must+be+exactly+double"));
        assertUploadedImageState(png1x, null, "150", "84", pngSmall, null, 0);

        // upload incompatible 2x image (different format from 2x, double the size)
        files.clear();
		files.put("image2x", jpgLarge);
		response = POST(URL,params,files);
        // should be rejected, and image data should stay the same
		assertStatus(302,response);
        assertTrue(response.cookies.get("PLAY_FLASH").value.contains("extension+must+match"));
        assertUploadedImageState(png1x, null, "150", "84", pngSmall, null, 0);

        // upload valid new 2x (same format as 1x, double the size)
		files.clear();
		files.put("image2x", pngLarge);
		response = POST(URL,params,files);
        // should be accepted and image data set for separate 1x and 2x
		assertStatus(302,response);
        assertUploadedImageState(png1x, png2x, "150", "84", pngSmall, pngLarge, 1);

        // delete 1x, leaving 2x in place
        params.clear();
		params.put("delete1x", "true");
        params.put("name", AppConfig.CIName.LEFT_LOGO.toString());
        files.clear();
        response = POST(URL,params,files);
        // should be accepted and image data set for remaining image to be standalone 2x
		assertStatus(302,response);
        assertUploadedImageState(png2x, png2x, "150", "84", null, pngLarge, 2);

        // upload invalid 1x (same extension as 2x, not half the size)
        params.clear();
        params.put("submit_upload", "true");
        params.put("name", AppConfig.CIName.LEFT_LOGO.toString());
        files.clear();
        files.put("image1x", pngLarge);
        response = POST(URL,params,files);
        // should be rejected, and image data should stay the same
		assertStatus(302,response);
        assertUploadedImageState(png2x, png2x, "150", "84", null, pngLarge, 2);

        // upload 1x onto existing 2x
        files.clear();
        files.put("image1x", pngSmall);
        response = POST(URL,params,files);
        // should be accepted and image data set for separate 1x and 2x
		assertStatus(302,response);
        assertUploadedImageState(png1x, png2x, "150", "84", pngSmall, pngLarge, 1);

        // delete 2x, leaving 1x in place
        params.clear();
		params.put("delete2x", "true");
        params.put("name", AppConfig.CIName.LEFT_LOGO.toString());
        files.clear();
        response = POST(URL,params,files);
        // should be accepted and data set for standalone 1x image
        assertStatus(302,response);
        assertUploadedImageState(png1x, null, "150", "84", pngSmall, null, 0);

        // delete 1x with no existing 2x, resulting in default state
        params.clear();
		params.put("delete1x", "true");
        params.put("name", AppConfig.CIName.LEFT_LOGO.toString());
        files.clear();
        response = POST(URL,params,files);
        // should be default
		assertStatus(302,response);
        assertUploadedImageState(default1x, default2x, defaultWidth, defaultHeight, null, null, 1);

        // upload 1x, upload 2x (valid combination)
        params.clear();
        params.put("submit_upload", "true");
        params.put("name", AppConfig.CIName.LEFT_LOGO.toString());
        files.clear();
        files.put("image1x", pngSmall);
        files.put("image2x", pngLarge);
        response = POST(URL,params,files);
        // should be accepted and data set for separate 1x and 2x
        assertStatus(302,response);
        assertUploadedImageState(png1x, png2x, "150", "84", pngSmall, pngLarge, 1);

        // delete2x, upload different suddenly-valid 1x (JPEG), upload different 2x that's now an invalid combination with the JPEG
        params.clear();
        params.put("delete2x", "true");
        params.put("submit_upload", "true");
        params.put("name", AppConfig.CIName.LEFT_LOGO.toString());
        files.clear();
        files.put("image1x", jpgLarge);
        files.put("image2x", pngSmall);
        response = POST(URL,params,files);
        // should be rejected ultimately, but not until 2x deleted, and 1x updated and data set for standalone 1x
        assertStatus(302,response);
        assertUploadedImageState(jpg1x, null, "300", "168", jpgLarge, null, 0);

        // upload a 1x GIF onto existing 1x JPG
        files.clear();
		files.put("image1x", gifLarge);
		response = POST(URL,params,files);
        // should be accepted and replace the previous 1x
		assertStatus(302, response);
        assertUploadedImageState(gif1x, null, "300", "168", gifLarge, null, 0);

        // delete1x and attempt to submit a 2x, but fail to set submit_upload, resulting in default state
        params.clear();
		params.put("delete1x", "true");
        params.put("name", AppConfig.CIName.LEFT_LOGO.toString());
        files.clear();
        files.put("image1x", pngSmall);
        response = POST(URL,params,files);
        // should be default
		assertStatus(302,response);
        assertUploadedImageState(default1x, default2x, defaultWidth, defaultHeight, null, null, 1);
	}
}
