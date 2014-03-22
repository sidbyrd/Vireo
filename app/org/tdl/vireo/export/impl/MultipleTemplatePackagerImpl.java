package org.tdl.vireo.export.impl;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.tdl.vireo.export.ExportPackage;
import org.tdl.vireo.model.Attachment;
import org.tdl.vireo.model.PersonRepository;
import org.tdl.vireo.model.SettingsRepository;
import org.tdl.vireo.model.Submission;
import org.tdl.vireo.model.SubmissionRepository;
import org.tdl.vireo.proquest.ProquestVocabularyRepository;
import org.tdl.vireo.services.StringVariableReplacement;

import play.Play;
import play.exceptions.TemplateNotFoundException;
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
	Map<String,VirtualFile> templates = new HashMap<String,VirtualFile>(); // injected via setTemplatePaths()
	public String format = null;
	public Map<String,Object> templateArguments = new HashMap<String,Object>();

	// Repositories to be injected into template for convenience
	public PersonRepository personRepo;
	public SubmissionRepository subRepo;
	public SettingsRepository settingRepo;
	public ProquestVocabularyRepository proquestRepo;

    // this should generally stay null if there are multiple output files.
    public String mimeType = null;

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
			
    		// Blow up at construction time if template not found.
			if ( templatePath == null || !templateFile.exists()) {
				throw new TemplateNotFoundException(templatePath);
            }
			
			this.templates.put(name, templateFile);
		}
	}

    /**
     * Accessor for something that is really an implementation detail.
     * Included for easy testing.
     * @return template names => template filenames
     */
    public Map<String, VirtualFile> getTemplates() {
        return templates;
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

    /**
     * Executes the named template for a specific submission, its string replacement parameters, and its
     * already-customized entry name.
     * Provides an opportunity for subclasses to override the template name as bound to the 'manifestName'
     * template parameter, and in the returned filename.
     * @param templateName the name of the template to execute
     * @param submission the submission to customize for
     * @param customEntryName the entry name to use. Basically string replacement on this.entryName, but it's
     * already done by the expected caller, so save having to duplicate the effort.
     * @param parameters string replacement parameters corresponding to submission, but already extracted.
     * @return two strings. index 0 is the filename to use for the rendered template (which can be overridden
     * by subclasses), and index 1 is the rendered template contents.
     */
    protected String[] renderTemplate(String templateName, Submission submission, String customEntryName, Map<String, String> parameters) {
        VirtualFile templateFile = templates.get(templateName);

        //customize after retrieving the template file
        String customTemplateName = StringVariableReplacement.applyParameterSubstitutionWithFallback(templateName, parameters);

        Map<String, Object> templateBinding = new HashMap<String,Object>();
        templateBinding.put("sub", submission);
        templateBinding.put("personRepo", personRepo);
        templateBinding.put("subRepo", subRepo);
        templateBinding.put("settingRepo", settingRepo);
        templateBinding.put("proquestRepo", proquestRepo);
        templateBinding.put("packageType", packageType.name());
        templateBinding.put("format", format);
        templateBinding.put("mimeType", mimeType);
        templateBinding.put("entryName", customEntryName);
        templateBinding.put("attachmentTypes", attachmentTypes);
        templateBinding.put("manifestName", customTemplateName);
        templateBinding.put("template", customTemplateName);
        templateBinding.put("templates", templates);
        if (templateArguments != null) {
            templateBinding.putAll(templateArguments);
        }

        Template template = TemplateLoader.load(templateFile);
        String[] results = new String[2];
        results[0] = customTemplateName;
        results[1] = template.render(templateBinding);
        return results;
    }

    /**
     * For each configured template, render it and write it to an entry in a zip archive.
     * @param zos the zip archive to write to
     * @param submission the submission being packaged
     * @param customEntryName the entry name to use. Basically string replacement on this.entryName, but it's
     * already done by the expected caller, so save having to duplicate the effort.
     * @param parameters string replacement parameters corresponding to submission, but already extracted.
     * @throws IOException if something couldn't be copied
     */
    protected void writeTemplatesToZip(ZipOutputStream zos, Submission submission, String customEntryName, Map<String, String> parameters) throws IOException {
        for (String templateName : templates.keySet()) {
            final String[] templateResults = renderTemplate(templateName, submission, customEntryName, parameters);
            templateName = templateResults[0]; // customized
            final String templateRendered = templateResults[1];

            // Copy the manifest into the zip archive as a new entry
            ZipEntry ze = new ZipEntry(templateName);
            zos.putNextEntry(ze);
            InputStream manifestStream = IOUtils.toInputStream(templateRendered, "UTF-8");
            byte[] buf = new byte[1024];
            int len;
            while ((len = manifestStream.read(buf)) > 0) {
                zos.write(buf, 0, len);
            }
            zos.closeEntry();
        }
    }

    /**
     * For each configured template, render it and write it to a file in a directory.
     * @param pkg the directory to write to
     * @param submission the submission being packaged
     * @param customEntryName the entry name to use. Basically string replacement on this.entryName, but it's
     * already done by the expected caller, so save having to duplicate the effort.
     * @param parameters string replacement parameters corresponding to submission, but already extracted.
     * @throws IOException if something couldn't be copied
     */
    protected void writeTemplatesToDir(File pkg, Submission submission, String customEntryName, Map<String, String> parameters) throws IOException {
        for (String templateName : templates.keySet()) {
            final String[] templateResults = renderTemplate(templateName, submission, customEntryName, parameters);
            templateName = templateResults[0]; // customized
            final String templateRendered = templateResults[1];

            // Copy the manifest into the pkg dir
            File manifestFile = new File(pkg, templateName);
            FileUtils.writeStringToFile(manifestFile, templateRendered);
        }
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

        // Set String Replacement Parameters
        Map<String, String> parameters = StringVariableReplacement.setParameters(submission);
        // Customize Entry Name
        String customEntryName = StringVariableReplacement.applyParameterSubstitutionWithFallback(entryName, parameters);

		try {
			File pkg;
			
			// Check the package type set in the spring configuration
            if (packageType==PackageType.zip) {
                // Create output zip archive
				pkg = File.createTempFile("template-export-", ".zip");
                ZipOutputStream zos = null;
                try {
                    zos = new ZipOutputStream(new FileOutputStream(pkg));
                    writeTemplatesToZip(zos, submission, customEntryName, parameters);
                    writeAttachmentsToZip(zos, submission, parameters);
                } finally {
                    if (zos!=null) { IOUtils.closeQuietly(zos); } // also closes wrapped fos
                }
            } else if (packageType==PackageType.dir) {
                if (attachmentTypes.isEmpty() && templates.size()==1) {
					// There's only one template and nothing else, so export as a single file.
                    final String[] templateResults = renderTemplate(templates.keySet().iterator().next(), submission, customEntryName, parameters);
                    String templateName = templateResults[0];
                    final String templateRendered = templateResults[1];

                    // It's a file, not a dir, so it needs a file type.
                    String extension = FilenameUtils.getExtension(templateName);
                    if (extension.length() > 0) {
                        extension = "."+extension;
                    }
                    pkg = File.createTempFile("template-export", extension);
                    FileUtils.writeStringToFile(pkg, templateRendered);
                } else {
                    // Create output directory
                    pkg = File.createTempFile("template-export-", ".dir");
                    pkg.delete();
                    pkg.mkdir();

                    writeTemplatesToDir(pkg, submission, customEntryName, parameters);
                    writeAttachmentsToDir(pkg, submission, parameters);
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
	 */
	public static class TemplatePackage extends AbstractExportPackage implements ExportPackage {

		// Members
		public final String mimeType;
		public final String format;

		public TemplatePackage(Submission submission, String mimeType, String format, File file, String entryName) {
            super(submission, file, entryName);
			this.mimeType = mimeType;
			this.format = format;
		}

		@Override
		public String getMimeType() {
			return mimeType;
		}

		@Override
		public String getFormat() {
			return format;
		}
	}
}
