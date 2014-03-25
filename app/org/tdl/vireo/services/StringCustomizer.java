package org.tdl.vireo.services;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.TransformerUtils;
import org.apache.commons.lang.StringUtils;
import org.tdl.vireo.model.NameFormat;
import org.tdl.vireo.model.Preference;
import org.tdl.vireo.model.Submission;
import play.mvc.Router;
import play.mvc.Router.ActionDefinition;
import play.templates.Template;
import play.templates.TemplateLoader;

import java.io.File;
import java.text.DateFormatSymbols;
import java.util.*;

import static org.tdl.vireo.security.impl.LDAPAuthenticationMethodImpl.personPrefKeyForStudentID;

/**
 * This service allows for the manipulation of strings--both setting parameters and handling 
 * place holder replacements.
 *  
 * @author Micah Cooper
 * @author Jeremy Huff
 */
public class StringCustomizer {

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
        /** submission id. Useful for ExportPackage entry name */
        SUBMISSION_ID,
        /** not filled by setParameters(), but used by Packagers. Represents attachment filename. */
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

    /** A token that, used at the beginning of the string, indicates to process the string in fallback mode */
    public static final String FALLBACK_MODE = "{FALLBACK_MODE}";

    /** A token that, used at the beginning of the string, indicates to process the string in template mode */
    public static final String TEMPLATE_MODE = "{TEMPLATE_MODE}";

    /** A token that, used between two other strings from the list above inside a variable name scope,
        indicates fallback substitution (when in fallback substitution mode). */
    public static final String OR = "||";





    /** the submission from which to draw customized values, stored so we can lazily evaluate against it */
    private Submission submission = null;

    /** all variable replacements that have been computed or externally put. */
    private Map<String, String> parameters;

    /**
     * Like a regular HashMap<String, String>, except that:
     * - if the key passed to get() is the name of a Variable enum that can be looked up from the
     *   submission, the answer is lazily computed when asked for, and is always present (even
     *   though it may be null, which is a valid value for many Variable names).
     * - if a value is manually put(), its key will also be added to varNames.
     */
    private class LazyParameterMap extends HashMap<String, String> {
        /** all possible Variable names, plus all externally put keys */
        private final Set<String> keys;

        public LazyParameterMap(int initialCapacity) {
            super(initialCapacity);
            // starting keys I "have" are all the Variable names.
            keys = new LinkedHashSet<String>(Variable.names()); // linked for fast iteration
        }

        @Override
        public boolean isEmpty() {
            return false; // we always have Variables, even if we haven't computed them yet.
        }

        @Override
        public int size() {
            return keys.size();
        }

        @Override
        public Set<String> keySet() {
            return keys;
        }

        @Override
        public Collection<String> values() {
            throw new UnsupportedOperationException("StringCustomizer.LazyParameterMap.values() unsupported");
        }

        @Override
        public Set<Map.Entry<String,String>> entrySet() {
            throw new UnsupportedOperationException("StringCustomizer.LazyParameterMap.entrySet() unsupported");
        }

        @Override
        public String put(String key, String value) {
            keys.add(key);
            return super.put(key, value);
        }

        @Override
        public void putAll(Map<? extends String,? extends String> map) {
            for (Map.Entry<? extends String, ? extends String> entry : map.entrySet()) {
                put(entry.getKey(), entry.getValue());
            }
        }

        @Override
        public String remove(Object keyObj) {
            // is the key a Variable name?
            String key = (String)keyObj;
            try {
                Variable.valueOf(key);
                // yes: do nothing
            } catch (IllegalArgumentException e) {
                // no: remove the key from my key Set too
                keys.remove(key);
            }
            return super.remove(keyObj); // un-compute, if key was a Variable name
        }

        @Override
        public void clear() {
            keys.clear();
            keys.addAll(Variable.names());
            super.clear();
        }

        @Override
        public boolean containsKey(Object key) {
            // if we actually do have the value, this is easy.
            boolean really = super.containsKey(key);
            if (really) {
                return true;
            }
            // if we don't but it's a Variable name, still pretend we do.
            try {
                Variable.valueOf((String)key);
            } catch (IllegalArgumentException e) {
                return false; // not a Variable name
            }
            return true;
        }

        @Override
        public boolean containsValue(Object value) {
            throw new UnsupportedOperationException("StringCustomizer.LazyParameterMap.containsValue() unsupported");
        }

        /**
         * Any key to look up. If it's a Variable name, value will be computed the first time.
         * If it's just a regular string, there will be a value if it has been put() before.
         * @param keyObj string or Variable name to look up
         * @return value retrieved, which may be null; or the value null
         */
        @Override
        public String get(Object keyObj) {
            // if we have it already, this is easy
            String key = (String)keyObj;
            if (super.containsKey(key)) {
                return super.get(key);
            }
            // get the Variable being requested
            Variable var;
            try {
                var = Variable.valueOf(key);
            } catch (IllegalArgumentException e) {
                super.put(key, null); // fail easier next time, but don't add to varNames
                return null;
            }
            // find value for this Variable
            String value = null;
            switch (var) {

                case FULL_NAME:
                    if (submission.getStudentFirstName() != null || submission.getStudentLastName() != null) {
                        value = submission.getStudentFormattedName(NameFormat.FIRST_LAST);
                    }
                    break;

                case FIRST_NAME:
                    if (submission.getStudentFirstName() != null) {
                        value = submission.getStudentFirstName();
                    }
                    break;

                case LAST_NAME:
                    if (submission.getStudentLastName() != null) {
                        value = submission.getStudentLastName();
                    }
                    break;

                case STUDENT_ID:
                    // if student ID becomes a regular Person property, simplify this to match, of course
                    if (submission.getSubmitter() != null) {
                        Preference studentIdPref = submission.getSubmitter().getPreference(personPrefKeyForStudentID);
                        if (studentIdPref != null && !StringUtils.isBlank(studentIdPref.getValue())) {
                            value = studentIdPref.getValue();
                        }
                    }
                    break;

                case STUDENT_NETID:
                    if (submission.getSubmitter() != null && submission.getSubmitter().getNetId() != null) {
                        value = submission.getSubmitter().getNetId();
                    }
                    break;

                case STUDENT_EMAIL:
                    if (submission.getSubmitter() != null && submission.getSubmitter().getEmail() != null) {
                        value = submission.getSubmitter().getEmail();
                    }
                    break;

                case DOCUMENT_TITLE:
                    if (submission.getDocumentTitle() != null) {
                        value = submission.getDocumentTitle();
                    }
                    break;

                case DOCUMENT_TYPE:
                    if (submission.getDocumentType() != null) {
                        value = submission.getDocumentType();
                    }
                    break;

                case SUBMISSION_STATUS:
                    if (submission.getState() != null) {
                        value = submission.getState().getDisplayName();
                    }
                    break;

                case GRAD_SEMESTER:
                    if (submission.getGraduationYear() != null) {
                        String gradSemester = String.valueOf(submission.getGraduationYear());
                        if (submission.getGraduationMonth() != null) {
                            final Integer monthInt = submission.getGraduationMonth();
                            final String monthName = new DateFormatSymbols().getMonths()[monthInt];
                            gradSemester = monthName+", "+gradSemester;
                        }
                        value = gradSemester;
                    }
                    break;

                case SUBMISSION_ASSIGNED_TO:
                    if (submission.getAssignee() != null) {
                        value = submission.getAssignee().getFormattedName(NameFormat.FIRST_LAST);
                    } // if you want the old default of "n/a", use {SUBMISSION_ASSIGNED_TO||n/a}
                    break;

                case STUDENT_URL:
                    // URL for the student to view their submission(s)
                    ActionDefinition studentAction = Router.reverse("Student.submissionList");
                    studentAction.absolute();
                    value = studentAction.url;
                    break;

                case ADVISOR_URL:
                    // Advisor url for reviews
                    if (submission.getCommitteeEmailHash() != null) {
                        Map<String,Object> routeArgs = new HashMap<String,Object>(1);
                        routeArgs.put("token", submission.getCommitteeEmailHash());
                        ActionDefinition advisorAction = Router.reverse("Advisor.review",routeArgs);
                        advisorAction.absolute();
                        value = advisorAction.url;
                    }
                    break;

                case SUBMISSION_ID:
                    value = String.valueOf(submission.getId());
                    break;

                //noinspection deprecation
                case SEPARATOR:
                    value = File.separator;
                    break;
            }
            // Save value, even if it was null, and return.
            super.put(key, value);
            return value;
        }
    }

	/**
	 * Initializes the customizer with a submission from which to draw customized string variable replacement values.
	 * @param submission the submission for which to customize variables
	 */
    // TODO make the Lazy Map thing a separate class, and make this class parameterized on it.
	public StringCustomizer(Submission submission) {
        if (this.submission!=null) {
            throw new IllegalStateException("StringCustomizers are not reusable. Cannot change submission.");
        }
		this.submission = submission;
		parameters = new LazyParameterMap(Variable.values().length);
    }

	public static String applyParameterSubstitution(String string, Map<String, String> parameters) {
        // TODO find everyone who uses this and switch them over
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
     * @return the new string with replacements / eliminations done, or null if the input string was null.
     */
    public String customizeString(String string) {
        if (string==null) {
            return null;
        }
        if (string.startsWith(TEMPLATE_MODE)) {
            return customizeTemplate(string.substring(TEMPLATE_MODE.length()));
        }

        final StringBuilder result = new StringBuilder(string.length()*2);
        final int length = string.length();
        int prevPos = 0;
        int pos = string.indexOf('{');

        // Check string in chunks starting with each '{'
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
                for (String varName : parameters.keySet()) {
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
                if (foundVariable && chainPos+ OR.length()<=length && string.substring(chainPos, chainPos+ OR.length()).equals(OR)) {
                    // Fallback token right after valid variable name. Keep consuming the fallback chain.
                    chainPos += OR.length();
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

    /**
     * Renders the supplied string using Play's template engine, supplying the given parameters as
     * arguments to the template. Useful for more complicated substitution logic.
     * @param string the template to run, as a string
     * @return the rendered template result
     */
    public String customizeTemplate(String string) {
        // make a key so the compiled template can be cached in case we see this exact same string again later
        String key = String.valueOf(string.hashCode());
        Template template = TemplateLoader.load(key, string);

        /// render the template with the given parameters (have to change type to Map<String, Object>)
        Map<String, Object> templateBinding = new HashMap<String,Object>(parameters);
        return template.render(templateBinding);
    }

}