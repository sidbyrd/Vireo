package org.tdl.vireo.export.impl;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.tdl.vireo.export.ExportPackage;
import org.tdl.vireo.model.Attachment;
import org.tdl.vireo.model.AttachmentType;
import org.tdl.vireo.model.Submission;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.tdl.vireo.services.StringVariableReplacement.*;

/**
 * Generic packager that uses a the standard play templating system to generate
 * manifests for packages. Each packaged produced will consist of a
 * manifest file, along with a series of files. This packaged is zipped together
 * into a single bundle ready for deposit.
 * 
 * The values that define what format, which files, etc, are all injected by
 * spring. This allows for many different package formats to be created by just
 * adding a new spring bean definition. See each of the injection methods below
 * for a description of the various injectable settings.
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
			throw new IllegalArgumentException("Unable to generate a package because the submission is null, or has not been persisted.");
        }
		
        // Set String replacement parameters
        Map<String, String> parameters = setParameters(submission);

		try {
			File pkg = null;
			
			//Check the package type set in the spring configuration
            if (packageType==PackageType.zip) {
				pkg = File.createTempFile("file-export-", ".zip");
                ZipOutputStream zos = null;
                try {
                    zos = new ZipOutputStream(new FileOutputStream(pkg));
                    writeAttachmentsToZip(zos, submission, parameters);
                } finally {
                    if (zos!=null) { IOUtils.closeQuietly(zos); }// also closes wrapped fos
                }
            } else if (packageType==PackageType.dir) {
				pkg = File.createTempFile("file-export-", ".dir"); // actually a directory
                pkg.delete();
                pkg.mkdir();

                writeAttachmentsToDir(pkg, submission, parameters);
            } else {
                throw new RuntimeException("FilePackager: unsupported package type '"+packageType+'\'');
            }

			// Create the package
            String customEntryName = applyParameterSubstitutionWithFallback(entryName, parameters);
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
	 * 
	 */
	public static class FilePackage implements ExportPackage {

		// Members
		public final Submission submission;
		public final File file;
		public final String entryName;

		public FilePackage(Submission submission, File file, String entryName) {
			this.submission = submission;
			this.file = file;
			this.entryName = entryName;
		}

		@Override
		public Submission getSubmission() {
			return submission;
		}
		
		@Override
		public String getMimeType() {
			return null;
		}

		@Override
		public String getFormat() {
			return "File System";
		}

		@Override
		public File getFile() {
			return file;
		}
		
		@Override
		public String getEntryName() {
			return entryName;
		}

		@Override
		public void delete() {
			if (file != null && file.exists()) {

				if (file.isDirectory()) {
					try {
						FileUtils.deleteDirectory(file);
					} catch (IOException ioe) {
						throw new RuntimeException("Unable to cleanup export package: " + file.getAbsolutePath(),ioe);
					}
				} else {
					file.delete();
				}

			}
		}

		/**
		 * If we do get garbage collected, delete the file resource.
		 */
		public void finalize() {
			delete();
		}

	}
	
}
