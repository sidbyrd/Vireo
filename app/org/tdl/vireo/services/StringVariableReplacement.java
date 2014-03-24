package org.tdl.vireo.services;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.TransformerUtils;
import org.tdl.vireo.model.NameFormat;
import org.tdl.vireo.model.Preference;
import org.tdl.vireo.model.Submission;
import org.tdl.vireo.security.impl.LDAPAuthenticationMethodImpl;
import play.mvc.Router;
import play.mvc.Router.ActionDefinition;

import java.io.File;
import java.text.DateFormatSymbols;
import java.util.*;

/**
 * This service allows for the manipulation of strings--both setting parameters and handling 
 * place holder replacements.
 *  
 * @author Micah Cooper
 * @author Jeremy Huff
 */
public class StringVariableReplacement {

    /** All the replacement strings available for substitution.*/
    public enum Variable {
        /** submitting student's full name */
        FULL_NAME,
        /** submitting student's first name */
        FIRST_NAME,
        /** submitting student's last name */
        LAST_NAME,
        /** submitting student's student ID */
        STUDENT_ID,
        /** submitting student's NetID */
        STUDENT_NETID,
        /** submitting student's email address */
        STUDENT_EMAIL,
        /** submission title */
        DOCUMENT_TITLE,
        /** submission type */
        DOCUMENT_TYPE,
        /** submission current status */
        SUBMISSION_STATUS,
        /** submission graduation semester as monthname, year */
        GRAD_SEMESTER,
        /** submission assignee, or "n/a" */
        SUBMISSION_ASSIGNED_TO,
        /** student submission view URL */
        STUDENT_URL,
        /** advisor submission review URL */
        ADVISOR_URL,
        /** not filled by setParameters(), but used by FilePackager. Represents attachment filename. */
        FILE_NAME,
        /** File separator for current platform. This should never be needed and could cause problems if used improperly */
        @Deprecated
        SEPARATOR;

        /**
         * Gets all the names of these Variables.
         * Unmodifiable and created only once for efficiency.
         * @return Set of all Variable names.
         */
        public static Collection<String> names() {
            return namesLazyInit.names;
        }
        private static class namesLazyInit {
            @SuppressWarnings("unchecked")
            private static final Collection<String>names = (Collection<String>)Collections.unmodifiableCollection(CollectionUtils.collect(Arrays.asList(Variable.values()), TransformerUtils.stringValueTransformer()));
        }
    }

    /** A token that, used between two other strings from the list above inside a variable name scope,
        indicates fallback substitution (see applyParameterSubstitutionWithFallback() ). */
    public static final String FALLBACK = "||";

	/**
	 * This generates a map of the strings to be replaced with a specific submission's metadata.
	 * 
	 * 
	 * @param sub
	 * 		The Submission object.
	 * 
	 * @return
	 * 		A key value Map of the Source String and the Replacement String.
	 * 
	 */
	public static Map<String, String> setParameters(Submission sub) {
		
		Map<String, String> parameters = new HashMap<String, String>();
		
		if (sub.getStudentFirstName() != null || sub.getStudentLastName() != null)
			parameters.put(Variable.FULL_NAME.name(), sub.getStudentFormattedName(NameFormat.FIRST_LAST));
		
		if (sub.getStudentFirstName() != null)
			parameters.put(Variable.FIRST_NAME.name(), sub.getStudentFirstName());
		
		if (sub.getStudentLastName() != null)
			parameters.put(Variable.LAST_NAME.name(), sub.getStudentLastName());

        // if student ID becomes a real Person property, simplify this to match, of course
        Preference studentID = sub.getSubmitter()==null?null : sub.getSubmitter().getPreference(LDAPAuthenticationMethodImpl.personPrefKeyForStudentID);
        if (studentID != null && studentID.getValue() != null && studentID.getValue().trim().length() > 0) {
            parameters.put(Variable.STUDENT_ID.name(), studentID.getValue());
        }

        if (sub.getSubmitter() != null && sub.getSubmitter().getNetId() != null) {
            parameters.put(Variable.STUDENT_NETID.name(), sub.getSubmitter().getNetId());
        }

        if (sub.getSubmitter() != null && sub.getSubmitter().getEmail() != null) {
            parameters.put(Variable.STUDENT_EMAIL.name(), sub.getSubmitter().getEmail());
        }
		
		if (sub.getDocumentTitle() != null)
			parameters.put(Variable.DOCUMENT_TITLE.name(), sub.getDocumentTitle());
		
		if (sub.getDocumentType() != null)
			parameters.put(Variable.DOCUMENT_TYPE.name(), sub.getDocumentType());
		
		if (sub.getState() != null)
			parameters.put(Variable.SUBMISSION_STATUS.name(),sub.getState().getDisplayName());
		
		if (sub.getGraduationYear() != null) {
			String gradSemester = String.valueOf(sub.getGraduationYear());
			if (sub.getGraduationMonth() != null) {
				Integer monthInt = sub.getGraduationMonth();
				String monthName = new DateFormatSymbols().getMonths()[monthInt];
				
				gradSemester = monthName+", "+gradSemester;
			}
			
			parameters.put(Variable.GRAD_SEMESTER.name(), gradSemester);
		}
		
		if (sub.getAssignee() != null)
			parameters.put(Variable.SUBMISSION_ASSIGNED_TO.name(),sub.getAssignee().getFormattedName(NameFormat.FIRST_LAST));
		else 
			parameters.put(Variable.SUBMISSION_ASSIGNED_TO.name(), "n/a");
		
		
		// URL for the student to view their submission(s)
		ActionDefinition studentAction = Router.reverse("Student.submissionList");
		studentAction.absolute();
		parameters.put(Variable.STUDENT_URL.name(), studentAction.url);
		
		// Advisor url for reviews
		if (sub.getCommitteeEmailHash() != null) {
			Map<String,Object> routeArgs = new HashMap<String,Object>(1);
			routeArgs.put("token", sub.getCommitteeEmailHash());
			
			ActionDefinition advisorAction = Router.reverse("Advisor.review",routeArgs);
			advisorAction.absolute();
			
			parameters.put(Variable.ADVISOR_URL.name(), advisorAction.url);
		}

        //noinspection deprecation
        parameters.put(Variable.SEPARATOR.name(), File.separator);
		
		return parameters;
		
	}
	
	
	/**
	 * This replaces placeholders within a string with the corresponding value in the parameters, if present.
	 * 
	 * @param string
	 * 		A string containing values to be replaced.
	 * @param parameters
	 * 		A map of the strings to be replaced with their corresponding value
	 * @return
	 * 		The new string, or null if the input string was null
	 */
	public static String applyParameterSubstitution(String string, Map<String, String> parameters) {
		
		if(string == null)
			return null;
		
		for (Map.Entry<String, String> stringStringEntry : parameters.entrySet()) {
            final String name =stringStringEntry.getKey();
			final String value = stringStringEntry.getValue();

			string = string.replaceAll("\\{"+ name +"\\}", value);
		}
	
		return string;
		
	}

    /**
     * This searches a string for variable names bracketed by {} characters. If a corresponding value is present
     * in parameters, it is substituted for the variable name, otherwise the variable name is removed. If a
     * single {} scope contains a chain of multiple variable names separated by ||, then the entire chain will
     * be replaced with the value of the leftmost variable for which a non-null substitution is available. If the
     * last thing in the chain, before the closing '}', is plain text with no { or ||, then it will be treated
     * as a default fallback value when no variables match. If there is no default and no matches, the entire
     * chain will be replaced with an empty value.
     *
     * You may use variable names that are not in Variable.names() only if you make sure the appropriate entry is
     * in the parameters map. Otherwise, names of such rogue variables cannot be recognized as such when they lack
     * a mapped value--this will cause the entire {} variable fallback scope containing the unrecognized variable
     * name to be considered invalid and treated as only plain text (unless the rogue variable name is at the end,
     * in which case it may be treated as a valid fallback default value). Except for oddball use, consider adding
     * any oft-used variable names to the Variable enum.
     *
     * Example: "Welcome, {FULL_NAME||LAST_NAME||Name Missing}." has two variables and a default value in one
     * fallback chain.
     *
     * @param string a String containing {}-bracketed Variable names to be replaced, singly or in fallback chains
     * @param parameters a Map of the variable names to be replaced with their corresponding values.
     * @return the new string with replacements / eliminations done, or null if the input string was null.
     */
    public static String applyParameterSubstitutionWithFallback(String string, Map<String, String> parameters) {
        if (string==null) {
            return null;
        }

        final StringBuilder result = new StringBuilder(string.length()*2);
        final int length = string.length();
        int prevPos = 0;
        int pos = string.indexOf('{');

        // Check string in chunks starting with each '{'
        while (pos != -1) {
            // Time to build list of all possible variable names: mostly static, plus any rogues that are only in parameters.
            final Collection<String> varNames = new LinkedHashSet<String>(Variable.names()); // linked for fast iteration
            varNames.addAll(parameters.keySet()); // set operation: if no rogue variable names are in parameters, this adds nothing.

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
                for (String varName : varNames) {
                    if (chainPos+varName.length()<=length && string.substring(chainPos, chainPos+varName.length()).equals(varName)) {
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
                        if (!defaultValue.contains(FALLBACK) && !defaultValue.contains("{")) { // invalid if contains special stuff
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
                if (foundVariable && chainPos+FALLBACK.length()<=length && string.substring(chainPos, chainPos+FALLBACK.length()).equals(FALLBACK)) {
                    // Fallback token right after valid variable name. Keep consuming the fallback chain.
                    chainPos += FALLBACK.length();
                } else if (foundVariable && chainPos+1 <= length && string.substring(chainPos, chainPos+1).equals("}")) {
                    // Substitution end token right after variable name/default value. Chain came to a valid end.
                    chainPos += 1; // for closing '}'
                    validChain = 1; // stop: success
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
}