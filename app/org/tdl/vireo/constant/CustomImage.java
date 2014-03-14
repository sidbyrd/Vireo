package org.tdl.vireo.constant;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.tdl.vireo.model.Configuration;
import org.tdl.vireo.model.SettingsRepository;
import play.i18n.Messages;
import play.modules.spring.Spring;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Static utility methods for dealing with the configuration values
 * for custom images and their 2x resolution versions. Everything needed to
 * generate HTML to display an image should be here, as long as you know its
 * name and the resolution you want.
 */
public class CustomImage {

    private static SettingsRepository settingRepo = Spring.getBeanOfType(SettingsRepository.class);

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
        return (!is2x && !is1xScaled(name)) || (is2x && !is2xNone(name));
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
                return Messages.get("CI_FILE_DESC_DEFAULT").replace("EXT", extension(name));
            } else {
                return Messages.get("CI_FILE_DESC_CUSTOM").replace("EXT", extension(name));
            }
        } else {
            if (is2x) {
                return Messages.get("CI_FILE_DESC_NONE");
            } else {
                return Messages.get("CI_FILE_DESC_SCALED");
            }
        }
    }

    /**
     * Internal--you probably don't need this to just display the image. Use url() instead.
     * Returns the saved url path for the image that should be served for
     * @1x resolution. This may actually be a double-resolution file, with "@2x" in
     * the filename, depending on other settings.
     * @param name constant identifying the image in app settings
     * @return base URL path, relative to application base
     */
    private static String baseUrl(AppConfig.CIName name) {
        return settingRepo.getConfigValue(name + AppConfig.CI_URLPATH);
    }

    /**
     * Does scaling need to be applied to make the 1x image display at the
     *  correct display resolution? (2x images are always scaled.)
     *  I.e. does the image use one file that is appropriate for both 1x and 2x resolution?
     * @param name constant identifying the image in app settings
     * @return whether 1x and 2x use the same file
     */
    public static boolean is1xScaled(AppConfig.CIName name) {
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
     * @return whether there is no file appropriate for 2x
     */
    public static boolean is2xNone(AppConfig.CIName name) {
        return settingRepo.getConfigValue(name+AppConfig.CI_2X).equals(AppConfig.CI_2XVAL_NONE);
    }

    /**
     * Verifies that:
     *  1) the file extension isn't empty.
     *  2) the file extension indicates an understood image type.
     *  3) that type either represents JPEG, PNG, or GIF.
     *  4) the file actually is a decodable example of the image type its extension indicates.
     * Once the file is verified, the dimensions are determined, without having to
     *   instantiate and decode the entire image contents if possible, depending on the
     *   image reader implementation.
     * This could be done without matching and verifying the file extension, but since
     *   the image is intended to be served on the web, the extension helps with mimetype
     *   selection and universal compatibility, so requiring its correctness is a feature here.
     * @param image image file
     * @param extension the type extension to indicate image's format
     * @return dimensions of image, or null if it didn't verify or couldn't be decoded
     */
    public static Dimension verifyFormatAndGetDimensions(File image, String extension) {
        // There are only three image formats we wish to accept for display in common browsers.
        // These are the formatName of the standard Java ImageReader for those three types.
        final List<String> allowedReaders = Arrays.asList("JPEG", "gif", "png");

        if (!StringUtils.isBlank(extension)) {
            // let Java figure out that both ".jpg" and ".jpeg" both mean the image reader
            //  whose name is "JPEG", etc.
            Iterator<ImageReader> it = ImageIO.getImageReadersBySuffix(extension);
            if (it.hasNext()) {
                ImageReader reader = it.next();
                try {
                    if (allowedReaders.contains(reader.getFormatName())) {
                        ImageInputStream stream = new FileImageInputStream(image);
                        reader.setInput(stream);
                        int width = reader.getWidth(reader.getMinIndex());
                        int height = reader.getHeight(reader.getMinIndex());
                        return new Dimension(width, height);
                    }
                } catch (IOException e) { //
                } finally {
                    reader.dispose();
                }
            }
        }

        return null;
    }
}
