package org.tdl.vireo.theme;

import org.apache.commons.lang.StringUtils;
import play.Logger;

import java.io.File;
import java.io.IOException;

/**
 * Handles basic operations involving the theme assets directory
 */
public class ThemeDirectory {

    // If anyone cares, PATH and URL_PREFIX could be configured in application.conf to be somewhere else.
    // You'd have to adjust routes to match, though.

    /** filesystem path to theme directory, relative to Play! app */
    public static final String PATH = "conf"+ File.separator+"theme"+File.separator;

    /** URL path for serving things in the theme directory, as configured in routes */
    public static final String URL_PREFIX = "theme/";

    /**
     * Make sure the theme directory exists.
     * @throws java.io.IOException if theme dir doesn't exist and couldn't be created
     */
    public static void check() throws IOException {
        final File themeDir = new File(PATH);
        if(!themeDir.exists()){
            if (!themeDir.mkdir()) {
                throw new IOException("Could not create theme directory "+themeDir.getPath());
            }
        }
    }

    /**
     * Given a URL for an resource being served from the theme directory, return the corresponding File
     * (without checking whether it actually exists).
     * Simpler than using play.mvc.Router if the relevant constants are correct.
     * @param url url for the resource
     * @return File for the resource
     */
    public static File fileForUrl(String url) {
        return new File(PATH + StringUtils.substringAfter(url, URL_PREFIX));
    }

    /**
     * Given a File representing a file in the theme directory, return the URL where that file would
     * be served from (without checking whether it actually exists).
     * Simpler than using play.mvc.Router if the relevant constants are correct.
     * @param file a file in the theme directory
     * @return the URL for the file
     */
    public static String urlForFile(File file) {
        return URL_PREFIX +StringUtils.substringAfter(file.getPath(), PATH);
    }

    /**
     * Deletes a file from the theme directory, if it exists.
     * @param filename the name of the file, with no path component
     */
    public static void deleteFile(String filename) {
        final File file = new File(PATH +filename);
        if(file.exists()){
            if (!file.delete()) {
                // Can't delete. Probably not a real problem except for some wasted disk space--at least not yet--but do log it.
                Logger.error("theme-dir: could not delete existing file " + file.getAbsolutePath());
            }
        }
    }
}
