package controllers.settings;

import controllers.Authentication;
import controllers.Security;
import controllers.SettingsTab;
import org.tdl.vireo.model.Configuration;
import org.tdl.vireo.model.RoleType;
import org.tdl.vireo.theme.CustomImage;
import play.Logger;
import play.mvc.With;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.tdl.vireo.constant.AppConfig.*;

/**
 * Theme settings
 * 
 * @author <a href="http://www.scottphillips.com">Scott Phillips</a>
 */
@With(Authentication.class)
public class ThemeSettingsTab extends SettingsTab {

    /** max size in bytes of uploaded custom images */
    public final static int MAX_UPLOAD_SIZE = 1024*1024; // if you change this, change the error message in verifySize()

	@Security(RoleType.MANAGER)
	public static void themeSettings() {
		
		renderArgs.put("FRONT_PAGE_INSTRUCTIONS", settingRepo.getConfigValue(FRONT_PAGE_INSTRUCTIONS));
		renderArgs.put("SUBMIT_INSTRUCTIONS", settingRepo.getConfigValue(SUBMIT_INSTRUCTIONS));
		renderArgs.put("CORRECTION_INSTRUCTIONS", settingRepo.getConfigValue(CORRECTION_INSTRUCTIONS));
		
		// Colors and CSS
		renderArgs.put("BACKGROUND_MAIN_COLOR", settingRepo.getConfigValue(BACKGROUND_MAIN_COLOR));
		renderArgs.put("BACKGROUND_HIGHLIGHT_COLOR", settingRepo.getConfigValue(BACKGROUND_HIGHLIGHT_COLOR));
		renderArgs.put("BUTTON_MAIN_COLOR_ON", settingRepo.getConfigValue(BUTTON_MAIN_COLOR_ON));
		renderArgs.put("BUTTON_HIGHLIGHT_COLOR_ON", settingRepo.getConfigValue(BUTTON_HIGHLIGHT_COLOR_ON));
		renderArgs.put("BUTTON_MAIN_COLOR_OFF", settingRepo.getConfigValue(BUTTON_MAIN_COLOR_OFF));
		renderArgs.put("BUTTON_HIGHLIGHT_COLOR_OFF", settingRepo.getConfigValue(BUTTON_HIGHLIGHT_COLOR_OFF));
		renderArgs.put("CUSTOM_CSS", settingRepo.getConfigValue(CUSTOM_CSS));
				
		// Logos
        renderArgs.put("LEFT_LOGO_1X", CustomImage.url(CIName.LEFT_LOGO, false));
        renderArgs.put("LEFT_LOGO_2X", CustomImage.url(CIName.LEFT_LOGO, true));
        renderArgs.put("LEFT_LOGO_HEIGHT", CustomImage.displayHeight(CIName.LEFT_LOGO));
        renderArgs.put("LEFT_LOGO_WIDTH", CustomImage.displayWidth(CIName.LEFT_LOGO));
        final boolean leftHasCustom1x = CustomImage.hasCustomFile(CIName.LEFT_LOGO, false);
        final boolean leftHasCustom2x = CustomImage.hasCustomFile(CIName.LEFT_LOGO, true);
        renderArgs.put("LEFT_LOGO_FILE_DESC_1X", CustomImage.fileDescription(CIName.LEFT_LOGO, false));
        renderArgs.put("LEFT_LOGO_FILE_DESC_2X", CustomImage.fileDescription(CIName.LEFT_LOGO, true));
        renderArgs.put("RIGHT_LOGO_1X", CustomImage.url(CIName.RIGHT_LOGO, false));
        renderArgs.put("RIGHT_LOGO_2X", CustomImage.url(CIName.RIGHT_LOGO, true));
        renderArgs.put("RIGHT_LOGO_HEIGHT", CustomImage.displayHeight(CIName.RIGHT_LOGO));
        renderArgs.put("RIGHT_LOGO_WIDTH", CustomImage.displayWidth(CIName.RIGHT_LOGO));
        final boolean rightHasCustom1x = CustomImage.hasCustomFile(CIName.RIGHT_LOGO, false);
        final boolean rightHasCustom2x = CustomImage.hasCustomFile(CIName.RIGHT_LOGO, true);
        renderArgs.put("RIGHT_LOGO_FILE_DESC_1X", CustomImage.fileDescription(CIName.RIGHT_LOGO, false));
        renderArgs.put("RIGHT_LOGO_FILE_DESC_2X", CustomImage.fileDescription(CIName.RIGHT_LOGO, true));

		final String nav = "settings";
		final String subNav = "theme";
		renderTemplate("SettingTabs/themeSettings.html", nav, subNav, leftHasCustom1x, leftHasCustom2x, rightHasCustom1x, rightHasCustom2x);
	}
	
	@Security(RoleType.MANAGER)
	public static void updateThemeSettingsJSON(String field, String value) {

		try {
            // None at the moment but we expect some in the future.
			@SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
            List<String> booleanFields = new ArrayList<String>();

			List<String> textFields = new ArrayList<String>();
			textFields.add(FRONT_PAGE_INSTRUCTIONS);
			textFields.add(SUBMIT_INSTRUCTIONS);
			textFields.add(CORRECTION_INSTRUCTIONS);
			textFields.add(CUSTOM_CSS);
			
			List<String> inputFields = new ArrayList<String>();
			inputFields.add(BACKGROUND_MAIN_COLOR);
			inputFields.add(BACKGROUND_HIGHLIGHT_COLOR);
			inputFields.add(BUTTON_MAIN_COLOR_ON);
			inputFields.add(BUTTON_HIGHLIGHT_COLOR_ON);
			inputFields.add(BUTTON_MAIN_COLOR_OFF);
			inputFields.add(BUTTON_HIGHLIGHT_COLOR_OFF);

			if (booleanFields.contains(field)) {
				// This is a boolean field
				boolean booleanValue = true;
				if (value == null || value.trim().length() == 0)
					booleanValue = false;
				
				Configuration config = settingRepo.findConfigurationByName(field);
				if (!booleanValue && config != null)
					config.delete();
				else if (booleanValue && config == null)
					settingRepo.createConfiguration(field, "true").save();
				
				
			} else if (textFields.contains(field)) {
				// This is a free-form text field
				settingRepo.saveConfiguration(field, value);
			} else if (inputFields.contains(field)) {
				// This is a input field
				settingRepo.saveConfiguration(field, value);
			} else {
				throw new IllegalArgumentException("Unknown field '"+field+"'");
			}
			
			field = escapeJavaScript(field);
			value = escapeJavaScript(value);
			
			renderJSON("{ \"success\": \"true\", \"field\": \""+field+"\", \"value\": \""+value+"\" }");
		} catch (IllegalArgumentException iae) {
			String message = escapeJavaScript(iae.getMessage());			
			renderJSON("{ \"failure\": \"true\", \"message\": \"" + message + "\" }");
		} catch (RuntimeException re) {
			Logger.error(re,"theme-settings: unable to update settings: "+re.getMessage());
			String message = escapeJavaScript(re.getMessage());			
			renderJSON("{ \"failure\": \"true\", \"message\": \""+message+"\" }");
		}
	}

    /**
     * Upload and delete files for a customized image. Sets all configuration values
     * as needed. Rejects bad inputs and saves a descriptive flash.error.
     * unlisted params:
     * delete1x set to delete the existing low-res version of the image
     * delete2x set to delete the existing high-res version of the image
     * submit_upload, to indicate that uploaded files should be processed
     * @param name The value of a AppConfig.CIName constant identifying which custom image.
     *             If no constant matches, nothing will happen.
     * @param image1x the low-res version of the image, used if submit_upload is present.
     * @param image2x the high-res version of the image, used if submit_upload is present.
     */
	@Security(RoleType.MANAGER)
	public static void uploadImage(String name, File image1x, File image2x) {
		try {
            for (CIName verifiedName : CIName.values()) {
                if (verifiedName.toString().equals(name)) {
                    // Found correct image name.
                    if (params.get("delete1x") != null) {
                        CustomImage.deleteFile(verifiedName, false);
                    }
                    if (params.get("delete2x") != null) {
                        CustomImage.deleteFile(verifiedName, true);
                    }
                    if (params._contains("submit_upload") && image1x != null) {
                        verifySize(image1x);
                        CustomImage.replaceFile(verifiedName, false, image1x);
                    }
                    if (params._contains("submit_upload") && image2x != null) {
                        verifySize(image2x);
                        CustomImage.replaceFile(verifiedName, true, image2x);
                    }
                    break;
                }
            }
        } catch (IOException e) {
            Logger.error("theme-settings: IOException: could not update custom image because "+e.getMessage());
            flash.error("The server failed to update the image.");
        } catch (IllegalArgumentException e) {
            flash.error(e.getMessage());
        }
		themeSettings();
	}

    /**
     * Checks that a CustomImage intended for use as a main logo image is appropriate.
     * @param file the file to check
     * @throws IllegalArgumentException if the file is too big, too small, or not really a file
     */
    private static void verifySize(File file) throws IllegalArgumentException {
        if (file.length() <= 0) {
            // apparently browsers/play will often fail to even deliver a size==0 file, but might as well check.
            throw new IllegalArgumentException("The image was empty"); // more informative than the "invalid format" error that would happen later
        }
        if (file.length() > MAX_UPLOAD_SIZE) {
            throw new IllegalArgumentException("The maximum image size allowed is 1 MB. Website graphics should be small.");
        }
    }
}
