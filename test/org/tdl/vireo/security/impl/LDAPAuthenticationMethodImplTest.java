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
 * Test the LDAP authentication feature against a stub LDAP server that gives
 * pre-selected responses.
 */
public class LDAPAuthenticationMethodImplTest extends UnitTest {

	/* Dependencies */
	public static SecurityContext context = Spring.getBeanOfType(SecurityContext.class);
	public static PersonRepository personRepo = Spring.getBeanOfType(PersonRepository.class);
	public static LDAPAuthenticationMethodImpl instance = Spring.getBeanOfType(LDAPAuthenticationMethodImpl.class);

	// predefined persons to test with.
	public Person person1 = null;
	public Person person2 = null;

	// save all original fields from instance
	Map<String, Object> origFields = null;
	String origDirFactory = null;

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
		origFields = new HashMap<String, Object>(instance.getClass().getDeclaredFields().length);
		try {
			for (Field field : instance.getClass().getDeclaredFields()) {
				field.setAccessible(true);
				origFields.put(field.getName(), field.get(instance));
			}
		} catch (IllegalAccessException e) {
			fail("Couldn't reflect into instance for setup");
		}

		// Set the instance's state to what the tests expect.
		instance.setProviderURL("ldapstub:test");
		instance.setObjectContext("OU=people,DC=myu,DC=edu");
		instance.setSearchContext("OU=people,DC=myu,DC=edu");
		instance.setSearchAnonymous(true);
		instance.setSearchUser(null);
		instance.setSearchPassword(null);
		instance.setSearchUser(null);
		instance.setSearchPassword(null);
		instance.setNetIDEmailDomain(null);
		instance.setAllowNetIdAsMissingName(false);
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
		ldapFieldNames.put(LDAPAuthenticationMethodImpl.AttributeName.UserStatus, "myuUserStatus");
		instance.setLdapFieldNames(ldapFieldNames);

		// set the stub values for testing
		origDirFactory = instance.swapInitialDirContextFactory(StubLdapFactory.class.getName());

		StubLdapFactory.adminDn = null;
		StubLdapFactory.adminPass = null;
		StubLdapFactory.netIdLdapFieldName = ldapFieldNames.get(LDAPAuthenticationMethodImpl.AttributeName.NetID);
		StubLdapFactory.objectContext = "OU=people,DC=myu,DC=edu";
		StubLdapFactory.searchContext = "OU=people,DC=myu,DC=edu"; // My university has these the same. Someone whose are different can fully test this feature.
		StubLdapFactory.attributes = new HashMap<String, String>(25);
		StubLdapFactory.attributes.put("uid", "netid1");
		StubLdapFactory.attributes.put("mail", "mail1@email.com");
		StubLdapFactory.attributes.put("givenName", "Billy");
		StubLdapFactory.attributes.put("middleName", "Bob");
		StubLdapFactory.attributes.put("sn", "Thornton");
		StubLdapFactory.attributes.put("myuBirthYear", "1955");
		StubLdapFactory.attributes.put("eduPersonPrimaryAffiliation", "student");
		StubLdapFactory.attributes.put("telephoneNumber", "+99 (55) 555-5555");
		StubLdapFactory.attributes.put("postalAddress", "1100 Congress Ave");
		StubLdapFactory.attributes.put("l", "Austin");
		StubLdapFactory.attributes.put("st", "TX");
		StubLdapFactory.attributes.put("postalCode", "78701");
		StubLdapFactory.attributes.put("myuPermPhone", "555-555-1932");
		StubLdapFactory.attributes.put("myuPermAddress", "Another City$Another State 54321");
		StubLdapFactory.attributes.put("mail3", "perm@email.com");
		StubLdapFactory.attributes.put("myuDegree", "Ph.D.");
		StubLdapFactory.attributes.put("ou", "Performance Studies");
		StubLdapFactory.attributes.put("myuCollege", "Clown College");
		StubLdapFactory.attributes.put("myuMajor", "Trapeze");
		StubLdapFactory.attributes.put("myuGradYear", "2014");
		StubLdapFactory.attributes.put("myuGradMonth", "01");
		StubLdapFactory.attributes.put("myuID", "S01234567");
		StubLdapFactory.attributes.put("myuUserStatus", "active");
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
		instance.swapInitialDirContextFactory(origDirFactory);
		try {
			for (Field field : instance.getClass().getDeclaredFields()) {
				if (Modifier.isFinal(field.getModifiers())) {
					continue;
				}
				field.setAccessible(true);
				field.set(instance, origFields.get(field.getName()));
			}
		} catch (IllegalAccessException e) {
			fail("failed to restore field");
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
		StubLdapFactory.attributes.remove("myuUserStatus");
		StubLdapFactory.attributes.put("myuUserStatus", "inactive");
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
		StubLdapFactory.attributes.remove("uid");
		StubLdapFactory.attributes.put("uid", "netid3");
		StubLdapFactory.attributes.remove("mail");
		StubLdapFactory.attributes.put("mail", "mail3@email.com");
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
		StubLdapFactory.attributes.remove("uid");
		StubLdapFactory.attributes.put("uid", "netid3");
		StubLdapFactory.attributes.remove("mail");
		StubLdapFactory.attributes.put("mail", "mail3@email.com");
		StubLdapFactory.attributes.remove("myuUserStatus");
		StubLdapFactory.attributes.put("myuUserStatus", "inactive");
		instance.setValueUserStatusActive(null);
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
		StubLdapFactory.attributes.remove("uid");
		StubLdapFactory.attributes.put("uid", "netid3");
		StubLdapFactory.attributes.remove("mail");
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
		StubLdapFactory.attributes.remove("uid");
		StubLdapFactory.attributes.put("uid", "netid3");
		StubLdapFactory.attributes.remove("mail");
		StubLdapFactory.attributes.put("mail", "mail3@email.com");
		StubLdapFactory.attributes.remove("givenName");
		StubLdapFactory.attributes.remove("sn");
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
		StubLdapFactory.attributes.remove("uid");
		StubLdapFactory.attributes.put("uid", "netid2");
		StubLdapFactory.attributes.remove("mail");
		StubLdapFactory.attributes.put("mail", "mail2@email.com");
		AuthenticationResult result = instance.authenticate("netid2", "secret", null);

		assertEquals(AuthenticationResult.SUCCESSFULL, result);
		assertNotNull(context.getPerson());
		assertEquals(person2,context.getPerson());
		// make sure NetID got set
		assertEquals("netid2", person2.getNetId());
	}

	/**
	 * Positive cases that should result in someone successfully authenticating:
	 * User has never logged in before and has no email set, but after applying netIdEmailDomain
	 * the email is the same as an existing user (person2),
	 * and existing user has no netID set
	 */
	@Test
	public void testPositiveCaseNewUserClaimsWithNetIdEmailDomain() {
		StubLdapFactory.attributes.remove("uid");
		StubLdapFactory.attributes.put("uid", "mail2");
		StubLdapFactory.attributes.remove("mail");
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
	 * Positive cases that should result in someone successfully authenticating:
	 * Server required authenticated search, and supplied credentials are correct
	 */
	@Test
	public void testPositiveSearchCredentialsCorrect() {
		instance.setSearchUser("uid=search,OU=admin");
		instance.setSearchPassword("adminsecret");
		StubLdapFactory.adminDn="uid=search,OU=admin";
		StubLdapFactory.adminPass="adminsecret";
		AuthenticationResult result = instance.authenticate("netid1", "secret", null);

		assertEquals(AuthenticationResult.SUCCESSFULL, result);
		assertNotNull(context.getPerson());
		assertEquals(person1, context.getPerson());
	}

	/**
	 * Positive cases that should result in someone successfully authenticating:
	 * There's a difference between the supplied subjectContext and
	 * what the server requires, but it doesn't matter when doing a flat DN lookup
	 */
	@Test
	public void testPositiveSearchContextWrongFlatDN() {
		instance.setSearchContext("wrong");
		instance.setSearchAnonymous(false);
		AuthenticationResult result = instance.authenticate("netid1", "secret", null);

		assertEquals(AuthenticationResult.SUCCESSFULL, result);
		assertNotNull(context.getPerson());
		assertEquals(person1, context.getPerson());
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
	 * Mismatch between NetID and LDAP's stored NetID
	 */
	@Test
	public void testNegativeCasesWrongNetID() {
		// LDAP-supplied NetID does not match expected DN
		StubLdapFactory.attributes.remove("uid");
		StubLdapFactory.attributes.put("uid", "netid2");
		AuthenticationResult result = instance.authenticate("netid1", "secret", null);

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
		StubLdapFactory.attributes.remove("uid");
		StubLdapFactory.attributes.put("uid", "netid2");
		// ldap email for netid2 is now mail1@email.com, which is already taken in Vireo by existing user netid1
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
		StubLdapFactory.attributes.remove("uid");
		StubLdapFactory.attributes.put("uid", "netid2");
		StubLdapFactory.attributes.remove("mail");
		StubLdapFactory.attributes.put("mail", "mail2@email.com");
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
		StubLdapFactory.attributes.remove("uid");
		StubLdapFactory.attributes.put("uid", "netid3");
		StubLdapFactory.attributes.remove("mail");
		StubLdapFactory.attributes.put("mail", "mail3@email.com");
		StubLdapFactory.attributes.remove("myuUserStatus");
		StubLdapFactory.attributes.put("myuUserStatus", "inactive");
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
		StubLdapFactory.attributes.remove("uid");
		StubLdapFactory.attributes.put("uid", "netid3");
		StubLdapFactory.attributes.remove("mail");
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
		StubLdapFactory.attributes.remove("uid");
		StubLdapFactory.attributes.put("uid", "netid3");
		StubLdapFactory.attributes.remove("mail");
		StubLdapFactory.attributes.put("mail", "mail3@email.com");
		StubLdapFactory.attributes.remove("givenName");
		StubLdapFactory.attributes.remove("sn");
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
		StubLdapFactory.attributes.remove("uid");
		StubLdapFactory.attributes.put("uid", "netid3");
		StubLdapFactory.attributes.remove("mail");
		StubLdapFactory.attributes.put("mail", "no-at-character");
		AuthenticationResult result = instance.authenticate("netid3", "secret", null);

		assertEquals(AuthenticationResult.BAD_CREDENTIALS, result);
		Person person = context.getPerson();
		assertNull(person);

		// no email reported by LDAP, and invalid configuration of netIdEmailDomain
		StubLdapFactory.attributes.remove("mail");
		instance.setNetIDEmailDomain("myu.edu"); // no leading "@"
		result = instance.authenticate("netid3", "secret", null);

		assertEquals(AuthenticationResult.BAD_CREDENTIALS, result);
		person = context.getPerson();
		assertNull(person);
	}

	/**
	 * Negative cases that should result in failures:
	 * Server required authenticated search, but supplied credentials are incorrect
	 */
	@Test
	public void testNegativeSearchCredentialsIncorrect() {
		instance.setSearchUser("uid=search,OU=admin");
		instance.setSearchPassword("oops");
		StubLdapFactory.adminDn="uid=search,OU=admin";
		StubLdapFactory.adminPass="adminsecret";
		AuthenticationResult result = instance.authenticate("netid1", "secret", null);

		assertEquals(AuthenticationResult.BAD_CREDENTIALS, result);
		Person person = context.getPerson();
		assertNull(person);
	}

	/**
	 * Negative cases that should result in failures:
	 * There's a difference between the supplied objectContext or
	 * searchContext and what the server requires, and we are using
	 * user attribute search.
	 */
	@Test
	public void testNegativeContextMismatch() {
		instance.setObjectContext("wrong");
		AuthenticationResult result = instance.authenticate("netid1", "secret", null);

		assertEquals(AuthenticationResult.BAD_CREDENTIALS, result);
		Person person = context.getPerson();
		assertNull(person);

		instance.setObjectContext("OU=people,DC=myu,DC=edu");
		instance.setSearchContext("wrong");
		result = instance.authenticate("netid1", "secret", null);

		assertEquals(AuthenticationResult.BAD_CREDENTIALS, result);
		person = context.getPerson();
		assertNull(person);
	}

	/**
	 * Negative cases that should result in failures:
	 * There's a difference between the supplied objectContext and
	 * what the server requires, even when doing a flat DN lookup
	 */
	@Test
	public void testNegativeObjectContextWrongFlatDN() {
		instance.setObjectContext("wrong");
		instance.setSearchAnonymous(false);
		AuthenticationResult result = instance.authenticate("netid1", "secret", null);

		assertEquals(AuthenticationResult.BAD_CREDENTIALS, result);
		Person person = context.getPerson();
		assertNull(person);
	}

	/**
	 * Negative cases that should result in failures:
	 * The provider URL is wrong
	 */
	@Test
	public void testNegativeWrongProviderURL() {
		instance.setProviderURL("wrong");
		 AuthenticationResult result = instance.authenticate("netid1", "secret", null);

		assertEquals(AuthenticationResult.UNKNOWN_FAILURE, result);
		Person person = context.getPerson();
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
		StubLdapFactory.attributes.remove("uid");
		StubLdapFactory.attributes.put("uid", "netid3");
		StubLdapFactory.attributes.remove("mail");
		StubLdapFactory.attributes.put("mail", "mail3@email.com");
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
		StubLdapFactory.attributes.remove("mail");
		// email would be collision with person2, but shouldn't matter because shouldn't be set
		StubLdapFactory.attributes.put("mail", "mail2@email.com");
		StubLdapFactory.attributes.remove("mail2"); // perm email
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
		StubLdapFactory.attributes.remove("uid");
		StubLdapFactory.attributes.put("uid", "netid3");
		StubLdapFactory.attributes.remove("mail");
		StubLdapFactory.attributes.put("mail", "mail3@email.com");
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
		StubLdapFactory.attributes.remove("l"); // currentPostalCity
		StubLdapFactory.attributes.remove("myuMajor"); // currentMajor
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
		Map<String, String> ldap = StubLdapFactory.attributes;
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