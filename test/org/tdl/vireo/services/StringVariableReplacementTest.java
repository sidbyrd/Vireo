package org.tdl.vireo.services;

import org.junit.Before;
import org.junit.Test;
import org.tdl.vireo.model.MockPerson;
import org.tdl.vireo.model.MockSubmission;
import org.tdl.vireo.security.impl.LDAPAuthenticationMethodImpl;
import org.tdl.vireo.state.MockState;
import play.test.UnitTest;
import org.tdl.vireo.services.StringVariableReplacement.Variable;

import java.io.File;
import java.util.Map;

public class StringVariableReplacementTest extends UnitTest {

    // shorter, for convenience.
    private static final String FB = StringVariableReplacement.FALLBACK;

    private MockSubmission sub = null;

    @Before public void setup() {
        MockPerson nobody = new MockPerson();
        nobody.addPreference(LDAPAuthenticationMethodImpl.personPrefKeyForStudentID, "S01234");
        nobody.setEmail("nobody@gmail.com");
        nobody.setNetId("nobody");

        sub = new MockSubmission();
        sub.submitter = nobody;
    }

    @Test public void testSetParameters_completeness() {
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

        Map<String, String> params = StringVariableReplacement.setParameters(sub);
        assertEquals("Zanoni Ineffable", params.remove(Variable.FULL_NAME.name()));
        assertEquals("Zanoni", params.remove(Variable.FIRST_NAME.name()));
        assertEquals("Ineffable", params.remove(Variable.LAST_NAME.name()));
        assertEquals("S01234", params.remove(Variable.STUDENT_ID.name()));
        assertEquals("nobody", params.remove(Variable.STUDENT_NETID.name()));
        assertEquals("nobody@gmail.com", params.remove(Variable.STUDENT_EMAIL.name()));
        assertEquals("title", params.remove(Variable.DOCUMENT_TITLE.name()));
        assertEquals("type", params.remove(Variable.DOCUMENT_TYPE.name()));
        assertEquals("status", params.remove(Variable.SUBMISSION_STATUS.name()));
        assertEquals("March, 2014", params.remove(Variable.GRAD_SEMESTER.name()));
        assertEquals("first last", params.remove(Variable.SUBMISSION_ASSIGNED_TO.name()));
        assertTrue(params.remove(Variable.STUDENT_URL.name()).endsWith("submit"));
        assertTrue(params.remove(Variable.ADVISOR_URL.name()).endsWith("/advisor/whatever/review"));
        assertEquals("999", params.remove(Variable.SUBMISSION_ID.name()));
        assertEquals(File.separator, params.remove(Variable.SEPARATOR.name()));
        assertEquals(0, params.size()); // make sure there aren't any non-Variable things in there.
    }

    @Test public void testSetParameters_gradYearNoMonth() {
        sub.setGraduationYear(2014);
        Map<String, String> params = StringVariableReplacement.setParameters(sub);
        assertEquals("2014", params.remove(Variable.GRAD_SEMESTER.name()));
    }

    @Test public void testSetParameters_minimal() {
        sub = new MockSubmission(); // as empty as possible
        Map<String, String> params = StringVariableReplacement.setParameters(sub);
        assertEquals("n/a", params.remove(Variable.SUBMISSION_ASSIGNED_TO.name()));
        params.remove(Variable.STUDENT_URL.name());
        assertEquals(String.valueOf(sub.getId()), params.remove(Variable.SUBMISSION_ID.name()));
        params.remove(Variable.SEPARATOR.name());
        assertEquals(0, params.size()); // that should be all
    }

    @Test public void testFallback_tokensNoVars() {
        final Map<String, String> params = StringVariableReplacement.setParameters(sub);

        // starts and ends with invalid fallback token
        String test = FB+"this has no vars in it, but does have non-active uses of a "+FB+" token."+FB;
        String result = StringVariableReplacement.applyParameterSubstitutionWithFallback(test, params);
        assertEquals(test, result); // no tokens removed, nothing altered

        // starts and ends with plain text, back-to-back invalid fallback tokens in the middle
        test = "This has some "+FB+FB+" tokens in the middle";
        result = StringVariableReplacement.applyParameterSubstitutionWithFallback(test, params);
        assertEquals(test, result); // no tokens removed, nothing altered
    }

    @Test public void testFallback_yes_no() {
        final Map<String, String> params = StringVariableReplacement.setParameters(sub);

        final String test = "The first of {"+ Variable.STUDENT_ID+FB+Variable.LAST_NAME+"} should work";
        assertTrue(params.containsKey(Variable.STUDENT_ID.name()));
        assertFalse(params.containsKey(Variable.LAST_NAME.name()));
        final String resultWithFallback = StringVariableReplacement.applyParameterSubstitutionWithFallback(test, params);
        assertEquals("The first of S01234 should work", resultWithFallback); // choose the first substitution
    }

    @Test public void testFallback_no_yes_no() {
        final Map<String, String> params = StringVariableReplacement.setParameters(sub);

        final String test = "The second of {"+ Variable.FIRST_NAME+FB+Variable.STUDENT_ID+FB+Variable.LAST_NAME+"} should work";
        assertFalse(params.containsKey(Variable.FIRST_NAME.name()));
        assertTrue(params.containsKey(Variable.STUDENT_ID.name()));
        assertFalse(params.containsKey(Variable.LAST_NAME.name()));
        final String resultWithFallback = StringVariableReplacement.applyParameterSubstitutionWithFallback(test, params);
        assertEquals("The second of S01234 should work", resultWithFallback); // choose the middle substitution
    }

    @Test public void testFallback_no_yes_yes() {
        final Map<String, String> params = StringVariableReplacement.setParameters(sub);

        final String test = "The second of {"+ Variable.FIRST_NAME+FB+Variable.STUDENT_ID+FB+Variable.STUDENT_NETID+"} should work";
        assertFalse(params.containsKey(Variable.FIRST_NAME.name()));
        assertTrue(params.containsKey(Variable.STUDENT_ID.name()));
        assertTrue(params.containsKey(Variable.STUDENT_NETID.name()));
        final String resultWithFallback = StringVariableReplacement.applyParameterSubstitutionWithFallback(test, params);
        assertEquals("The second of S01234 should work", resultWithFallback); // choose the middle substitution
    }

    @Test public void testFallback_no_no_no() {
        final Map<String, String> params = StringVariableReplacement.setParameters(sub);

        final String test = "None of {"+ Variable.FIRST_NAME+FB+Variable.FULL_NAME+FB+Variable.LAST_NAME+"} should work";
        assertFalse(params.containsKey(Variable.FIRST_NAME.name()));
        assertFalse(params.containsKey(Variable.FULL_NAME.name()));
        assertFalse(params.containsKey(Variable.LAST_NAME.name()));
        final String resultWithFallback = StringVariableReplacement.applyParameterSubstitutionWithFallback(test, params);
        assertEquals("None of  should work", resultWithFallback); // remove all substitutions
    }

    @Test public void testFallback_no_no_default() {
        final Map<String, String> params = StringVariableReplacement.setParameters(sub);

        final String test = "This {"+ Variable.FIRST_NAME+FB+Variable.FULL_NAME+FB+"chain} should use the default";
        assertFalse(params.containsKey(Variable.FIRST_NAME.name()));
        assertFalse(params.containsKey(Variable.FULL_NAME.name()));
        final String resultWithFallback = StringVariableReplacement.applyParameterSubstitutionWithFallback(test, params);
        assertEquals("This chain should use the default", resultWithFallback); // use default
    }

    @Test public void testFallback_yes_default() {
        final Map<String, String> params = StringVariableReplacement.setParameters(sub);

        final String test = "This chain {"+ Variable.STUDENT_ID+FB+"default} should not get to the default";
        assertTrue(params.containsKey(Variable.STUDENT_ID.name()));
        final String resultWithFallback = StringVariableReplacement.applyParameterSubstitutionWithFallback(test, params);
        assertEquals("This chain S01234 should not get to the default", resultWithFallback);
    }

    @Test public void testFallback_defaultOnly() {
        final Map<String, String> params = StringVariableReplacement.setParameters(sub);

        final String test = "You can't start with a {default}.";
        final String resultWithFallback = StringVariableReplacement.applyParameterSubstitutionWithFallback(test, params);
        assertEquals(test, resultWithFallback); // invalid chain, unchanged
    }

    @Test public void testFallback_var_default_var() {
        final Map<String, String> params = StringVariableReplacement.setParameters(sub);

        final String test = "The default {"+Variable.STUDENT_ID+FB+"default"+FB+Variable.DOCUMENT_TITLE+"} has to be last.";
        final String resultWithFallback = StringVariableReplacement.applyParameterSubstitutionWithFallback(test, params);
        assertEquals(test, resultWithFallback); // invalid chain, unchanged
    }

    @Test public void testFallback_rogueVar() {
        final Map<String, String> params = StringVariableReplacement.setParameters(sub);
        params.put("ROGUE", "rogue");

        String test = "This {"+Variable.STUDENT_ID+FB+"ROGUE"+FB+"default} is a standard var.";
        String resultWithFallback = StringVariableReplacement.applyParameterSubstitutionWithFallback(test, params);
        assertEquals("This S01234 is a standard var.", resultWithFallback);

        test = "This {"+Variable.FULL_NAME+FB+"ROGUE"+FB+"default} is a rogue var.";
        resultWithFallback = StringVariableReplacement.applyParameterSubstitutionWithFallback(test, params);
        assertEquals("This rogue is a rogue var.", resultWithFallback);

        params.remove("ROGUE");
        // same test, but rogue var is now invalid.
        resultWithFallback = StringVariableReplacement.applyParameterSubstitutionWithFallback(test, params);
        assertEquals(test, resultWithFallback); // unchanged

        test = "This {"+Variable.FULL_NAME+FB+"ROGUE} is a rogue var turned default.";
        resultWithFallback = StringVariableReplacement.applyParameterSubstitutionWithFallback(test, params);
        assertEquals("This ROGUE is a rogue var turned default.", resultWithFallback);
    }

    @Test public void testFallback_var_default_default() {
        final Map<String, String> params = StringVariableReplacement.setParameters(sub);

        final String test = "You can't have {"+Variable.STUDENT_ID+FB+"one"+FB+"two} defaults.";
        final String resultWithFallback = StringVariableReplacement.applyParameterSubstitutionWithFallback(test, params);
        assertEquals(test, resultWithFallback); // invalid chain, unchanged
    }

    @Test public void testFallback_no_null_yes() {
        final Map<String, String> params = StringVariableReplacement.setParameters(sub);
        params.put(Variable.DOCUMENT_TITLE.name(), null);

        String test = "Null is allowed {"+Variable.DOCUMENT_TYPE+FB+Variable.DOCUMENT_TITLE+FB+Variable.STUDENT_ID+"} but won't match.";
        String resultWithFallback = StringVariableReplacement.applyParameterSubstitutionWithFallback(test, params);
        assertEquals("Null is allowed S01234 but won't match.", resultWithFallback);

        params.put("ROGUE", null);

        test = "Even with a rogue var {"+Variable.DOCUMENT_TYPE+FB+"ROGUE"+FB+Variable.STUDENT_ID+'}';
        resultWithFallback = StringVariableReplacement.applyParameterSubstitutionWithFallback(test, params);
        assertEquals("Even with a rogue var S01234", resultWithFallback);
    }

    @Test public void testFallback_firstAndLast() {
        final Map<String, String> params = StringVariableReplacement.setParameters(sub);

        String test = "{"+ Variable.FIRST_NAME+FB+Variable.FULL_NAME+"} empty start and full end {"+Variable.STUDENT_ID+FB+Variable.STUDENT_NETID+"}";
        assertFalse(params.containsKey(Variable.FIRST_NAME.name()));
        assertFalse(params.containsKey(Variable.FULL_NAME.name()));
        assertTrue(params.containsKey(Variable.STUDENT_ID.name()));
        assertTrue(params.containsKey(Variable.STUDENT_NETID.name()));
        String resultWithFallback = StringVariableReplacement.applyParameterSubstitutionWithFallback(test, params);
        assertEquals(" empty start and full end S01234", resultWithFallback);

        test = "{"+ Variable.FIRST_NAME+FB+"default}";
        resultWithFallback = StringVariableReplacement.applyParameterSubstitutionWithFallback(test, params);
        assertEquals("default", resultWithFallback);
    }

    @Test public void testFallback_braceNoise() {
        final Map<String, String> params = StringVariableReplacement.setParameters(sub);
        final String test = "{}}{{"+Variable.STUDENT_ID+"}{";
        assertTrue(params.containsKey(Variable.STUDENT_ID.name()));
        final String resultWithFallback = StringVariableReplacement.applyParameterSubstitutionWithFallback(test, params);
        assertEquals("{}}{S01234{", resultWithFallback); // unchanged
    }

    @Test public void testFallback_malformed() {
        final Map<String, String> params = StringVariableReplacement.setParameters(sub);

        String test = "{}";
        String resultWithFallback = StringVariableReplacement.applyParameterSubstitutionWithFallback(test, params);
        assertEquals(test, resultWithFallback); // unchanged

        test = '{'+FB+'}';
        resultWithFallback = StringVariableReplacement.applyParameterSubstitutionWithFallback(test, params);
        assertEquals(test, resultWithFallback); //unchanged

        test = "{"+Variable.STUDENT_ID;
        resultWithFallback = StringVariableReplacement.applyParameterSubstitutionWithFallback(test, params);
        assertEquals(test, resultWithFallback); //unchanged

        test = "{"+Variable.STUDENT_ID+FB;
        resultWithFallback = StringVariableReplacement.applyParameterSubstitutionWithFallback(test, params);
        assertEquals(test, resultWithFallback); //unchanged

        test = Variable.STUDENT_ID+FB+'}';
        resultWithFallback = StringVariableReplacement.applyParameterSubstitutionWithFallback(test, params);
        assertEquals(test, resultWithFallback); //unchanged

        test = "{"+Variable.STUDENT_ID+Variable.STUDENT_ID+'}';
        resultWithFallback = StringVariableReplacement.applyParameterSubstitutionWithFallback(test, params);
        assertEquals(test, resultWithFallback); //unchanged

        test = "{"+Variable.STUDENT_ID+'}'+Variable.STUDENT_ID+'}';
        resultWithFallback = StringVariableReplacement.applyParameterSubstitutionWithFallback(test, params);
        assertEquals("S01234"+Variable.STUDENT_ID+'}', resultWithFallback); // first valid, second not

        test = "{"+Variable.STUDENT_ID+'{'+Variable.STUDENT_ID+'}';
        resultWithFallback = StringVariableReplacement.applyParameterSubstitutionWithFallback(test, params);
        assertEquals("{"+Variable.STUDENT_ID+"S01234", resultWithFallback); // second valid, first not
    }

    @Test public void testDefault_malformed() {
        final Map<String, String> params = StringVariableReplacement.setParameters(sub);

        String test = "{"+Variable.STUDENT_ID+FB+"default"+FB+'}';
        String resultWithFallback = StringVariableReplacement.applyParameterSubstitutionWithFallback(test, params);
        assertEquals(test, resultWithFallback); // unchanged

        test = "{"+Variable.STUDENT_ID+FB+"default{}";
        resultWithFallback = StringVariableReplacement.applyParameterSubstitutionWithFallback(test, params);
        assertEquals(test, resultWithFallback); //unchanged

        test = "{"+Variable.STUDENT_ID+"default}"; // no FALLBACK
        resultWithFallback = StringVariableReplacement.applyParameterSubstitutionWithFallback(test, params);
        assertEquals(test, resultWithFallback); //unchanged
    }

    @Test public void testFallback_VarsOnlyNoFallback() {
        final Map<String, String> params = StringVariableReplacement.setParameters(sub);

        // starts and ends with vars
        String test = "{STUDENT_NETID}This has no FB tokens but does have vars like {STUDENT_ID} and {STUDENT_EMAIL}";
        String resultNoFallback = StringVariableReplacement.applyParameterSubstitution(test, params);
        String resultWithFallback = StringVariableReplacement.applyParameterSubstitutionWithFallback(test, params);
        assertEquals(resultNoFallback, resultWithFallback); // same as no fallback version

        // starts and ends with plain text, back-to-back vars in the middle
        test = "This tests vars like {STUDENT_NETID}{STUDENT_ID} back-to-back in the middle.";
        resultNoFallback = StringVariableReplacement.applyParameterSubstitution(test, params);
        resultWithFallback = StringVariableReplacement.applyParameterSubstitutionWithFallback(test, params);
        assertEquals(resultNoFallback, resultWithFallback); // same as no fallback version
    }
}
