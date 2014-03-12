package controllers.settings;

import controllers.Authentication;
import controllers.Security;
import controllers.SettingsTab;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.tdl.vireo.constant.AppConfig;
import org.tdl.vireo.constant.CustomImage;
import org.tdl.vireo.model.Configuration;
import org.tdl.vireo.model.RoleType;
import play.Logger;
import play.mvc.With;
import sun.awt.image.ImageFormatException;

import java.awt.*;
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
	
	public static final String THEME_PATH = "conf"+File.separator+"theme"+File.separator;
    public static final String THEME_URL_PREFIX = "theme/";

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
		boolean leftLogoIsDefault = CustomImage.isDefault(CIName.LEFT_LOGO);
        renderArgs.put("LEFT_LOGO_1X", CustomImage.url(CIName.LEFT_LOGO, false));
        renderArgs.put("LEFT_LOGO_2X", CustomImage.url(CIName.LEFT_LOGO, true));
        renderArgs.put("LEFT_LOGO_HEIGHT", CustomImage.displayHeight(CIName.LEFT_LOGO));
        renderArgs.put("LEFT_LOGO_WIDTH", CustomImage.displayWidth(CIName.LEFT_LOGO));
        renderArgs.put("LEFT_LOGO_EXT", CustomImage.extension(CIName.LEFT_LOGO));
        boolean rightLogoIsDefault = CustomImage.isDefault(CIName.RIGHT_LOGO);
        renderArgs.put("RIGHT_LOGO_1X", CustomImage.url(CIName.RIGHT_LOGO, false));
        renderArgs.put("RIGHT_LOGO_2X", CustomImage.url(CIName.RIGHT_LOGO, true));
        renderArgs.put("RIGHT_LOGO_HEIGHT", CustomImage.displayHeight(CIName.RIGHT_LOGO));
        renderArgs.put("RIGHT_LOGO_WIDTH", CustomImage.displayWidth(CIName.RIGHT_LOGO));
        renderArgs.put("RIGHT_LOGO_EXT", CustomImage.extension(CIName.RIGHT_LOGO));

		String nav = "settings";
		String subNav = "theme";
		renderTemplate("SettingTabs/themeSettings.html", nav, subNav, leftLogoIsDefault, rightLogoIsDefault);
	}
	
    @SuppressWarnings({"UnusedDeclaration"})
	@Security(RoleType.MANAGER)
	public static void updateThemeSettingsJSON(String field, String value) {

		try {
            // None at the moment but we expect some in the future.
			@SuppressWarnings({"MismatchedQueryAndUpdateOfCollection"})
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
				saveField(field, value);
			} else if (inputFields.contains(field)) {
				// This is a input field
				saveField(field, value);
			} else {
				throw new IllegalArgumentException("Unknown field '"+field+"'");
			}
			
			field = escapeJavaScript(field);
			value = escapeJavaScript(value);
			
			renderJSON("{ \"success\": \"true\", \"field\": \""+field+"\", \"value\": \""+value+"\" }");
		} catch (IllegalArgumentException iae) {
			String message = escapeJavaScript(iae.getMessage());			
			renderJSON("{ \"failure\": \"true\", \"message\": \""+message+"\" }");
		} catch (RuntimeException re) {
			Logger.error(re,"Unable to update application settings");
			String message = escapeJavaScript(re.getMessage());			
			renderJSON("{ \"failure\": \"true\", \"message\": \""+message+"\" }");
		}
	}

    /**
     * Upload and delete files for a customized image. Sets all configuration values
     * as needed. Rejects bad inputs and saves a flash.error.
     * unlisted params:
     * delete1x set to delete the existing low-res version of the image
     * delete2x set to delete the existing high-res version of the image
     * @param name The value of a AppConfig.CIName constant identifying which custom image.
     *             If no constant matches, nothing will happen.
     * @param image1x the low-res version of the image
     * @param image2x the high-res version of the image
     */
    @SuppressWarnings({"UnusedDeclaration"})
	@Security(RoleType.MANAGER)
	public static void uploadImage(String name, File image1x, File image2x) {
		try {
            checkThemeDir();

            for (CIName cn : CIName.values()) {
                if (cn.toString().equals(name)) {

                    // Found correct image name.
                    if (params.get("delete1x") != null) {
                        deleteImage(cn, false);
                    }
                    if (params.get("delete2x") != null) {
                        deleteImage(cn, true);
                    }
                    if (image1x != null) {
                        replaceImage(cn, false, image1x);
                    }
                    if (image2x != null) {
                        replaceImage(cn, true, image2x);
                    }
                    break;
                }
            }
        } catch (IOException e) {
            Logger.error("tab-settings: could not update custom image because "+e.getMessage());
            flash.error("The server failed to update the image.");
        } catch (ImageFormatException e) {
            flash.error(e.getMessage());
        } catch (IllegalArgumentException e) {
            flash.error(e.getMessage());
        }
		themeSettings();
	}

    //TODO
    // make reset button replace delete button on file choice, or make input autosubmit on choice
    // make table same size whether or not there's a delete button
    // add retina image for stickies
    // test for new ThemeSettingsTab behavior
    // make file input selection display dark text—don't use an input?

    /**
     * Make sure the theme directory exists.
     * @throws IOException if theme dir doesn't exist and couldn't be created
     */
    private static void checkThemeDir() throws IOException {
        File themeDir = new File(THEME_PATH);
        if(!themeDir.exists()){
            if (!themeDir.mkdir()) {
                throw new IOException("Could not create theme directory "+themeDir.getPath());
            }
        }
    }

    /**
     * Given a URL for an resource being served from the theme directory, return the corresponding File
     * (without checking whether it actually exists).
     * Simpler than using play.mvc.Router if the relevant constants are correct.
     * @param url url for the resource
     * @return File for the resource
     */
    private static File fileForThemeUrl(String url) {
        return new File(THEME_PATH+StringUtils.substringAfter(url, THEME_URL_PREFIX));
    }

    /**
     * Given a File representing a file in the theme directory, return the URL where that file would
     * be served from (without checking whether it actually exists).
     * Simpler than using play.mvc.Router if the relevant constants are correct.
     * @param file a file in the theme directory
     * @return the URL for the file
     */
    private static String urlForThemeFile(File file) {
        return THEME_URL_PREFIX +StringUtils.substringAfter(file.getPath(), THEME_PATH);
    }

    /**
     * Deletes a file from the theme directory, if it exists.
     * @param filename the name of the file, with no path component
     */
    private static void deleteThemeFile(String filename) {
        File file = new File(THEME_PATH+filename);
        if(file.exists()){
            if (!file.delete()) {
                // Can't delete. Not a real problem except for some wasted disk space--at least not yet--but do log it.
                Logger.error("tab-settings: could not delete existing file "+file.getAbsolutePath());
            }
        }
    }

    /**
     * Saves a Configuration value, whether it is new or an overwrite.
     * @param field field to save to
     * @param value value to save
     */
    private static void saveField(String field, String value) {
        Configuration config = settingRepo.findConfigurationByName(field);

        if (config == null)
            config = settingRepo.createConfiguration(field, value);
        else {
            config.setValue(value);
        }
        config.save();
    }

    /**
     * Convenience method to interpret a stored Configuration value as an int, not a String.
     * @param field the Configuration field to look up
     * @return the value of the field as an integer, or 0 if not an int.
     */
    private static int getIntConfig(String field) {
        try {
            return Integer.parseInt(settingRepo.getConfigValue(field));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Saves the file for one resolution of a custom image (either new or replacing a
     * previous customization), and sets all relevant config settings appropriately.
     * @param name constant identifying which custom image
     * @param is2x whether to save the file as the high-resolution version
     * @param file the new customized file to save
     * @throws IOException thrown on error storing or deleting image files
     * @throws ImageFormatException if image format could not be understood
     * @throws IllegalArgumentException if 2x uploaded but format extension doesn't match corresponding 1x
     */
    private static void replaceImage (CIName name, boolean is2x, File file) throws IOException, ImageFormatException, IllegalArgumentException {
        if (file != null) {
            // Uploading a new file.
            // Check that it's a valid image with known dimensions.
            String extension = FilenameUtils.getExtension(file.getName());
            Dimension dim = CustomImage.verifyAndGetDimensions(file, extension);
            if (dim == null) {
                throw new ImageFormatException("Image format not recognized");
            }

            // If there are existing customization files, deal with it.
            if (!CustomImage.isDefault(name)) {
                String extensionOld = CustomImage.extension(name);

                // If we need to reject a file that mismatches its customized counterpart, do it before copying the file.
                if (CustomImage.hasCustomFile(name, !is2x)) {
                    if (!extension.equals(extensionOld)) {
                        throw new IllegalArgumentException("The new file extension must match the existing file extension \"."+extensionOld+"\". Try deleting the other file first.");
                    }
                    int factorOfDisplayDims = (is2x)? 2 : 1;
                    if (dim.getHeight() != factorOfDisplayDims* getIntConfig(name+AppConfig.CI_HEIGHT) || dim.getWidth() != factorOfDisplayDims* getIntConfig(name+AppConfig.CI_WIDTH)) {
                        throw new IllegalArgumentException("The 2x file dimensions must be exactly double the 1x file dimensions. Try deleting the other file first.");
                    }
                }

                // Explicitly delete previous if present--the new standardized filename may differ due to extension.
                deleteThemeFile(CustomImage.standardFilename(name, is2x, extensionOld));
            }

            // Save file.
            File newFile = new File(THEME_PATH+CustomImage.standardFilename(name, is2x, extension));
            FileUtils.copyFile(file, newFile);

            // Set 2x metadata
            if (CustomImage.hasCustomFile(name, !is2x)) {
                // added file to existing counterpart customization
                saveField(name+AppConfig.CI_2X, AppConfig.CI_2XVAL_SEPARATE);
                if (is2x) {
                    // keep existing name and size of 1x
                    return;
                }
            } else if (!is2x) {
                // 1x added as standalone
                saveField(name+AppConfig.CI_2X, AppConfig.CI_2XVAL_NONE);
            } else {
                // 2x added as standalone
                saveField(name+AppConfig.CI_2X, AppConfig.CI_2XVAL_SAME);
                // use 2x name and display at half size
                dim.setSize(0.5*dim.getWidth(), 0.5*dim.getHeight());
            }

            // Set basic image metadata.
            saveField(name+AppConfig.CI_URLPATH, urlForThemeFile(newFile));
            saveField(name+AppConfig.CI_HEIGHT, String.valueOf((int)dim.getHeight()));
            saveField(name+AppConfig.CI_WIDTH, String.valueOf((int)dim.getWidth()));
        }
    }

    /**
     * Deletes the customized file for one resolution of a custom image, if present, and sets all
     * relevant config settings to values appropriate for the customization being gone, which will
     * be default values if both resolutions are now gone.
     * @param name constant identifying which custom image
     * @param is2x whether to delete the file for the high-resolution version
    */
    private static void deleteImage (CIName name, boolean is2x) {
        if (!CustomImage.isDefault(name)) {
            deleteThemeFile(CustomImage.standardFilename(name, is2x, CustomImage.extension(name)));
            if (!CustomImage.hasFile(name, !is2x)) {
                // No counterpart exists - switch to defaults
                resetImageMetadata(name);
            } else {
                // Counterpart exists - switch to it (if it isn't already)
                saveField(name + AppConfig.CI_URLPATH, CustomImage.url(name, !is2x));
                saveField(name+AppConfig.CI_2X, (is2x)? AppConfig.CI_2XVAL_NONE : AppConfig.CI_2XVAL_SAME);
            }
        }
    }

    /**
     * Sets all config values for a custom image back to their defaults.
     * @param name constant identifying which custom image
     */
    private static void resetImageMetadata (CIName name) {
        settingRepo.findConfigurationByName(name+AppConfig.CI_URLPATH).delete();
        settingRepo.findConfigurationByName(name+AppConfig.CI_HEIGHT).delete();
        settingRepo.findConfigurationByName(name+AppConfig.CI_WIDTH).delete();
        settingRepo.findConfigurationByName(name+AppConfig.CI_2X).delete();
    }
}
