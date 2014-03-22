package org.tdl.vireo.export.impl;

import org.tdl.vireo.export.ExportPackage;
import org.tdl.vireo.model.Submission;
import play.vfs.VirtualFile;

import java.util.HashMap;
import java.util.Map;

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
public class TemplatePackagerImpl extends MultipleTemplatePackagerImpl {
		
	/* Spring injected parameters */
	public String manifestName = "mets.xml";
    // also manifestTemplatePath, which is injected via setter here but is stored using
    // the mechanisms of the superclass.

	/**
	 * (REQUIRED) Set the template for generating the manifest file. This
	 * parameter is always required.
	 * 
	 * @param templatePath
	 *            The path (relative to the application's directory)
	 */
	public void setManifestTemplatePath(String templatePath) {
        // Adapt singular call here, with manifest name set separately,
        // to superclass plural call.
        Map<String, String> arg = new HashMap<String, String>(1);
        arg.put(manifestName, templatePath);
		setTemplatePaths(arg);
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
        // while storing the new manifest name, don't forget that it's the key
        // for the template file in the templates map.
        VirtualFile oldTemplate = templates.remove(this.manifestName);
		this.manifestName = manifestName;
        templates.put(manifestName, oldTemplate);
	}
	
    /**
     * (ALTERNATE FORM) Configure the manifest template path and name at the same
     * time via a Map from name to filename. Calling this (with exactly one entry
     * in the Map) yields the same result as calling both setManifestName() and
     * setManifestTemplatePath().
     *
     * @param templates
     *            A map from filenames to templates paths.
     * @throws IllegalArgumentException if templates does not contain exactly one entry.
     */
    @Override
    public void setTemplatePaths(Map<String, String> templates) {
        if (templates.size() != 1) {
            throw new IllegalArgumentException("A TemplatePackagerImpl can ony have exactly one template.");
        }
        manifestName = templates.entrySet().iterator().next().getKey();
        super.setTemplatePaths(templates);
    }

	/**
	 * (OPTIONAL) Set a list of arguments which may be accessed as variables in
	 * the template syntax. The variable "sub" will always be the submission
	 * which is being packaged. This is an alternate name for setTemplateArguments().
	 * 
	 * @param arguments
	 *            Template arguments
	 */
	public void setManifestTemplateArguments(Map<String,Object> arguments) {
        // this is just a rename for a superclass method, here for historical reasons.
		setTemplateArguments(arguments);
	}

    @Override
	public ExportPackage generatePackage(Submission submission) {

		if (manifestName == null)
			throw new IllegalStateException("Unable to generate package because no manifest name has been defined.");

        return super.generatePackage(submission);
	}
}
