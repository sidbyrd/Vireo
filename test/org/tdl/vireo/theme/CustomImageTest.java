package org.tdl.vireo.theme;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.tdl.vireo.constant.AppConfig;
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

    /** names for image+resolution, once installed */
    private final String urlDefault1x ="public"+File.separatorChar+"images"+File.separatorChar+"vireo-logo-sm.png";
    private final String urlDefault2x ="public"+File.separatorChar+"images"+File.separatorChar+"vireo-logo-sm@2x.png";
    private final String urlPng1x ="test-logo.png";
    private final String urlPng2x ="test-logo@2x.png";
    private final String urlJpg1x ="test-logo.jpg";
    private final String urlGif1x ="test-logo.gif";

    /** test files available */
    private File filePngSmall;
    private File filePngLarge;
    private File filePngOdd;
    private File fileJpgLarge;
    private File fileGifLarge;

    public CustomImageTest() {
        try {
            filePngSmall = Utilities.getResourceFile("SampleLogo-single.png"); // single: 150*84
            filePngLarge = Utilities.getResourceFile("SampleLogo-double.png"); // double: 300*168
            filePngOdd = Utilities.getResourceFile("SampleFeedbackDocument.png"); // odd: 541x378
            fileJpgLarge = Utilities.getResourceFile("SampleLogo-double.jpg"); // any jpg
            fileGifLarge = Utilities.getResourceFile("SampleLogo-double.gif"); // any gif
        } catch (IOException e) { /**/ }
    }

    @BeforeClass
    public static void setupBeforeClass() throws IOException{
        context.turnOffAuthorization();
        CustomImage.reset(AppConfig.CIName.TEST_LOGO); // if it wasn't already somehow, make it default.

        JPA.em().getTransaction().commit();
        JPA.em().clear();
        JPA.em().getTransaction().begin();
    }

    @After
    public void cleanup() throws IOException{
        JPA.em().getTransaction().rollback();
        JPA.em().clear();
        JPA.em().getTransaction().begin();

        CustomImage.reset(AppConfig.CIName.TEST_LOGO); // make sure to leave no files from failed tests
    }

    @AfterClass
    public static void cleanupAfterClass() {
        context.restoreAuthorization();
    }

    /**
     * Helper to assert that pretty much everything about the saved state of a custom image is as expected.
     * Methods whose return value is tested here:
     * - CustomImage.url(name, false)
     * - CustomImage.url(name, true)
     * - CustomImage.displayWidth(name)
     * - CustomImage.displayHeight(name)
     * - CustomImage.isDefault(name)
     * - CustomImage.hasCustomFile(name, false)
     * - CustomImage.hasCustomFile(name, true)
     * - CustomImage.is2xNone(name)
     * - CustomImage.is2xSeparate(name)
     * - CustomImage.is1xScaled(name)
     * Non-method-return output tested here (exposed directly to user by HTTP, not via any Java method call):
     * - presence/absence of custom 1x file (if applicable).
     * - contents of custom 1x file (if applicable).
     * - presence/absence of custom 2x file (if applicable).
     * - contents of custom 2x file (if applicable).
     * - lack of any theme files that look like they're for this image, but weren't expected.
     * @param urlName1x correct filename only (no path) from CustomImage.url(name, false).
     * @param urlName2x correct filename only (no path) from CustomImage.url(name, true), or null if that method should return null.
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
    public static void checkCustomImage(String urlName1x, String urlName2x,
                                         String width, String height,
                                         File custom1xFile, File custom2xFile, int code2x) throws IOException {

        final boolean shouldBeDefault = custom1xFile==null && custom2xFile==null;

        // check image metadata queries
        assertEquals((shouldBeDefault?"":ThemeDirectory.URL_PREFIX) + urlName1x, CustomImage.url(AppConfig.CIName.TEST_LOGO, false));
        assertEquals(urlName2x==null?null:(shouldBeDefault?"":ThemeDirectory.URL_PREFIX) + urlName2x, CustomImage.url(AppConfig.CIName.TEST_LOGO, true));
        assertEquals(width, CustomImage.displayWidth(AppConfig.CIName.TEST_LOGO));
        assertEquals(height, CustomImage.displayHeight(AppConfig.CIName.TEST_LOGO));
        assertEquals(!(custom1xFile!=null || custom2xFile!=null), CustomImage.isDefault(AppConfig.CIName.TEST_LOGO));
        assertEquals(custom1xFile != null, CustomImage.hasCustomFile(AppConfig.CIName.TEST_LOGO, false));
        assertEquals(custom2xFile != null, CustomImage.hasCustomFile(AppConfig.CIName.TEST_LOGO, true));
        assertEquals(code2x == 0, CustomImage.is2xNone(AppConfig.CIName.TEST_LOGO));
        assertEquals(code2x == 1, CustomImage.is2xSeparate(AppConfig.CIName.TEST_LOGO));
        assertEquals(code2x == 2, CustomImage.is1xScaled(AppConfig.CIName.TEST_LOGO));

        // If there are any customizations, make sure there is a theme dir.
        File themeDir = new File(ThemeDirectory.PATH);
        if (!shouldBeDefault) {
            assertTrue(themeDir.exists());
        }
        // Check that no files for this image are in the theme dir except those we want,
        // and check that the file contents are correct on those we do want.
        if (themeDir.exists()) {
            final String ext = FilenameUtils.getExtension(urlName1x);
            File filename1x = new File(ThemeDirectory.PATH +CustomImage.standardFilename(AppConfig.CIName.TEST_LOGO, false, ext));
            File filename2x = new File(ThemeDirectory.PATH +CustomImage.standardFilename(AppConfig.CIName.TEST_LOGO, true, ext));
            FileFilter imageFilter = new WildcardFileFilter(AppConfig.CIName.TEST_LOGO.toString().replace("_","-")+"*");
            File[] foundFiles = themeDir.listFiles(imageFilter);
            for (File file : foundFiles) {
                // if anticipated filename found, check the file's contents.
                if (custom1xFile!=null && file.compareTo(filename1x)==0) {
                    assertTrue(FileUtils.contentEquals(custom1xFile, file));
                    custom1xFile = null; // mark that we found it.
                } else if (custom2xFile!=null && file.compareTo(filename2x)==0) {
                    assertTrue(FileUtils.contentEquals(custom2xFile, file));
                    custom2xFile = null; // mark that we found it.
                } else {
                    fail("Found customized image file in theme directory that should not be there: '"+file.getPath()+"'");
                }
            }
        }
        // Check that we didn't fail to find and check any files we should have.
        assertNull("Failed to find expected custom 1x image file", custom1xFile);
        assertNull("Failed to find expected custom 2x image file", custom2xFile);
    }

    /* *****************************
     *   transitions from default
     * *****************************/

    // default: add 1x (odd-sized) -> success
    @Test public void test_success_fromDefault_add1xOdd() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, false, filePngOdd);
        // check: 1x only, pngOdd
        checkCustomImage(urlPng1x, null, "541", "378", filePngOdd, null, 0);
    }

    // default: add 2x -> success
    @Test public void test_success_fromDefault_add2x() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, true, filePngSmall);
        // check: 2x only, pngSmall
        checkCustomImage(urlPng2x, urlPng2x, "75", "42", null, filePngSmall, 2);
    }

    // default: delete1x -> unchanged, delete2x -> unchanged, reset -> unchanged
    @Test public void test_unchanged_fromDefault_remove1xRemove2xReset() throws IOException {
        CustomImage.deleteFile(AppConfig.CIName.TEST_LOGO, false);
        // check: default
        checkCustomImage(urlDefault1x, urlDefault2x, "150", "50", null, null, 1);

        CustomImage.deleteFile(AppConfig.CIName.TEST_LOGO, true);
        // check: default
        checkCustomImage(urlDefault1x, urlDefault2x, "150", "50", null, null, 1);

        CustomImage.reset(AppConfig.CIName.TEST_LOGO);
        // check: default
        checkCustomImage(urlDefault1x, urlDefault2x, "150", "50", null, null, 1);
    }

    /* *************************
     *   transitions from 1x
     * *************************/

    // 1x: replace 1x -> success
    @Test public void test_success_from1x_add1x() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, false, filePngSmall);
        
        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, false, filePngLarge);
        // check: 1x only, pngLarge
        checkCustomImage(urlPng1x, null, "300", "168", filePngLarge, null, 0);
    }

    // 1x: add 2x (match OK) -> success
    @Test public void test_success_from1x_add2xMatch() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, false, filePngSmall);
        
        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, true, filePngLarge);
        // check: 1x+2x, pngSmall+pngLarge
        checkCustomImage(urlPng1x, urlPng2x, "150", "84", filePngSmall, filePngLarge, 1);
    }

    // 1x: add 2x (mismatch) -> error
    @Test public void test_error_from1x_add2xMismatch() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, false, filePngSmall);

        try {
            CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, true, filePngSmall);
            fail("Should not accept 2x image with mismatched size");
        } catch (IllegalArgumentException e) {
            assertEquals(Messages.get("CI_ERROR_MISMATCHED_DIMENSIONS"), e.getMessage());
        }
        // check: 1x only, pngSmall
        checkCustomImage(urlPng1x, null, "150", "84", filePngSmall, null, 0);

        try {
            CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, true, fileJpgLarge);
            fail("Should not accept 2x image with mismatched format");
        } catch (IllegalArgumentException e) {
            assertEquals(Messages.get("CI_ERROR_MISMATCHED_FORMAT").replace("EXT", "png"), e.getMessage());
        }
        // check: 1x only, pngSmall
        checkCustomImage(urlPng1x, null, "150", "84", filePngSmall, null, 0);
    }

    // 1x: remove1x -> success
    @Test public void test_success_from1x_remove1x() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, false, filePngSmall);

        CustomImage.deleteFile(AppConfig.CIName.TEST_LOGO, false);
        // check: default
        checkCustomImage(urlDefault1x, urlDefault2x, "150", "50", null, null, 1);
    }

    // 1x: remove2x -> unchanged
    @Test public void test_unchanged_from1x_remove2x() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, false, filePngSmall);

        CustomImage.deleteFile(AppConfig.CIName.TEST_LOGO, true);
        // check: 1x only, pngSmall
        checkCustomImage(urlPng1x, null, "150", "84", filePngSmall, null, 0);
    }

    // 1x: reset -> success
    @Test public void test_success_from1x_reset() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, false, filePngSmall);

        CustomImage.reset(AppConfig.CIName.TEST_LOGO);
        // check: default
        checkCustomImage(urlDefault1x, urlDefault2x, "150", "50", null, null, 1);
    }

    /* *************************
     *   transitions from 2x
     * *************************/

    // 2x: replace 2x -> success
    @Test public void test_success_from2x_add2x() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, true, filePngSmall);

        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, true, filePngLarge);
        // check: 2x only, pngLarge
        checkCustomImage(urlPng2x, urlPng2x, "150", "84", null, filePngLarge, 2);
    }

    // 2x: add 1x (match OK) -> success
    @Test public void test_success_from2x_add1xMatch() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, true, filePngLarge);

        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, false, filePngSmall);
        // check: 1x+2x, pngSmall+pngLarge
        checkCustomImage(urlPng1x, urlPng2x, "150", "84", filePngSmall, filePngLarge, 1);
    }

    // 2x: add 1x (mismatch) -> error
    @Test public void test_error_from2x_add1xMismatch() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, true, filePngLarge);

        try {
            CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, false, filePngLarge);
            fail("Should not accept 1x image with mismatched size");
        } catch (IllegalArgumentException e) {
            assertEquals(Messages.get("CI_ERROR_MISMATCHED_DIMENSIONS"), e.getMessage());
        }
        // check: 2x only, pngLarge
        checkCustomImage(urlPng2x, urlPng2x, "150", "84", null, filePngLarge, 2);
    }

    // 2x: remove2x -> success
    @Test public void test_success_from2x_remove2x() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, true, filePngSmall);

        CustomImage.deleteFile(AppConfig.CIName.TEST_LOGO, true);
        // check: default
        checkCustomImage(urlDefault1x, urlDefault2x, "150", "50", null, null, 1);
    }

    // 2x: remove1x -> unchanged
    @Test public void test_unchanged_from2x_remove1x() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, true, filePngSmall);

        CustomImage.deleteFile(AppConfig.CIName.TEST_LOGO, false);
        // check: 2x only, pngSmall
        checkCustomImage(urlPng2x, urlPng2x, "75", "42", null, filePngSmall, 2);
    }

    // 2x: reset -> success
    @Test public void test_success_from2x_reset() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, true, filePngSmall);

        CustomImage.reset(AppConfig.CIName.TEST_LOGO);
        // check: default
        checkCustomImage(urlDefault1x, urlDefault2x, "150", "50", null, null, 1);
    }

    /* ***************************
     *   transitions from 1x+2x
     * ***************************/

    // 1x+2x: remove1x -> success
    @Test public void test_success_from1x2x_remove1x() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, false, filePngSmall);
        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, true, filePngLarge);

        CustomImage.deleteFile(AppConfig.CIName.TEST_LOGO, false);
        // check: 2x only, pngLarge
        checkCustomImage(urlPng2x, urlPng2x, "150", "84", null, filePngLarge, 2);
    }

    // 1x+2x: remove2x -> success
    @Test public void test_success_from1x2x_remove2x() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, false, filePngSmall);
        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, true, filePngLarge);

        CustomImage.deleteFile(AppConfig.CIName.TEST_LOGO, true);
        // check: 1x only, pngSmall
        checkCustomImage(urlPng1x, null, "150", "84", filePngSmall, null, 0);
    }

/* I know this works--this class is over-tested already--and I don't want to have to include 2 more resource image files just for this.
    // 1x+2x: replace 1x (matching) -> success, replace 2x (matching) -> success
    @Test public void test_success_from1x2x_add1xMatch() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, false, filePngSmall);
        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, true, filePngLarge);

        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, false, filePngSmall2);
        // check: 1x+2x, pngSmall2+pngLarge
        checkCustomImage(urlPng1x, urlPng2x, "150", "84", filePngSmall2, filePngLarge, 1);

        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, true, filePngLarge2);
        // check: 1x+2x, pngSmall2+pngLarge2
        checkCustomImage(urlPng1x, urlPng2x, "150", "84", filePngSmall2, filePngLarge2, 1);
    }
*/

    // 1x+2x: replace 1x (mismatch) -> error, replace 2x (mismatch) -> error
    @Test public void test_error_from1x_add1xMismatchAdd2xMismatch() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, false, filePngSmall);
        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, true, filePngLarge);

        try {
            CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, false, filePngLarge);
            fail("Should not accept 1x image with mismatched size");
        } catch (IllegalArgumentException e) {
            assertEquals(Messages.get("CI_ERROR_MISMATCHED_DIMENSIONS"), e.getMessage());
        }
        // check: 1x+2x, pngSmall+pngLarge
        checkCustomImage(urlPng1x, urlPng2x, "150", "84", filePngSmall, filePngLarge, 1);

        try {
            CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, true, filePngSmall);
            fail("Should not accept 2x image with mismatched size");
        } catch (IllegalArgumentException e) {
            assertEquals(Messages.get("CI_ERROR_MISMATCHED_DIMENSIONS"), e.getMessage());
        }
        // check: 1x+2x, pngSmall+pngLarge
        checkCustomImage(urlPng1x, urlPng2x, "150", "84", filePngSmall, filePngLarge, 1);
    }

    // 1x+2x: reset -> success
    @Test public void test_success_from1x2x_reset() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, false, filePngSmall);
        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, true, filePngLarge);

        CustomImage.reset(AppConfig.CIName.TEST_LOGO);
        // check: default
        checkCustomImage(urlDefault1x, urlDefault2x, "150", "50", null, null, 1);
    }

    /* ******************************
     *   Accept/reject image files
     *     regardless of state
     * ******************************/

    // add 2x (odd-sized) -> error
    @Test public void test_error_add2xOdd() throws IOException {
        try {
            CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, true, filePngOdd);
            fail("Should not accept odd-sized 2x image");
        } catch (IllegalArgumentException e) {
            assertEquals(Messages.get("CI_ERROR_DIMENSIONS_2X_ODD"), e.getMessage());
        }
        // check: default
        checkCustomImage(urlDefault1x, urlDefault2x, "150", "50", null, null, 1);
    }

    // add image (file extension doesn't match true format) -> error
    @Test public void test_error_addIncorrectExtension() throws IOException {
        try {
            CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, false, Utilities.getResourceFileWithExtension("SampleLogo-single.png", "gif"));
            fail("Should not accept image file with incorrect file extension");
        } catch (IllegalArgumentException e) {
            assertEquals(Messages.get("CI_ERROR_FORMAT_UNKNOWN"), e.getMessage());
        }
        // check: default
        checkCustomImage(urlDefault1x, urlDefault2x, "150", "50", null, null, 1);
    }

    // add PDF file -> error
    @Test public void test_error_addInvalidImage() throws IOException {
        try {
            CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, false, Utilities.getResourceFile("SamplePrimaryDocument.pdf"));
            fail("Should not accept file that isn't a common webserver image format");
        } catch (IllegalArgumentException e) {
            assertEquals(Messages.get("CI_ERROR_FORMAT_UNKNOWN"), e.getMessage());
        }
        // check: default
        checkCustomImage(urlDefault1x, urlDefault2x, "150", "50", null, null, 1);
    }

    // add GIF image -> success
    @Test public void test_success_addGif() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, false, fileGifLarge);

        // check: 1x only, gifLarge
        checkCustomImage(urlGif1x, null, "300", "168", fileGifLarge, null, 0);
    }

    // add JPG image -> success
    @Test public void test_success_addJpg() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, false, fileJpgLarge);

        // check: 1x only, gifLarge
        checkCustomImage(urlJpg1x, null, "300", "168", fileJpgLarge, null, 0);
    }

    // not tested: CustomImage.tallerHeight(name1, name2).
    // It requires two test images so would be a bit of work, but it's a trivial method.
}
