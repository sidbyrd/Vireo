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
        // TODO put files back here, so I can pre-populate pickers with them.
		boolean leftLogoIsDefault = CustomImage.isDefault(CIName.LEFT_LOGO);
        boolean rightLogoIsDefault = CustomImage.isDefault(CIName.RIGHT_LOGO);
        renderArgs.put("LEFT_LOGO_URLPATH", settingRepo.getConfigValue(CIName.LEFT_LOGO+CI_URLPATH));
        renderArgs.put("LEFT_LOGO_HEIGHT", settingRepo.getConfigValue(CIName.LEFT_LOGO+CI_HEIGHT));
        renderArgs.put("LEFT_LOGO_WIDTH", settingRepo.getConfigValue(CIName.LEFT_LOGO+CI_WIDTH));
        renderArgs.put("LEFT_LOGO_2X", settingRepo.getConfigValue(CIName.LEFT_LOGO+CI_2X));
        renderArgs.put("RIGHT_LOGO_URLPATH", settingRepo.getConfigValue(CIName.RIGHT_LOGO+CI_URLPATH));
        renderArgs.put("RIGHT_LOGO_HEIGHT", settingRepo.getConfigValue(CIName.RIGHT_LOGO+CI_HEIGHT));
        renderArgs.put("RIGHT_LOGO_WIDTH", settingRepo.getConfigValue(CIName.RIGHT_LOGO+CI_WIDTH));
        renderArgs.put("RIGHT_LOGO_2X", settingRepo.getConfigValue(CIName.RIGHT_LOGO+CI_2X));
		
		String nav = "settings";
		String subNav = "theme";
		renderTemplate("SettingTabs/themeSettings.html",nav, subNav, leftLogoIsDefault, rightLogoIsDefault);
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
	public static void uploadLogos(File leftLogo, File leftLogo2x, File rightLogo, File rightLogo2x) {
		try {
            checkThemeDir();

            if (leftLogo != null) {
                replaceLogo(CIName.LEFT_LOGO, false, leftLogo);
            }
            if (params.get("deleteLeftLogo") != null || leftLogo != null) {
                replaceLogo(CIName.LEFT_LOGO, false, leftLogo);
            }
            if (params.get("deleteRightLogo") != null || rightLogo != null) {
                replaceLogo(CIName.RIGHT_LOGO, false, rightLogo);
            }

        } catch (IOException e) {
            Logger.error("tab-settings: could not update logo because "+e.getMessage());
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
    public static void checkThemeDir() throws IOException {
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
     * Updates the files and config settings for one top logo.
     * @param name constant identifying which logo
     * @param logo the new customized logo, or null to delete any previous customization and reset to
     * default values.
     * @throws IOException thrown on error storing or deleting image files
     * @throws ImageFormatException if image format could not be understood
     * @throws IllegalArgumentException if 2x uploaded but format extension doesn't match corresponding 1x
     */
    private static void replaceLogo (CIName name, boolean is2x, File logo) throws IOException, ImageFormatException, IllegalArgumentException {
        if (logo != null) {
            // Uploading a new file.
            // Check that it's a valid image with known dimensions.
            String extension = FilenameUtils.getExtension(logo.getName());
            Dimension dim = CustomImage.verifyAndGetDimensions(logo, extension);
            if (dim == null) {
                throw new ImageFormatException("Image format not recognized");
            }

            // If we need to reject, do it before copying the file.
            if (is2x) {
                if (!CustomImage.isDefault(name) && !CustomImage.is2xSame(name)) {
                    // If adding a customized 2x image to an existing real 1x customized image, they need to share a file extension.
                    String extension1x = FilenameUtils.getExtension(CustomImage.url(name, false));
                    if (!extension.equals(extension1x)) {
                        throw new IllegalArgumentException("2x file extension must match existing 1x extension \"."+extension1x+"\". Try deleting the 1x file first.");
                    }
                    // They should also have the correct dimensions relationship
                    try {
                        int displayH = Integer.parseInt(settingRepo.getConfigValue(name+AppConfig.CI_HEIGHT));
                        int displayW = Integer.parseInt(settingRepo.getConfigValue(name+AppConfig.CI_WIDTH));
                        if (dim.getHeight() != 2*displayH || dim.getWidth() != 2*displayW) {
                            throw new IllegalArgumentException("2x logo file must be twice the dimensions of existing 1x file. Try deleting the 1x file first.");
                        }
                    } catch (NumberFormatException e) {/**/}
                }
            } else {
                if (!CustomImage.isDefault(name) && !CustomImage.is2xNone(name)) {
                    // If adding a customized 1x image and there's already a 2x, they need to share a file extension.
                    String extension2x = FilenameUtils.getExtension(CustomImage.url(name, true));
                    if (!extension.equals(extension2x)) {
                        throw new IllegalArgumentException("1x file extension must match existing 2x extension \"."+extension2x+"\". Try deleting the 2x file first.");
                    }
                    // The new 1x also has to match the stored display dimensions of the 2x image.
                    try {
                        int displayH = Integer.parseInt(settingRepo.getConfigValue(name+AppConfig.CI_HEIGHT));
                        int displayW = Integer.parseInt(settingRepo.getConfigValue(name+AppConfig.CI_WIDTH));
                        if (dim.getHeight() != displayH || dim.getWidth() != displayW) {
                            throw new IllegalArgumentException("1x logo file must be half the dimensions of existing 2x file. Try deleting the 2x file first.");
                        }
                    } catch (NumberFormatException e) {/**/}
                }
            }

            // Save file. Explicitly delete previous if present--filenames may differ due to extension.
            if (!CustomImage.isDefault(name)) {
                String extensionOld = FilenameUtils.getExtension(settingRepo.getConfigValue(name + AppConfig.CI_URLPATH));
                deleteThemeFile(CustomImage.standardFilename(name, is2x, extensionOld));
            }
            File newFile = new File(THEME_PATH+CustomImage.standardFilename(name, is2x, extension));
            FileUtils.copyFile(logo, newFile);

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

    private static void resetLogo (CustomImage name, boolean is2x) throws IOException {
        // if 1x
            // if 2x==none
                // reset metadata to default
                // delete urlpath
            // if 2x==same
                // nothing
            // if 2x==separate
                // 2x=same
                // delete urlpath
        // if 2x
            // if 2x==none
                // nothing
            // if 2x==same
                // reset metadata to default
                // delete urlpath+@2x
            // if 2x==separate
                // delete urlpath+@2x


        // Delete old customized logo file if present

        if (!is2x) {
            // 1x: Reset to default image metadata.
            settingRepo.findConfigurationByName(name+AppConfig.CI_URLPATH).delete();
            settingRepo.findConfigurationByName(name+AppConfig.CI_HEIGHT).delete();
            settingRepo.findConfigurationByName(name+AppConfig.CI_WIDTH).delete();
        } else {
            // 2x: note that we have no 2x anymore.
            settingRepo.findConfigurationByName(name+AppConfig.CI_2X).delete();
        }
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
