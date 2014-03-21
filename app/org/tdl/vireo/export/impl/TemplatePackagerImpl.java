package org.tdl.vireo.export.impl;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.tools.ant.types.resources.Files;
import org.tdl.vireo.export.ExportPackage;
import org.tdl.vireo.model.Attachment;
import org.tdl.vireo.model.AttachmentType;
import org.tdl.vireo.model.PersonRepository;
import org.tdl.vireo.model.SettingsRepository;
import org.tdl.vireo.model.Submission;
import org.tdl.vireo.model.SubmissionRepository;
import org.tdl.vireo.proquest.ProquestVocabularyRepository;
import org.tdl.vireo.services.StringVariableReplacement;

import play.Logger;
import play.Play;
import play.exceptions.TemplateNotFoundException;
import play.modules.spring.Spring;
import play.templates.Template;
import play.templates.TemplateLoader;
import play.vfs.VirtualFile;

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
public class TemplatePackagerImpl extends AbstractPackagerImpl {
		
	/* Spring injected parameters */
	public VirtualFile templateFile = null;
	public String mimeType = null;
	public String format = null;
	public String manifestName = "mets.xml";
	public Map<String,Object> templateArguments = null;

	// Repositories to be injected into template for convenience
	public PersonRepository personRepo;
	public SubmissionRepository subRepo;
	public SettingsRepository settingRepo;
	public ProquestVocabularyRepository proquestRepo;
	
	/**
	 * Inject the repository of people and their preferences.
	 * 
	 * @param personRepo
	 *            Person Repository
	 */
	public void setPersonRepository(PersonRepository personRepo) {
		this.personRepo = personRepo;
	}

	/**
	 * Inject the repository of submission and their related objects: committee
	 * members, attachments, custom action values.
	 * 
	 * @param subRepo
	 *            Submission Repository
	 */
	public void setSubmissionRepository(SubmissionRepository subRepo) {
		this.subRepo = subRepo;
	}

	/**
	 * Inject the repository of system-wide settings & configuration.
	 * 
	 * @param settingRepo
	 *            Settings Repository
	 */
	public void setSettingsRepository(SettingsRepository settingRepo) {
		this.settingRepo = settingRepo;
	}
		
	/**
	 * Inject the repository of proquest vocabulary.
	 * 
	 * @param proquestRepo
	 *            Proquest Vocabulary Repository
	 */
	public void setProquestVocabularyRepository(ProquestVocabularyRepository proquestRepo) {
		this.proquestRepo = proquestRepo;
	}

	/**
	 * (REQUIRED) Set the template for generating the manifest file. This
	 * parameter is always required.
	 * 
	 * @param templatePath
	 *            The path (relative to the application's directory)
	 */
	public void setManifestTemplatePath(String templatePath) {
		VirtualFile templateFile = Play.getVirtualFile(templatePath);

		// Blow up at construction time if template not found.
		if (templateFile == null || !templateFile.exists()) 
            throw new TemplateNotFoundException(templatePath);
		
		this.templateFile = templateFile;
	}

	/**
	 * (OPTIONAL) Set the mimetype of the resulting package generated. For most
	 * cases the mimetype should be null, meaning that there are multiple files
	 * generated contained within a directory. However, if the packager format
	 * generates a single file, then the mimetype should be set to the type of
	 * that file. Such as "text/xml". By default if no mimetype is set, then
	 * null is assumed.
	 * 
	 * @param mimeType
	 *            The mime type of the package.
	 */
	public void setMimeType(String mimeType) {
		this.mimeType = mimeType;
	}

	/**
	 * (REQUIRED) Set the format descriptor of this package. This will vary
	 * widely between package formats. It is typically a URL to an XML schema
	 * describing the format of the mainifest file. In vireo 1, the format was
	 * always "http://purl.org/net/sword-types/METSDSpaceSIP".
	 * 
	 * @param format 
	 * 			The format of the package generated.
	 */
	public void setFormat(String format) {
		this.format = format;
	}
	
	/**
	 * (OPTIONAL) Set the name of the manifest file file. This will vary between
	 * package formats but some popular ones are "mets.xml", or "mods.xml". See
	 * the specific format to understand what the file is going to be called.
	 * 
	 * If no manifest name is set, then "mets.xml" will be used.
	 * 
	 * @param manifestName
	 *            The name of the manifest file.
	 */
	public void setManifestName(String manifestName) {
		this.manifestName = manifestName;
	}
	
	/**
	 * (OPTIONAL) Set a list of arguments which may be accessed as variables in
	 * the template syntax. The variable "sub" will always be the submission
	 * which is being packaged.
	 * 
	 * @param arguments
	 *            Template arguments
	 */
	public void setManifestTemplateArguments(Map<String,Object> arguments) {
		templateArguments = arguments;
	}
	
	
	@Override
	public ExportPackage generatePackage(Submission submission) {
		
		// Check that we have everything that we need.
		if (submission == null || submission.getId() == null)
			throw new IllegalArgumentException("Unable to generate a package because the submission is null, or has not been persisted.");
		
		if (templateFile == null)
			throw new IllegalStateException("Unable to generate package because no template file exists.");
		
		if (manifestName == null)
			throw new IllegalStateException("Unable to generate package because no manifest name has been defined.");
		
		if (format == null)
			throw new IllegalStateException("Unable to generate package because no package format name has been defined.");
		
		try {
			
			// Set String Replacement Parameters
			Map<String, String> parameters = new HashMap<String, String>();
			parameters = StringVariableReplacement.setParameters(submission);
			
			// Customize Manifest Name
			String customManifestName = StringVariableReplacement.applyParameterSubstitution(manifestName, parameters);
			
			// Customize Entry Name
			String customEntryName = StringVariableReplacement.applyParameterSubstitution(entryName, parameters);
			
			// Generate the manifest.
			Map<String, Object> templateBinding = new HashMap<String,Object>();
			templateBinding.put("sub", submission);
			templateBinding.put("personRepo", personRepo);
			templateBinding.put("subRepo",subRepo);
			templateBinding.put("settingRepo",settingRepo);
			templateBinding.put("proquestRepo",proquestRepo);
			templateBinding.put("packageType", packageType.name());
			templateBinding.put("manifestName",customManifestName);
			templateBinding.put("entryName", customEntryName);
			templateBinding.put("format", format);
			templateBinding.put("mimeType", mimeType);
			templateBinding.put("attachmentTypes", attachmentTypes);
			if (templateArguments != null)
				templateBinding.putAll(templateArguments);
			Template template = TemplateLoader.load(templateFile);
			String manifest = template.render(templateBinding);
			
			File pkg = null;
			
			//Check the package type set in the spring configuration
            if (packageType==PackageType.zip) {
				pkg = File.createTempFile("template-export-", ".zip");

                ZipOutputStream zos = null;
                FileInputStream in = null;
                try {
                    zos = new ZipOutputStream(new FileOutputStream(pkg));
                    byte[] buf = new byte[1024];
                    int len;

                    // Copy the manifest into the zip archive as a new entry
					ZipEntry ze = new ZipEntry(customManifestName);
					zos.putNextEntry(ze);

                    InputStream manifestStream = IOUtils.toInputStream(manifest, "UTF-8");
                    while ((len = manifestStream.read(buf)) > 0) {
                        zos.write(buf, 0, len);
                    }
                    zos.closeEntry();

                    // Add all the attachments
                    for(Attachment attachment : submission.getAttachments()) {
                        // Do we include this type?
                        if (!attachmentTypes.contains(attachment.getType())) {
                            continue;
                        }

                        // Process custom options for filename and file directory
                        String fileName = getAttachmentFileName(attachment, parameters);
                        String dirName = getAttachmentDirectoryName(attachment, parameters);

                        // Copy file from attachment into zip archive
                        ze = new ZipEntry(dirName+fileName);
                        zos.putNextEntry(ze);

                        in = new FileInputStream(attachment.getFile());
                        while ((len = in.read(buf)) > 0) {
                            zos.write(buf, 0, len);
                        }
                        in.close();
                        in=null;

                        zos.closeEntry();
                    }
                } finally {
                    if (in!=null) { IOUtils.closeQuietly(in); }
                    if (zos!=null) { IOUtils.closeQuietly(zos); }// also closes wrapped fos
                }
            } else if (packageType==PackageType.dir) {
                if (!attachmentTypes.isEmpty()) {
                    pkg = File.createTempFile("template-export-", ".dir"); // actually a directory
                    pkg.delete();
                    pkg.mkdir();

					// Copy the manifest
					File manifestFile = new File(pkg, customManifestName);
					FileUtils.writeStringToFile(manifestFile, manifest);

                    // Add all the attachments
                    for(Attachment attachment : submission.getAttachments()) {
                        // Do we include this type?
                        if (!attachmentTypes.contains(attachment.getType())) {
                            continue;
                        }

                        // Process custom options for filename and file directory
                        String fileName = getAttachmentFileName(attachment, parameters);
                        String dirName = getAttachmentDirectoryName(attachment, parameters);

                        // Copy file from attachment into package directory
                        File exportFile = new File(pkg.getPath()+dirName, fileName);
                        FileUtils.copyFile(attachment.getFile(), exportFile);
                    }
                } else {
					// There's only one file, so export as a single file.
					String extension = FilenameUtils.getExtension(customManifestName);
					if (extension.length() > 0)
						extension = "."+extension;

					pkg = File.createTempFile("template-export", extension);
					FileUtils.writeStringToFile(pkg, manifest);
                }
            } else {
                throw new RuntimeException("FilePackager: unsupported package type '"+packageType+'\'');
            }

			// Create the actual package!
			return new TemplatePackage(submission, mimeType, format, pkg, customEntryName);

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
	public static class TemplatePackage implements ExportPackage {

		// Members
		public final Submission submission;
		public final String mimeType;
		public final String format;
		public final File file;
		public final String entryName;

		public TemplatePackage(Submission submission, String mimeType, String format, File file, String entryName) {
			this.submission = submission;
			this.mimeType = mimeType;
			this.format = format;
			this.file = file;
			this.entryName = entryName;
		}

		@Override
		public Submission getSubmission() {
			return submission;
		}
		
		@Override
		public String getMimeType() {
			return mimeType;
		}

		@Override
		public String getFormat() {
			return format;
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
