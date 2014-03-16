package org.tdl.vireo.theme;

import org.apache.commons.io.FilenameUtils;
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
import java.io.IOException;

import static org.junit.Assert.*;
import static org.junit.Assert.assertTrue;

public class CustomImageTest extends UnitTest {

    public static SecurityContext context = Spring.getBeanOfType(SecurityContext.class);
    public static PersonRepository personRepo = Spring.getBeanOfType(PersonRepository.class);
    public static SettingsRepository settingRepo = Spring.getBeanOfType(SettingsRepository.class);

    /** names for image+resolution, once installed */
    private final String nameDefault1x="public"+File.separatorChar+"images"+File.separatorChar+"vireo-logo-sm.png";
    private final String nameDefault2x="public"+File.separatorChar+"images"+File.separatorChar+"vireo-logo-sm@2x.png";
    private final String namePng1x="test-logo.png";
    private final String namePng2x="test-logo@2x.png";
    private final String nameJpg1x="test-logo.jpg";
    private final String nameGif1x="test-logo.gif";

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
    public static void setupBeforeClass() {
        context.turnOffAuthorization();
        CustomImage.reset(AppConfig.CIName.TEST_LOGO); // if it wasn't already somehow, make it default.

        JPA.em().getTransaction().commit();
        JPA.em().clear();
        JPA.em().getTransaction().begin();
    }

    @After
    public void cleanup() {
        JPA.em().getTransaction().rollback();
        JPA.em().clear();
        JPA.em().getTransaction().begin();

        CustomImage.reset(AppConfig.CIName.TEST_LOGO); // make sure to leave no files from failed tests
    }

    @AfterClass
    public static void cleanupAfterClass() {
        context.restoreAuthorization();
    }

    // tallerHeight(name1, name2)
    // fileDescription(name, is2x)

    /**
     * Helper to assert that pretty much everything about the saved state of a custom image is as expected.
     * 
     * Methods whose return value is tested here:
     * CustomImage.url(name, false)
     * CustomImage.url(name, true)
     * CustomImage.displayWidth(name)
     * CustomImage.displayHeight(name)
     * CustomImage.isDefault(name)
     * CustomImage.hasCustomFile(name, false)
     * CustomImage.hasCustomFile(name, true)
     * CustomImage.is2xNone(name)
     * CustomImage.is2xSeparate(name)
     * CustomImage.is1xScaled(name)
     *
     * Non-method-return output tested here (exposed directly to user by HTTP, not via any Java method call):
     * presence/absence of custom 1x file (if applicable)
     * contents of custom 1x file (if applicable)
     * presence/absence of custom 2x file (if applicable)
     * contents of custom 2x file (if applicable)
     *
     * Also tested:
     * direct read of CI_URLPATH Configuration value
     *
     * @param nameBase correct filename only (no path) for the image to be used for 1x. Works for both customized and default settings.
     * @param name1x correct filename only (no path) from CustomImage.url(name, false).
     * @param name2x correct filename only (no path) from CustomImage.url(name, true), or null if that method should return null.
     * @param width correct display width
     * @param height correct display height
     * @param hasCustom1x null if correct result of CustomImage.hasCustomFile(name, false) is null,
     *          else a File with the correct contents of the stored 1x custom image.
     * @param hasCustom2x null if correct result of CustomImage.hasCustomFile(name, true) is null,
     *          else a File with the correct contents of the stored 2x custom image.
     * @param code2x indicates which one (and only one) these should be true:
     *              0: is2xNone==true
     *              1: is2xSeparate==true
     *              2: is1xScaled==true (a.k.a is2xSame==true)
     * @throws IOException if error while checking custom file existence or content
     */
    public static void checkCustomImage(String nameBase, String name1x, String name2x,
                                         String width, String height,
                                         File hasCustom1x, File hasCustom2x, int code2x) throws IOException {

        final boolean shouldBeDefault = hasCustom1x==null && hasCustom2x==null;

        // check image metadata
        assertEquals((shouldBeDefault?"":ThemeDirectory.URL_PREFIX)+nameBase, settingRepo.getConfigValue(AppConfig.CIName.TEST_LOGO+AppConfig.CI_URLPATH));
        assertEquals((shouldBeDefault?"":ThemeDirectory.URL_PREFIX) + name1x, CustomImage.url(AppConfig.CIName.TEST_LOGO, false));
        assertEquals(name2x==null?null:(shouldBeDefault?"":ThemeDirectory.URL_PREFIX) + name2x, CustomImage.url(AppConfig.CIName.TEST_LOGO, true));
        assertEquals(width, CustomImage.displayWidth(AppConfig.CIName.TEST_LOGO));
        assertEquals(height, CustomImage.displayHeight(AppConfig.CIName.TEST_LOGO));
        assertEquals(!(hasCustom1x!=null || hasCustom2x!=null), CustomImage.isDefault(AppConfig.CIName.TEST_LOGO));
        assertEquals(hasCustom1x != null, CustomImage.hasCustomFile(AppConfig.CIName.TEST_LOGO, false));
        assertEquals(hasCustom2x != null, CustomImage.hasCustomFile(AppConfig.CIName.TEST_LOGO, true));
        assertEquals(code2x == 0, CustomImage.is2xNone(AppConfig.CIName.TEST_LOGO));
        assertEquals(code2x == 1, CustomImage.is2xSeparate(AppConfig.CIName.TEST_LOGO));
        assertEquals(code2x == 2, CustomImage.is1xScaled(AppConfig.CIName.TEST_LOGO));

        // check custom file existence and contents
        if (!shouldBeDefault) {
            final String ext = FilenameUtils.getExtension(nameBase);
            File file = new File(ThemeDirectory.PATH +CustomImage.standardFilename(AppConfig.CIName.TEST_LOGO, false, ext));
            assertEquals(hasCustom1x!=null, file.exists());
            if (hasCustom1x!=null) {
                assertTrue(file.getPath(), file.exists());
                assertTrue(org.apache.commons.io.FileUtils.contentEquals(hasCustom1x, file));
            }
            file = new File(ThemeDirectory.PATH +CustomImage.standardFilename(AppConfig.CIName.TEST_LOGO, true, ext));
            assertEquals(hasCustom2x!=null, file.exists());
            if (hasCustom2x!=null) {
                assertTrue(file.getPath(), file.exists());
                assertTrue(org.apache.commons.io.FileUtils.contentEquals(hasCustom2x, file));
            }
        }
    }

    /* *****************************
     *   transitions from default
     * *****************************/

    // default: add 1x (odd-sized) -> success
    @Test public void test_success_fromDefault_add1xOdd() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, false, filePngOdd);
        // check: 1x only, pngOdd
        checkCustomImage(namePng1x, namePng1x, null, "541", "378", filePngOdd, null, 0);
    }

    // default: add 2x -> success
    @Test public void test_success_fromDefault_add2x() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, true, filePngSmall);
        // check: 2x only, pngSmall
        checkCustomImage(namePng2x, namePng2x, namePng2x, "75", "42", null, filePngSmall, 2);
    }

    // default: delete1x -> unchanged, delete2x -> unchanged, reset -> unchanged
    @Test public void test_unchanged_fromDefault_remove1xRemove2xReset() throws IOException {
        CustomImage.deleteFile(AppConfig.CIName.TEST_LOGO, false);
        // check: default
        checkCustomImage(nameDefault1x, nameDefault1x, nameDefault2x, "150", "50", null, null, 1);

        CustomImage.deleteFile(AppConfig.CIName.TEST_LOGO, true);
        // check: default
        checkCustomImage(nameDefault1x, nameDefault1x, nameDefault2x, "150", "50", null, null, 1);

        CustomImage.reset(AppConfig.CIName.TEST_LOGO);
        // check: default
        checkCustomImage(nameDefault1x, nameDefault1x, nameDefault2x, "150", "50", null, null, 1);
    }

    /* *************************
     *   transitions from 1x
     * *************************/

    // 1x: replace 1x -> success
    @Test public void test_success_from1x_add1x() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, false, filePngSmall);
        
        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, false, filePngLarge);
        // check: 1x only, pngLarge
        checkCustomImage(namePng1x, namePng1x, null, "300", "168", filePngLarge, null, 0);
    }

    // 1x: add 2x (match OK) -> success
    @Test public void test_success_from1x_add2xMatch() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, false, filePngSmall);
        
        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, true, filePngLarge);
        // check: 1x+2x, pngSmall+pngLarge
        checkCustomImage(namePng1x, namePng1x, namePng2x, "150", "84", filePngSmall, filePngLarge, 1);
    }

    // 1x: add 2x (mismatch) -> error
    @Test public void test_error_from1x_add2xMismatch() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, false, filePngSmall);

        try {
            CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, true, filePngSmall);
            fail("Should not accept 2x image with mismatched size");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().equals(Messages.get("CI_ERROR_MISMATCHED_DIMENSIONS")));
        }
        // check: 1x only, pngSmall
        checkCustomImage(namePng1x, namePng1x, null, "150", "84", filePngSmall, null, 0);

        try {
            CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, true, fileJpgLarge);
            fail("Should not accept 2x image with mismatched format");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().equals(Messages.get("CI_ERROR_MISMATCHED_FORMAT")));
        }
        // check: 1x only, pngSmall
        checkCustomImage(namePng1x, namePng1x, null, "150", "84", filePngSmall, null, 0);
    }

    // 1x: remove1x -> success
    @Test public void test_success_from1x_remove1x() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, false, filePngSmall);

        CustomImage.deleteFile(AppConfig.CIName.TEST_LOGO, false);
        // check: default
        checkCustomImage(nameDefault1x, nameDefault1x, nameDefault2x, "150", "50", null, null, 1);
    }

    // 1x: remove2x -> unchanged
    @Test public void test_unchanged_from1x_remove2x() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, false, filePngSmall);

        CustomImage.deleteFile(AppConfig.CIName.TEST_LOGO, true);
        // check: 1x only, pngSmall
        checkCustomImage(namePng1x, namePng1x, null, "150", "84", filePngSmall, null, 0);
    }

    // 1x: reset -> success
    @Test public void test_success_from1x_reset() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, false, filePngSmall);

        CustomImage.reset(AppConfig.CIName.TEST_LOGO);
        // check: default
        checkCustomImage(nameDefault1x, nameDefault1x, nameDefault2x, "150", "50", null, null, 1);
    }

    /* *************************
     *   transitions from 2x
     * *************************/

    // 2x: replace 2x -> success
    @Test public void test_success_from2x_add2x() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, true, filePngSmall);

        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, true, filePngLarge);
        // check: 2x only, pngLarge
        checkCustomImage(namePng2x, namePng2x, namePng2x, "150", "84", null, filePngLarge, 2);
    }

    // 2x: add 1x (match OK) -> success
    @Test public void test_success_from2x_add1xMatch() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, true, filePngLarge);

        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, false, filePngSmall);
        // check: 1x+2x, pngSmall+pngLarge
        checkCustomImage(namePng1x, namePng1x, namePng2x, "150", "84", filePngSmall, filePngLarge, 1);
    }

    // 2x: add 1x (mismatch) -> error
    @Test public void test_error_from2x_add1xMismatch() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, true, filePngLarge);

        try {
            CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, false, filePngLarge);
            fail("Should not accept 1x image with mismatched size");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().equals(Messages.get("CI_ERROR_MISMATCHED_DIMENSIONS")));
        }
        // check: 2x only, pngLarge
        checkCustomImage(namePng2x, namePng2x, namePng2x, "150", "84", null, filePngLarge, 2);
    }

    // 2x: remove2x -> success
    @Test public void test_success_from2x_remove2x() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, true, filePngSmall);

        CustomImage.deleteFile(AppConfig.CIName.TEST_LOGO, true);
        // check: default
        checkCustomImage(nameDefault1x, nameDefault1x, nameDefault2x, "150", "50", null, null, 1);
    }

    // 2x: remove1x -> unchanged
    @Test public void test_unchanged_from2x_remove1x() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, true, filePngSmall);

        CustomImage.deleteFile(AppConfig.CIName.TEST_LOGO, false);
        // check: 2x only, pngSmall
        checkCustomImage(namePng2x, namePng2x, namePng2x, "75", "42", null, filePngSmall, 2);
    }

    // 2x: reset -> success
    @Test public void test_success_from2x_reset() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, true, filePngSmall);

        CustomImage.reset(AppConfig.CIName.TEST_LOGO);
        // check: default
        checkCustomImage(nameDefault1x, nameDefault1x, nameDefault2x, "150", "50", null, null, 1);
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
        checkCustomImage(namePng2x, namePng2x, namePng2x, "150", "84", null, filePngLarge, 2);
    }

    // 1x+2x: remove2x -> success
    @Test public void test_success_from1x2x_remove2x() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, false, filePngSmall);
        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, true, filePngLarge);

        CustomImage.deleteFile(AppConfig.CIName.TEST_LOGO, true);
        // check: 1x only, pngSmall
        checkCustomImage(namePng1x, namePng1x, null, "150", "84", filePngSmall, null, 0);
    }

/* I know this works--this class is over-tested already--and I don't want to have to include 2 more resource image files just for this.
    // 1x+2x: replace 1x (matching) -> success, replace 2x (matching) -> success
    @Test public void test_success_from1x2x_add1xMatch() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, false, filePngSmall);
        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, true, filePngLarge);

        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, false, filePngSmall2);
        // check: 1x+2x, pngSmall2+pngLarge
        checkCustomImage(namePng1x, namePng1x, namePng2x, "150", "84", filePngSmall2, filePngLarge, 1);

        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, true, filePngLarge2);
        // check: 1x+2x, pngSmall2+pngLarge2
        checkCustomImage(namePng1x, namePng1x, namePng2x, "150", "84", filePngSmall2, filePngLarge2, 1);
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
            assertTrue(e.getMessage().equals(Messages.get("CI_ERROR_MISMATCHED_DIMENSIONS")));
        }
        // check: 1x+2x, pngSmall+pngLarge
        checkCustomImage(namePng1x, namePng1x, namePng2x, "150", "84", filePngSmall, filePngLarge, 1);

        try {
            CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, true, filePngSmall);
            fail("Should not accept 2x image with mismatched size");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().equals(Messages.get("CI_ERROR_MISMATCHED_DIMENSIONS")));
        }
        // check: 1x+2x, pngSmall+pngLarge
        checkCustomImage(namePng1x, namePng1x, namePng2x, "150", "84", filePngSmall, filePngLarge, 1);
    }

    // 1x+2x: reset -> success
    @Test public void test_success_from1x2x_reset() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, false, filePngSmall);
        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, true, filePngLarge);

        CustomImage.reset(AppConfig.CIName.TEST_LOGO);
        // check: default
        checkCustomImage(nameDefault1x, nameDefault1x, nameDefault2x, "150", "50", null, null, 1);
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
            assertTrue(e.getMessage().equals(Messages.get("CI_ERROR_DIMENSIONS_2X_ODD")));
        }
        // check: default
        checkCustomImage(nameDefault1x, nameDefault1x, nameDefault2x, "150", "50", null, null, 1);
    }

    // add image (file extension doesn't match true format) -> error
    @Test public void test_error_addIncorrectExtension() throws IOException {
        try {
            CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, false, Utilities.getResourceFileWithExtension("SampleLogo-single.png", "gif"));
            fail("Should not accept image file with incorrect file extension");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().equals(Messages.get("CI_ERROR_FORMAT_UNKNOWN")));
        }
        // check: default
        checkCustomImage(nameDefault1x, nameDefault1x, nameDefault2x, "150", "50", null, null, 1);
    }

    // add PDF file -> error
    @Test public void test_error_addInvalidImage() throws IOException {
        try {
            CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, false, Utilities.getResourceFile("SampleFeedbackDocument.pdf"));
            fail("Should not accept file that isn't a common webserver image format");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().equals(Messages.get("CI_ERROR_FORMAT_UNKNOWN")));
        }
        // check: default
        checkCustomImage(nameDefault1x, nameDefault1x, nameDefault2x, "150", "50", null, null, 1);
    }

    // add GIF image -> success
    @Test public void test_success_addGif() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, false, fileGifLarge);

        // check: 1x only, gifLarge
        checkCustomImage(nameGif1x, nameGif1x, null, "300", "168", fileGifLarge, null, 0);
    }

    // add JPG image -> success
    @Test public void test_success_addJpg() throws IOException {
        CustomImage.replaceFile(AppConfig.CIName.TEST_LOGO, false, fileJpgLarge);

        // check: 1x only, gifLarge
        checkCustomImage(nameJpg1x, nameJpg1x, null, "300", "168", fileJpgLarge, null, 0);
    }
}
