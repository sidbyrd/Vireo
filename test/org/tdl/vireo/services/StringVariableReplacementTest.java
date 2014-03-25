package org.tdl.vireo.services;

import org.junit.Before;
import org.junit.Test;
import org.tdl.vireo.export.impl.FilePackagerImpl;
import org.tdl.vireo.model.MockPerson;
import org.tdl.vireo.model.MockSubmission;
import org.tdl.vireo.security.impl.LDAPAuthenticationMethodImpl;
import org.tdl.vireo.state.MockState;
import play.modules.spring.Spring;
import play.test.UnitTest;
import org.tdl.vireo.services.StringCustomizer.Variable;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.tdl.vireo.services.StringCustomizer.*;
import static org.tdl.vireo.services.StringCustomizer.Variable.*;

public class StringVariableReplacementTest extends UnitTest {

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

        Map<String, String> params = StringCustomizer.setParameters(sub);
        assertEquals("Zanoni Ineffable", params.remove(Variable.FULL_NAME.name()));
        assertEquals("Zanoni", params.remove(Variable.FIRST_NAME.name()));
        assertEquals("Ineffable", params.remove(LAST_NAME.name()));
        assertEquals("S01234", params.remove(STUDENT_ID.name()));
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
        Map<String, String> params = StringCustomizer.setParameters(sub);
        assertEquals("2014", params.remove(Variable.GRAD_SEMESTER.name()));
    }

    @Test public void testSetParameters_minimal() {
        sub = new MockSubmission(); // as empty as possible
        Map<String, String> params = StringCustomizer.setParameters(sub);
        assertEquals("n/a", params.remove(Variable.SUBMISSION_ASSIGNED_TO.name()));
        params.remove(Variable.STUDENT_URL.name());
        assertEquals(String.valueOf(sub.getId()), params.remove(Variable.SUBMISSION_ID.name()));
        params.remove(Variable.SEPARATOR.name());
        assertEquals(0, params.size()); // that should be all
    }

    //TODO test: {SUBMISSION_ASSIGNED_TO||n/a}

    @Test public void testModeSelection() {
        Map<String, String> params = StringCustomizer.setParameters(sub);

        // This string will be interpreted differently by each mode.
        final String indicator = "simple-{"+ STUDENT_ID +"}-fallback-{"+ FIRST_NAME+OR+STUDENT_ID +"}-template-${ "+STUDENT_ID+" }-end";

        // simple replacement mode - only gets the first part
        assertEquals("simple-S01234-fallback-{" + FIRST_NAME + OR + STUDENT_ID + "}-template-${ " + STUDENT_ID + " }-end",
                applyParameterSubstitution(indicator, params));

        // fallback mode - gets the first and second parts
        assertEquals("simple-S01234-fallback-S01234-template-${ "+STUDENT_ID+" }-end",
                applyParameterSubstitution(FALLBACK_MODE+indicator, params));

        // template mode - only gets the third part
        assertEquals("simple-{"+ STUDENT_ID +"}-fallback-{"+ FIRST_NAME+OR+STUDENT_ID +"}-template-S01234-end",
                applyParameterSubstitution(TEMPLATE_MODE + indicator, params));
    }

    @Test public void testTemplate() {
        // I don't have to thoroughly test everything about how templates are applied. I assume Play does that
        // correctly. Instead, just test that they are applied at all, and the arguments are supplied correctly.
        Map<String, String> params = new HashMap(4);
        params.put(STUDENT_ID.name(), "S01234");
        params.put(STUDENT_NETID.name(), "nobody");
        params.put(STUDENT_EMAIL.name(), "root@aol.com");
        params.put(SUBMISSION_ID.name(), "999");

        final String templated = "template-#{if "+STUDENT_ID+".equals(\"newID\")}match#{/if}#{else}nomatch#{/else}";
        assertEquals("template-nomatch", applyTemplateParameterSubstitution(templated, params));
        params.put(STUDENT_ID.name(), "newID");
        assertEquals("template-match", applyTemplateParameterSubstitution(templated, params));

        // want to see the configuration need that drove the template parameter substitution mode to start with?
        final String onBase = "{TEMPLATE_MODE}#{if STUDENT_ID!=null}sid=${STUDENT_ID}#{/if}#{elseif STUDENT_NETID!=null}netid=${STUDENT_NETID}#{/elseif}#{else}email=${STUDENT_EMAIL}#{/else},thesis-id=${SUBMISSION_ID}";
        assertEquals("sid=newID,thesis-id=999", applyParameterSubstitution(onBase, params));
        params.remove(STUDENT_ID.name());
        assertEquals("netid=nobody,thesis-id=999", applyParameterSubstitution(onBase, params));
        params.remove(STUDENT_NETID.name());
        assertEquals("email=root@aol.com,thesis-id=999", applyParameterSubstitution(onBase, params));

        final FilePackagerImpl obPackager = (FilePackagerImpl)Spring.getBean("OnBaseExport");
        assertEquals(onBase, obPackager.entryName);
    }

/////////////////////////// thorough testing of fallback mode ///////////////////////////////////

    @Test public void testFallback_tokensNoVars() {
        final Map<String, String> params = StringCustomizer.setParameters(sub);

        // starts and ends with invalid fallback token
        String test = OR +"this has no vars in it, but does have non-active uses of a "+ OR +" token."+ OR;
        String result = customizeString(test, params);
        assertEquals(test, result); // no tokens removed, nothing altered

        // starts and ends with plain text, back-to-back invalid fallback tokens in the middle
        test = "This has some "+ OR + OR +" tokens in the middle";
        result = customizeString(test, params);
        assertEquals(test, result); // no tokens removed, nothing altered
    }

    @Test public void testFallback_yes_no() {
        final Map<String, String> params = StringCustomizer.setParameters(sub);

        final String test = "The first of {"+ STUDENT_ID+ OR + LAST_NAME+"} should work";
        assertTrue(params.containsKey(STUDENT_ID.name()));
        assertFalse(params.containsKey(LAST_NAME.name()));
        final String resultWithFallback = customizeString(test, params);
        assertEquals("The first of S01234 should work", resultWithFallback); // choose the first substitution
    }

    @Test public void testFallback_no_yes_no() {
        final Map<String, String> params = StringCustomizer.setParameters(sub);

        final String test = "The second of {"+ Variable.FIRST_NAME+ OR + STUDENT_ID+ OR + LAST_NAME+"} should work";
        assertFalse(params.containsKey(Variable.FIRST_NAME.name()));
        assertTrue(params.containsKey(STUDENT_ID.name()));
        assertFalse(params.containsKey(LAST_NAME.name()));
        final String resultWithFallback = customizeString(test, params);
        assertEquals("The second of S01234 should work", resultWithFallback); // choose the middle substitution
    }

    @Test public void testFallback_no_yes_yes() {
        final Map<String, String> params = StringCustomizer.setParameters(sub);

        final String test = "The second of {"+ Variable.FIRST_NAME+ OR + STUDENT_ID+ OR +Variable.STUDENT_NETID+"} should work";
        assertFalse(params.containsKey(Variable.FIRST_NAME.name()));
        assertTrue(params.containsKey(STUDENT_ID.name()));
        assertTrue(params.containsKey(Variable.STUDENT_NETID.name()));
        final String resultWithFallback = customizeString(test, params);
        assertEquals("The second of S01234 should work", resultWithFallback); // choose the middle substitution
    }

    @Test public void testFallback_no_no_no() {
        final Map<String, String> params = StringCustomizer.setParameters(sub);

        final String test = "None of {"+ Variable.FIRST_NAME+ OR +Variable.FULL_NAME+ OR + LAST_NAME+"} should work";
        assertFalse(params.containsKey(Variable.FIRST_NAME.name()));
        assertFalse(params.containsKey(Variable.FULL_NAME.name()));
        assertFalse(params.containsKey(LAST_NAME.name()));
        final String resultWithFallback = customizeString(test, params);
        assertEquals("None of  should work", resultWithFallback); // remove all substitutions
    }

    @Test public void testFallback_no_no_default() {
        final Map<String, String> params = StringCustomizer.setParameters(sub);

        final String test = "This {"+ Variable.FIRST_NAME+ OR +Variable.FULL_NAME+ OR +"chain} should use the default";
        assertFalse(params.containsKey(Variable.FIRST_NAME.name()));
        assertFalse(params.containsKey(Variable.FULL_NAME.name()));
        final String resultWithFallback = customizeString(test, params);
        assertEquals("This chain should use the default", resultWithFallback); // use default
    }

    @Test public void testFallback_yes_default() {
        final Map<String, String> params = StringCustomizer.setParameters(sub);

        final String test = "This chain {"+ STUDENT_ID+ OR +"default} should not get to the default";
        assertTrue(params.containsKey(STUDENT_ID.name()));
        final String resultWithFallback = customizeString(test, params);
        assertEquals("This chain S01234 should not get to the default", resultWithFallback);
    }

    @Test public void testFallback_defaultOnly() {
        final Map<String, String> params = StringCustomizer.setParameters(sub);

        final String test = "You can't start with a {default}.";
        final String resultWithFallback = customizeString(test, params);
        assertEquals(test, resultWithFallback); // invalid chain, unchanged
    }

    @Test public void testFallback_var_default_var() {
        final Map<String, String> params = StringCustomizer.setParameters(sub);

        final String test = "The default {"+ STUDENT_ID+ OR +"default"+ OR +Variable.DOCUMENT_TITLE+"} has to be last.";
        final String resultWithFallback = customizeString(test, params);
        assertEquals(test, resultWithFallback); // invalid chain, unchanged
    }

    @Test public void testFallback_rogueVar() {
        final Map<String, String> params = StringCustomizer.setParameters(sub);
        params.put("ROGUE", "rogue");

        String test = "This {"+ STUDENT_ID+ OR +"ROGUE"+ OR +"default} is a standard var.";
        String resultWithFallback = customizeString(test, params);
        assertEquals("This S01234 is a standard var.", resultWithFallback);

        test = "This {"+Variable.FULL_NAME+ OR +"ROGUE"+ OR +"default} is a rogue var.";
        resultWithFallback = customizeString(test, params);
        assertEquals("This rogue is a rogue var.", resultWithFallback);

        params.remove("ROGUE");
        // same test, but rogue var is now invalid.
        resultWithFallback = customizeString(test, params);
        assertEquals(test, resultWithFallback); // unchanged

        test = "This {"+Variable.FULL_NAME+ OR +"ROGUE} is a rogue var turned default.";
        resultWithFallback = customizeString(test, params);
        assertEquals("This ROGUE is a rogue var turned default.", resultWithFallback);
    }

    @Test public void testFallback_var_default_default() {
        final Map<String, String> params = StringCustomizer.setParameters(sub);

        final String test = "You can't have {"+ STUDENT_ID+ OR +"one"+ OR +"two} defaults.";
        final String resultWithFallback = customizeString(test, params);
        assertEquals(test, resultWithFallback); // invalid chain, unchanged
    }

    @Test public void testFallback_no_null_yes() {
        final Map<String, String> params = StringCustomizer.setParameters(sub);
        params.put(Variable.DOCUMENT_TITLE.name(), null);

        String test = "Null is allowed {"+Variable.DOCUMENT_TYPE+ OR +Variable.DOCUMENT_TITLE+ OR + STUDENT_ID+"} but won't match.";
        String resultWithFallback = customizeString(test, params);
        assertEquals("Null is allowed S01234 but won't match.", resultWithFallback);

        params.put("ROGUE", null);

        test = "Even with a rogue var {"+Variable.DOCUMENT_TYPE+ OR +"ROGUE"+ OR + STUDENT_ID+'}';
        resultWithFallback = customizeString(test, params);
        assertEquals("Even with a rogue var S01234", resultWithFallback);
    }

    @Test public void testFallback_firstAndLast() {
        final Map<String, String> params = StringCustomizer.setParameters(sub);

        String test = "{"+ Variable.FIRST_NAME+ OR +Variable.FULL_NAME+"} empty start and full end {"+ STUDENT_ID+ OR +Variable.STUDENT_NETID+"}";
        assertFalse(params.containsKey(Variable.FIRST_NAME.name()));
        assertFalse(params.containsKey(Variable.FULL_NAME.name()));
        assertTrue(params.containsKey(STUDENT_ID.name()));
        assertTrue(params.containsKey(Variable.STUDENT_NETID.name()));
        String resultWithFallback = customizeString(test, params);
        assertEquals(" empty start and full end S01234", resultWithFallback);

        test = "{"+ Variable.FIRST_NAME+ OR +"default}";
        resultWithFallback = customizeString(test, params);
        assertEquals("default", resultWithFallback);
    }

    @Test public void testFallback_braceNoise() {
        final Map<String, String> params = StringCustomizer.setParameters(sub);
        final String test = "{}}{{"+ STUDENT_ID+"}{";
        assertTrue(params.containsKey(STUDENT_ID.name()));
        final String resultWithFallback = customizeString(test, params);
        assertEquals("{}}{S01234{", resultWithFallback); // unchanged
    }

    @Test public void testFallback_malformed() {
        final Map<String, String> params = StringCustomizer.setParameters(sub);

        String test = "{}";
        String resultWithFallback = customizeString(test, params);
        assertEquals(test, resultWithFallback); // unchanged

        test = '{'+ OR +'}';
        resultWithFallback = customizeString(test, params);
        assertEquals(test, resultWithFallback); //unchanged

        test = "{"+ STUDENT_ID;
        resultWithFallback = customizeString(test, params);
        assertEquals(test, resultWithFallback); //unchanged

        test = "{"+ STUDENT_ID+ OR;
        resultWithFallback = customizeString(test, params);
        assertEquals(test, resultWithFallback); //unchanged

        test = STUDENT_ID+ OR +'}';
        resultWithFallback = customizeString(test, params);
        assertEquals(test, resultWithFallback); //unchanged

        test = "{"+ STUDENT_ID+ STUDENT_ID+'}';
        resultWithFallback = customizeString(test, params);
        assertEquals(test, resultWithFallback); //unchanged

        test = "{"+ STUDENT_ID+'}'+ STUDENT_ID+'}';
        resultWithFallback = customizeString(test, params);
        assertEquals("S01234"+ STUDENT_ID+'}', resultWithFallback); // first valid, second not

        test = "{"+ STUDENT_ID+'{'+ STUDENT_ID+'}';
        resultWithFallback = customizeString(test, params);
        assertEquals("{"+ STUDENT_ID+"S01234", resultWithFallback); // second valid, first not
    }

    @Test public void testFallback_DefaultMalformed() {
        final Map<String, String> params = StringCustomizer.setParameters(sub);

        String test = "{"+ STUDENT_ID+ OR +"default"+ OR +'}';
        String resultWithFallback = customizeString(test, params);
        assertEquals(test, resultWithFallback); // unchanged

        test = "{"+ STUDENT_ID+ OR +"default{}";
        resultWithFallback = customizeString(test, params);
        assertEquals(test, resultWithFallback); //unchanged

        test = "{"+ STUDENT_ID+"default}"; // no OR
        resultWithFallback = customizeString(test, params);
        assertEquals(test, resultWithFallback); //unchanged
    }

    @Test public void testFallback_VarsOnlyNoFallback() {
        final Map<String, String> params = StringCustomizer.setParameters(sub);

        // starts and ends with vars
        String test = "{STUDENT_NETID}This has no OR tokens but does have vars like {STUDENT_ID} and {STUDENT_EMAIL}";
        String resultNoFallback = StringCustomizer.applyParameterSubstitution(test, params);
        String resultWithFallback = customizeString(test, params);
        assertEquals(resultNoFallback, resultWithFallback); // same as no fallback version

        // starts and ends with plain text, back-to-back vars in the middle
        test = "This tests vars like {STUDENT_NETID}{STUDENT_ID} back-to-back in the middle.";
        resultNoFallback = StringCustomizer.applyParameterSubstitution(test, params);
        resultWithFallback = customizeString(test, params);
        assertEquals(resultNoFallback, resultWithFallback); // same as no fallback version
    }
}
