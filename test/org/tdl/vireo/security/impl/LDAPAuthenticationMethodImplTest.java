package org.tdl.vireo.security.impl;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.tdl.vireo.model.Person;
import org.tdl.vireo.model.PersonRepository;
import org.tdl.vireo.model.RoleType;
import org.tdl.vireo.security.AuthenticationResult;
import org.tdl.vireo.security.SecurityContext;
import play.db.jpa.JPA;
import play.modules.spring.Spring;
import play.test.UnitTest;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

/**
 * Test the LDAP authentication method. These tests require the method to
 * be configured in certain ways so that we know exactly what input to give it.
 * To make these tests work with a variety of configurations before each test
 * we save the current state of, run our tests, then restore the state after the
 * test has run. This means that if a tests fails we may leave the method in a
 * changed state, but other than that it should remain as original configured.
 *
 * Note: unlike with Shibboleth where the HTTP headers contain everything, all
 * LDAP responses must be mocked since there is no universally available and consistent
 * live server to talk to. We can test everything this way except password checking
 * (which Shibboleth tests can't do either), configuration issues, and server
 * connection issues.
 */
@SuppressWarnings({"NullableProblems"})
public class LDAPAuthenticationMethodImplTest extends UnitTest {

	/* Dependencies */
	public static SecurityContext context = Spring.getBeanOfType(SecurityContext.class);
	public static PersonRepository personRepo = Spring.getBeanOfType(PersonRepository.class);
	public static LDAPAuthenticationMethodImpl instance = Spring.getBeanOfType(LDAPAuthenticationMethodImpl.class);

	// predefined persons to test with.
	public Person person1;
	public Person person2;

    // save all original fields from instance
    Map<String, Object> original;
	
	/**
	 * Setup for a test by doing three things:
	 * 
	 * 1) Create two users for us to test with.
	 * 2) Save the current state of the shibboleth authentication method.
	 * 3) Establish our expected state (i.e. what all the headers are named);
	 */
	@Before
	public void setup() {
		// Setup two user accounts;
		context.turnOffAuthorization();
		person1 = personRepo.createPerson("netid1", "mail1@email.com", null, "last1", RoleType.NONE).save();
        person1.setPassword("secret1");
		person2 = personRepo.createPerson(null, "mail2@email.com", "first2", null, RoleType.NONE).save();
        person1.setPassword("secret2");
		context.restoreAuthorization();
		
		// Save the method's current state.
        // Reflect into the class to get all fields, including private ones.
        original = new HashMap<String, Object>(instance.getClass().getDeclaredFields().length);
        try {
            for (Field field : instance.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                original.put(field.getName(), field.get(instance));
            }
        } catch (IllegalAccessException e) {
            assertTrue("Couldn't reflect into instance for setup", false);
        }

		// Set the instance's state to what the tests expect.
        instance.setObjectContext("OU=people,DC=myu,DC=edu");
        instance.setSearchContext("OU=people,DC=myu,DC=edu");
        instance.setSearchAnonymous(true);
        instance.setSearchUser(null);
        instance.setSearchPassword(null);
        instance.setNetIDEmailDomain(null);
        instance.setAllowNewUserEmailMatch(true);
        instance.setValueInstitutionalIdentifier("My University");
        instance.setValueUserStatusActive("active");
        HashMap<LDAPAuthenticationMethodImpl.AttributeName, String> ldapFieldNames = new HashMap<LDAPAuthenticationMethodImpl.AttributeName, String>(25);
        ldapFieldNames.put(LDAPAuthenticationMethodImpl.AttributeName.NetID,                "uid");
        ldapFieldNames.put(LDAPAuthenticationMethodImpl.AttributeName.Email,                "mail");
        ldapFieldNames.put(LDAPAuthenticationMethodImpl.AttributeName.FirstName,            "givenName");
        ldapFieldNames.put(LDAPAuthenticationMethodImpl.AttributeName.MiddleName,           "middleName");
        ldapFieldNames.put(LDAPAuthenticationMethodImpl.AttributeName.LastName,             "sn");
        ldapFieldNames.put(LDAPAuthenticationMethodImpl.AttributeName.BirthYear,            "myuBirthYear");
        ldapFieldNames.put(LDAPAuthenticationMethodImpl.AttributeName.Affiliations,         "eduPersonPrimaryAffiliation");
        ldapFieldNames.put(LDAPAuthenticationMethodImpl.AttributeName.CurrentPhoneNumber,   "telephoneNumber");
        ldapFieldNames.put(LDAPAuthenticationMethodImpl.AttributeName.CurrentPostalAddress, "postalAddress");
        ldapFieldNames.put(LDAPAuthenticationMethodImpl.AttributeName.CurrentPostalCity,    "l");
        ldapFieldNames.put(LDAPAuthenticationMethodImpl.AttributeName.CurrentPostalState,   "st");
        ldapFieldNames.put(LDAPAuthenticationMethodImpl.AttributeName.CurrentPostalZip,     "postalCode");
        ldapFieldNames.put(LDAPAuthenticationMethodImpl.AttributeName.PermanentPhoneNumber, "myuPermPhone");
        ldapFieldNames.put(LDAPAuthenticationMethodImpl.AttributeName.PermanentPostalAddress,"myuPermAddress");
        ldapFieldNames.put(LDAPAuthenticationMethodImpl.AttributeName.PermanentEmailAddress,"mail3");
        ldapFieldNames.put(LDAPAuthenticationMethodImpl.AttributeName.CurrentDegree,        "myuDegree");
        ldapFieldNames.put(LDAPAuthenticationMethodImpl.AttributeName.CurrentDepartment,    "ou");
        ldapFieldNames.put(LDAPAuthenticationMethodImpl.AttributeName.CurrentCollege,       "myuCollege");
        ldapFieldNames.put(LDAPAuthenticationMethodImpl.AttributeName.CurrentMajor,         "myuMajor");
        ldapFieldNames.put(LDAPAuthenticationMethodImpl.AttributeName.CurrentGraduationYear,"myuGradYear");
        ldapFieldNames.put(LDAPAuthenticationMethodImpl.AttributeName.CurrentGraduationMonth,"myuGradMonth");
        ldapFieldNames.put(LDAPAuthenticationMethodImpl.AttributeName.StudentID,            "myuID");
        ldapFieldNames.put(LDAPAuthenticationMethodImpl.AttributeName.UserStatus,           "myuUserStatus");
        instance.setLdapFieldNames(ldapFieldNames);
        instance.setMock(true);
        Map<String, String> mockAttributes = new HashMap<String, String>(25);
        mockAttributes.put("uid", "netid1");
        mockAttributes.put("mail", "mail1@email.com");
        mockAttributes.put("givenName", "Billy");
        mockAttributes.put("middleName", "Bob");
        mockAttributes.put("sn", "Thornton");
        mockAttributes.put("myuBirthYear", "1955");
        mockAttributes.put("eduPersonPrimaryAffiliation", "student");
        mockAttributes.put("telephoneNumber", "+99 (55) 555-5555");
        mockAttributes.put("postalAddress", "1100 Congress Ave");
        mockAttributes.put("l", "Austin");
        mockAttributes.put("st", "TX");
        mockAttributes.put("postalCode", "78701");
        mockAttributes.put("myuPermPhone", "555-555-1932");
        mockAttributes.put("myuPermAddress", "Another City$Another State 54321");
        mockAttributes.put("mail3", "perm@email.com");
        mockAttributes.put("myuDegree", "Ph.D.");
        mockAttributes.put("ou", "Performance Studies");
        mockAttributes.put("myuCollege", "Clown College");
        mockAttributes.put("myuMajor", "Trapeze");
        mockAttributes.put("myuGradYear", "2014");
        mockAttributes.put("myuGradMonth", "01");
        mockAttributes.put("myuID", "S01234567");
        mockAttributes.put("myuUserStatus", "active");
        instance.setMockAttributes(mockAttributes);
        instance.setMockUserDn("uid=netid1,OU=people,DC=myu,DC=edu");
	}
	
	/**
	 * Clean up after a test by doing three things:
	 * 
	 * 1) Restore the original state of the LDAP authentication method
	 * 2) Cleanup test persons created.
	 * 3) Roll back the database transaction.
	 */
	@After
	public void cleanup() {
		
		// Restore the method's state.
        try {
            for (Field field : instance.getClass().getDeclaredFields()) {
                if (Modifier.isFinal(field.getModifiers()))
                    continue;
                field.setAccessible(true);
                field.set(instance, original.get(field.getName()));
            }
        } catch (IllegalAccessException e) {
            assertTrue("failed to restore field", false);
        }

		context.turnOffAuthorization();
		personRepo.findPerson(person1.getId()).delete();
		personRepo.findPerson(person2.getId()).delete();
		context.restoreAuthorization();
		context.logout();

		JPA.em().getTransaction().rollback();
		JPA.em().getTransaction().begin();
	}

    /**
     * Positive cases that should result in someone successfully authenticating:
     * User logs in as person with existing netID, both with status
     * active and with status inactive
     */
    @Test
    public void testPositiveCaseExistingUser() {
        // with status=active
        AuthenticationResult result = instance.authenticate("netid1", "secret", null);

        assertEquals(AuthenticationResult.SUCCESSFULL, result);
        assertNotNull(context.getPerson());
        assertEquals(person1, context.getPerson());

        context.logout();

        // with status=inactive
        instance.getMockAttributes().remove("myuUserStatus");
        instance.getMockAttributes().put("myuUserStatus", "inactive");
        result = instance.authenticate("netid1", "secret", null);

        assertEquals(AuthenticationResult.SUCCESSFULL, result);
        assertNotNull(context.getPerson());
        assertEquals(person1, context.getPerson());
    }

    /**
     * Positive cases that should result in someone successfully authenticating:
     * User has never logged in before, no email match, and status is reported as active
     */
    @Test
    public void testPositiveCaseNewUser() {
        instance.getMockAttributes().remove("uid");
        instance.getMockAttributes().put("uid", "netid3");
        instance.getMockAttributes().remove("mail");
        instance.getMockAttributes().put("mail", "mail3@email.com");
        instance.setMockUserDn("uid=netid3,OU=people,DC=myu,DC=edu");
        AuthenticationResult result = instance.authenticate("netid3", "secret", null);

        assertEquals(AuthenticationResult.SUCCESSFULL, result);
        Person person = context.getPerson();
        assertNotNull(person);
        // make sure person is new
        assertNotSame(person1, person);
        assertNotSame(person2, person);
        // clean up person
        context.logout();
        context.turnOffAuthorization();
		person.delete();
		context.restoreAuthorization();
    }

    /**
     * Positive cases that should result in someone successfully authenticating:
     * User has never logged in before, no email match,
     * status is inactive, but status checking is disabled
     */
    @Test
    public void testPositiveCaseNewUserInactiveUnchecked() {
        instance.getMockAttributes().remove("uid");
        instance.getMockAttributes().put("uid", "netid3");
        instance.getMockAttributes().remove("mail");
        instance.getMockAttributes().put("mail", "mail3@email.com");
        instance.getMockAttributes().remove("myuUserStatus");
        instance.getMockAttributes().put("myuUserStatus", "inactive");
        instance.setValueUserStatusActive(null);
        instance.setMockUserDn("uid=netid3,OU=people,DC=myu,DC=edu");
        AuthenticationResult result = instance.authenticate("netid3", "secret", null);

        assertEquals(AuthenticationResult.SUCCESSFULL, result);
        Person person = context.getPerson();
        assertNotNull(person);
        // make sure person is new
        assertNotSame(person1,person);
        assertNotSame(person2, person);
        // clean up person
        context.logout();
        context.turnOffAuthorization();
		person.delete();
		context.restoreAuthorization();
    }

    /**
     * Positive cases that should result in someone successfully authenticating:
     * New user with no provided email, but NetIdEmailDomain is set
     */
    @Test
    public void testPositiveCaseNetIdEmailDomain() {
        instance.getMockAttributes().remove("uid");
        instance.getMockAttributes().put("uid", "netid3");
        instance.getMockAttributes().remove("mail");
        instance.setMockUserDn("uid=netid3,OU=people,DC=myu,DC=edu");
        instance.setNetIDEmailDomain("@myu.edu");
        AuthenticationResult result = instance.authenticate("netid3", "secret", null);

        assertEquals(AuthenticationResult.SUCCESSFULL, result);
        Person person = context.getPerson();
        assertNotNull(person);
        // make sure person is new
        assertNotSame(person1,person);
        assertNotSame(person2, person);
        // make sure email got set
        assertEquals("netid3@myu.edu", person.getEmail());
        // clean up person
        context.logout();
        context.turnOffAuthorization();
		person.delete();
		context.restoreAuthorization();
    }

    /**
     * Positive cases that should result in someone successfully authenticating:
     * New user with no provided name, but allowNetIdAsMissingName is set
     */
    @Test
    public void testPositiveCaseAllowNetIdAsMissingName() {
        instance.getMockAttributes().remove("uid");
        instance.getMockAttributes().put("uid", "netid3");
        instance.getMockAttributes().remove("mail");
        instance.getMockAttributes().put("mail", "mail3@email.com");
        instance.getMockAttributes().remove("givenName");
        instance.getMockAttributes().remove("sn");
        instance.setMockUserDn("uid=netid3,OU=people,DC=myu,DC=edu");
        instance.setAllowNetIdAsMissingName(true);
        AuthenticationResult result = instance.authenticate("netid3", "secret", null);

        assertEquals(AuthenticationResult.SUCCESSFULL, result);
        Person person = context.getPerson();
        assertNotNull(person);
        // make sure person is new
        assertNotSame(person1,person);
        assertNotSame(person2,person);
        // make sure lastName got set
        assertEquals("netid3", person.getLastName());
        // clean up person
        context.logout();
        context.turnOffAuthorization();
		person.delete();
		context.restoreAuthorization();
    }

    /**
     * Positive cases that should result in someone successfully authenticating:
     * User has never logged in before and has the same email as an existing user,
     * and existing user (person2) has no netID set, and allowNewUserEmailMatch is set.
     */
    @Test
    public void testPositiveCaseNewUserClaimsEmail() {
        instance.getMockAttributes().remove("uid");
        instance.getMockAttributes().put("uid", "netid2");
        instance.getMockAttributes().remove("mail");
        instance.getMockAttributes().put("mail", "mail2@email.com");
        instance.setMockUserDn("uid=netid2,OU=people,DC=myu,DC=edu");
        AuthenticationResult result = instance.authenticate("netid2", "secret", null);

        assertEquals(AuthenticationResult.SUCCESSFULL, result);
        assertNotNull(context.getPerson());
        assertEquals(person2,context.getPerson());
        // make sure NetID got set
        assertEquals(person2.getNetId(), "netid2");
    }

    /**
     * Positive cases that should result in someone successfully authenticating:
     * User has never logged in before and has no email set, but after applying netIdEmailDomain
     * the email is the same as an existing user (person2),
     * and existing user has no netID set
     */
    @Test
    public void testPositiveCaseNewUserClaimsWithNetIdEmailDomain() {
        instance.getMockAttributes().remove("uid");
        instance.getMockAttributes().put("uid", "mail2");
        instance.getMockAttributes().remove("mail");
        instance.setMockUserDn("uid=mail2,OU=people,DC=myu,DC=edu");
        instance.setNetIDEmailDomain("@email.com");
        AuthenticationResult result = instance.authenticate("mail2", "secret", null);

        assertEquals(AuthenticationResult.SUCCESSFULL, result);
        assertNotNull(context.getPerson());
        assertEquals(person2,context.getPerson());
        // make sure NetID got set
        assertEquals("mail2", person2.getNetId());
        // make sure email is still correct
        assertEquals("mail2@email.com", person2.getEmail());
    }

    /**
     * Negative cases that should result in failures:
     * Missing username or password, or wrong password
     */
    @Test
    public void testNegativeCasesMissingCredential() {
        // Missing username
        AuthenticationResult result = instance.authenticate("\n", "secret", null);

        assertEquals(AuthenticationResult.MISSING_CREDENTIALS, result);
        assertNull(context.getPerson());

        // Missing password
        result = instance.authenticate("user1", "", null);

        assertEquals(AuthenticationResult.MISSING_CREDENTIALS, result);
        assertNull(context.getPerson());

        // wrong password
        result = instance.authenticate("netid1", "incorrect", null);

        assertEquals(AuthenticationResult.BAD_CREDENTIALS, result);
        assertNull(context.getPerson());
    }

    /**
     * Negative cases that should result in failures:
     * Mismatch between NetID / LDAP-NetID / expected DN
     */
    @Test
    public void testNegativeCasesWrongNetID() {
        // User-supplied NetID does not match expected DN
        instance.setMockUserDn("uid=netid2,OU=people,DC=myu,DC=edu");
        AuthenticationResult result = instance.authenticate("netid1", "secret", null);

        assertEquals(AuthenticationResult.BAD_CREDENTIALS, result);
        assertNull(context.getPerson());

        // User-supplied NetID does not match LDAP-supplied NetID
        instance.setMockUserDn("uid=netid1,OU=people,DC=myu,DC=edu");
        result = instance.authenticate("netid2", "secret", null);

        assertEquals(AuthenticationResult.BAD_CREDENTIALS, result);
        assertNull(context.getPerson());

        // LDAP-supplied NetID does not match expected DN
        instance.getMockAttributes().remove("uid");
        instance.getMockAttributes().put("uid", "netid2");
        instance.setMockUserDn("uid=netid1,OU=people,DC=myu,DC=edu");
        result = instance.authenticate("netid1", "secret", null);

        assertEquals(AuthenticationResult.BAD_CREDENTIALS, result);
        assertNull(context.getPerson());
    }

    /**
     * Negative cases that should result in failures:
     * The name of the LDAP field for NetID is mis-configured
     */
    @Test
    public void testNegativeCasesWrongNetIDFieldName() {
        Map<LDAPAuthenticationMethodImpl.AttributeName, String> ldapFieldNames = instance.getLdapFieldNames();
        ldapFieldNames.remove(LDAPAuthenticationMethodImpl.AttributeName.NetID);
        ldapFieldNames.put(LDAPAuthenticationMethodImpl.AttributeName.NetID, "mail");
        instance.setLdapFieldNames(ldapFieldNames);
        AuthenticationResult result = instance.authenticate("netid1", "secret", null);

        assertEquals(AuthenticationResult.BAD_CREDENTIALS, result);
        assertNull(context.getPerson());
    }

    /**
     * Negative cases that should result in failures:
     * User has never logged in before and has the same email as an existing user,
     * but existing user (person1) already has a different NetID
     */
    @Test
    public void testNegativeCasesNewUserEmailTaken() {
        instance.getMockAttributes().remove("uid");
        instance.getMockAttributes().put("uid", "netid2");
        // ldap email is still email1@email.com
        instance.setMockUserDn("uid=netid2,OU=people,DC=myu,DC=edu");
        AuthenticationResult result = instance.authenticate("netid2", "secret", null);

        assertEquals(AuthenticationResult.BAD_CREDENTIALS, result);
        assertNull(context.getPerson());
    }

    /**
     * Negative cases that should result in failures:
     * User has never logged in before and has the same email as an existing user,
     * and existing user (person2) has no netID set, but allowNewUserEmailMatch is false.
     */
    @Test
    public void testNegativeCaseNewUserClaimsEmailNotAllowed() {
        instance.getMockAttributes().remove("uid");
        instance.getMockAttributes().put("uid", "netid2");
        instance.getMockAttributes().remove("mail");
        instance.getMockAttributes().put("mail", "mail2@email.com");
        instance.setMockUserDn("uid=netid2,OU=people,DC=myu,DC=edu");
        instance.setAllowNewUserEmailMatch(false);
        AuthenticationResult result = instance.authenticate("netid2", "secret", null);

        assertEquals(AuthenticationResult.BAD_CREDENTIALS, result);
        assertNull(context.getPerson());
    }

    /**
     * Negative cases that should result in failures:
     * User has never logged in before, and status is reported as inactive
     */
    @Test
    public void testNegativeCasesNewUserInactive() {
        // User has never logged in before, and status is reported as inactive
        instance.getMockAttributes().remove("uid");
        instance.getMockAttributes().put("uid", "netid3");
        instance.getMockAttributes().remove("mail");
        instance.getMockAttributes().put("mail", "mail3@email.com");
        instance.getMockAttributes().remove("myuUserStatus");
        instance.getMockAttributes().put("myuUserStatus", "inactive");
        instance.setMockUserDn("uid=netid3,OU=people,DC=myu,DC=edu");
        AuthenticationResult result = instance.authenticate("netid3", "secret", null);

        assertEquals(AuthenticationResult.BAD_CREDENTIALS, result);
        assertNull(context.getPerson());
    }

    /**
     * Negative cases that should result in failures::
     * New user with no provided email, and NetIdEmailDomain is null
     */
    @Test
    public void testNegativeCaseNetIdEmailDomain() {
        instance.getMockAttributes().remove("uid");
        instance.getMockAttributes().put("uid", "netid3");
        instance.getMockAttributes().remove("mail");
        instance.setMockUserDn("uid=netid3,OU=people,DC=myu,DC=edu");
        AuthenticationResult result = instance.authenticate("netid3", "secret", null);

        assertEquals(AuthenticationResult.BAD_CREDENTIALS, result);
        Person person = context.getPerson();
        assertNull(person);
    }

    /**
     * Negative cases that should result in failures:
     * New user with no provided name, and allowNetIdAsMissingName is false
     */
    @Test
    public void testNegativeCaseAllowNetIdAsMissingName() {
        instance.getMockAttributes().remove("uid");
        instance.getMockAttributes().put("uid", "netid3");
        instance.getMockAttributes().remove("mail");
        instance.getMockAttributes().put("mail", "mail3@email.com");
        instance.getMockAttributes().remove("givenName");
        instance.getMockAttributes().remove("sn");
        instance.setMockUserDn("uid=netid3,OU=people,DC=myu,DC=edu");
        AuthenticationResult result = instance.authenticate("netid3", "secret", null);

        assertEquals(AuthenticationResult.BAD_CREDENTIALS, result);
        Person person = context.getPerson();
        assertNull(person);
    }

    /**
     * Negative cases that should result in failures:
     * New user with whose reported email address isn't valid.
     * Should test getting rejected by personRepo.createUser()
     */
    @Test
    public void testNegativeCaseIllegalEmail() {
        // invalid email reported by LDAP
        instance.getMockAttributes().remove("uid");
        instance.getMockAttributes().put("uid", "netid3");
        instance.getMockAttributes().remove("mail");
        instance.getMockAttributes().put("mail", "no-at-character");
        instance.setMockUserDn("uid=netid3,OU=people,DC=myu,DC=edu");
        AuthenticationResult result = instance.authenticate("netid3", "secret", null);

        assertEquals(AuthenticationResult.BAD_CREDENTIALS, result);
        Person person = context.getPerson();
        assertNull(person);

        // no email reported by LDAP, and invalid configuration of netIdEmailDomain
        instance.getMockAttributes().remove("mail");
        instance.setNetIDEmailDomain("myu.edu"); // no leading "@"
        result = instance.authenticate("netid3", "secret", null);

        assertEquals(AuthenticationResult.BAD_CREDENTIALS, result);
        person = context.getPerson();
        assertNull(person);

    }

    /**
     * Setting searchAnonymous to false with no searchUser or searchPassword should
     * trigger construction of a flat DN instead of search. When this happens,
     * attributes cannot be retrieved and so cannot be added to the existing user.
     */
    @Test
    public void testFlatDirectoryExistingUser() {
        instance.setSearchAnonymous(false);
        AuthenticationResult result = instance.authenticate("netid1", "secret", null);

        assertEquals(AuthenticationResult.SUCCESSFULL, result);
        assertNotNull(context.getPerson());
        assertEquals(person1, context.getPerson());
        assertNull(person1.getFirstName());
        assertNull(person1.getBirthYear());
    }

    /**
     * Setting searchAnonymous to false with no searchUser or searchPassword should
     * trigger construction of a flat DN instead of search. When this happens,
     * attributes cannot be retrieved and so cannot be added to the existing user.
     * In the case of a new user, this means the login will be rejected when
     * allowNetIdAsMissingName==false or netIdEmailDomain==null.
     */
    @Test
    public void testFlatDirectoryNewUser() {
        instance.getMockAttributes().remove("uid");
        instance.getMockAttributes().put("uid", "netid3");
        instance.getMockAttributes().remove("mail");
        instance.getMockAttributes().put("mail", "mail3@email.com");
        instance.setMockUserDn("uid=netid3,OU=people,DC=myu,DC=edu");
        instance.setSearchAnonymous(false);
        AuthenticationResult result = instance.authenticate("netid3", "secret", null);

        assertEquals(AuthenticationResult.BAD_CREDENTIALS, result);
        Person person = context.getPerson();
        assertNull(person);
    }

    /**
     * An existing user logs in.
     * Email and all name fields should be unaffected.
     * Optional attributes should be added if not already present,
     * left unchanged if already present,
     * and left unchanged if not present in LDAP
     */
    @Test
    public void testAttributesExistingUser() {
        // LDAP has Billy, Bob, Thornton as name parts.
        instance.getMockAttributes().remove("mail");
        // email would be collision with person2, but shouldn't matter because shouldn't be set
        instance.getMockAttributes().put("mail", "mail2@email.com");
        instance.getMockAttributes().remove("mail2"); // perm email
        // put some optional attributes onto person1
        context.turnOffAuthorization();
        person1.setCurrentCollege("Electoral");
        person1.setPermanentEmailAddress("forever@gradschool.edu");
        person1.addPreference(LDAPAuthenticationMethodImpl.personPrefKeyForStudentID, "S9999");
        person1.save();
        context.restoreAuthorization();
        AuthenticationResult result = instance.authenticate("netid1", "secret", null);

        assertEquals(AuthenticationResult.SUCCESSFULL, result);
        assertNotNull(context.getPerson());
        Person person = context.getPerson();
        assertEquals(person1, person);

        // make sure email and name did not update
        assertEquals("mail1@email.com", person.getEmail());
        assertNull(person.getFirstName());
        assertNull(person.getMiddleName());
        assertEquals("last1", person.getLastName());

        // make sure existing optional fields was not affected by different value in LDAP
        assertEquals("Electoral", person.getCurrentCollege());
        assertEquals("S9999", person.getPreference(LDAPAuthenticationMethodImpl.personPrefKeyForStudentID).getValue());
        assertEquals(1, person.getPreferences().size());

        // make sure existing optional fields was not affected by missing value in LDAP
        assertEquals("forever@gradschool.edu", person.getPermanentEmailAddress());

        // make sure all previously empty optional fields did update
        assertEquals(Integer.valueOf(1955), person.getBirthYear());
        assertEquals("student", person.getAffiliations().get(0));
        assertEquals("+99 (55) 555-5555", person.getCurrentPhoneNumber());
        assertEquals("1100 Congress Ave\nAustin, TX 78701", person.getCurrentPostalAddress());
        assertEquals("555-555-1932", person.getPermanentPhoneNumber());
        assertEquals("Another City\nAnother State 54321", person.getPermanentPostalAddress());
        assertEquals("Ph.D.", person.getCurrentDegree());
        assertEquals("Performance Studies", person.getCurrentDepartment());
        assertEquals("Trapeze", person.getCurrentMajor());
        assertEquals(Integer.valueOf(2014), person.getCurrentGraduationYear());
        assertEquals(Integer.valueOf(1), person.getCurrentGraduationMonth());
    }

    /**
     * A new user logs in.
     * Name, email, and all optional fields should update from LDAP.
     */
    @Test
    public void testAttributesNewUser() {
        instance.getMockAttributes().remove("uid");
        instance.getMockAttributes().put("uid", "netid3");
        instance.getMockAttributes().remove("mail");
        instance.getMockAttributes().put("mail", "mail3@email.com");
        instance.setMockUserDn("uid=netid3,OU=people,DC=myu,DC=edu");
        AuthenticationResult result = instance.authenticate("netid3", "secret", null);

        assertEquals(AuthenticationResult.SUCCESSFULL, result);
        Person person = context.getPerson();
        assertNotNull(person);
        // make sure person is new
        assertNotSame(person1, person);
        assertNotSame(person2, person);

        // make sure name and email were set
        assertEquals("mail3@email.com", person.getEmail());
        assertEquals("Billy", person.getFirstName());
        assertEquals("Bob", person.getMiddleName());
        assertEquals("Thornton", person.getLastName());

        // make sure all optional fields were set
        assertEquals(Integer.valueOf(1955), person.getBirthYear());
        assertEquals("student", person.getAffiliations().get(0));
        assertEquals("+99 (55) 555-5555", person.getCurrentPhoneNumber());
        assertEquals("1100 Congress Ave\nAustin, TX 78701", person.getCurrentPostalAddress());
        assertEquals("555-555-1932", person.getPermanentPhoneNumber());
        assertEquals("Another City\nAnother State 54321", person.getPermanentPostalAddress());
        assertEquals("perm@email.com", person.getPermanentEmailAddress());
        assertEquals("Ph.D.", person.getCurrentDegree());
        assertEquals("Performance Studies", person.getCurrentDepartment());
        assertEquals("Trapeze", person.getCurrentMajor());
        assertEquals("Clown College", person.getCurrentCollege());
        assertEquals(Integer.valueOf(2014), person.getCurrentGraduationYear());
        assertEquals(Integer.valueOf(1), person.getCurrentGraduationMonth());
        assertEquals("S01234567", person.getPreference(LDAPAuthenticationMethodImpl.personPrefKeyForStudentID).getValue());
        assertEquals(1, person.getPreferences().size());

        // clean up person
        context.logout();
        context.turnOffAuthorization();
		person.delete();
		context.restoreAuthorization();
    }

    /**
     * User logs into account with no InstitutionalIdentifier, and one is
     * set in configs. Should be added.
     */
    @Test
    public void testInstitutionalIdentifier() {
        instance.setValueInstitutionalIdentifier("University of Atlantis");
        AuthenticationResult result = instance.authenticate("netid1", "secret", null);

        assertEquals(AuthenticationResult.SUCCESSFULL, result);
        assertNotNull(context.getPerson());
        Person person = context.getPerson();
        assertEquals(person1, person);
        assertEquals("University of Atlantis", person.getInstitutionalIdentifier());
    }

    /**
     * LDAP has attributes that aren't in the Vireo config, and
     * Vireo config has attributes configured that aren't returned by LDAP.
     */
    @Test
    public void testExtraAndMissingLdapData() {
        instance.getLdapFieldNames().remove(LDAPAuthenticationMethodImpl.AttributeName.CurrentPostalZip);
        instance.getLdapFieldNames().remove(LDAPAuthenticationMethodImpl.AttributeName.CurrentDegree);
        instance.getMockAttributes().remove("l"); // currentPostalCity
        instance.getMockAttributes().remove("myuMajor"); // currentMajor
        AuthenticationResult result = instance.authenticate("netid1", "secret", null);

        assertEquals(AuthenticationResult.SUCCESSFULL, result);
        assertNotNull(context.getPerson());
        Person person = context.getPerson();
        assertEquals(person1, person);
        // missing things should be missing
        assertEquals("1100 Congress Ave, TX", person.getCurrentPostalAddress());
        assertNull(person.getCurrentDegree());
        assertNull(person.getCurrentMajor());
        // present things should be present
        assertEquals("last1", person.getLastName());
        assertEquals("Clown College", person.getCurrentCollege());
    }

    /**
     * Test special per-field data transformations/validations done to
     * optional attributes as they're added
     * Specifically:
     * PermanentEmailAddress should not be saved if it doesn't validate.
     * CurrentPhoneNumber and PermanentPhoneNumber should not be saved if "-";
     * CurrentMajor should not be saved if "Undeclared" (case insensitive).
     * CurrentPostalAddress should be assembled from address, city, state,
     *   and zip, whichever are present, as long as at least address is present.
     * PermanentPostalAddress should have any "$" characters replaced with newlines.
     * BirthYear, CurrentGraduationMonth, CurrentGraduationYear should not be
     *   saved if values are not integers (negative is valid in Person, so I
     *   guess it's fine with me). CurrentGraduationMonth must be in [0..11].
     */
    @Test
    public void testOptionalDataTransformations() {
        Map<String, String> ldap = instance.getMockAttributes();
        // invalid permanent email
        ldap.remove("mail3");
        ldap.put("mail3", "invalidEmail");
        // pseudo-null telephone numbers
        ldap.remove("telephoneNumber");
        ldap.put("telephoneNumber", "-");
        ldap.remove("myuPermPhone");
        ldap.put("myuPermPhone", "-");
        // pseudo-null major
        ldap.remove("myuMajor");
        ldap.put("myuMajor", "unDECLared");
        // missing address portion of current address
        ldap.remove("postalAddress");
        // permanent address boils down to empty
        ldap.remove("myuPermAddress");
        ldap.put("myuPermAddress", "$$$");
        // non-integer values
        ldap.remove("myuBirthYear");
        ldap.put("myuBirthYear", "0xfeed");
        ldap.remove("myuGradYear");
        ldap.put("myuGradYear", "3.14159");
        ldap.remove("myuGradMonth");
        ldap.put("myuGradMonth", "12");

        AuthenticationResult result = instance.authenticate("netid1", "secret", null);

        assertEquals(AuthenticationResult.SUCCESSFULL, result);
        assertNotNull(context.getPerson());
        Person person = context.getPerson();
        assertEquals(person1, person);

        // everything above should be rejected as empty or invalid
        assertNull(person.getPermanentEmailAddress());
        assertNull(person.getCurrentPhoneNumber());
        assertNull(person.getPermanentPhoneNumber());
        assertNull(person.getCurrentMajor());
        assertNull(person.getCurrentPostalAddress());
        assertNull(person.getPermanentPostalAddress());
        assertNull(person.getBirthYear());
        assertNull(person.getCurrentGraduationYear());
        assertNull(person.getCurrentGraduationMonth());

        context.logout();

        // newlines in permanent address
        ldap.remove("myuPermAddress");
        ldap.put("myuPermAddress", "line1$line2$line3");
        // combining current address
        ldap.put("postalAddress", "1234 Main St");
        ldap.remove("l"); // no city
        // telephone with "-" but other things too
        ldap.remove("telephoneNumber");
        ldap.put("telephoneNumber", "-55 (82) 123-456");

        result = instance.authenticate("netid1", "secret", null);

        assertEquals(AuthenticationResult.SUCCESSFULL, result);
        assertNotNull(context.getPerson());
        person = context.getPerson();
        assertEquals(person1, person);

        // everything above should be accepted, even if it's changed in the process.
        assertEquals("line1\nline2\nline3", person.getPermanentPostalAddress());
        assertEquals("1234 Main St, TX 78701", person.getCurrentPostalAddress());
        assertEquals("-55 (82) 123-456", person.getCurrentPhoneNumber());
    }
}