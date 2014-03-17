package org.tdl.vireo.theme;

import org.apache.commons.lang.StringUtils;
import play.Logger;
import play.libs.Files;
import play.mvc.Router;
import play.vfs.VirtualFile;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Handles basic operations involving the theme assets directory
 */
public class ThemeDirectory {

    /** URL path for serving things in the theme directory, matching rule configured in routes */
    public static final String URL_PREFIX = "theme/";

    /** filesystem path to theme directory, relative to play app base*/
    private static String path = null;

    /**
     * Get filesystem path, relative to play app base, where files are being
     * served from at the URL_PREFIX url. The value is read from the current Play
     * routes config.
     * Keep this private to ensure that other code uses the various access methods
     * provided below to get at any theme files. That way, this class can
     * confidently do things like set test mode.
     * @return path to theme directory
     */
    private static String getPath() {
        if (path != null) {
            return path;
        }

        for (Router.Route route : Router.routes) {
            if (route.method.equals("GET") && route.path.equals("/theme/")) { // note preceding / on "/theme/".
                if (route.action.startsWith("staticDir:")) {
                    path = route.action.substring("staticDir:".length());
                    break;
                }
            }
        }
        return path;
    }

    /**
     * Swaps out the normal path to the theme dir for another one.
     * Intended to be used with a temp dir for testing.
     * @param newPath the new directory to swap in
     * @return the previous path that was swapped out
     */
    public static String swapPath(String newPath) {
        String oldPath = path;
        path = newPath;
        return oldPath;
    }
    
   /**
     * Verify existence of the theme directory
     * @param create whether to create it if it doesn't exist
     * @return whether the theme directory exists (after trying to create it, if requested)
     */
    public static boolean check(boolean create) {
        final File themeDir = new File(getPath());
        if(!themeDir.exists() && create) {
            return themeDir.mkdir();
        }
        return themeDir.exists();
    }

    /**
     * Given a URL for an resource being served from the theme directory, return the corresponding File
     * (without checking whether it actually exists).
     * Simpler than using play.mvc.Router if the relevant constants are correct.
     * @param url url for the resource
     * @return File for the resource
     */
    public static File fileForUrl(String url) {
        return new File(getPath() + StringUtils.substringAfter(url, URL_PREFIX));
    }

    /**
     * Given a File representing a file in the theme directory, return the URL where that file would
     * be served from (without checking whether it actually exists).
     * @param file a file in the theme directory, e.g. new File("conf/theme/file.foo")
     * @return the URL for the file, e.g. "theme/file.foo"
     */
    public static String urlForFile(File file) {
        //return Router.reverse(VirtualFile.fromRelativePath(file.getPath()));
        return URL_PREFIX +StringUtils.substringAfter(file.getPath(), getPath());
    }

    /**
     * Gets a File reference for a filename in the theme directory. The file
     * may not actually exist--check that yourself if you care.
     * @param filename the name of the file relative to the theme directory
     * @return a File reference to the named file
     */
    public static File getFile(String filename) {
        return new File(getPath()+filename);
    }

    /**
     * List files in the theme directory, applying the given file filter
     * @param filter filter
     * @return files in the dir, or empty array if dir not currently created
     */
    public static File[] listFiles(FileFilter filter) {
        if (!check(false)) {
            return new File[0];
        }
        return new File(getPath()).listFiles(filter);
    }

    /**
     * Creates a new File in the theme directory.
     * Note: this is just a Java File reference, not an actual file on
     * disk yet.
     * @param filename the name of the file relative to the theme directory
     * @return the new file
     */
    public static File createFile(String filename) {
        Logger.info("*** create "+getPath()+filename);
        return new File(getPath()+filename);
    }

    /**
     * Deletes a file from the theme directory, if it exists.
     * @param filename the name of the file relative to the theme directory
     */
    public static void deleteFile(String filename) {
        final File file = new File(getPath() + filename);
        if (file.exists()){
            Logger.info("*** delete "+getPath()+filename);
            if (!file.delete()) {
                // Can't delete. Probably not a real problem except for some wasted disk space--at least not yet--but do log it.
                Logger.error("theme-dir: could not delete existing file " + file.getAbsolutePath());
            }
        }
    }
}
