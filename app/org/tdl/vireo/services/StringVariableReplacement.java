package org.tdl.vireo.services;

import org.tdl.vireo.model.NameFormat;
import org.tdl.vireo.model.Preference;
import org.tdl.vireo.model.Submission;
import org.tdl.vireo.security.impl.LDAPAuthenticationMethodImpl;
import play.Logger;
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

    /** All the replacement strings available for substitution */
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
        /** File separator for current platform */
        SEPARATOR
    }
    /** a token that, used between two other strings from the list above,
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
			Map<String,Object> routeArgs = new HashMap<String,Object>();
			routeArgs.put("token", sub.getCommitteeEmailHash());
			
			ActionDefinition advisorAction = Router.reverse("Advisor.review",routeArgs);
			advisorAction.absolute();
			
			parameters.put(Variable.ADVISOR_URL.name(), advisorAction.url);
		}
		
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
		
		for (String name : parameters.keySet()) {
			String value = parameters.get(name);
			
			string = string.replaceAll("\\{"+name+"\\}", value);
		}
	
		return string;
		
	}

    /**
     * This replaces placeholders within a string with the corresponding value in the parameters, if present.
     * If any variable in StringVariableReplacement.Variable is not present in 'parameters', it will be removed
     * from 'string'. If several such Variables are chained together, separated only by
     * StringVariableReplacement.FALLBACK, then the whole chain will be replaced with the first substitution
     * in the chain for which a replacement was found in 'parameters', or an empty value if none found.
     * @param string a String containing values to be replaced, singly or in fallback chains
     * @param parameters a Map of the strings to be replaced with their corresponding values, so long
     *      as the string key is a name of a valid StringVariableReplacement.Variable.
     * @return the new string with replacements / eliminations done, or null if the input string was null.
     */
    public static String applyParameterSubstitutionWithFallback(String string, Map<String, String> parameters) {
        if (string==null) {
            return null;
        }
        Logger.info("***************************************");

        StringBuilder result = new StringBuilder();
        final int length = string.length();
        int prevPos = 0;
        int pos = string.indexOf('{');

        // Check string in chunks starting with each '{'
        while (pos != -1) {
            // Append everything from the end of the previous substitution to just before the current '{'
            Logger.info("appendBefore=["+prevPos+'-'+pos+']');
            result.append(string.substring(prevPos, pos));
            Logger.info("up to '" + result.toString() + "' and then open {");

            String beingReplaced = "{";
            String replacement = null;

            // Possible substitution starting at this '{'. Check, and keep checking until '}' or
            // something is invalid, in which case it wasn't a substitution after all.
            int keepGoing = 0;
            int subPos = pos+1; // just after opening '{'

            while (keepGoing == 0) {
                Logger.info("  looking with subpos="+subPos);
                boolean foundParam = false;
                for (Variable var : Variable.values()) {
                    final String varName = var.name();
                    if (subPos+varName.length()<=length && string.substring(subPos, subPos+var.name().length()).equals(var.name())) {
                        // do substitution
                        if (replacement == null) {
                            replacement = parameters.get(varName);
                        }
                        beingReplaced += varName;
                        subPos += varName.length();

                        foundParam = true;
                        Logger.info("  param="+varName);
                        break; // done looking for which parameter this is
                    }
                }
                Logger.info("  done with subpos="+subPos);
                if (foundParam && subPos+FALLBACK.length()<=length && string.substring(subPos, subPos+FALLBACK.length()).equals(FALLBACK)) {
                    // Fallback token right after valid parameter name. Keep going.
                    beingReplaced += FALLBACK;
                    subPos += FALLBACK.length();
                    Logger.info("  fallback");
                } else if (foundParam && subPos+1 <= length && string.substring(subPos, subPos+1).equals("}")) {
                    // Substitution end token right after valid parameter name. We made it.
                    beingReplaced += '}';
                    Logger.info("found end }");
                    keepGoing = 1; // success
                } else {
                    // Something was invalid before substitution fallback chain came to a clean end.
                    Logger.info("didn't find param : foundParam="+foundParam);
                    if (subPos+1 <= length) {
                        Logger.info("string[subpos, subpos+1] = '"+string.substring(subPos, subPos+1)+'\'');
                    } else {
                        Logger.info("subpos+1="+subPos+1+" > length="+length);
                    }
                    keepGoing = -1; // failure
                }
            }

            if (keepGoing == -1) {
                // it may be a '{', but it didn't actually start a valid substitution
                replacement = beingReplaced;
            }

            // account for what we just consumed (in 'string') and what we just produced (in 'result').
            Logger.info("append=" + replacement);
            result.append(replacement);
            Logger.info("  up to '"+result.toString()+'\'');
            prevPos = pos+beingReplaced.length();

            // move to next chunk that starts with a '{'
            pos = string.indexOf('{', prevPos);
        }
        
        // append leftover
        result.append(string.substring(prevPos));
        return result.toString();
    }
}