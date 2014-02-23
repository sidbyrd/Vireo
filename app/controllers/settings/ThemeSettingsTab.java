package controllers.settings;

import controllers.Authentication;
import controllers.Security;
import controllers.SettingsTab;
import org.apache.commons.io.FileUtils;
import org.tdl.vireo.model.Configuration;
import org.tdl.vireo.model.RoleType;
import play.Logger;
import play.mvc.With;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.ImageInputStream;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
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
        File leftLogo = new File("conf"+File.separator+settingRepo.getConfigValue(LEFT_LOGO_PATH));
        File rightLogo = new File("conf"+File.separator+settingRepo.getConfigValue(RIGHT_LOGO_PATH));
		boolean leftLogoIsDefault = settingRepo.getConfigValue(LEFT_LOGO_PATH).equals(Configuration.DEFAULTS.get(LEFT_LOGO_PATH));
        boolean rightLogoIsDefault = settingRepo.getConfigValue(RIGHT_LOGO_PATH).equals(Configuration.DEFAULTS.get(RIGHT_LOGO_PATH));
        renderArgs.put("LEFT_LOGO_PATH", settingRepo.getConfigValue(LEFT_LOGO_PATH));
        renderArgs.put("LEFT_LOGO_HEIGHT", settingRepo.getConfigValue(LEFT_LOGO_HEIGHT));
        renderArgs.put("LEFT_LOGO_WIDTH", settingRepo.getConfigValue(LEFT_LOGO_WIDTH));
        renderArgs.put("RIGHT_LOGO_PATH", settingRepo.getConfigValue(RIGHT_LOGO_PATH));
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
			themeDir.mkdir();			
		}
		
		if(params.get("deleteLeftLogo") != null) {
			File logoFile = new File(THEME_PATH + "left-logo");
			
			if(logoFile.exists()){
				logoFile.delete();
			}
            saveField(LEFT_LOGO_PATH, Configuration.DEFAULTS.get(LEFT_LOGO_PATH));
            saveField(LEFT_LOGO_HEIGHT, Configuration.DEFAULTS.get(LEFT_LOGO_HEIGHT));
            saveField(LEFT_LOGO_WIDTH, Configuration.DEFAULTS.get(LEFT_LOGO_WIDTH));
            saveField(TALLEST_LOGO_HEIGHT_PLUS_45, String.valueOf(45+Math.max(
                    Integer.parseInt(settingRepo.getConfigValue(LEFT_LOGO_HEIGHT)),
                    Integer.parseInt(settingRepo.getConfigValue(RIGHT_LOGO_HEIGHT)))));
		}
		
		if(params.get("deleteRightLogo") != null) {
			File logoFile = new File(THEME_PATH + "right-logo");
			
            if(logoFile.exists()){
				logoFile.delete();
			}
            saveField(RIGHT_LOGO_PATH, Configuration.DEFAULTS.get(RIGHT_LOGO_PATH));
            saveField(RIGHT_LOGO_HEIGHT, Configuration.DEFAULTS.get(RIGHT_LOGO_HEIGHT));
            saveField(RIGHT_LOGO_WIDTH, Configuration.DEFAULTS.get(RIGHT_LOGO_WIDTH));
            saveField(TALLEST_LOGO_HEIGHT_PLUS_45, String.valueOf(45+Math.max(
                    Integer.parseInt(settingRepo.getConfigValue(LEFT_LOGO_HEIGHT)),
                    Integer.parseInt(settingRepo.getConfigValue(RIGHT_LOGO_HEIGHT)))));
		}
		
		if(leftLogo != null) {
			File logoFile = new File(THEME_PATH + "left-logo");
            Dimension dim = getImageDimension(leftLogo);

            if(logoFile.exists()){
				logoFile.delete();
			}
			
			FileUtils.copyFile(leftLogo, logoFile);
            saveField(LEFT_LOGO_PATH, String.valueOf("theme/left-logo"));
            saveField(LEFT_LOGO_HEIGHT, String.valueOf((int)dim.getHeight()));
            saveField(LEFT_LOGO_WIDTH, String.valueOf((int)dim.getWidth()));
            saveField(TALLEST_LOGO_HEIGHT_PLUS_45, String.valueOf(45+Math.max((int)dim.getHeight(),
                    Integer.parseInt(settingRepo.getConfigValue(RIGHT_LOGO_HEIGHT)))));
		}

		if(rightLogo != null) {
			File logoFile = new File(THEME_PATH + "right-logo");
            Dimension dim = getImageDimension(rightLogo);

            if(logoFile.exists()){
				logoFile.delete();
			}
			
			FileUtils.copyFile(rightLogo, logoFile);
            saveField(RIGHT_LOGO_PATH, String.valueOf("theme/right-logo"));
            saveField(RIGHT_LOGO_HEIGHT, String.valueOf((int)dim.getHeight()));
            saveField(RIGHT_LOGO_WIDTH, String.valueOf((int)dim.getWidth()));
            saveField(TALLEST_LOGO_HEIGHT_PLUS_45, String.valueOf(45+Math.max((int)dim.getHeight(),
                    Integer.parseInt(settingRepo.getConfigValue(LEFT_LOGO_HEIGHT)))));
		}
		
		themeSettings();
	}

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
     * @return dimensions of image
     * @throws IOException if the file is not a known image
     */
    public static Dimension getImageDimension(File imgFile) throws IOException {
        // if file extension present, use that
        int pos = imgFile.getName().lastIndexOf(".");
        if (pos > -1) {
            String suffix = imgFile.getName().substring(pos + 1);
            Iterator<ImageReader> iter = ImageIO.getImageReadersBySuffix(suffix);
            if (iter.hasNext()) {
                ImageReader reader = iter.next();
                try {
                    ImageInputStream stream = new FileImageInputStream(imgFile);
                    reader.setInput(stream);
                    int width = reader.getWidth(reader.getMinIndex());
                    int height = reader.getHeight(reader.getMinIndex());
                    return new Dimension(width, height);
                } catch (IOException e) {
                    // we have more things left to try
                } finally {
                    reader.dispose();
                }
            }
        }

        // can't use file extension, so try slower generic method
        ImageIcon imageIcon = new ImageIcon(imgFile.getAbsolutePath());
        BufferedImage readImage = ImageIO.read(imgFile);
        return new Dimension(imageIcon.getIconWidth(), imageIcon.getIconHeight());
    }
}
