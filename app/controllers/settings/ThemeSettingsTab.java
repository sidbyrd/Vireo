package controllers.settings;

import controllers.Authentication;
import controllers.Security;
import controllers.SettingsTab;
import org.apache.commons.io.FileUtils;
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
        File leftLogo = new File("conf"+File.separator+settingRepo.getConfigValue(LEFT_LOGO_URLPATH));
        File rightLogo = new File("conf"+File.separator+settingRepo.getConfigValue(RIGHT_LOGO_URLPATH));
		boolean leftLogoIsDefault = settingRepo.getConfigValue(LEFT_LOGO_URLPATH).equals(Configuration.DEFAULTS.get(LEFT_LOGO_URLPATH));
        boolean rightLogoIsDefault = settingRepo.getConfigValue(RIGHT_LOGO_URLPATH).equals(Configuration.DEFAULTS.get(RIGHT_LOGO_URLPATH));
        renderArgs.put("LEFT_LOGO_URLPATH", settingRepo.getConfigValue(LEFT_LOGO_URLPATH));
        renderArgs.put("LEFT_LOGO_HEIGHT", settingRepo.getConfigValue(LEFT_LOGO_HEIGHT));
        renderArgs.put("LEFT_LOGO_WIDTH", settingRepo.getConfigValue(LEFT_LOGO_WIDTH));
        renderArgs.put("RIGHT_LOGO_URLPATH", settingRepo.getConfigValue(RIGHT_LOGO_URLPATH));
        renderArgs.put("RIGHT_LOGO_HEIGHT", settingRepo.getConfigValue(RIGHT_LOGO_HEIGHT));
        renderArgs.put("RIGHT_LOGO_WIDTH", settingRepo.getConfigValue(RIGHT_LOGO_WIDTH));
		
		String nav = "settings";
		String subNav = "theme";
		renderTemplate("SettingTabs/themeSettings.html",nav, subNav, leftLogo, rightLogo, leftLogoIsDefault, rightLogoIsDefault);
	}
	
	
	@Security(RoleType.MANAGER)
	public static void updateThemeSettingsJSON(String field, String value) {

		try {
			List<String> booleanFields = new ArrayList<String>();
			// None at the moment but we expect some in the future.
			
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
	
	@Security(RoleType.MANAGER)
	public static void uploadLogos(File leftLogo, File rightLogo) throws IOException{
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
                updateLogo("left", leftLogo);
            }
            if (params.get("deleteRightLogo") != null || rightLogo != null) {
                updateLogo("right", rightLogo);
            }

        } catch (IOException e) {
            Logger.error("tab-settings: could not update logo because "+e.getMessage());
            flash.error("The server failed to update the image.");
        } catch (ImageFormatException e) {
            flash.error("Image format not recognized.");
        }

		themeSettings();
	}

    /**
     * Updates the files and config settings for one top logo.
     * @param side either "left" or "right"
     * @param newLogoFile the new customized logo, or null to delete any previous customization and reset to
     * default values.
     * @throws IOException thrown on error storing or deleting image files
     * @throws ImageFormatException if image format could not be understood
     */
    private static void updateLogo (String side, File newLogoFile) throws IOException, ImageFormatException {
        // get previously stored customized logo file, if present
        File logoFile = new File(THEME_PATH + side+"-logo");

        if (newLogoFile != null) {
            // uploading a new file:
            // check that it's a valid image with known dimensions.
            Dimension dim = getImageDimension(newLogoFile);
            if (dim == null) {
                throw new ImageFormatException("Not a recognized image format");
            }
            // it's valid. Save its info.
            FileUtils.copyFile(newLogoFile, logoFile);
            saveField(side+"_logo_urlpath", String.valueOf("theme/"+side+"-logo"));
            saveField(side+"_logo_height", String.valueOf((int)dim.getHeight()));
            saveField(side+"_logo_width", String.valueOf((int)dim.getWidth()));
        } else {
            // delete old customized logo file
            if(logoFile.exists()){
                if (!logoFile.delete()) {
                    // Not a real problem until user tries to set another customization. At
                    // worst, some disk space will be wasted. But do log it.
                    Logger.error("tab-settings: could not delete existing "+side+"-logo customization "+logoFile.getAbsolutePath());
                }
            }
            // reset to default info
            settingRepo.findConfigurationByName(side+"_logo_urlpath").delete();
            settingRepo.findConfigurationByName(side+"_logo_height").delete();
            settingRepo.findConfigurationByName(side+"_logo_width").delete();
/*            saveField(side+"_logo_urlpath", Configuration.DEFAULTS.get(side+"_logo_urlpath"));
            saveField(side+"_logo_height", Configuration.DEFAULTS.get(side+"_logo_height"));
            saveField(side+"_logo_width", Configuration.DEFAULTS.get(side+"_logo_width"));*/
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
     * Gets image dimensions for given file
     * @param imgFile image file
     * @return dimensions of image, or null if it couldn't be read and understood
     */
    public static Dimension getImageDimension(File imgFile) {
        // if file extension present, use that
        int pos = imgFile.getName().lastIndexOf(".");
        if (pos > -1) {
            String suffix = imgFile.getName().substring(pos + 1);
            Iterator<ImageReader> it = ImageIO.getImageReadersBySuffix(suffix);
            if (it.hasNext()) {
                ImageReader reader = it.next();
                try {
                    ImageInputStream stream = new FileImageInputStream(imgFile);
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
        ImageIcon imageIcon = new ImageIcon(imgFile.getAbsolutePath());
        if (imageIcon.getIconWidth() >= 0) {
            return new Dimension(imageIcon.getIconWidth(), imageIcon.getIconHeight());
        }

        // nothing worked.
        return null;
    }
}
