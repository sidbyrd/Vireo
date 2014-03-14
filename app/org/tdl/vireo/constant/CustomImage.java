package org.tdl.vireo.constant;

import org.apache.commons.collections.IteratorUtils;
import org.apache.commons.collections.iterators.IteratorEnumeration;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.tdl.vireo.model.Configuration;
import org.tdl.vireo.model.SettingsRepository;
import play.modules.spring.Spring;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.ImageInputStream;
import javax.swing.event.ListSelectionEvent;
import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

/**
 * Static utility methods for dealing with the configuration values
 * for custom images and their 2x resolution versions. Everything needed to
 * generate HTML to display an image should be here, as long as you know its
 * name and the resolution you want.
 */
public class CustomImage {

    private static SettingsRepository settingRepo = Spring.getBeanOfType(SettingsRepository.class);

    // Text returned by fileDescription().
    private static final String DESC_DEFAULT = "default image"; // special: "EXT" (if present) gets replaced with file extension
    private static final String DESC_CUSTOM = "existing custom .EXT image"; // special: "EXT" (if present) gets replaced with file extension
    private static final String DESC_NONE = "[none]";
    private static final String DESC_SCALED = "[using scaled-down 2x image]";

    /**
     * Internal. Returns the saved url path for the image that should be served for
     * @1x resolution. This may actually be a double-resolution file, with "@2x" in
     * the filename, depending on other settings.
     * @param name constant identifying the image in app settings
     * @return base URL path, relative to application base
     */
    private static String baseUrl(AppConfig.CIName name) {
        return settingRepo.getConfigValue(name+AppConfig.CI_URLPATH);
    }

    /**
     * Get the url for serving the specified image and resolution, according to
     *  the combination of all relevant config values. The referenced file
     *  might natively be the requested resolution, or it might be a different
     *  resolution that the current config says to serve for the requested resolution.
     * @param name constant identifying the image in app settings
     * @param want2x whether the high-res version is desired
     * @return Either a path to the best image file currently configured for the
     * given resolution, or null if asking for high-res and there isn't one (we
     * never scale up, only down).
     */
    public static String url(AppConfig.CIName name, boolean want2x) {
        if (want2x && is2xNone(name)) {
            return null;
        }

        String url = baseUrl(name);
        if (want2x && is2xSeparate(name)) {
            url = url.replaceFirst("\\.\\w+$", "@2x$0");
        }
        return url;
    }

    /**
     * Whether @1x or @2x, a given image is always the same CSS width. What is it?
     * @param name constant identifying the image in app settings
     * @return the correct pixel width at which to display the image
     */
    public static String displayWidth(AppConfig.CIName name) {
        return settingRepo.getConfigValue(name+AppConfig.CI_WIDTH);
    }

    /**
     * Whether @1x or @2x, a given image is always the same CSS height. What is it?
     * @param name constant identifying the image in app settings
     * @return the correct pixel height at which to display the image
     */
    public static String displayHeight(AppConfig.CIName name) {
        return settingRepo.getConfigValue(name+AppConfig.CI_HEIGHT);
    }

    /**
     * Find the height of the taller of two images
     * @param name1 constant identifying the first image to compare
     * @param name2 constant identifying the second image to compare
     * @return the taller height (or 0 if something was invalid)
     */
    public static int tallerHeight(AppConfig.CIName name1, AppConfig.CIName name2) {
        try{
            return Math.max(Integer.parseInt(displayHeight(name1)), Integer.parseInt(displayHeight(name2)));
        } catch (NumberFormatException e) {}
        return 0;
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
     * For an image, resolution, and file type, make a standardized filename
     * (The extension is there so web servers get the mimetype right.)
     * @param name constant identifying the image in app settings
     * @param is2x whether this is a high-res image
     * @param extension file extension of the original image file
     * @return Name where in theme directory this image should be stored.
     */
    public static String standardFilename(AppConfig.CIName name, boolean is2x, String extension) {
        return name.toString().replace('_', '-') + ((is2x)? "@2x.":".") + extension;
    }

    /**
     * Looks up the file extension of the current image (either customized or default)
     * @param name constant identifying the image in app settings
     * @return the extension of the file or an empty string if none exists
     */
    public static String extension(AppConfig.CIName name) {
        return FilenameUtils.getExtension(baseUrl(name));
    }

    /**
     * Is the image set to the built-in default image?
     * @param name constant identifying the image in app settings
     * @return true if there is no custom image set
     */
    public static boolean isDefault(AppConfig.CIName name) {
        return baseUrl(name).equals(Configuration.DEFAULTS.get(name + AppConfig.CI_URLPATH));
    }

    /**
     * Is there an image file saved whose native resolution is the given scale?
     * @param name constant identifying the image in app settings
     * @param is2x whether to check the high-res version
     * @return whether the current configuration says there's such a file
     */
    public static boolean hasFile(AppConfig.CIName name, boolean is2x) {
        return (!is2x && !is2xSame(name)) || (is2x && !is2xNone(name));
    }

    /**
     * Is there a customized (i.e. non-default) image file saved whose native
     * resolution is the given scale? 
     * @param name constant identifying the image in app settings
     * @param is2x whether to check the high-res version
     * @return whether the current configuration says there's such a file
     */
    public static boolean hasCustomFile(AppConfig.CIName name, boolean is2x) {
        return  !isDefault(name) && hasFile(name, is2x);
    }

    /**
     * Get a description of the saved file for an image and resolution, depending
     * on the current configuration settings. Uses description constants at top
     * of this file.
     * @param name constant identifying the image in app settings
     * @param is2x whether to describe the file for the high-res version
     * @return a description of the file
     */
    public static String fileDescription(AppConfig.CIName name, boolean is2x) {
        if (hasFile(name, is2x)) {
            if (isDefault(name)) {
                return DESC_DEFAULT.replace("EXT", extension(name));
            } else {
                return DESC_CUSTOM.replace("EXT", extension(name));
            }
        } else {
            if (is2x) {
                return DESC_NONE;
            } else {
                return DESC_SCALED;
            }
        }
    }

    /**
     * Gets image dimensions for given file. Also verifies that the image is a valid, readable format.
     * @param image image file
     * @param extension optionally, a file type extension to indicate image's format
     * @return dimensions of image, or null if it couldn't be read and understood
     */
    public static Dimension verifyFormatAndGetDimensions(File image, String extension) {
        // There are only three image formats we wish to accept for display in common browsers.
        java.util.List<ImageReader> allowedReaders = new ArrayList<ImageReader>(3);
        allowedReaders.addAll(IteratorUtils.toList(ImageIO.getImageReadersByFormatName("jpeg")));
        allowedReaders.addAll(IteratorUtils.toList(ImageIO.getImageReadersByFormatName("gif")));
        allowedReaders.addAll(IteratorUtils.toList(ImageIO.getImageReadersByFormatName("png")));

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

        // nothing worked.
        return null;
    }
}
