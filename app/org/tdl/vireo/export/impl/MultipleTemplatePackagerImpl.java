package org.tdl.vireo.export.impl;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.mutable.MutableInt;
import org.tdl.vireo.export.ExportPackage;
import org.tdl.vireo.model.PersonRepository;
import org.tdl.vireo.model.SettingsRepository;
import org.tdl.vireo.model.Submission;
import org.tdl.vireo.model.SubmissionRepository;
import org.tdl.vireo.proquest.ProquestVocabularyRepository;
import org.tdl.vireo.services.StringCustomizer;
import play.Play;
import play.exceptions.TemplateNotFoundException;
import play.templates.Template;
import play.templates.TemplateLoader;
import play.vfs.VirtualFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipOutputStream;

import static org.tdl.vireo.export.impl.AbstractPackagerImpl.PackageType.dir;
import static org.tdl.vireo.export.impl.AbstractPackagerImpl.PackageType.zip;
import static org.tdl.vireo.services.StringCustomizer.TEMPLATE_MODE;

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
	
	/* Spring injected parameters */
	Map<String,VirtualFile> templates = new HashMap<String,VirtualFile>(4); // injected via setTemplatePaths()
	public String format = null;
	public Map<String,Object> templateArguments = new HashMap<String,Object>(2); // not expecting very many

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

		for (Map.Entry<String, String>entry : templates.entrySet()) {
            final String name = entry.getKey();
            if (!name.startsWith(TEMPLATE_MODE) && name.contains(File.separator)) {
                throw new IllegalArgumentException("Template name '"+name+"' cannot contain '"+File.separator+"'.");
            }

            final String templatePath = entry.getValue();
			VirtualFile templateFile = Play.getVirtualFile(templatePath);
			
    		// Blow up at construction time if template not found.
			if ( templatePath == null || templateFile==null || !templateFile.exists()) {
				throw new TemplateNotFoundException("path="+templatePath+", file="+templateFile);
            }
			
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
	 * @param templateArguments
	 *            Template arguments
	 */
	public void setTemplateArguments(Map<String,Object> templateArguments) {
        this.templateArguments = templateArguments;
	}

    /**
     * Executes the named template for a specific submission, its string replacement parameters, and its
     * already-customized entry name.
     *
     * @param templateName the name of the template to execute
     * @param submission the submission to customize for
     * @param customEntryName the entry name to use. Basically string replacement on this.entryName, but it's
     * already done by the expected caller, so save having to duplicate the effort.
     * @return the rendered template contents as a string.
     */
    protected String renderTemplate(String templateName, Submission submission, String customEntryName) {
        VirtualFile templateFile = templates.get(templateName);

        // We used to customize template name at this point, and pass the customized version in. But we never customized
        // the keys in 'templates', which are comparable information. I can't think of a reason to pass the customized
        // template name:
        // - to check current name against a list (as DSpaceSimpleArchive does): un-customized is more predictable.
        // - to convey information: all the information came from 'submission', which is also passed directly.
        // - to refer to other templates: but the 'templates' hash uses un-customized keys.
        // - because existing configs rely on it: none of the default ones do, and I doubt any/many ever did.
        // Reasons not to: it matches the keys in 'templates', it's more predictable for comparison, and it's easier.
        // So I've changed it to pass the un-customized template name. The customized name will still be the filename.

        // The 'templates' binding could maybe also use some thought. Currently, not a single default template uses it,
        // and it seems odd to pass a Map from template name to VirtualFile. Should it just be a list of template names?
        // Or even just be omitted entirely? Not sure what the use case is, but at least it seems harmless.

        Map<String, Object> templateBinding = new HashMap<String,Object>(13 + templateArguments.size());
        templateBinding.put("sub", submission);
        templateBinding.put("personRepo", personRepo);
        templateBinding.put("subRepo", subRepo);
        templateBinding.put("settingRepo", settingRepo);
        templateBinding.put("proquestRepo", proquestRepo);
        templateBinding.put("packageType", packageType.name());
        templateBinding.put("format", format);
        templateBinding.put("mimeType", mimeType); // will often be null, but that's fine.
        templateBinding.put("entryName", customEntryName);
        templateBinding.put("attachmentTypes", attachmentTypes);
        templateBinding.put("manifestName", templateName); // dupe, key preferred by single templates
        templateBinding.put("template", templateName); // dupe, key preferred by multiple templates
        templateBinding.put("templates", templates); // only relevant if multiple templates, but harmless if single.
        templateBinding.putAll(templateArguments);

        Template template = TemplateLoader.load(templateFile);
        return template.render(templateBinding);
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
            final String templateRendered = renderTemplate(templateName, submission, customEntryName);
            // now customize the template name
            templateName = StringCustomizer.applyParameterSubstitution(templateName, parameters);
            if (templateName.contains(File.separator)) {
                templateName = templateName.replace(File.separator, "-");
            }

            // Make sure new file isn't a duplicate name.
            File manifestFile = new File(pkg, templateName);
            MutableInt copyNumber = new MutableInt(1);
            while (manifestFile.exists()) {
                // Duplicate found.
                manifestFile = new File(pkg, incrementFilename(templateName, copyNumber));
            }

            // Copy the manifest into the pkg dir
            FileUtils.writeStringToFile(manifestFile, templateRendered);
        }
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
            final String templateRendered = renderTemplate(templateName, submission, customEntryName);
            // now customize the template name
            templateName = StringCustomizer.applyParameterSubstitution(templateName, parameters);
            if (templateName.contains(File.separator)) {
                templateName = templateName.replace(File.separator, "-");
            }

            // Make sure new file isn't a duplicate name.
            MutableInt copyNumber = new MutableInt(1);
            ZipEntry ze = new ZipEntry(templateName);
            while (copyNumber!=null) {
                try {
                    zos.putNextEntry(ze);
                    copyNumber=null; // done
                } catch (ZipException e) {
                    if (!e.getMessage().contains("duplicate entry")) {
                        throw e; // something else happened; don't catch it.
                    }
                    // Duplicate found. We have to rename the new one, e.g. file.txt to file_1.txt
                    ze = new ZipEntry(incrementFilename(templateName, copyNumber));
                }
            }

            // Copy the manifest into the zip archive as a new entry
            InputStream manifestStream = IOUtils.toInputStream(templateRendered, "UTF-8");
            byte[] buf = new byte[1024];
            int len;
            while ((len = manifestStream.read(buf)) > 0) {
                zos.write(buf, 0, len);
            }
            zos.closeEntry();
        }
    }

	@Override
	public ExportPackage generatePackage(Submission submission) {

		// Check that we have everything that we need.
		if (submission == null || submission.getId() == null) {
			throw new IllegalArgumentException("Unable to generate a package because the submission is null, or has not been persisted.");
        }

		if (templates == null || templates.isEmpty()) {
			throw new IllegalStateException("Unable to generate package because no template file exists.");
        }

		if (format == null) {
			throw new IllegalStateException("Unable to generate package because no package format name has been defined.");
        }

        // Set string replacement parameters
        Map<String, String> parameters = StringCustomizer.setParameters(submission);
        // Customize entry name
        String customEntryName = StringCustomizer.applyParameterSubstitution(entryName, parameters);

		try {
			File pkg;
			
			// Produce the correct output format.
            if (packageType==dir && attachmentTypes.isEmpty() && templates.size()==1) {
                String templateName = templates.keySet().iterator().next();
                // Only one template to export, so instead of a dir, just make one file (with correct extension).
                String extension = FilenameUtils.getExtension(templateName);
                pkg = File.createTempFile("template-export", (extension.length()>0?'.':"")+extension);

                // Render the one template directly to the package file.
                // (No need for templateName customization since the Depositor will change its base filename anyway.)
                FileUtils.writeStringToFile(pkg, renderTemplate(templateName, submission, customEntryName));

            } else if (packageType==dir) {
                // Create output directory
                pkg = File.createTempFile("template-export-", ".dir");
                FileUtils.deleteQuietly(pkg);
                if (!pkg.mkdir()) {
                    throw new IOException("Could not create package directory '"+pkg.getPath()+'\'');
                }

                writeTemplatesToDir(pkg, submission, customEntryName, parameters);
                writeAttachmentsToDir(pkg, submission, parameters);

            } else if (packageType==zip) {
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

            } else {
                throw new RuntimeException("Packager: unsupported package type '"+packageType+'\'');
            }

			// Create the package
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
	public static class TemplatePackage extends AbstractExportPackage {

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
