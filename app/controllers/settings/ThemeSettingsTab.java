package controllers.settings;

import controllers.Authentication;
import controllers.Security;
import controllers.SettingsTab;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.tdl.vireo.constant.AppConfig;
import org.tdl.vireo.model.Configuration;
import org.tdl.vireo.model.RoleType;
import play.Logger;
import play.mvc.With;
import sun.awt.image.ImageFormatException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.ImageInputStream;
import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
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
	public static final String LEFT_LOGO_PATH = THEME_PATH + "left-logo";
	public static final String RIGHT_LOGO_PATH = THEME_PATH + "right-logo";

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
        File leftLogo = fileForLogo("left", false);
        File rightLogo = fileForLogo("right", false);
		boolean leftLogoIsDefault = isLogoDefault("left");
        boolean rightLogoIsDefault = isLogoDefault("right");
        renderArgs.put("LEFT_LOGO_URLPATH", settingRepo.getConfigValue(LEFT_LOGO_URLPATH));
        renderArgs.put("LEFT_LOGO_HEIGHT", settingRepo.getConfigValue(LEFT_LOGO_HEIGHT));
        renderArgs.put("LEFT_LOGO_WIDTH", settingRepo.getConfigValue(LEFT_LOGO_WIDTH));
        renderArgs.put("LEFT_LOGO_2X", settingRepo.getConfigValue(LEFT_LOGO_2X));
        renderArgs.put("RIGHT_LOGO_URLPATH", settingRepo.getConfigValue(RIGHT_LOGO_URLPATH));
        renderArgs.put("RIGHT_LOGO_HEIGHT", settingRepo.getConfigValue(RIGHT_LOGO_HEIGHT));
        renderArgs.put("RIGHT_LOGO_WIDTH", settingRepo.getConfigValue(RIGHT_LOGO_WIDTH));
        renderArgs.put("RIGHT_LOGO_2X", settingRepo.getConfigValue(RIGHT_LOGO_2X));
		
		String nav = "settings";
		String subNav = "theme";
		renderTemplate("SettingTabs/themeSettings.html",nav, subNav, leftLogo, rightLogo, leftLogoIsDefault, rightLogoIsDefault);
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
		File themeDir = new File(THEME_PATH);

		if(!themeDir.exists()){
			if (!themeDir.mkdir()) {
                Logger.error("tab-settings: failed to create theme directory.");
                flash.error("The server cannot write new theme settings.");
                themeSettings();
                return;
            }
		}
		
		try {
            if (params.get("deleteLeftLogo") != null || leftLogo != null) {
                updateLogo("left", false, leftLogo);
            }
            if (params.get("deleteRightLogo") != null || rightLogo != null) {
                updateLogo("right", false, rightLogo);
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
     * @param side pick which logo: either "left" or "right"
     * @param logo the new customized logo, or null to delete any previous customization and reset to
     * default values.
     * @throws IOException thrown on error storing or deleting image files
     * @throws ImageFormatException if image format could not be understood
     * @throws IllegalArgumentException if 2x uploaded but format extension doesn't match corresponding 1x
     */
    private static void replaceLogo (String side, boolean is2x, File logo) throws IOException, ImageFormatException, IllegalArgumentException {
        if (logo != null) {
            // Uploading a new file.
            // Check that it's a valid image with known dimensions.
            String extension = FilenameUtils.getExtension(logo.getName());
            Dimension dim = getImageDimension(logo, extension);
            if (dim == null) {
                throw new ImageFormatException("Image format not recognized");
            }

            if (is2x) {
                if (!isLogoDefault(side)) {
                    // If it's a 2x image and the 1x image is set, they need to be the same file format extension.
                    String extension1x = FilenameUtils.getExtension(fileForLogo(side, false).getName());
                    if (!extension.equals(extension1x)) {
                        throw new IllegalArgumentException("2x logo file format extension must match 1x");
                    }
                } else {
                    // This is tricky. Setting a 2x image with no 1x image means to actually save it
                    //  as 1x, but save halved dimensions. That way everyone everyone just gets the 2x file
                    //  at the correct size, although 1x browsers will waste bandwidth.
                    //  Remember to delete any 2x image that existed before.
                    deleteLogo(side, true);
                    saveField(side+"_logo_2x", AppConfig.LOGO_2X_SAME_AS_1X);
                    is2x = false;
                    dim.setSize(0.5*dim.getWidth(), 0.5*dim.getHeight());
                }
            }

            // Put it in the theme directory with a standardized name for organization, but
            // keep its original file extension so web servers get the mimetype right.
            File newFile = new File(THEME_PATH + side + "-logo" + "." + ((is2x)? "@2x":"") + extension);
            FileUtils.copyFile(logo, newFile);

            if (!is2x) {
                // 1x: Save image metadata.
                saveField(side+"_logo_urlpath", String.valueOf("theme/"+side+"-logo"));
                saveField(side+"_logo_height", String.valueOf((int)dim.getHeight()));
                saveField(side+"_logo_width", String.valueOf((int)dim.getWidth()));
            } else {
                // 2x: Just note that we have it.
                saveField(side+"_logo_2x", AppConfig.LOGO_2X_SEPARATE);
            }
        }
    }

    private static void deleteLogo (String side, boolean is2x) throws IOException {
        // Delete old customized logo file if present.
        File oldFile = fileForLogo(side, is2x);
        if(oldFile.exists() // TODO make sure it's in theme dir){
            if (!oldFile.delete()) {
                // Can't delete. Not a real problem except for some wasted disk space--at least not yet--but do log it.
                Logger.error("tab-settings: could not delete existing "+side+" logo customization "+oldFile.getAbsolutePath());
            }
        }

        if (!is2x) {
            // 1x: Reset to default image metadata.
            settingRepo.findConfigurationByName(side+"_logo_urlpath").delete();
            settingRepo.findConfigurationByName(side+"_logo_height").delete();
            settingRepo.findConfigurationByName(side+"_logo_width").delete();
        } else {
            // 2x: note that we have no 2x anymore.
            settingRepo.findConfigurationByName(side+"_logo_2x").delete();
        }
    }

    /**
     * Is the logo image set to the built-in default image?
     * @param side pick which logo: either "left" or "right"
     * @return true if there is no custom 1x logo set for the selected side
     */
    private static boolean isLogoDefault(String side) {
        return settingRepo.getConfigValue(side+"_logo_urlpath").equals(Configuration.DEFAULTS.get(side+"_logo_urlpath"));
    }

    /**
     * Makes a File pointing to the location where the specified logo file is,
     * or would be if it had been stored.
     * @param side either "left" or "right"
     * @param get2x if false, the 1x file specified by the current configuration, which could
     *              be either a file in the theme directory or a default file built into the app.
     *              If true, put "@2x" in the filename, regardless of whether that file exists.
     * @return
     */
    private static File customLogoFile (String side, boolean get2x) {
        String urlPath = settingRepo.getConfigValue(side+"_logo_urlpath");
        if (get2x) {
            urlPath = urlPath.replaceFirst("\\.\\w+$", "@2x$0");
        }
        return new File("conf"+File.separator+urlPath);
    }

    /**
     * Gets image dimensions for given file
     * @param image image file
     * @param extension optionally, a file type extension to indicate image's format
     * @return dimensions of image, or null if it couldn't be read and understood
     */
    private static Dimension getImageDimension(File image, String extension) {
        // if file extension present, sometimes we can use that to read height and width without
        //  loading the whole image
        if (!StringUtils.isBlank(extension)) {
            Iterator<ImageReader> it = ImageIO.getImageReadersBySuffix(extension);
            if (it.hasNext()) {
                ImageReader reader = it.next();
                try {
                    ImageInputStream stream = new FileImageInputStream(image);
                    reader.setInput(stream);
                    int width = reader.getWidth(reader.getMinIndex());
                    int height = reader.getHeight(reader.getMinIndex());
                    return new Dimension(width, height);
                } catch (IOException e) {
                    // we have more things left to try; not a failure yet.
                } finally {
                    reader.dispose();
                }
            }
        }

        // can't use file extension, so try slower generic method
        ImageIcon imageIcon = new ImageIcon(image.getAbsolutePath());
        if (imageIcon.getIconWidth() >= 0) {
            return new Dimension(imageIcon.getIconWidth(), imageIcon.getIconHeight());
        }

        // nothing worked.
        return null;
    }
}
