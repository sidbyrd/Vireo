package org.tdl.vireo.services;

import org.apache.commons.io.FilenameUtils;
import play.Play;

import java.io.*;

/**
 * A catch-all class for various Vireo utilities
 * 
 * @author Alexey Maslov
 */
public class Utilities {

	private static final String[] CONTROL_RANGES = {
		"\u0000-\u0009", // CO Control (including: Bell, Backspace, and Horizontal Tab)
		"\u000B-\u000C", // CO Control (Line Tab and Form Feed)
		"\u000E-\u001F", // CO Control (including: Escape)
		"\u007F",        // CO Control (Delete Character)
		"\u0080-\u009F"  // C1 Control
	};
	
	/**
	 * Scrub UNICODE control characters out of the provided string, deleteing them 
	 * @param input
	 * @return
	 */
	public static String scrubControl(String input) {
		return scrubControl(input, "");
	}
	
	/**
	 * Scrub UNICODE control characters out of the provided string, replacing them with the specified string 
	 * @param input
	 * @param replace
	 * @return
	 */
	public static String scrubControl(String input, String replace) {
		
		if (input == null)
			return null;
		if ("".equals(input))
			return "";
		
		return input.replaceAll("[" + CONTROL_RANGES[0] + CONTROL_RANGES[1] + 
				CONTROL_RANGES[2] + CONTROL_RANGES[3] + CONTROL_RANGES[4] + "]", replace);
	}

    /**
     * Extract the file from the jar and place it in a temporary location for the test to operate from.
     *
     * @param filePath The path, relative to the classpath, of the file to reference.
     * @return A Java File object reference.
     * @throws java.io.IOException
     */
    public static File getResourceFile(String filePath) throws IOException {
        // use the original file's correct extension
        return getResourceFileWithExtension(filePath, FilenameUtils.getExtension(filePath));
    }

    /**
     * Extract the file from the jar and place it in a temporary location for the test to operate from.
     * Manually override the file extension that the temp file will get.
     *
     * @param filePath The path, relative to the classpath, of the file to reference.
     * @param extension the file extension for the created temp file
     * @return A Java File object reference.
     * @throws IOException
     */
    public static File getResourceFileWithExtension(String filePath, String extension) throws IOException {
        File file = File.createTempFile("resource", '.'+extension);
        file.deleteOnExit();

        // While we're packaged by play we have to ask Play for the inputstream instead of the classloader.
        //InputStream is = DSpaceCSVIngestServiceImplTests.class
        //		.getResourceAsStream(filePath);
        InputStream is = Play.classloader.getResourceAsStream(filePath);
        OutputStream os = new FileOutputStream(file);
        try {
            // Copy the file out of the jar into a temporary space.
            byte[] buffer = new byte[1024];
            int len;
            while ((len = is.read(buffer)) > 0) {
                os.write(buffer, 0, len);
            }
        } finally {
            is.close();
            os.close();
        }

        return file;
    }

    /**
     * Creates and returns a temp file with empty contents of the requested size
     * @param filesize how many bytes of empty to put in the file
     * @param extension the file extension to give the new file
     * @return temp file of given size
     * @throws IOException if couldn't be created
     */
    public static File blankFileWithSize(int filesize, String extension) throws IOException {
        File file = File.createTempFile("blank", '.'+extension);
        file.deleteOnExit();

        OutputStream os = new FileOutputStream(file);
        try {
            byte[] buffer = new byte[1024];
            for (int pos=0; pos < filesize; pos += 1024) {
                os.write(buffer, 0, Math.min(1024, filesize-pos));
            }
        } finally {
            os.close();
        }

        return file;
    }
}
