package org.tdl.vireo.services;

import org.junit.Before;
import org.junit.Test;
import org.tdl.vireo.export.impl.FilePackagerImpl;
import org.tdl.vireo.services.SubmissionParams.Variable;
import play.modules.spring.Spring;
import play.test.UnitTest;

import java.util.HashMap;
import java.util.Map;

import static org.tdl.vireo.services.StringVariableReplacement.*;
import static org.tdl.vireo.services.SubmissionParams.Variable.*;

public class StringVariableReplacementTest extends UnitTest {

	private final Map<String, String> params = new HashMap<String, String>(8);

	@Before public void setup() {
		// vars that have valid replacements
		params.put(STUDENT_ID.name(), "S01234");
		params.put(STUDENT_EMAIL.name(), "nobody@gmail.com");
		params.put(STUDENT_NETID.name(), "nobody");
		params.put(SUBMISSION_ID.name(), "999");
		// vars that are valid var names, but have null replacements
		params.put(FIRST_NAME.name(), null);
		params.put(LAST_NAME.name(), null);
		params.put(FULL_NAME.name(), null);
		// everything else is not a recognized var name.
	}

	@Test public void testModeSelection() {
		// This string will be interpreted differently by each mode.
		final String indicator = "simple-{"+ STUDENT_ID +"}-fallback-{"+ FIRST_NAME+OR+STUDENT_ID +"}-template-${ "+STUDENT_ID+" }-end";

		// fallback mode - gets the first and second parts
		assertEquals("simple-S01234-fallback-S01234-template-${ "+STUDENT_ID+" }-end",
				applyParameterSubstitution(indicator, params));

		// template mode - only gets the third part
		assertEquals("simple-{"+ STUDENT_ID +"}-fallback-{"+ FIRST_NAME+OR+STUDENT_ID +"}-template-S01234-end",
				applyParameterSubstitution(TEMPLATE_MODE + indicator, params));
	}

	@Test public void testTemplate() {
		// I don't have to thoroughly test everything about how templates are applied. I assume Play does that
		// correctly. Instead, just test that they are applied at all, and the arguments are supplied correctly.

		final String template = "template-#{if "+STUDENT_ID+".equals(\"newID\")}match#{/if}#{else}nomatch#{/else}";
		assertEquals("template-nomatch", applyTemplateParameters(template, params));
		params.put(STUDENT_ID.name(), "newID");
		assertEquals("template-match", applyTemplateParameters(template, params));

		// the configuration need that spurred the template parameter substitution mode's development
		final String onBase = "{TEMPLATE_MODE}#{if STUDENT_ID!=null}sid=${STUDENT_ID}#{/if}#{elseif STUDENT_NETID!=null}netid=${STUDENT_NETID}#{/elseif}#{else}email=${STUDENT_EMAIL}#{/else},thesis-id=${SUBMISSION_ID}";
		assertEquals("sid=newID,thesis-id=999", applyParameterSubstitution(onBase, params));
		params.put(STUDENT_ID.name(), null);
		assertEquals("netid=nobody,thesis-id=999", applyParameterSubstitution(onBase, params));
		params.put(STUDENT_NETID.name(), null);
		assertEquals("email=nobody@gmail.com,thesis-id=999", applyParameterSubstitution(onBase, params));

		final FilePackagerImpl obPackager = (FilePackagerImpl)Spring.getBean("OnBaseExport");
		assertEquals(onBase, obPackager.entryName);
	}

/////////////////////////// thorough testing of fallback mode ///////////////////////////////////

	@Test public void testFallback_tokensNoVars() {
		// starts and ends with invalid fallback token
		String test = OR +"this has no vars in it, but does have non-active uses of a "+ OR +" token."+ OR;
		String result = applyParameterSubstitution(test, params);
		assertEquals(test, result); // no tokens removed, nothing altered

		// starts and ends with plain text, back-to-back invalid fallback tokens in the middle
		test = "This has some "+ OR + OR +" tokens in the middle";
		result = applyParameterSubstitution(test, params);
		assertEquals(test, result); // no tokens removed, nothing altered
	}

	@Test public void testFallback_yes_no() {
		final String test = "The first of {"+ STUDENT_ID+ OR + LAST_NAME+"} should work";
		final String resultWithFallback = applyParameterSubstitution(test, params);
		assertEquals("The first of S01234 should work", resultWithFallback); // choose the first substitution
	}

	@Test public void testFallback_no_yes_no() {
		final String test = "The second of {"+ Variable.FIRST_NAME+ OR + STUDENT_ID+ OR + LAST_NAME+"} should work";
		final String resultWithFallback = applyParameterSubstitution(test, params);
		assertEquals("The second of S01234 should work", resultWithFallback); // choose the middle substitution
	}

	@Test public void testFallback_no_yes_yes() {
		final String test = "The second of {"+ Variable.FIRST_NAME+ OR + STUDENT_ID+ OR +Variable.STUDENT_NETID+"} should work";
		final String resultWithFallback = applyParameterSubstitution(test, params);
		assertEquals("The second of S01234 should work", resultWithFallback); // choose the middle substitution
	}

	@Test public void testFallback_no_no_no() {
		final String test = "None of {"+ Variable.FIRST_NAME+ OR +Variable.FULL_NAME+ OR + LAST_NAME+"} should work";
		final String resultWithFallback = applyParameterSubstitution(test, params);
		assertEquals("None of  should work", resultWithFallback); // remove all substitutions
	}

	@Test public void testFallback_no_no_default() {
		final String test = "This {"+ Variable.FIRST_NAME+ OR +Variable.FULL_NAME+ OR +"chain} should use the default";
		final String resultWithFallback = applyParameterSubstitution(test, params);
		assertEquals("This chain should use the default", resultWithFallback); // use default
	}

	@Test public void testFallback_yes_default() {
		final String test = "This chain {"+ STUDENT_ID+ OR +"default} should not get to the default";
		final String resultWithFallback = applyParameterSubstitution(test, params);
		assertEquals("This chain S01234 should not get to the default", resultWithFallback);
	}

	@Test public void testFallback_defaultOnly() {
		final String test = "You can't start with a {default}.";
		final String resultWithFallback = applyParameterSubstitution(test, params);
		assertEquals(test, resultWithFallback); // invalid chain, unchanged
	}

	@Test public void testFallback_var_default_var() {
		final String test = "The default {"+ STUDENT_ID+ OR +"default"+ OR +Variable.DOCUMENT_TITLE+"} has to be last.";
		final String resultWithFallback = applyParameterSubstitution(test, params);
		assertEquals(test, resultWithFallback); // invalid chain, unchanged
	}

	@Test public void testFallback_rogueVar() {
		params.put("ROGUE", "rogue");

		String test = "This {"+ STUDENT_ID+ OR +"ROGUE"+ OR +"default} is a standard var.";
		String resultWithFallback = applyParameterSubstitution(test, params);
		assertEquals("This S01234 is a standard var.", resultWithFallback);

		test = "This {"+Variable.FULL_NAME+ OR +"ROGUE"+ OR +"default} is a rogue var.";
		resultWithFallback = applyParameterSubstitution(test, params);
		assertEquals("This rogue is a rogue var.", resultWithFallback);

		params.put("ROGUE", null);
		// same test, but rogue var is now empty.
		resultWithFallback = applyParameterSubstitution(test, params);
		assertEquals("This default is a rogue var.", resultWithFallback); // unchanged

		params.remove("ROGUE");
		// same test, but rogue var is now invalid for substitution.
		resultWithFallback = applyParameterSubstitution(test, params);
		assertEquals(test, resultWithFallback); // unchanged

		test = "This {"+Variable.FULL_NAME+ OR +"ROGUE} is a rogue var turned default.";
		resultWithFallback = applyParameterSubstitution(test, params);
		assertEquals("This ROGUE is a rogue var turned default.", resultWithFallback);
	}

	@Test public void testFallback_var_default_default() {
		final String test = "You can't have {"+ STUDENT_ID+ OR +"one"+ OR +"two} defaults.";
		final String resultWithFallback = applyParameterSubstitution(test, params);
		assertEquals(test, resultWithFallback); // invalid chain, unchanged
	}

	@Test public void testFallback_no_null_yes() {
		params.put(Variable.DOCUMENT_TITLE.name(), null);

		String test = "Null is allowed {"+Variable.FIRST_NAME+ OR +Variable.LAST_NAME+ OR + STUDENT_ID+"} but won't match.";
		String resultWithFallback = applyParameterSubstitution(test, params);
		assertEquals("Null is allowed S01234 but won't match.", resultWithFallback);

		params.put("ROGUE", null);

		test = "Even with a rogue var {"+Variable.FIRST_NAME+ OR +"ROGUE"+ OR + STUDENT_ID+'}';
		resultWithFallback = applyParameterSubstitution(test, params);
		assertEquals("Even with a rogue var S01234", resultWithFallback);
	}

	@Test public void testFallback_firstAndLast() {
		String test = "{"+ Variable.FIRST_NAME+ OR +Variable.FULL_NAME+"} empty start and full end {"+ STUDENT_ID+ OR +Variable.STUDENT_NETID+ '}';
		String resultWithFallback = applyParameterSubstitution(test, params);
		assertEquals(" empty start and full end S01234", resultWithFallback);

		test = "{"+ Variable.FIRST_NAME+ OR +"default}";
		resultWithFallback = applyParameterSubstitution(test, params);
		assertEquals("default", resultWithFallback);
	}

	@Test public void testFallback_braceNoise() {
		final String test = "{}}{{"+ STUDENT_ID+"}{";
		final String resultWithFallback = applyParameterSubstitution(test, params);
		assertEquals("{}}{S01234{", resultWithFallback); // unchanged
	}

	@Test public void testFallback_malformed() {
		String test = "{}";
		String resultWithFallback = applyParameterSubstitution(test, params);
		assertEquals(test, resultWithFallback); // unchanged

		test = '{'+ OR +'}';
		resultWithFallback = applyParameterSubstitution(test, params);
		assertEquals(test, resultWithFallback); //unchanged

		test = "{"+ STUDENT_ID;
		resultWithFallback = applyParameterSubstitution(test, params);
		assertEquals(test, resultWithFallback); //unchanged

		test = "{"+ STUDENT_ID+ OR;
		resultWithFallback = applyParameterSubstitution(test, params);
		assertEquals(test, resultWithFallback); //unchanged

		test = STUDENT_ID+ OR +'}';
		resultWithFallback = applyParameterSubstitution(test, params);
		assertEquals(test, resultWithFallback); //unchanged

		test = "{"+ STUDENT_ID+ STUDENT_ID+'}';
		resultWithFallback = applyParameterSubstitution(test, params);
		assertEquals(test, resultWithFallback); //unchanged

		test = "{"+ STUDENT_ID+'}'+ STUDENT_ID+'}';
		resultWithFallback = applyParameterSubstitution(test, params);
		assertEquals("S01234"+ STUDENT_ID+'}', resultWithFallback); // first valid, second not

		test = "{"+ STUDENT_ID+'{'+ STUDENT_ID+'}';
		resultWithFallback = applyParameterSubstitution(test, params);
		assertEquals("{"+ STUDENT_ID+"S01234", resultWithFallback); // second valid, first not
	}

	@Test public void testFallback_DefaultMalformed() {
		String test = "{"+ STUDENT_ID+ OR +"default"+ OR +'}';
		String resultWithFallback = applyParameterSubstitution(test, params);
		assertEquals(test, resultWithFallback); // unchanged

		test = "{"+ STUDENT_ID+ OR +"default{}";
		resultWithFallback = applyParameterSubstitution(test, params);
		assertEquals(test, resultWithFallback); //unchanged

		test = "{"+ STUDENT_ID+"default}"; // no OR
		resultWithFallback = applyParameterSubstitution(test, params);
		assertEquals(test, resultWithFallback); //unchanged
	}

	@Test public void testFallback_VarsOnlyNoFallback() {
		// starts and ends with vars
		String test = "{STUDENT_NETID}This has no OR tokens but does have vars like {STUDENT_ID} and {STUDENT_EMAIL}";
		String resultNoFallback = StringVariableReplacement.applyParameterSubstitution(test, params);
		String resultWithFallback = applyParameterSubstitution(test, params);
		assertEquals(resultNoFallback, resultWithFallback); // same as no fallback version

		// starts and ends with plain text, back-to-back vars in the middle
		test = "This tests vars like {STUDENT_NETID}{STUDENT_ID} back-to-back in the middle.";
		resultNoFallback = StringVariableReplacement.applyParameterSubstitution(test, params);
		resultWithFallback = applyParameterSubstitution(test, params);
		assertEquals(resultNoFallback, resultWithFallback); // same as no fallback version
	}
}
