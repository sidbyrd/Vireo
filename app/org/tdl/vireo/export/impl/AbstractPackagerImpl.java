package org.tdl.vireo.export.impl;

import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.BeanNameAware;
import org.tdl.vireo.export.Packager;
import org.tdl.vireo.model.Attachment;
import org.tdl.vireo.model.AttachmentType;
import org.tdl.vireo.services.StringVariableReplacement;

import java.io.File;
import java.util.*;

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

    /** Supported package types. Spring makes this easy. */
    public enum PackageType {
        /** all files in a single filesystem directory */
        dir,
        /** all files in a single .zip archive file */
        zip
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
     * @param attachmentTypeNames
     *            List of attachment types to include.
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
     * (OPTIONAL) Set the name of the entry. The default will be
     * submission_{submission ID}, but this can be customized
     * in application-context.xml.
     *
     * @param entryName
     * 			The name of the entry.
     */
    public void setEntryName(String entryName) {
        this.entryName = entryName;
    }

    /**
     * (OPTIONAL) Inject the package type for the export.
     *
     * @param packageType
     * 			Package Type (directory, zip, etc)
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
    String getAttachmentFileName(Attachment attachment, Map<String, String> parameters) {
        String fileName = attachment.getName();
        final String pattern = attachmentAttributes.get(attachment.getType().name()).getProperty("customName");
        if (pattern!=null) {
            final String extension = FilenameUtils.getExtension(fileName);
            if (!parameters.containsKey(StringVariableReplacement.Variable.FILE_NAME.name())) {
                String baseName = FilenameUtils.getExtension(fileName);
                parameters.put(StringVariableReplacement.Variable.FILE_NAME.name(), baseName);
            }
            fileName = applyParameterSubstitutionWithFallback(pattern, parameters)+'.'+extension;
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
    String getAttachmentDirectoryName(Attachment attachment, Map<String, String> parameters) {
        String dirName = "";
        final String pattern = attachmentAttributes.get(attachment.getType().name()).getProperty("directory");
        if (pattern!=null) {
            if (!parameters.containsKey(StringVariableReplacement.Variable.FILE_NAME.name())) {
                String baseName = FilenameUtils.getExtension(attachment.getName());
                parameters.put(StringVariableReplacement.Variable.FILE_NAME.name(), baseName);
            }
            dirName = applyParameterSubstitutionWithFallback(pattern, parameters)+ File.separator;
        }
        return dirName;
    }
}
