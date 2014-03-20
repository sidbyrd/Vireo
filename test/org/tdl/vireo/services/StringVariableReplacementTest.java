package org.tdl.vireo.services;

import org.junit.Before;
import org.junit.Test;
import org.tdl.vireo.model.MockPerson;
import org.tdl.vireo.model.MockSubmission;
import org.tdl.vireo.security.impl.LDAPAuthenticationMethodImpl;
import play.test.UnitTest;
import org.tdl.vireo.services.StringVariableReplacement.Variable;

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

    // Tests for new methods added to class. Existing methods remain untested.

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

    @Test public void testFallback_var_default_default() {
        final Map<String, String> params = StringVariableReplacement.setParameters(sub);

        final String test = "You can't have {"+Variable.STUDENT_ID+FB+"one"+FB+"two} defaults.";
        final String resultWithFallback = StringVariableReplacement.applyParameterSubstitutionWithFallback(test, params);
        assertEquals(test, resultWithFallback); // invalid chain, unchanged
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

    @Test public void testFallback_noise() {
        final Map<String, String> params = StringVariableReplacement.setParameters(sub);
        final String test = "}{"+Variable.STUDENT_ID+"}{";
        assertTrue(params.containsKey(Variable.STUDENT_ID.name()));
        final String resultWithFallback = StringVariableReplacement.applyParameterSubstitutionWithFallback(test, params);
        assertEquals("}S01234{", resultWithFallback); // unchanged
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
        assertEquals(resultNoFallback, resultWithFallback); // same

        // starts and ends with plain text, back-to-back vars in the middle
        test = "This tests vars like {STUDENT_NETID}{STUDENT_ID} back-to-back in the middle.";
        resultNoFallback = StringVariableReplacement.applyParameterSubstitution(test, params);
        resultWithFallback = StringVariableReplacement.applyParameterSubstitutionWithFallback(test, params);
        assertEquals(resultNoFallback, resultWithFallback); // same
    }
}
