package org.tdl.vireo.services;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.TransformerUtils;
import org.apache.commons.lang.StringUtils;
import org.tdl.vireo.model.NameFormat;
import org.tdl.vireo.model.Preference;
import org.tdl.vireo.model.Submission;
import play.mvc.Router;

import java.io.File;
import java.text.DateFormatSymbols;
import java.util.*;

import static org.tdl.vireo.security.impl.LDAPAuthenticationMethodImpl.personPrefKeyForStudentID;

/**
 * Like a regular HashMap<String, String>, except that it always "has" values
 * related to the Submission with which it's initialized, which are lazily evaluated
 * when requested.
 */
public class SubmissionParams extends HashMap<String, String> {


/* *********************************************************************
 *                        Variables definition
 * *********************************************************************/

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
		/** degree level at a string: "MASTERS", "DOCTORAL", etc. */
		DEGREE_LEVEL,
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
		/** Platform File.separator().
		 * Was more useful before code improve to not require a trailing separator on Packagers'
		 * customized directory names. Now more likely to cause problems than be useful if included
		 * in most configurable settings. */
		@Deprecated
		SEPARATOR;

		/**
		 * Gets all the names of these Variables.
		 * Unmodifiable and created only once for efficiency.
		 * @return Set of all Variable names.
		 */
		public static Set<String> names() {
			return namesLazyInit.names;
		}
		private static class namesLazyInit {
			@SuppressWarnings("unchecked")
			private static final Set<String>names = (Set<String>) Collections.unmodifiableSet(new LinkedHashSet<String>(CollectionUtils.collect(Arrays.asList(Variable.values()), TransformerUtils.stringValueTransformer())));
		}
	}


/* *********************************************************************
 *                   Constructor with a Submission
 * *********************************************************************/

	/** the submission from which to draw customized values, stored so we can lazily evaluate against it */
	private final Submission submission;

	/** all possible Variable names, plus all externally put keys */
	private final Set<String> keys;

	public SubmissionParams(Submission submission) {
		super(Variable.names().size());
		this.submission = submission;
		// starting keys I "have" are all the Variable names.
		keys = new LinkedHashSet<String>(Variable.names()); // linked for fast iteration
	}


/* *********************************************************************
 *                Lazy-evaluating Map<String, String>
 * *********************************************************************/

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
		throw new UnsupportedOperationException("SubmissionParams.values() operations on values are unsupported");
	}

	@Override
	public Set<Map.Entry<String,String>> entrySet() {
		// We have to have this one because Play's template engine calls it.
		// So make a simple lazy-evaluating closure entry set to return.
		final Set<Map.Entry<String,String>> entrySet = new HashSet<Map.Entry<String, String>>(keys.size());
		for (String key : keys) {
			final Map.Entry<String, String> entry = new SimpleEntry<String, String>(key, null) {
				@Override public String getValue() {
					return get(getKey());
				}
			};
			entrySet.add(entry);
		}
		return entrySet;
	}

	/**
	 * Any key to look up. If it's a Variable name, value will be computed the first time.
	 * If it's just a regular string, there will be a value if it has been put() before.
	 * @param keyObj string or Variable name to look up
	 * @return value retrieved, which may be null; or the value null
	 */
	@Override
	public String get(Object keyObj) {
		// If we have that value already, this is easy
		String key = (String)keyObj;
		if (super.containsKey(key)) {
			return super.get(key);
		}
		// Get the Variable being requested
		Variable var;
		try {
			var = Variable.valueOf(key);
		} catch (IllegalArgumentException e) {
			return null;
		}
		// Retrieve value from the Submission, save it (even if it was null), and return.
		String value = getParameter(var);
		super.put(key, value); // don't add to varNames because it should already be there.
		return value;
	}

	@Override
	public String put(String key, String value) {
		keys.add(key); // harmless if it's already there
		return super.put(key, value);
	}

	@Override
	public String remove(Object keyObj) {
		if (!Variable.names().contains((String)keyObj)) {
			// not a variable: remove the key from my key Set too
			keys.remove((String)keyObj);
		}
		return super.remove(keyObj); // un-compute, if key was a Variable name
	}

	@Override
	public void clear() {
		super.clear();
		// set keys back to default
		keys.clear();
		keys.addAll(Variable.names());
	}

	@Override
	public boolean containsKey(Object keyObj) {
		return keys.contains((String)keyObj);
	}

	@Override
	public boolean containsValue(Object value) {
		throw new UnsupportedOperationException("SubmissionParams.containsValue(): operations on values are unsupported");
	}


/* *********************************************************************
 *                       Lazy parameter extraction
 * *********************************************************************/

	/**
	 * Extract the value of the requested Variable from the Submission.
	 * @param var the Variable to compute
	 * @return the value of the variable, which may have a value of null.
	 */
	public String getParameter(Variable var) {
		switch (var) {

			case FULL_NAME:
				if (submission.getStudentFirstName() != null || submission.getStudentLastName() != null) {
					return submission.getStudentFormattedName(NameFormat.FIRST_LAST);
				}
				break;

			case FIRST_NAME:
				if (submission.getStudentFirstName() != null) {
					return submission.getStudentFirstName();
				}
				break;

			case LAST_NAME:
				if (submission.getStudentLastName() != null) {
					return submission.getStudentLastName();
				}
				break;

			case STUDENT_ID:
				// if student ID becomes a regular Person property, simplify this to match, of course
				if (submission.getSubmitter() != null) {
					Preference studentIdPref = submission.getSubmitter().getPreference(personPrefKeyForStudentID);
					if (studentIdPref != null && !StringUtils.isBlank(studentIdPref.getValue())) {
						return studentIdPref.getValue();
					}
				}
				break;

			case STUDENT_NETID:
				if (submission.getSubmitter() != null && submission.getSubmitter().getNetId() != null) {
					return submission.getSubmitter().getNetId();
				}
				break;

			case STUDENT_EMAIL:
				if (submission.getSubmitter() != null && submission.getSubmitter().getEmail() != null) {
					return submission.getSubmitter().getEmail();
				}
				break;

			case DOCUMENT_TITLE:
				if (submission.getDocumentTitle() != null) {
					return submission.getDocumentTitle();
				}
				break;

			case DOCUMENT_TYPE:
				if (submission.getDocumentType() != null) {
					return submission.getDocumentType();
				}
				break;

			case DEGREE_LEVEL:
				if (submission.getDegreeLevel() != null) {
					return submission.getDegreeLevel().toString();
				}
				break;

			case SUBMISSION_STATUS:
				if (submission.getState() != null) {
					return submission.getState().getDisplayName();
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
					return gradSemester;
				}
				break;

			case SUBMISSION_ASSIGNED_TO:
				if (submission.getAssignee() != null) {
					return submission.getAssignee().getFormattedName(NameFormat.FIRST_LAST);
				} // if you want the old default of "n/a", use {SUBMISSION_ASSIGNED_TO||n/a}
				break;

			case STUDENT_URL:
				// URL for the student to view their submission(s)
				Router.ActionDefinition studentAction = Router.reverse("Student.submissionList");
				studentAction.absolute();
				return studentAction.url;
				// break; // unreachable statement, but good form to keep this line as a reminder anyway.

			case ADVISOR_URL:
				// Advisor url for reviews
				if (submission.getCommitteeEmailHash() != null) {
					Map<String,Object> routeArgs = new HashMap<String,Object>(1);
					routeArgs.put("token", submission.getCommitteeEmailHash());
					Router.ActionDefinition advisorAction = Router.reverse("Advisor.review",routeArgs);
					advisorAction.absolute();
					return advisorAction.url;
				}
				break;

			case SUBMISSION_ID:
				return String.valueOf(submission.getId());
				// break; // unreachable statement, but good form to keep this line as a reminder anyway.

			//noinspection deprecation
			case SEPARATOR:
				return File.separator;
				// break; // unreachable statement, but good form to keep this line as a reminder anyway.
		}
		// didn't have a value in this Submission
		return null;

	}

}
