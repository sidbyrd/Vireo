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
        boolean rightLogoIsDefault = CustomImage.isDefault(CIName.RIGHT_LOGO);
        renderArgs.put("LEFT_LOGO_1X", CustomImage.url(CIName.LEFT_LOGO, false));
        renderArgs.put("LEFT_LOGO_2X", CustomImage.url(CIName.LEFT_LOGO, true));
        renderArgs.put("LEFT_LOGO_HEIGHT", CustomImage.displayHeight(CIName.LEFT_LOGO));
        renderArgs.put("LEFT_LOGO_WIDTH", CustomImage.displayWidth(CIName.LEFT_LOGO));
        renderArgs.put("LEFT_LOGO_EXT", CustomImage.extension(CIName.LEFT_LOGO));
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

    @SuppressWarnings({"UnusedDeclaration"})
	@Security(RoleType.MANAGER)
	public static void uploadImage(String name, File image1x, File image2x) {
		try {
            checkThemeDir();

            for (CIName cn : CIName.values()) {
                if (cn.toString().equals(name)) {
                    // found correct image name
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

    /**
     * Make sure the theme directory exists.
     * @throws IOException if dir doesn't exist and couldn't be created
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
     * @param url url for the resource
     * @return File for the resource
     */
    private static File fileForThemeUrl(String url) {
        return new File(THEME_PATH+StringUtils.substringAfter(url, THEME_URL_PREFIX));
    }

    /**
     * Given a
     * @param file
     * @return
     */
    private static String urlForThemeFile(File file) {
        return THEME_URL_PREFIX +StringUtils.substringAfter(file.getPath(), THEME_PATH);
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
     * Updates the files and config settings for one custom image.
     * @param name constant identifying which file
     * @param file the new customized file, or null to delete any previous customization and reset to
     * default values.
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

            // Determine proper 2x settings
            if (is2x) {
                if (!CustomImage.isDefault(name) && !CustomImage.is2xSame(name)) {
                    // Adding customized 2x image to existing real customized 1x image.
                    // Dimensions and basic urlpath stay the same.
                    saveField(name+AppConfig.CI_2X, AppConfig.CI_2XVAL_SEPARATE);
                    return;
                } else {
                    // Setting a customized 2x image with no customized 1x image means that the 2x image is used for both roles.
                    saveField(name+AppConfig.CI_2X, AppConfig.CI_2XVAL_SAME);
                    // Dimensions should describe the display size, not the hi-res doubled size.
                    dim.setSize(0.5*dim.getWidth(), 0.5*dim.getHeight());
                }
            } else {
                if (CustomImage.isDefault(name)) {
                    // If adding a custom image to a default setup, we override the default 2x setting, too.
                    saveField(name+AppConfig.CI_2X, AppConfig.CI_2XVAL_NONE);
                }
                if (!CustomImage.isDefault(name) && CustomImage.is2xSame(name)) {
                    // If adding a customized 1x to an existing customized 2x that used to be shared for both resolutions, separate them.
                    saveField(name+AppConfig.CI_2X, AppConfig.CI_2XVAL_SEPARATE);
                }
                // If adding a customized 1x to an existing 2x that was separate already, or if there was no 2x already,
                // the 2x setting is correct already and doesn't need changing.
            }

            // Save basic image metadata.
            saveField(name+AppConfig.CI_URLPATH, urlForThemeFile(newFile));
            saveField(name+AppConfig.CI_HEIGHT, String.valueOf((int)dim.getHeight()));
            saveField(name+AppConfig.CI_WIDTH, String.valueOf((int)dim.getWidth()));
        }
    }

    private static void deleteImage (CIName name, boolean is2x) throws IOException {
        if (CustomImage.isDefault(name)) {
            return;
        }

        if (!is2x) {
            if (CustomImage.is2xNone(name)) {
                // delete file
                // reset metadata to default
                deleteThemeFile(CustomImage.standardFilename(name, is2x, CustomImage.extension(name)));
                resetImageMetadata(name);
            } else if (CustomImage.is2xSeparate(name)) {
                // delete file
                // urlpath=urlpath+@2x
                // 2x=same
                deleteThemeFile(CustomImage.standardFilename(name, is2x, CustomImage.extension(name)));
                saveField(name+AppConfig.CI_URLPATH, CustomImage.url(name, true));
                saveField(name+AppConfig.CI_2X, AppConfig.CI_2XVAL_SAME);
            }
        } else {
            if (CustomImage.is2xSame(name)) {
                // delete file+@2x
                // reset metadata to default
                deleteThemeFile(CustomImage.standardFilename(name, is2x, CustomImage.extension(name)));
                resetImageMetadata(name);
            } else if (CustomImage.is2xSeparate(name)) {
                // delete file+@2x
                // 2x=none
                deleteThemeFile(CustomImage.standardFilename(name, is2x, CustomImage.extension(name)));
                saveField(name+AppConfig.CI_2X, AppConfig.CI_2XVAL_NONE);
            }
        }
    }

    private static void resetImageMetadata (CIName name) {
        settingRepo.findConfigurationByName(name+AppConfig.CI_URLPATH).delete();
        settingRepo.findConfigurationByName(name+AppConfig.CI_HEIGHT).delete();
        settingRepo.findConfigurationByName(name+AppConfig.CI_WIDTH).delete();
        settingRepo.findConfigurationByName(name+AppConfig.CI_2X).delete();
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
}
