package org.tdl.vireo.export.impl;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.BeanNameAware;
import org.tdl.vireo.export.ExportPackage;
import org.tdl.vireo.export.Packager;
import org.tdl.vireo.model.Attachment;
import org.tdl.vireo.model.AttachmentType;
import org.tdl.vireo.model.Submission;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.tdl.vireo.export.impl.AbstractPackagerImpl.AttachmentPropertyKey.customName;
import static org.tdl.vireo.export.impl.AbstractPackagerImpl.AttachmentPropertyKey.directory;
import static org.tdl.vireo.services.StringVariableReplacement.Variable.FILE_NAME;
import static org.tdl.vireo.services.StringVariableReplacement.applyParameterSubstitutionWithFallback;

/**
 * Abstract packager implementation.
 * 
 * It is expected that there will be many different packager implementations.
 * This class may be used to share common code between these various
 * implementations. The goal is to help remove some of the burden of creating a
 * new packager from scratch.
 * 
 * @author <a href="http://www.scottphillips.com">Scott Phillips</a>
 */
public abstract class AbstractPackagerImpl implements Packager, BeanNameAware {

    /**
     * Supported package types for setPackageType().
     */
    public enum PackageType {
        /** All files will go in a filesystem directory */
        dir,

        /** All files will go in a single .zip archive file */
        zip
    }

    /**
     * These are keys in a java.util.Properties that gets associated with an attachment
     * type via setAttachmentTypeNames().
     */
    public enum AttachmentPropertyKey {
        /**
         * For each applicable attachment, this key's associated Properties value will be
         * customized with StringValueReplacement and then used as the filename for the attachment.
         */
        customName,

        /**
         * For each applicable attachment, this key's associated Properties value will be
         * customized with StringValueReplacement and then used as the directory within the
         * output package where the attachment will be exported.
         */
        directory
    }

	/* Spring injected parameters */
    public String beanName;
    public String displayName;
	public List<AttachmentType> attachmentTypes = new ArrayList<AttachmentType>();
	public LinkedHashMap<String, Properties> attachmentAttributes = new LinkedHashMap<String, Properties>();
	public String entryName = null;
	public PackageType packageType = PackageType.dir;

	@Override
	public String getBeanName() {
		return beanName;
	}

	/**
	 * Spring injected bean name, from beanNameAware
	 * 
	 * @param beanName
	 *            The new bean name of this object.
	 */
	public void setBeanName(String beanName) {
		this.beanName = beanName;
	}

	@Override
	public String getDisplayName() {
		return displayName;
	}

	/**
	 * Spring injected display name.
	 * 
	 * @param displayName
	 *            The new display name of this object.
	 */
	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

    /**
     * (OPTIONAL) Set the attachment types which will be included in the
     * package. Since not all attachments should be deposited, this allows the
     * package to filter which files to include. They must be the exact name
     * (all uppercase) of types listed in the AttachmentType enum.
     *
     * If no types are specified then no attachments will be included.
     *
     * For each attachment type, the associated Properties object contains
     * keys which customize the output of the attachment files. Allowable
     * keys are those listed in the AttachmentPropertyKey enum, or no keys for no
     * customization.
     *
     * @param attachmentTypeNames Name of attachment types to include, each mapped
     *  to customization properties for that type.
     */
    public void setAttachmentTypeNames(LinkedHashMap<String, Properties> attachmentTypeNames) {

        this.attachmentTypes = new ArrayList<AttachmentType>();
        this.attachmentAttributes = new LinkedHashMap<String, Properties>();

        if (attachmentTypeNames != null ) {
            this.attachmentAttributes = attachmentTypeNames;
            for (String name : attachmentTypeNames.keySet()) {
                AttachmentType type = AttachmentType.valueOf(name);
                this.attachmentTypes.add(type);
            }
        }
    }

    /**
     * (OPTIONAL) Set the name of the entry, to be used by the Depositor that
     * ultimately takes the ExportPackage produced here. This can
     * be customized in application-context.xml, otherwise the usual default
     * from the Depositor would be submission_{submission ID}.
     *
     * @param entryName
     * 			The name of the entry.
     */
    public void setEntryName(String entryName) {
        this.entryName = entryName;
    }

    /**
     * (OPTIONAL) Set the type of packaging that will be generated.
     *
     * @param packageType which packaging type to use. Allowable values
     * can be found in the PackageType enum.
     */
    public void setPackageType(PackageType packageType) {
        this.packageType = packageType;
    }

    /**
     * Gets the correct filename for an exported attachment, applying any per-attachment-type customization
     * to the filename.
     * @param attachment the attachment being exported
     * @param parameters variable substitution map for the submission
     * @return the filename the attachment should have, with any customizations applied.
     */
    protected String getAttachmentFileName(Attachment attachment, Map<String, String> parameters) {
        String fileName = attachment.getName();
        final String pattern = attachmentAttributes.get(attachment.getType().name()).getProperty(customName.name());
        if (pattern!=null) {
            parameters.put(FILE_NAME.name(), FilenameUtils.getBaseName(fileName));
            fileName = applyParameterSubstitutionWithFallback(pattern, parameters)+'.'+FilenameUtils.getExtension(fileName);
            parameters.remove(FILE_NAME.name());
        }
        return fileName;
    }

    /**
     * Gets the correct relative directory for an exported attachment to go in, applying any
     * per-attachment-type customization to the directory name.
     * @param attachment the attachment being exported
     * @param parameters variable substitution map for the submission
     * @return the relative directory path the exported attachment should go in, with a trailing /, or "" if the
     * attachment should just be in the root level of the export.
     */
    protected String getAttachmentDirName(Attachment attachment, Map<String, String> parameters) {
        String dirName = "";
        final String pattern = attachmentAttributes.get(attachment.getType().name()).getProperty(directory.name());
        if (pattern!=null) {
            parameters.put(FILE_NAME.name(), FilenameUtils.getBaseName(attachment.getName()));
            dirName = applyParameterSubstitutionWithFallback(pattern, parameters)+File.separator;
            parameters.remove(FILE_NAME.name());
        }
        return dirName;
    }
    
    /**
     * For each applicable attachment, copy it to a customized entry in a zip archive. The file to
     * write may have a customized name and/or go in a customized subdirectory in the zip archive.
     * @param zos the zip archive to write to
     * @param submission the submission being packaged
     * @param parameters string replacement parameters corresponding to submission, but already extracted.
     * @throws IOException if something couldn't be copied
     */
    protected void writeAttachmentsToZip(ZipOutputStream zos, Submission submission, Map<String, String> parameters) throws IOException {
        for(Attachment attachment : submission.getAttachments()) {
            // Do we include this attachment type?
            if (!attachmentTypes.contains(attachment.getType())) {
                continue;
            }

            // Process custom options for filename and file directory
            String fileName = getAttachmentFileName(attachment, parameters);
            String dirName = getAttachmentDirName(attachment, parameters);

            // Copy file from attachment into zip archive
            // (Zip entries include directory name, so we don't have to do some sort of mkdir equivalent
            //  first. It's allowed to make an entry for the directory first, but it doesn't seem required.)
            ZipEntry ze = new ZipEntry(dirName+fileName);
            zos.putNextEntry(ze);

            FileInputStream in = null;
            try {
                in = new FileInputStream(attachment.getFile());
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    zos.write(buf, 0, len);
                }
            } finally {
                if (in != null) { in.close(); }
            }

            zos.closeEntry();
        }
    }

    /**
     * For each applicable attachment, copy it to a customized file in a directory. The file to
     * write may have a customized name and/or go in a customized subdirectory within the dir.
     * @param pkg the directory to write to
     * @param submission the submission being packaged
     * @param parameters string replacement parameters corresponding to submission, but already extracted.
     * @throws IOException if something couldn't be copied
     */
    protected void writeAttachmentsToDir(File pkg, Submission submission, Map<String, String> parameters) throws IOException {
        for(Attachment attachment : submission.getAttachments()) {
            // Do we include this attachment type?
            if (!attachmentTypes.contains(attachment.getType())) {
                continue;
            }

            // Process custom options for filename and file directory
            String fileName = getAttachmentFileName(attachment, parameters);
            String dirName = getAttachmentDirName(attachment, parameters);

            // Copy file from attachment into package directory
            File exportFileSubdir= new File(pkg.getPath()+File.separator+dirName);
            exportFileSubdir.mkdir(); // make sure it exists, if it didn't already.
            File exportFile = new File(exportFileSubdir, fileName);
            FileUtils.copyFile(attachment.getFile(), exportFile);
        }
    }

	/**
	 * The package interface.
	 *
	 * This is the class that represents the actual package. It contains the
	 * file we've built along with some basic metadata.
	 *
	 */
	public static abstract class AbstractExportPackage implements ExportPackage {

		// Members
		public final Submission submission;
		public final File file;
		public final String entryName;

		public AbstractExportPackage(Submission submission, File file, String entryName) {
			this.submission = submission;
			this.file = file;
			this.entryName = entryName;
		}

		@Override
		public Submission getSubmission() {
			return submission;
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
		public void finalize() throws Throwable {
			delete();
            super.finalize();
		}

	}
}
