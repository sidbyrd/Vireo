package org.tdl.vireo.services;

import org.junit.Before;
import org.junit.Test;
import org.tdl.vireo.model.MockPerson;
import org.tdl.vireo.model.MockSubmission;
import org.tdl.vireo.security.impl.LDAPAuthenticationMethodImpl;
import org.tdl.vireo.state.MockState;
import play.test.UnitTest;

import java.io.File;
import java.util.Map;

import static org.tdl.vireo.services.SubmissionParams.Variable.LAST_NAME;
import static org.tdl.vireo.services.SubmissionParams.Variable.STUDENT_ID;

public class SubmissionParamsTest extends UnitTest {
	private MockSubmission sub = null;

	@Before
	public void setup() {
		MockPerson nobody = new MockPerson();
		nobody.addPreference(LDAPAuthenticationMethodImpl.personPrefKeyForStudentID, "S01234");
		nobody.setEmail("nobody@gmail.com");
		nobody.setNetId("nobody");

		sub = new MockSubmission();
		sub.submitter = nobody;
	}

	@Test
	public void testSubmissionParameters_completeness() {
		// test that all Variables get a value, and that nothing else does.
		sub.setStudentFirstName("Zanoni");
		sub.setStudentMiddleName("the");
		sub.setStudentLastName("Ineffable");
		sub.setStudentBirthYear(1842);
		sub.setDocumentTitle("title");
		sub.setDocumentType("type");
		final MockState state = new MockState();
		state.displayName = "status";
		sub.setState(state);
		sub.setGraduationYear(2014);
		sub.setGraduationMonth(2);
		final MockPerson assignee = new MockPerson();
		assignee.firstName = "first";
		assignee.lastName = "last";
		sub.setAssignee(assignee);
		sub.setCommitteeEmailHash("whatever");
		sub.id=999L;

		final Map<String, String> params = new SubmissionParams(sub);
		assertEquals("Zanoni Ineffable", params.get(SubmissionParams.Variable.FULL_NAME.name()));
		assertEquals("Zanoni", params.get(SubmissionParams.Variable.FIRST_NAME.name()));
		assertEquals("Ineffable", params.get(LAST_NAME.name()));
		assertEquals("S01234", params.get(STUDENT_ID.name()));
		assertEquals("nobody", params.get(SubmissionParams.Variable.STUDENT_NETID.name()));
		assertEquals("nobody@gmail.com", params.get(SubmissionParams.Variable.STUDENT_EMAIL.name()));
		assertEquals("title", params.get(SubmissionParams.Variable.DOCUMENT_TITLE.name()));
		assertEquals("type", params.get(SubmissionParams.Variable.DOCUMENT_TYPE.name()));
		assertEquals("status", params.get(SubmissionParams.Variable.SUBMISSION_STATUS.name()));
		assertEquals("March, 2014", params.get(SubmissionParams.Variable.GRAD_SEMESTER.name()));
		assertEquals("first last", params.get(SubmissionParams.Variable.SUBMISSION_ASSIGNED_TO.name()));
		assertTrue(params.get(SubmissionParams.Variable.STUDENT_URL.name()).endsWith("submit"));
		assertTrue(params.get(SubmissionParams.Variable.ADVISOR_URL.name()).endsWith("/advisor/whatever/review"));
		assertEquals("999", params.get(SubmissionParams.Variable.SUBMISSION_ID.name()));
		assertEquals(File.separator, params.get(SubmissionParams.Variable.SEPARATOR.name()));
		assertEquals(15, params.size()); // make sure there's nothing left over.
	}

	@Test public void testSubmissionParameters_gradYearNoMonth() {
		sub.setGraduationYear(2014);
		final Map<String, String> params = new SubmissionParams(sub);
		assertEquals("2014", params.get(SubmissionParams.Variable.GRAD_SEMESTER.name()));
	}

	@Test public void testSubmissionParameters_minimal() {
		sub = new MockSubmission(); // as empty as possible
		// make the submission having empty values doesn't cause errors.
		final Map<String, String> params = new SubmissionParams(sub);
		assertNull(params.get(SubmissionParams.Variable.FULL_NAME.name()));
		assertNull(params.get(SubmissionParams.Variable.FIRST_NAME.name()));
		assertNull(params.get(LAST_NAME.name()));
		assertNull(params.get(STUDENT_ID.name()));
		assertNull(params.get(SubmissionParams.Variable.STUDENT_NETID.name()));
		assertNull(params.get(SubmissionParams.Variable.STUDENT_EMAIL.name()));
		assertNull(params.get(SubmissionParams.Variable.DOCUMENT_TITLE.name()));
		assertNull(params.get(SubmissionParams.Variable.DOCUMENT_TYPE.name()));
		assertNull(params.get(SubmissionParams.Variable.SUBMISSION_STATUS.name()));
		assertNull(params.get(SubmissionParams.Variable.GRAD_SEMESTER.name()));
		assertNull(params.get(SubmissionParams.Variable.SUBMISSION_ASSIGNED_TO.name()));
		assertNotNull(params.get(SubmissionParams.Variable.STUDENT_URL.name()));
		assertNull(params.get(SubmissionParams.Variable.ADVISOR_URL.name()));
		assertNotNull(params.get(SubmissionParams.Variable.SUBMISSION_ID.name()));
		assertNotNull(params.get(SubmissionParams.Variable.SEPARATOR.name()));
		assertEquals(15, params.size()); // make sure there's nothing left over.

		// test that the old behavior for SUBMISSION_ASSIGNED_TO can be duplicated
		// cleanly and more flexibly with a user-chosen default value
		assertEquals("n/a", StringVariableReplacement.applyParameterSubstitution("{SUBMISSION_ASSIGNED_TO||n/a}", params));
	}

	// TODO: test correctness of laziness
	// TODO: test Variable.names()
}
