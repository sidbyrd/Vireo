package org.tdl.vireo.export.impl;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.tdl.vireo.export.ExportPackage;
import org.tdl.vireo.model.Submission;
import org.tdl.vireo.services.StringVariableReplacement;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.zip.ZipOutputStream;

import static org.tdl.vireo.export.impl.AbstractPackagerImpl.PackageType.dir;
import static org.tdl.vireo.export.impl.AbstractPackagerImpl.PackageType.zip;

/**
 * Generic packager that exports attachment files only without any manifest. Each
 * package produced will be either a directory, or a zip archive of a directory.
 * 
 * The values that define what format, which files, etc, are all injected by
 * spring. This allows for many different package formats to be created by just
 * adding a new spring bean definition. See each of the injection methods below
 * and in AbstractPackager for a description of the various injectable settings.
 * 
 * @author <a href="http://www.scottphillips.com">Scott Phillips</a>
 * @author Micah Cooper
 * @author Jeremy Huff
 */
public class FilePackagerImpl extends AbstractPackagerImpl {

	@Override
	public ExportPackage generatePackage(Submission submission) {
		if (attachmentTypes.isEmpty()) {
			throw new IllegalArgumentException("Unable to generate package because no attachment types have been defined.");
		}
		
		// Check that we have everything that we need.
		if (submission == null || submission.getId() == null) {
			throw new IllegalArgumentException("Unable to generate package because the submission is null or has not been persisted.");
        }
		
        // Set string replacement parameters
        Map<String, String> parameters = StringVariableReplacement.setParameters(submission);
        // Customize entry name
        String customEntryName = StringVariableReplacement.applyParameterSubstitutionWithFallback(entryName, parameters);

		try {
			File pkg;
			
			// Produce the correct output format.
            if (packageType==dir) {
                // Create output directory
				pkg = File.createTempFile("file-export-", ".dir");
                FileUtils.deleteQuietly(pkg);
                if (!pkg.mkdir()) {
                    throw new IOException("Could not create package directory '"+pkg.getPath()+'\'');
                }

                writeAttachmentsToDir(pkg, submission, parameters);

            } else if (packageType==zip) {
                // Create output zip archive
				pkg = File.createTempFile("file-export-", ".zip");
                ZipOutputStream zos = null;
                try {
                    zos = new ZipOutputStream(new FileOutputStream(pkg));
                    writeAttachmentsToZip(zos, submission, parameters);
                } finally {
                    if (zos!=null) { IOUtils.closeQuietly(zos); } // also closes wrapped fos
                }

            } else {
                throw new RuntimeException("FilePackager: unsupported package type '"+packageType+'\'');
            }

			// Create the package
			return new FilePackage(submission, pkg, customEntryName);
			
		} catch (IOException ioe) {
			throw new RuntimeException("Unable to generate package",ioe);
		}
	}
	
	
	/**
	 * The package interface.
	 * 
	 * This is the class that represents the actual package. It contains the
	 * file we've built along with some basic metadata.
	 */
	public static class FilePackage extends AbstractExportPackage implements ExportPackage {

		public FilePackage(Submission submission, File file, String entryName) {
			super(submission, file, entryName);
		}

		@Override
		public String getMimeType() {
			return null;
		}

		@Override
		public String getFormat() {
			return "File System";
		}
	}
	
}
