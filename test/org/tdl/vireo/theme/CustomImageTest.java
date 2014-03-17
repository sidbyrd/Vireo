package org.tdl.vireo.theme;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.tdl.vireo.constant.AppConfig;
import org.tdl.vireo.model.MockSettingsRepository;
import org.tdl.vireo.model.PersonRepository;
import org.tdl.vireo.model.SettingsRepository;
import org.tdl.vireo.security.SecurityContext;
import org.tdl.vireo.services.Utilities;
import play.db.jpa.JPA;
import play.i18n.Messages;
import play.modules.spring.Spring;
import play.test.UnitTest;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;

public class CustomImageTest extends UnitTest {

    public static SecurityContext context = Spring.getBeanOfType(SecurityContext.class);
    public static PersonRepository personRepo = Spring.getBeanOfType(PersonRepository.class);
    public static SettingsRepository settingRepo = Spring.getBeanOfType(SettingsRepository.class);

    // This stuff is static because it's unchanging and only needs calculating/making
    // once ever (especially the image files), not once per test, whereas this class
    // is instantiated once per test.

    /** record defaults to check against later */
    private static String urlDefault1x;
    private static String urlDefault2x;
    private static String defaultWidth;
    private static String defaultHeight;

    /** proper URLs for images of a given resolution and format */
    private static String urlPng1x;
    private static String urlPng2x;
    private static String urlJpg1x;
    private static String urlGif1x;

    /** test files available */
    private static File filePngSmall;
    private static File filePngLarge;
    private static File filePngOdd;
    private static File fileJpgLarge;
    private static File fileGifLarge;

    /** stubs to avoid stomping real data during test */
    private static String origPath = null;
    private static File tempDir = null;
    private static SettingsRepository origRepo = null;

    @BeforeClass
    public static void setupClass() throws IOException{
        context.turnOffAuthorization();

        // don't stomp on existing theme dir
        tempDir = java.nio.file.Files.createTempDirectory(null).toFile();
        tempDir.deleteOnExit();
        origPath = ThemeDirectory.swapPath(tempDir.getPath());
        // don't stomp on existing Configuration values
        MockSettingsRepository mockRepo = new MockSettingsRepository();
        origRepo = CustomImage.swapSettingsRepo(mockRepo);

        // if it somehow wasn't already, reset to default.
        CustomImage.reset(AppConfig.CIName.LEFT_LOGO);

        // save default state so we can compare to it later
        urlDefault1x = CustomImage.url(AppConfig.CIName.LEFT_LOGO, false);
        urlDefault2x = CustomImage.url(AppConfig.CIName.LEFT_LOGO, true);
        defaultWidth = CustomImage.displayWidth(AppConfig.CIName.LEFT_LOGO);
        defaultHeight = CustomImage.displayHeight(AppConfig.CIName.LEFT_LOGO);

        // initialize unchanging data and resources used in multiple tests
        urlPng1x = ThemeDirectory.URL_PREFIX+"left-logo.png";
        urlPng2x = ThemeDirectory.URL_PREFIX+"left-logo@2x.png";
        urlJpg1x = ThemeDirectory.URL_PREFIX+"left-logo.jpg";
        urlGif1x = ThemeDirectory.URL_PREFIX+"left-logo.gif";

        filePngSmall = Utilities.getResourceFile("SampleLogo-single.png"); // single: 150*84
        filePngLarge = Utilities.getResourceFile("SampleLogo-double.png"); // double: 300*168
        filePngOdd = Utilities.getResourceFile("SampleFeedbackDocument.png"); // odd: 541x378
        fileJpgLarge = Utilities.getResourceFile("SampleLogo-double.jpg"); // any jpg
        fileGifLarge = Utilities.getResourceFile("SampleLogo-double.gif"); // any gif
    }

    @After
    public void cleanup() throws IOException{
        CustomImage.reset(AppConfig.CIName.LEFT_LOGO);
    }

    @AfterClass
    public static void cleanupClass() throws IOException {
        context.restoreAuthorization();
        for (File file : tempDir.listFiles()) {
            file.delete();
        }
        tempDir.delete();
        ThemeDirectory.swapPath(origPath);
        CustomImage.swapSettingsRepo(origRepo);
    }

    // Since these defaults are queried from CustomImage and then used to verify later operations,
    // make sure they're what I think they are, which is images outside the theme directory, and
    // therefore cannot somehow be equal to test resources that need to be different from default.
    @Test public void testDefaults() {
        assertNotNull(urlDefault1x);
        assertFalse(urlDefault1x.startsWith(ThemeDirectory.URL_PREFIX));
        assertFalse(urlDefault1x != null && urlDefault2x.startsWith(ThemeDirectory.URL_PREFIX));
    }

    /**
     * Helper to assert that pretty much everything about the saved state of a custom image is as expected.
     * Methods whose return value is tested here:
     * - CustomImage.url
     * - CustomImage.displayWidth
     * - CustomImage.displayHeight
     * - CustomImage.isDefault
     * - CustomImage.hasCustomFile
     * - CustomImage.is2xNone
     * - CustomImage.is2xSeparate
     * - CustomImage.is1xScaled
     * - customImage.standardFilename
     * Non-method-return output tested here (exposed directly to user by HTTP, not via any Java method call):
     * - presence/absence of custom 1x file (if applicable).
     * - contents of custom 1x file (if applicable).
     * - presence/absence of custom 2x file (if applicable).
     * - contents of custom 2x file (if applicable).
     * - lack of any theme files that look like they're for this image, but weren't expected.
     * @param urlName1x correct url from CustomImage.url(name, false).
     * @param urlName2x correct url from CustomImage.url(name, true), or null if should return null.
     * @param width correct display width
     * @param height correct display height
     * @param custom1xFile The correct contents of the stored 1x custom image, or null if it shouldn't exist.
     * @param custom2xFile The correct contents of the stored 2x custom image, or null if it shouldn't exist.
     * @param code2x indicates which one (and only one) these should be true:
     *              0: is2xNone==true
     *              1: is2xSeparate==true
     *              2: is1xScaled==true (a.k.a is2xSame==true)
     * @throws IOException if error while checking custom file existence or content
     */
    public static void assertCustomImageState(String urlName1x, String urlName2x,
                                              String width, String height,
                                              File custom1xFile, File custom2xFile, int code2x) throws IOException {

        final boolean shouldBeDefault = custom1xFile==null && custom2xFile==null;

        // check image metadata queries
        assertEquals(urlName1x, CustomImage.url(AppConfig.CIName.LEFT_LOGO, false));
        assertEquals(urlName2x, CustomImage.url(AppConfig.CIName.LEFT_LOGO, true));
        assertEquals(width, CustomImage.displayWidth(AppConfig.CIName.LEFT_LOGO));
        assertEquals(height, CustomImage.displayHeight(AppConfig.CIName.LEFT_LOGO));
        assertEquals(shouldBeDefault, CustomImage.isDefault(AppConfig.CIName.LEFT_LOGO));
        assertEquals(custom1xFile != null, CustomImage.hasCustomFile(AppConfig.CIName.LEFT_LOGO, false));
        assertEquals(custom2xFile != null, CustomImage.hasCustomFile(AppConfig.CIName.LEFT_LOGO, true));
        assertEquals(code2x == 0, CustomImage.is2xNone(AppConfig.CIName.LEFT_LOGO));
        assertEquals(code2x == 1, CustomImage.is2xSeparate(AppConfig.CIName.LEFT_LOGO));
        assertEquals(code2x == 2, CustomImage.is1xScaled(AppConfig.CIName.LEFT_LOGO));

        if (!shouldBeDefault) {
            assertTrue(ThemeDirectory.check(false));
            final String ext = FilenameUtils.getExtension(urlName1x);
            
            // Check that custom files exist with correct content,
            // and that CustomImage can come up with the right names for them
            File file1x = ThemeDirectory.fileForUrl(urlName1x);
            if (custom1xFile!=null) {
                assertTrue(file1x.compareTo(ThemeDirectory.getFile(CustomImage.standardFilename(AppConfig.CIName.LEFT_LOGO, false, ext)))==0);
                assertTrue(file1x.exists());
                assertTrue(FileUtils.contentEquals(custom1xFile, file1x));
            }
            File file2x = ThemeDirectory.fileForUrl(urlName2x);
            if (custom2xFile!=null) {
                assertTrue(file2x.compareTo(ThemeDirectory.getFile(CustomImage.standardFilename(AppConfig.CIName.LEFT_LOGO, true, ext)))==0);
                assertTrue(file2x.exists());
                assertTrue(FileUtils.contentEquals(custom2xFile, file2x));
            }

            // Check that no files for this image are in the theme dir except those we want.
            FileFilter imageFilter = new WildcardFileFilter(AppConfig.CIName.LEFT_LOGO.toString().replace("_","-")+"*");
            File[] foundFiles = ThemeDirectory.listFiles(imageFilter);
            for (File file : foundFiles) {
                if (! (custom1xFile!=null && file.compareTo(file1x)==0) || (custom2xFile!=null && file.compareTo(file2x)==0)) {
                    fail("Found customized image file in theme directory that should not be there: '"+file.getPath()+"'");
                }
            }
        }
    }

    /* *****************************
     *   transitions from default
     * *****************************/

    // default: add 1x (odd-sized) -> success
    @Test public void test_success_fromDefault_add1xOdd() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.LEFT_LOGO, false, filePngOdd);
        // check: 1x only, pngOdd
        assertCustomImageState(urlPng1x, null, "541", "378", filePngOdd, null, 0);
    }

    // default: add 2x -> success
    @Test public void test_success_fromDefault_add2x() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.LEFT_LOGO, true, filePngSmall);
        // check: 2x only, pngSmall
        assertCustomImageState(urlPng2x, urlPng2x, "75", "42", null, filePngSmall, 2);
    }

    // default: delete1x -> unchanged, delete2x -> unchanged, reset -> unchanged
    @Test public void test_unchanged_fromDefault_remove1xRemove2xReset() throws IOException {
        CustomImage.deleteFile(AppConfig.CIName.LEFT_LOGO, false);
        // check: default
        assertCustomImageState(urlDefault1x, urlDefault2x, defaultWidth, defaultHeight, null, null, 1);

        CustomImage.deleteFile(AppConfig.CIName.LEFT_LOGO, true);
        // check: default
        assertCustomImageState(urlDefault1x, urlDefault2x, defaultWidth, defaultHeight, null, null, 1);

        CustomImage.reset(AppConfig.CIName.LEFT_LOGO);
        // check: default
        assertCustomImageState(urlDefault1x, urlDefault2x, defaultWidth, defaultHeight, null, null, 1);
    }

    /* *************************
     *   transitions from 1x
     * *************************/

    // 1x: replace 1x -> success
    @Test public void test_success_from1x_add1x() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.LEFT_LOGO, false, filePngSmall);
        
        CustomImage.replaceFile(AppConfig.CIName.LEFT_LOGO, false, filePngLarge);
        // check: 1x only, pngLarge
        assertCustomImageState(urlPng1x, null, "300", "168", filePngLarge, null, 0);
    }

    // 1x: add 2x (match OK) -> success
    @Test public void test_success_from1x_add2xMatch() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.LEFT_LOGO, false, filePngSmall);
        
        CustomImage.replaceFile(AppConfig.CIName.LEFT_LOGO, true, filePngLarge);
        // check: 1x+2x, pngSmall+pngLarge
        assertCustomImageState(urlPng1x, urlPng2x, "150", "84", filePngSmall, filePngLarge, 1);
    }

    // 1x: add 2x (mismatch) -> error
    @Test public void test_error_from1x_add2xMismatch() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.LEFT_LOGO, false, filePngSmall);

        try {
            CustomImage.replaceFile(AppConfig.CIName.LEFT_LOGO, true, filePngSmall);
            fail("Should not accept 2x image with mismatched size");
        } catch (IllegalArgumentException e) {
            assertEquals(Messages.get("CI_ERROR_MISMATCHED_DIMENSIONS"), e.getMessage());
        }
        // check: 1x only, pngSmall
        assertCustomImageState(urlPng1x, null, "150", "84", filePngSmall, null, 0);

        try {
            CustomImage.replaceFile(AppConfig.CIName.LEFT_LOGO, true, fileJpgLarge);
            fail("Should not accept 2x image with mismatched format");
        } catch (IllegalArgumentException e) {
            assertEquals(Messages.get("CI_ERROR_MISMATCHED_FORMAT").replace("EXT", "png"), e.getMessage());
        }
        // check: 1x only, pngSmall
        assertCustomImageState(urlPng1x, null, "150", "84", filePngSmall, null, 0);
    }

    // 1x: remove1x -> success
    @Test public void test_success_from1x_remove1x() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.LEFT_LOGO, false, filePngSmall);

        CustomImage.deleteFile(AppConfig.CIName.LEFT_LOGO, false);
        // check: default
        assertCustomImageState(urlDefault1x, urlDefault2x, defaultWidth, defaultHeight, null, null, 1);
    }

    // 1x: remove2x -> unchanged
    @Test public void test_unchanged_from1x_remove2x() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.LEFT_LOGO, false, filePngSmall);

        CustomImage.deleteFile(AppConfig.CIName.LEFT_LOGO, true);
        // check: 1x only, pngSmall
        assertCustomImageState(urlPng1x, null, "150", "84", filePngSmall, null, 0);
    }

    // 1x: reset -> success
    @Test public void test_success_from1x_reset() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.LEFT_LOGO, false, filePngSmall);

        CustomImage.reset(AppConfig.CIName.LEFT_LOGO);
        // check: default
        assertCustomImageState(urlDefault1x, urlDefault2x, defaultWidth, defaultHeight, null, null, 1);
    }

    /* *************************
     *   transitions from 2x
     * *************************/

    // 2x: replace 2x -> success
    @Test public void test_success_from2x_add2x() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.LEFT_LOGO, true, filePngSmall);

        CustomImage.replaceFile(AppConfig.CIName.LEFT_LOGO, true, filePngLarge);
        // check: 2x only, pngLarge
        assertCustomImageState(urlPng2x, urlPng2x, "150", "84", null, filePngLarge, 2);
    }

    // 2x: add 1x (match OK) -> success
    @Test public void test_success_from2x_add1xMatch() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.LEFT_LOGO, true, filePngLarge);

        CustomImage.replaceFile(AppConfig.CIName.LEFT_LOGO, false, filePngSmall);
        // check: 1x+2x, pngSmall+pngLarge
        assertCustomImageState(urlPng1x, urlPng2x, "150", "84", filePngSmall, filePngLarge, 1);
    }

    // 2x: add 1x (mismatch) -> error
    @Test public void test_error_from2x_add1xMismatch() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.LEFT_LOGO, true, filePngLarge);

        try {
            CustomImage.replaceFile(AppConfig.CIName.LEFT_LOGO, false, filePngLarge);
            fail("Should not accept 1x image with mismatched size");
        } catch (IllegalArgumentException e) {
            assertEquals(Messages.get("CI_ERROR_MISMATCHED_DIMENSIONS"), e.getMessage());
        }
        // check: 2x only, pngLarge
        assertCustomImageState(urlPng2x, urlPng2x, "150", "84", null, filePngLarge, 2);
    }

    // 2x: remove2x -> success
    @Test public void test_success_from2x_remove2x() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.LEFT_LOGO, true, filePngSmall);

        CustomImage.deleteFile(AppConfig.CIName.LEFT_LOGO, true);
        // check: default
        assertCustomImageState(urlDefault1x, urlDefault2x, defaultWidth, defaultHeight, null, null, 1);
    }

    // 2x: remove1x -> unchanged
    @Test public void test_unchanged_from2x_remove1x() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.LEFT_LOGO, true, filePngSmall);

        CustomImage.deleteFile(AppConfig.CIName.LEFT_LOGO, false);
        // check: 2x only, pngSmall
        assertCustomImageState(urlPng2x, urlPng2x, "75", "42", null, filePngSmall, 2);
    }

    // 2x: reset -> success
    @Test public void test_success_from2x_reset() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.LEFT_LOGO, true, filePngSmall);

        CustomImage.reset(AppConfig.CIName.LEFT_LOGO);
        // check: default
        assertCustomImageState(urlDefault1x, urlDefault2x, defaultWidth, defaultHeight, null, null, 1);
    }

    /* ***************************
     *   transitions from 1x+2x
     * ***************************/

    // 1x+2x: remove1x -> success
    @Test public void test_success_from1x2x_remove1x() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.LEFT_LOGO, false, filePngSmall);
        CustomImage.replaceFile(AppConfig.CIName.LEFT_LOGO, true, filePngLarge);

        CustomImage.deleteFile(AppConfig.CIName.LEFT_LOGO, false);
        // check: 2x only, pngLarge
        assertCustomImageState(urlPng2x, urlPng2x, "150", "84", null, filePngLarge, 2);
    }

    // 1x+2x: remove2x -> success
    @Test public void test_success_from1x2x_remove2x() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.LEFT_LOGO, false, filePngSmall);
        CustomImage.replaceFile(AppConfig.CIName.LEFT_LOGO, true, filePngLarge);

        CustomImage.deleteFile(AppConfig.CIName.LEFT_LOGO, true);
        // check: 1x only, pngSmall
        assertCustomImageState(urlPng1x, null, "150", "84", filePngSmall, null, 0);
    }

/* Didn't want to have to include 2 more resource image files just for this.
    // 1x+2x: replace 1x (matching) -> success, replace 2x (matching) -> success
    @Test public void test_success_from1x2x_add1xMatch() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.LEFT_LOGO, false, filePngSmall);
        CustomImage.replaceFile(AppConfig.CIName.LEFT_LOGO, true, filePngLarge);

        CustomImage.replaceFile(AppConfig.CIName.LEFT_LOGO, false, filePngSmall2);
        // check: 1x+2x, pngSmall2+pngLarge
        checkCustomImage(urlPng1x, urlPng2x, "150", "84", filePngSmall2, filePngLarge, 1);

        CustomImage.replaceFile(AppConfig.CIName.LEFT_LOGO, true, filePngLarge2);
        // check: 1x+2x, pngSmall2+pngLarge2
        checkCustomImage(urlPng1x, urlPng2x, "150", "84", filePngSmall2, filePngLarge2, 1);
    }
*/

    // 1x+2x: replace 1x (mismatch) -> error, replace 2x (mismatch) -> error
    @Test public void test_error_from1x_add1xMismatchAdd2xMismatch() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.LEFT_LOGO, false, filePngSmall);
        CustomImage.replaceFile(AppConfig.CIName.LEFT_LOGO, true, filePngLarge);

        try {
            CustomImage.replaceFile(AppConfig.CIName.LEFT_LOGO, false, filePngLarge);
            fail("Should not accept 1x image with mismatched size");
        } catch (IllegalArgumentException e) {
            assertEquals(Messages.get("CI_ERROR_MISMATCHED_DIMENSIONS"), e.getMessage());
        }
        // check: 1x+2x, pngSmall+pngLarge
        assertCustomImageState(urlPng1x, urlPng2x, "150", "84", filePngSmall, filePngLarge, 1);

        try {
            CustomImage.replaceFile(AppConfig.CIName.LEFT_LOGO, true, filePngSmall);
            fail("Should not accept 2x image with mismatched size");
        } catch (IllegalArgumentException e) {
            assertEquals(Messages.get("CI_ERROR_MISMATCHED_DIMENSIONS"), e.getMessage());
        }
        // check: 1x+2x, pngSmall+pngLarge
        assertCustomImageState(urlPng1x, urlPng2x, "150", "84", filePngSmall, filePngLarge, 1);
    }

    // 1x+2x: reset -> success
    @Test public void test_success_from1x2x_reset() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.LEFT_LOGO, false, filePngSmall);
        CustomImage.replaceFile(AppConfig.CIName.LEFT_LOGO, true, filePngLarge);

        CustomImage.reset(AppConfig.CIName.LEFT_LOGO);
        // check: default
        assertCustomImageState(urlDefault1x, urlDefault2x, defaultWidth, defaultHeight, null, null, 1);
    }

    /* ******************************
     *   Accept/reject image files
     *     regardless of state
     * ******************************/

    // add 2x (odd-sized) -> error
    @Test public void test_error_add2xOdd() throws IOException {
        try {
            CustomImage.replaceFile(AppConfig.CIName.LEFT_LOGO, true, filePngOdd);
            fail("Should not accept odd-sized 2x image");
        } catch (IllegalArgumentException e) {
            assertEquals(Messages.get("CI_ERROR_DIMENSIONS_2X_ODD"), e.getMessage());
        }
        // check: default
        assertCustomImageState(urlDefault1x, urlDefault2x, defaultWidth, defaultHeight, null, null, 1);
    }

    // add image (file extension doesn't match true format) -> error
    @Test public void test_error_addIncorrectExtension() throws IOException {
        try {
            CustomImage.replaceFile(AppConfig.CIName.LEFT_LOGO, false, Utilities.getResourceFileWithExtension("SampleLogo-single.png", "gif"));
            fail("Should not accept image file with incorrect file extension");
        } catch (IllegalArgumentException e) {
            assertEquals(Messages.get("CI_ERROR_FORMAT_UNKNOWN"), e.getMessage());
        }
        // check: default
        assertCustomImageState(urlDefault1x, urlDefault2x, defaultWidth, defaultHeight, null, null, 1);
    }

    // add PDF file -> error
    @Test public void test_error_addInvalidImage() throws IOException {
        try {
            CustomImage.replaceFile(AppConfig.CIName.LEFT_LOGO, false, Utilities.getResourceFile("SamplePrimaryDocument.pdf"));
            fail("Should not accept file that isn't a common webserver image format");
        } catch (IllegalArgumentException e) {
            assertEquals(Messages.get("CI_ERROR_FORMAT_UNKNOWN"), e.getMessage());
        }
        // check: default
        assertCustomImageState(urlDefault1x, urlDefault2x, defaultWidth, defaultHeight, null, null, 1);
    }

    // add GIF image -> success
    @Test public void test_success_addGif() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.LEFT_LOGO, false, fileGifLarge);

        // check: 1x only, gifLarge
        assertCustomImageState(urlGif1x, null, "300", "168", fileGifLarge, null, 0);
    }

    // add JPG image -> success
    @Test public void test_success_addJpg() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.LEFT_LOGO, false, fileJpgLarge);

        // check: 1x only, gifLarge
        assertCustomImageState(urlJpg1x, null, "300", "168", fileJpgLarge, null, 0);
    }

    // not tested: CustomImage.tallerHeight(name1, name2).

    // Not tested: CustomImage.fileDescription.
    // It's just a very simple combination of other methods which *are* tested, but would be annoyingly wordy to test directly.
}
