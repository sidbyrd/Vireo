package org.tdl.vireo.services;

import org.junit.Before;
import org.junit.Test;
import org.tdl.vireo.model.*;
import org.tdl.vireo.model.jpa.JpaSubmissionRepositoryImpl;
import org.tdl.vireo.security.impl.LDAPAuthenticationMethodImpl;
import play.modules.spring.Spring;
import play.test.UnitTest;

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

    @Test public void testFallback_tokensNoVars() {
        final Map<String, String> params = StringVariableReplacement.setParameters(sub);

        final String test = FB+"this has no vars in it, but does have non-active uses of a "+FB+" token."+FB;
        final String result = StringVariableReplacement.applyParameterSubstitutionWithFallback(test, params);
        assertEquals(test, result); // no tokens removed, nothing altered
    }

    @Test public void testFallback_Special() {
        final Map<String, String> params = StringVariableReplacement.setParameters(sub);

        final String test = "The second of {FIRST_NAME||STUDENT_ID||LAST_NAME} should work";
        final String resultWithFallback = StringVariableReplacement.applyParameterSubstitutionWithFallback(test, params);
        assertEquals("The second of S01234 should work", resultWithFallback); // choose the middle substitution
    }

    @Test public void testFallback_VarsNoTokens() {
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
