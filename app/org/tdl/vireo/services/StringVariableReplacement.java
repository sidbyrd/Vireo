package org.tdl.vireo.services;

import play.templates.Template;
import play.templates.TemplateLoader;

import java.util.*;

/**
 * This service allows for the manipulation of strings--both setting parameters and handling
 * place holder replacements.
 *  
 * @author Micah Cooper
 * @author Jeremy Huff
 */
public class StringVariableReplacement {

	/** A token that, used at the beginning of the string, indicates to process the string in template mode
	 * instead of the usual replacement with fallback mode */
	public static final String TEMPLATE_MODE = "{TEMPLATE_MODE}";

	/** A token that, used between two other strings from the list above inside a variable name scope,
		indicates fallback substitution (when in fallback substitution mode). */
	public static final String OR = "||";

	/**
	 * This searches a string for variable names bracketed by {} characters. A variable name is anything in
	 * parameters.keySet(). If parameters contains corresponding non-null value for that key, it is
	 * substituted for the variable name, otherwise the variable name is removed. The surrounding {} are
	 * also removed.
	 *
	 * If a single {} scope contains a chain of multiple variable names separated by ||, then
	 * the entire chain will be replaced with the value of the leftmost variable for which a non-null value
	 * is available. If the last thing in the chain, before the closing '}', is plain text with no { or ||,
	 * then it will be treated as a default fallback value when no variables match. If there is no default
	 * and no matches, the entire chain will be replaced with an empty value.
	 *
	 * Example: "Welcome, {FULL_NAME||LAST_NAME||Name Missing}." has two variables (assuming both "FULL_NAME"
	 * and "LAST_NAME" are keys in the parameters) and a default value contained in a single fallback chain.
	 * It could evaluate to "Welcome, Billy Bob." or "Welcome, Name Missing." depending on the parameter
	 * values.
	 *
	 * This method seems complicated, but it proceeds fairly directly from the start of the input string to
	 * the end in one pass. It can be faster or slower than just using string.replace with every parameter,
	 * depending on how many { the string contains and how many keys are in Parameters.
	 *
	 * @param string a String containing {}-bracketed variable names to be replaced, singly or in fallback
	 * chains.
	 * @return the new string with replacements performed, or null if the input string was null.
	 */
	public static String applyParameterSubstitution(String string, Map<String, String> parameters) {
		if (string==null) {
			return null;
		}
		if (string.startsWith(TEMPLATE_MODE)) {
			return applyTemplateParameters(string.substring(TEMPLATE_MODE.length()), parameters);
		}

		final StringBuilder result = new StringBuilder(string.length()*2);
		final int length = string.length();
		int prevPos = 0;

		// Check string in chunks starting with each '{'
		int pos = string.indexOf('{');
		while (pos != -1) {
			// Append everything from the end of the previous substitution to just before the current '{'
			result.append(string.substring(prevPos, pos));

			// Possible substitution starting at this '{'. Check, and keep checking until '}' or
			// something is invalid, in which case it wasn't a substitution after all.
			int validChain = 0; // 0==not done, -1==invalid, 1==valid
			boolean firstLoop = true; // to prevent a default value from being first
			int chainPos = pos+1; // just after opening '{'
			String replacement = null;

			while (validChain == 0) {

				// A valid fallback chain has a Variable name here (or a default value).
				boolean foundVariable = false;
				for (String varName : parameters.keySet()) { // use key loop instead of entry loop to keep evaluation lazy
					if (chainPos+varName.length()<=length && string.regionMatches(chainPos, varName, 0, varName.length())) {
						// Do substitution (if a previous substitution in this chain hasn't already been done).
						if (replacement == null) {
							replacement = parameters.get(varName);
						}
						// Either way, mark the variable name as replaced.
						chainPos += varName.length();
						foundVariable = true;
						break; // from searching list of variable names
					}
				}
				if (!foundVariable && !firstLoop) {
					// No variable name. Would it be valid as a default value?
					final int endPos = string.indexOf('}', chainPos);
					if (endPos!=-1) {
						final String defaultValue = string.substring(chainPos, endPos);
						if (!defaultValue.contains(OR) && !defaultValue.contains("{")) { // invalid if contains special stuff
							// It's valid.
							if (replacement == null) {
								replacement = defaultValue;
							}
							chainPos = endPos; // the next char will be the '}'
							foundVariable = true;
						}
					}
				}

				// Done looking for variable name. Where to next?
				if (foundVariable && chainPos+1 <= length && string.charAt(chainPos)=='}') {
					// Substitution end token right after variable name/default value. Chain came to a valid end.
					chainPos += 1; // for closing '}'
					validChain = 1; // stop: success
				} else if (foundVariable && chainPos+ OR.length()<=length && string.regionMatches(chainPos, OR, 0, OR.length())) {
					// Fallback token right after valid variable name. Keep consuming the fallback chain.
					chainPos += OR.length();
				} else {
					// Invalid chain text before chain came to valid end. Cancel and treat at plain text.
					validChain = -1; // stop: failure
				}
				firstLoop = false;
			}

			// Account for what we just consumed and replaced, then move on to the next '{'
			if (validChain == -1) {
				result.append(string.substring(pos, chainPos)); // the failed fallback chain unaltered
			} else if (replacement!=null) { // null is legit result for chain with no supplied replacements, but don't actually append it.
				result.append(replacement);
			}
			prevPos = chainPos;
			pos = string.indexOf('{', prevPos);
		}

		// append leftovers
		result.append(string.substring(prevPos));
		return result.toString();
	}

	/**
	 * Renders the supplied string using Play's template engine, supplying the given parameters as
	 * arguments to the template. Useful for more complicated substitution logic.
	 * @param string the template to run, as a string
	 * @param parameters map of binding arguments to provide to the template
	 * @return the rendered template result
	 */
	public static String applyTemplateParameters(String string, Map<String, String> parameters) {
		// make a key so the compiled template can be cached in case we see this exact same string again later
		String key = String.valueOf(string.hashCode());
		Template template = TemplateLoader.load(key, string);

		/// render the template with the given parameters (have to change type to Map<String, Object>)
		Map<String, Object> templateBinding = new HashMap<String,Object>(parameters);
		return template.render(templateBinding);
	}

}