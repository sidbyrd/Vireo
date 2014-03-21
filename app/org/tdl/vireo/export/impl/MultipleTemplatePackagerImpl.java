package org.tdl.vireo.export.impl;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.tdl.vireo.export.ExportPackage;
import org.tdl.vireo.model.Attachment;
import org.tdl.vireo.model.AttachmentType;
import org.tdl.vireo.model.PersonRepository;
import org.tdl.vireo.model.SettingsRepository;
import org.tdl.vireo.model.Submission;
import org.tdl.vireo.model.SubmissionRepository;
import org.tdl.vireo.proquest.ProquestVocabularyRepository;
import org.tdl.vireo.services.StringVariableReplacement;

import play.Play;
import play.templates.Template;
import play.templates.TemplateLoader;
import play.vfs.VirtualFile;

/**
 * Generic packager that uses the standard play templating system to generate
 * files. Unlike the TemplatePackagerImpl, this class is capable of generating
 * multiple "manifests"--they're just called templates here. Each one is a
 * groovy template that can produce a file for the export.
 * 
 * @author <a href="http://www.scottphillips.com">Scott Phillips</a>
 * @author Micah Cooper
 * @author Jeremy Huff
 */
public class MultipleTemplatePackagerImpl extends AbstractPackagerImpl {
	
	/* Spring injected paramaters */
	public Map<String,VirtualFile> templates = new HashMap<String,VirtualFile>();
	public String format = null;
	public Map<String,Object> templateArguments = new HashMap<String,Object>();

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
	 * (REQUIRED) Configure the multiple templates that will be generated for
	 * this packager format. The map will contain file names, to template paths.
	 * 
	 * @param templates
	 *            A map from filenames to templates paths.
	 */
	public void setTemplatePaths(Map<String, String> templates) {

		for (String name : templates.keySet()) {
			
			String templatePath = templates.get(name);
			VirtualFile templateFile = Play.getVirtualFile(templatePath);
			
			if ( templatePath == null || !templateFile.exists())
				throw new IllegalArgumentException("Template '"+templatePath+"' does not exist.");
			
			this.templates.put(name, templateFile);
		
			
			
		}
	}

	/**
	 * (REQUIRED) Set the format descriptor of this package. This will vary
	 * widely between package formats. It is typically a URL to an XML schema
	 * describing the format of the manifest file. In vireo 1, the format was
	 * always "http://purl.org/net/sword-types/METSDSpaceSIP".
	 * 
	 * @param format 
	 * 			The format of the package generated.
	 */
	public void setFormat(String format) {
		this.format = format;
	}

	/**
	 * (OPTIONAL) Set a list of arguments which may be accessed as variables in
	 * the template syntax. The variable "sub" will always be the submission
	 * which is being packaged.
	 * 
	 * @param arguments
	 *            Template arguments
	 */
	public void setTemplateArguments(Map<String,Object> arguments) {
		templateArguments = arguments;
	}
	
	
	@Override
	public ExportPackage generatePackage(Submission submission) {

		// Check that we have everything that we need.
		if (submission == null || submission.getId() == null)
			throw new IllegalArgumentException("Unable to generate a package because the submission is null, or has not been persisted.");

		if (templates == null || templates.isEmpty())
			throw new IllegalStateException("Unable to generate package because no template file exists.");

		if (format == null)
			throw new IllegalStateException("Unable to generate package because no package format name has been defined.");

		try {
			
			//Set String Replacement Parameters
			Map<String, String> parameters = new HashMap<String, String>();
			parameters = StringVariableReplacement.setParameters(submission);
			
			//Customize Entry Name
			String customEntryName = StringVariableReplacement.applyParameterSubstitution(entryName, parameters);
			
			File pkg;
			
			//Check the package type set in the spring configuration
            if (packageType==PackageType.zip) {
				pkg = File.createTempFile("multi-template-export-", ".zip");

                ZipOutputStream zos = null;
                FileInputStream in = null;
                try {
                    zos = new ZipOutputStream(new FileOutputStream(pkg));
                    byte[] buf = new byte[1024];
                    int len;

                    // Generate each of the export files
                    for (String name : templates.keySet()) {
                        VirtualFile templateFile = templates.get(name);

                        //customize after retrieving the template file
                        name = StringVariableReplacement.applyParameterSubstitution(name, parameters);

                        Map<String, Object> templateBinding = new HashMap<String,Object>();
                        templateBinding.put("sub", submission);
                        templateBinding.put("personRepo", personRepo);
                        templateBinding.put("subRepo", subRepo);
                        templateBinding.put("settingRepo", settingRepo);
                        templateBinding.put("proquestRepo", proquestRepo);
                        templateBinding.put("format", format);
                        templateBinding.put("entryName", customEntryName);
                        templateBinding.put("attachmentTypes", attachmentTypes);
                        templateBinding.put("template", name);
                        templateBinding.put("templates", templates);
                        if (templateArguments != null) {
                            templateBinding.putAll(templateArguments);
                        }

                        Template template = TemplateLoader.load(templateFile);
                        String rendered = template.render(templateBinding);

                        // Copy the manifest into the zip archive as a new entry
                        ZipEntry ze = new ZipEntry(name);
                        zos.putNextEntry(ze);

                        InputStream manifestStream = IOUtils.toInputStream(rendered, "UTF-8");
                        while ((len = manifestStream.read(buf)) > 0) {
                            zos.write(buf, 0, len);
                        }
                        zos.closeEntry();
                    }

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
                        ZipEntry ze = new ZipEntry(dirName+fileName);
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
                pkg = File.createTempFile("multi-template-export-", ".dir"); // actually a directory
                pkg.delete();
                pkg.mkdir();

				// Generate each of the export files
				for (String name : templates.keySet()) {
					VirtualFile templateFile = templates.get(name);

                    name = StringVariableReplacement.applyParameterSubstitution(name, parameters);

					Map<String, Object> templateBinding = new HashMap<String,Object>();
					templateBinding.put("sub", submission);
					templateBinding.put("personRepo", personRepo);
					templateBinding.put("subRepo", subRepo);
					templateBinding.put("settingRepo", settingRepo);
					templateBinding.put("proquestRepo", proquestRepo);
					templateBinding.put("format", format);
					templateBinding.put("entryName", customEntryName);
					templateBinding.put("attachmentTypes", attachmentTypes);
					templateBinding.put("template", name);
					templateBinding.put("templates", templates);
					if (templateArguments != null) {
						templateBinding.putAll(templateArguments);
                    }

					Template template = TemplateLoader.load(templateFile);
					String rendered = template.render(templateBinding);

                    // Copy the manifest
                    File manifestFile = new File(pkg, name);
                    FileUtils.writeStringToFile(manifestFile, rendered);
                }

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
                throw new RuntimeException("FilePackager: unsupported package type '"+packageType+'\'');
            }

			// Create the actual package!
			return new TemplatePackage(submission, null, format, pkg, customEntryName);
			
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
