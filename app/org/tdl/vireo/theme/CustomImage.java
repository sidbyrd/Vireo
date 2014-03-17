package org.tdl.vireo.theme;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.commons.lang.StringUtils;
import org.tdl.vireo.constant.AppConfig;
import org.tdl.vireo.model.Configuration;
import org.tdl.vireo.model.SettingsRepository;
import play.Logger;
import play.i18n.Messages;
import play.modules.spring.Spring;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Manages persistence, manipulation, and information requests about customized
 * image assets that are saved in the theme directory and may have a 1x and/or
 * 2x resolution image file for display in the user's browser. Except where
 * specifically stated, all methods leave the overall configuration in a consistent
 * state at all times.
 */
public final class CustomImage {
    // Implementation note: this class (as currently implemented) uses Configurations
    // for all settings, and it stores files directly on disk. That means two things:
    // 1) no db schema changes, and
    // 2) all methods here are static.

    private CustomImage() { /* everything in this class is static; no need to ever instantiate. */}
    private static SettingsRepository settingRepo = Spring.getBeanOfType(SettingsRepository.class);

    /** goes right before the format extension in a double-resolution file's filename */
    private static final String marker2x = "@2x";

    /**
     * Swaps out the normal settings repository for another one.
     * Intended to be used with a MockSettingsRepository for testing.
     * @param newRepo the new settings repository to swap in
     * @return the previous settings repository that was swapped out
     */
    public static SettingsRepository swapSettingsRepo(SettingsRepository newRepo) {
        SettingsRepository oldRepo = settingRepo;
        settingRepo = newRepo;
        return oldRepo;
    }

    /*****************************************************************
     * Information about how to display (called from/passed to views)
     *****************************************************************/

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
            url = url.replaceFirst("\\.\\w+$", marker2x+"$0");
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
        } catch (NumberFormatException e) { /**/ }
        return 0;
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
     * on the current configuration settings. Uses constants defined in conf/messages
     * @param name constant identifying the image in app settings
     * @param is2x whether to describe the file for the high-res version
     * @return a description of the file
     */
    public static String fileDescription(AppConfig.CIName name, boolean is2x) {
        final String ext = FilenameUtils.getExtension(baseUrl(name));
        if (hasFile(name, is2x)) {
            if (isDefault(name)) {
                return Messages.get("CI_FILE_DESC_DEFAULT").replace("EXT", ext);
            } else {
                return Messages.get("CI_FILE_DESC_CUSTOM").replace("EXT", ext);
            }
        } else {
            if (is2x) {
                return Messages.get("CI_FILE_DESC_NONE");
            } else {
                return Messages.get("CI_FILE_DESC_SCALED");
            }
        }
    }

    /*****************************************
     * Manipulation (called from controllers)
     *****************************************/

    /**
     * Saves the file for one resolution of a custom image (either new or replacing a
     * previous customization), and sets all relevant config settings appropriately.
     * @param name constant identifying which custom image
     * @param is2x whether to save the file as the high-resolution version
     * @param file the new customized file to save
     * @throws IOException thrown on error storing or deleting image files
     * @throws IllegalArgumentException if image format could not be understood, or if new image size/format
     * doesn't match existing counterpart, or if trying to save a 2x image with odd dimensions. The Exception's
     * getMessage() will contain an explanation suitable for presentation to the user.
     */
    public static void replaceFile(AppConfig.CIName name, boolean is2x, File file) throws IOException, IllegalArgumentException {
        if (!ThemeDirectory.check(true)) {
            throw new IOException("Couldn't create theme directory");
        }

        if (file != null) {
            // Check that incoming file is a valid image with known dimensions.
            final String extension = FilenameUtils.getExtension(file.getName());
            final Dimension dim = CustomImage.verifyFormatAndGetDimensions(file, extension);
            if (dim == null) {
                throw new IllegalArgumentException(Messages.get("CI_ERROR_FORMAT_UNKNOWN"));
            }
            int h=(int)dim.getHeight();
            int w=(int)dim.getWidth();

            // 2x files with odd dimensions aren't allowed. What would they be the double-size of?
            if (is2x && ((h&1)==1 || (w&1)==1)) {
                throw new IllegalArgumentException(Messages.get("CI_ERROR_DIMENSIONS_2X_ODD"));
            }

            // If there are existing customization files, deal with that.
            if (!CustomImage.isDefault(name)) {
                final String extensionOld = FilenameUtils.getExtension(baseUrl(name));

                // If we need to reject a file that mismatches its customized counterpart, this is the
                // last chance to do it before modifying anything else.
                if (CustomImage.hasCustomFile(name, !is2x)) {
                    if (!extension.equals(extensionOld)) {
                        throw new IllegalArgumentException(Messages.get("CI_ERROR_MISMATCHED_FORMAT").replace("EXT",extensionOld));
                    }
                    final int factorOfDisplayDims = is2x? 2 : 1;
                    if (   h != factorOfDisplayDims* settingRepo.getConfigInt(name+AppConfig.CI_HEIGHT)
                        || w != factorOfDisplayDims* settingRepo.getConfigInt(name+AppConfig.CI_WIDTH)) {
                        throw new IllegalArgumentException(Messages.get("CI_ERROR_MISMATCHED_DIMENSIONS"));
                    }
                }

                // Explicitly delete previous if present--the new standardized filename may differ due to extension.
                ThemeDirectory.deleteFile(CustomImage.standardFilename(name, is2x, extensionOld));
            }

            // Save new file.
            final File newFile = ThemeDirectory.createFile(CustomImage.standardFilename(name, is2x, extension));
            FileUtils.copyFile(file, newFile);

            // Set 2x metadata
            if (CustomImage.hasCustomFile(name, !is2x)) {
                // added file to existing counterpart customization
                settingRepo.saveConfiguration(name+AppConfig.CI_2X, AppConfig.CI_2XVAL_SEPARATE);
                if (is2x) {
                    // keep existing name and size of 1x
                    return;
                }
            } else if (!is2x) {
                // 1x added as standalone
                settingRepo.saveConfiguration(name+AppConfig.CI_2X, AppConfig.CI_2XVAL_NONE);
            } else {
                // 2x added as standalone
                settingRepo.saveConfiguration(name+AppConfig.CI_2X, AppConfig.CI_2XVAL_SAME);
                // use 2x name and display at half size
                h *= 0.5; w *= 0.5;
            }

            // Set basic image metadata.
            settingRepo.saveConfiguration(name+AppConfig.CI_URLPATH, ThemeDirectory.urlForFile(newFile));
            settingRepo.saveConfiguration(name+AppConfig.CI_HEIGHT, String.valueOf(h));
            settingRepo.saveConfiguration(name+AppConfig.CI_WIDTH, String.valueOf(w));
        }
    }

    /**
     * Deletes the customized file for one resolution of a custom image, if present, and sets all
     * relevant config settings to values appropriate for the customization being gone, which will
     * be default values if both resolutions are now gone.
     * @param name constant identifying which custom image
     * @param is2x whether to delete the file for the high-resolution version
    */
    public static void deleteFile(AppConfig.CIName name, boolean is2x) {

        if (!CustomImage.isDefault(name)) {
            ThemeDirectory.deleteFile(CustomImage.standardFilename(name, is2x, FilenameUtils.getExtension(baseUrl(name))));
            if (!CustomImage.hasFile(name, !is2x)) {
                // No counterpart exists - switch to defaults
                resetMetadata(name);
            } else {
                // Counterpart exists - switch to it (if it isn't already)
                settingRepo.saveConfiguration(name+AppConfig.CI_URLPATH, CustomImage.url(name, !is2x));
                settingRepo.saveConfiguration(name+AppConfig.CI_2X, is2x? AppConfig.CI_2XVAL_NONE : AppConfig.CI_2XVAL_SAME);
            }
        }
    }

    /**
     * Remove any possible images left in the theme directory, regardless of resolution
     * or extension. Reset all related metadata. Leaves everything for the specified image
     * in a completely default state.
     * @param name constant identifying which custom image
     * @throws IOException if couldn't delete files
     */
    public static void reset(AppConfig.CIName name) throws IOException {
        resetMetadata(name);
        FileFilter imageFilter = new WildcardFileFilter(name.toString().replace("_","-")+"*");
        for (File imageFile : ThemeDirectory.listFiles(imageFilter)) {
            if (!imageFile.delete()) {
                throw new IOException("failed to delete file '"+imageFile.getPath()+"'");
            }
        }
    }

    /*****************************
     * Internal helpers
     *****************************/

    /**
     * For an image, resolution, and file type, make a standardized filename
     * (The extension is required so web servers get the mimetype right.)
     * This is public so test can use it, but you shouldn't generally need it for anything else.
     * @param name constant identifying the image in app settings
     * @param is2x whether this is a high-res image
     * @param extension file extension of the original image file
     * @return Name where in theme directory this image should be stored.
     */
    public static String standardFilename(AppConfig.CIName name, boolean is2x, String extension) {
        return name.toString().replace('_', '-') + (is2x? marker2x:"") + '.'+extension;
    }

    /**
     * Is there an image file saved whose native resolution is the given scale?
     * @param name constant identifying the image in app settings
     * @param is2x whether to check the high-res version
     * @return whether the current configuration says there's such a file
     */
    private static boolean hasFile(AppConfig.CIName name, boolean is2x) {
        return (!is2x && !is1xScaled(name)) || (is2x && !is2xNone(name));
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
        return settingRepo.getConfigValue(name+AppConfig.CI_URLPATH);
    }

    /**
     * Reset all metadata for one image to default values.
     * Does not update files.
     * @param name constant identifying which custom image
     */
    private static void resetMetadata(AppConfig.CIName name) {
        for (String key : Arrays.asList(AppConfig.CI_URLPATH, AppConfig.CI_WIDTH, AppConfig.CI_HEIGHT, AppConfig.CI_2X)) {
            Configuration config = settingRepo.findConfigurationByName(name+key);
            if (config != null) {
                config.delete();
            }
        }
    }

    /**
     * Verifies that:
     *  1) the file extension isn't empty.
     *  2) the file extension indicates an understood image type.
     *  3) that type is JPEG, PNG, or GIF.
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
    private static Dimension verifyFormatAndGetDimensions(File image, String extension) {
        // There are only three image formats we wish to accept for display in common browsers.
        // These are the formatName of the standard Java ImageReader for those three types.
        final List<String> allowedReaders = Arrays.asList("JPEG", "gif", "png");

        if (!StringUtils.isBlank(extension)) {
            // let Java figure out that both ".jpg" and ".jpeg" both mean the image reader
            //  whose name is "JPEG", etc.
            final Iterator<ImageReader> it = ImageIO.getImageReadersBySuffix(extension);
            if (it.hasNext()) {
                final ImageReader reader = it.next();
                try {
                    if (allowedReaders.contains(reader.getFormatName())) {
                        final ImageInputStream stream = new FileImageInputStream(image);
                        reader.setInput(stream);
                        return new Dimension(reader.getWidth(reader.getMinIndex()), reader.getHeight(reader.getMinIndex()));
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
