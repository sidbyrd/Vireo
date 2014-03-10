package org.tdl.vireo.constant;

import org.apache.commons.lang.StringUtils;
import org.tdl.vireo.model.Configuration;
import org.tdl.vireo.model.SettingsRepository;
import play.modules.spring.Spring;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.ImageInputStream;
import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;

/**
 * Static utility methods for dealing with the configuration values
 * for custom images and their 2x resolution versions. Everything needed to
 * generate HTML to display an image should be here.
 */
public class CustomImage {

    private static SettingsRepository settingRepo = Spring.getBeanOfType(SettingsRepository.class);

    /**
     * Get the url for serving the specified image and resolution, according to
     *  the combination of all relevant config values.
     * @param name constant identifying the image in app settings
     * @param want2x whether the high-res version is desired
     * @return Either a path to the best image file currently configured for the
     * given resolution, or null if asking for high-res and there isn't one.
     */
    public static String url(AppConfig.CIName name, boolean want2x) {
        if (want2x && is2xNone(name)) {
            return null;
        }

        String url = settingRepo.getConfigValue(name+AppConfig.CI_URLPATH);
        if (want2x && is2xSeparate(name)) {
            url = url.replaceFirst("\\.\\w+$", "@2x$0");
        }
        return url;
    }

    /**
     * For an image, resolution, and file type, make a standardized filename
     * (The extension is there so web servers get the mimetype right.)
     * @param name constant identifying the image in app settings
     * @param is2x whether this is a high-res image
     * @param extension file extension of the original image file
     * @return Name where in theme directory this image should be stored.
     */
    public static String standardFilename(AppConfig.CIName name, boolean is2x, String extension) {
        return name.toString().replace('_', '-') + "." + ((is2x)? "@2x":"") + extension;
    }

    /**
     * Is the image set to the built-in default image?
     * @param name constant identifying the image in app settings
     * @return true if there is no custom image set
     */
    public static boolean isDefault(AppConfig.CIName name) {
        return settingRepo.getConfigValue(name+AppConfig.CI_URLPATH).equals(Configuration.DEFAULTS.get(name+AppConfig.CI_URLPATH));
    }

    /**
     * Does the image use one file that is appropriate for both 1x and 2x resolution?
     * @param name constant identifying the image in app settings
     * @return whether 1x and 2x use the same file
     */
    public static boolean is2xSame(AppConfig.CIName name) {
        return settingRepo.getConfigValue(name+AppConfig.CI_2X).equals(AppConfig.CI_2XVAL_SAME);
    }

    /**
     * Does the image use two stored files, one for 1x resolution and a
     * separate one for 2x?
     * @param name constant identifying the image in app settings
     * @return whether 1x and 2x have separate files
     */
    public static boolean is2xSeparate(AppConfig.CIName name) {
        return settingRepo.getConfigValue(name+AppConfig.CI_2X).equals(AppConfig.CI_2XVAL_SEPARATE);
    }

    /**
     * Does the image have only a 1x file, and no file appropriate for 2x resolution?
     * @param name constant identifying the image in app settings
     * @return whethere there is no file appropriate for 2x
     */
    public static boolean is2xNone(AppConfig.CIName name) {
        return settingRepo.getConfigValue(name+AppConfig.CI_2X).equals(AppConfig.CI_2XVAL_NONE);
    }

    /**
     * Gets image dimensions for given file. Also verifies that the image is a valid, readable format.
     * @param image image file
     * @param extension optionally, a file type extension to indicate image's format
     * @return dimensions of image, or null if it couldn't be read and understood
     */
    public static Dimension verifyAndGetDimensions(File image, String extension) {
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
