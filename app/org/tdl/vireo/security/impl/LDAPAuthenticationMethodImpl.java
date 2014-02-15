package org.tdl.vireo.security.impl;

import org.tdl.vireo.model.Person;
import org.tdl.vireo.model.RoleType;
import org.tdl.vireo.security.AuthenticationMethod;
import org.tdl.vireo.security.AuthenticationResult;

import play.Logger;
import play.Play;
import play.mvc.Http.Request;
import sun.util.logging.resources.logging;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Authenticates netID and password against LDAP. Retrieves personal information.
 */
public class LDAPAuthenticationMethodImpl extends
		AbstractAuthenticationMethodImpl.AbstractExplicitAuthenticationMethod implements
		AuthenticationMethod.Explicit {

	/* Injected configuration */

	// Whether LDAP will be mocked or real.
	public boolean mock = (Play.mode == Play.Mode.DEV);

	// This is the url to the institution's ldap server. The /o=myu.edu
    // may or may not be required depending on the LDAP server setup.
    // A server may also require the ldaps:// protocol.
	public String provider_url = "ldaps://ldap.myu.edu:636/";

    // This is the object context used when authenticating the
    // user.  It is appended to the id_field and username.
    // For example uid=username,ou=people,o=myu.edu.  This must match
    // the LDAP server configuration.
    // object_context = ou=people,o=myu.edu
    public String object_context = "OU=people,DC=myu,DC=edu";
	
    // This is the search context used when looking up a user's
    // LDAP object to retrieve their data for autoregistering.
    // With autoregister turned on, when a user authenticates
    // without an EPerson object, a search on the LDAP directory to
    // get their name and email address is initiated so that DSpace
    // can create a EPerson object for them.  So after we have authenticated against
    // uid=username,ou=people,o=byu.edu we now search in ou=people
    // for filtering on [uid=username].  Often the
    // search_context is the same as the object_context
    // parameter.  But again this depends on each individual LDAP server
    // configuration.
    public String search_context = "OU=people,DC=myu,DC=edu";

    // 0 == object, 1 == one level, 2 == subtree
    public int searchScope = 2;

    // If true, the initial bind will be performed anonymously.
    public boolean searchAnonymous = false;

    // if hierarchical LDAP search is required, the login credentials for the search
    public String searchUser = null;
    public String searchPassword = null;

    // added to netid to create an email address if email not explicitly specified.
    public String netidEmailDomain = null;

	// Use netid or email address as primary account identifier.
	public boolean useNetIdAsIdentifier = true;

    /// All the header names
    public String headerNetId = "uid";
    public String headerEmail = "mail";
    public String headerFirstName = "givenName";
    public String headerMiddleName = null;
    public String headerLastName = "sn";
    public String headerBirthYear = null;
    public String headerDisplayName = "displayName"; // does this go into Person OK?
    public String headerAffiliations = "eduPersonPrimaryAffiliation"; // optional advisor credential, may be absent
    public String headerCurrentPhoneNumber = "telephoneNumber"; // "-" means null
    public String headerCurrentPostalAddress = "postalAddress"; // assemble—all parts optional. this is on-campus dept/office mail
    public String headerCurrentPostalCity = "l";
    public String headerCurrentPostalState = "st";
    public String headerCurrentPostalZip = "postalCode";
    public String headerCurrentEmailAddress = "mail";
    public String headerPermanentPhoneNumber = "myuPermPhone"; // "-" means null
    public String headerPermanentPostalAddress = "myuPermAddress"; // "$" is newline, or solo "$" means null
    public String headerPermanentEmailAddress = "mail";
    public String headerCurrentDegree = null;
    public String headerCurrentDepartment = "myuOrg"; // or myuDistOrg or ou: ours seem all the same
    public String headerCurrentCollege = null;
    public String headerCurrentMajor = "myuCurriculum"; // "Undeclared" is null
    public String headerCurrentGraduationYear = "SHIB_gradYear";
    public String headerCurrentGraduationMonth = "SHIB_gradMonth";
    public String headerStudentID = "myuID"; /// stored in Vireo as a user pref, since no dedicated field. Used for OnBase export

    public String valueInstitutionalIdentifier = "myu";

	// Map of mock LDAP attributes
	public Map<String,String> mockAttributes = new HashMap<String,String>();

    /**
     * This is the url to the institution's ldap server. The /o=myu.edu
     * may or may not be required depending on the LDAP server setup.
     * A server may also require the ldaps:// protocol.
     * @param provider_url URL required to talk to LDAP server
     */
    public void setProvider_url(String provider_url) {
        this.provider_url = provider_url;
    }

    /**
     * This is the object context used when authenticating the user.
     *  It is appended to the id_field and username.
     *  For example uid=username,ou=people,o=myu.edu.
     *  This must match the LDAP server configuration.
     * @param object_context LDAP object context where users may be found
     */
    public void setObject_context(String object_context) {
        this.object_context = object_context;
    }

    /**
     * This is the search context used when looking up a user's
     * LDAP object to retrieve their data for new user registration.
     * When a user authenticates without a Person object, a search on
     * the LDAP directory to get their name and email address is initiated
     * so that Vireo can create a Person object for them.  So after we
     * have authenticated against uid=username,ou=people,o=byu.edu we now
     * search in ou=people for filtering on [uid=username].  Often the
     * search_context is the same as the object_context.
     * But again this depends on each individual LDAP server configuration.
     * @param search_context LDAP context used to search for user info
     */
    public void setSearch_context(String search_context) {
        this.search_context = search_context;
    }

    /**
     If your users are spread out across a hierarchical tree on your
     LDAP server, you will need to search the tree to find the full DN of
     the user who is logging in.
     * If anonymous search is allowed on your LDAP server, you will need to set
       search.anonymous = true
     * If not, you will need to specify the full DN and password of a
       user that is allowed to bind in order to search for the users.
     * If neither search.anonymous is true, nor search.user is specified,
       LDAP will not do the hierarchical search for a DN and will assume
       a flat directory structure.
     * @param searchAnonymous If true, the initial bind will be performed anonymously.
     */
    public void setSearchAnonymous(boolean searchAnonymous) {
        this.searchAnonymous = searchAnonymous;
    }

    /**
     * This is the optional search scope value for the LDAP search during
     * user creation. This will depend on your LDAP server setup.
     * @param searchScope
     *      an integer indicating one of the following three options:
     *      0 : object scope
     *      1: one level scope
     *      2: subtree scope
     */
    public void setSearchScope(int searchScope) {
        this.searchScope = searchScope;
    }

    /**
     * Set credentials of a user allowed to connect to the LDAP server and
     * search for the DN of the user trying to log in.
     * @param searchUser the full DN of the search user
     */
    public void setSearchUser(String searchUser) {
        this.searchUser = searchUser;
    }

    /**
     * Set credentials of a user allowed to connect to the LDAP server and
     * search for the DN of the user trying to log in.
     * @param searchPassword the plaintext password of the search user
     */
    public void setSearchPassword(String searchPassword) {
        this.searchPassword=searchPassword;
    }

    /**
     * If your LDAP server does not hold an email address for a user, you can use
     * the following field to specify your email domain. This value is appended
     * to the netid in order to make an email address. E.g. a netid of 'user' and
     * netid_email_domain as '@example.com' would set the email of the user
     * to be 'user@example.com netid_email_domain = @example.com
     * @param netidEmailDomain domain to append
     */
    public void setNetidEmailDomain(String netidEmailDomain) {
        this.netidEmailDomain = netidEmailDomain;
    }

	/**
	 * @param mock
	 *            True if shibboleth authentication should be mocked with test
	 *            attributes. This allows you to use the application without it
	 *            talking to a real LDAP.
	 */
	public void setMock(boolean mock) {
		this.mock = mock;
	}

	/**
	 * If this authentication method is configured to Mock an LDAP
	 * connection then these are the LDAP attributes that will be assumed
	 * when authenticating.
	 *
	 * The map should contain the configured LDAP field names as
	 * keys to the map, while the value should be the actual value (or values)
	 * of the LDAP attribute.
	 *
	 * @param mockAttributes
	 *            A map of mock LDAP attributes.
	 */
	public void setMockAttributes(Map<String,String> mockAttributes) {
		this.mockAttributes = mockAttributes;
	}

	/**
	 * Set the primary account identifier, the only valid responses are "netid",
	 * or "email". The field set will be used to uniquely identify person
	 * objects. This must match the unique id field used by the LDAP server.
	 *
	 * @param primaryIdentifier
	 *            Either "netid" or "email".
	 */
	public void setPrimaryIdentifier(String primaryIdentifier) {
		if ("netid".equals(primaryIdentifier)) {
			useNetIdAsIdentifier = true;
		} else if ("email".equals(primaryIdentifier)) {
			useNetIdAsIdentifier = false;
		} else {
			throw new IllegalArgumentException("Invalid primary identifier: "+primaryIdentifier+", the only valid options are 'netid' or 'email'.");
		}
	}

    /**
     * LDAP is usually single-institution, so if this is set, all created users
     * will get this value set as their institutional identifier
     * @param valueInstitutionalIdentifier a boilerplate inst. id.
     */
    public void setValueInstitutionalIdentifier(String valueInstitutionalIdentifier) {
        this.valueInstitutionalIdentifier = valueInstitutionalIdentifier;
    }

	/**
	 * Set the LDAP header mapping to Vireo attributes. The keys of the
	 * map must equal one of the keys defined bellow. Then the value of that key
	 * must be the expected LDAP header name for that particular attribute. For a
	 * definition of what most attributes are, see the Person model.
     *
     * If data values are found in mapped fields for currentPostal City|State|Zip, they
     * will be appended to the value for currentPostalAddress.
     *
     * If a data value is found in mapped field for studentID, it will be stored as a
     * user preference, since there is no dedicated Person field for that info.
	 *
	 * Required Mapping: either netID or email, whichever is used as the user id
	 *
	 * Optional Mappings: displayName, middleName, birthYear, currentPhoneNumber, currentPostalAddress,
     * currentPostalCity, currentPostalState, currentPostalZip, currentEmailAddress, permanentPhoneNumber,
     * permanentPostalAddress, permanentEmailAddress, currentDegree, currentDepartment, currentCollege,
     * currentMajor, currentGraduationYear, currentGraduationMonth, studentID
	 *
	 * @param attributeMap
	 */
	public void setAttributeMap(Map<String,String> attributeMap) {

		// Store all mappings. (One of netID or email is required, but don't necessarily know which yet.)
		headerNetId = attributeMap.get("netId");
		headerEmail = attributeMap.get("email");
		headerFirstName = attributeMap.get("firstName");
		headerLastName = attributeMap.get("lastName");
        headerDisplayName = attributeMap.get("displayName");
        headerMiddleName = attributeMap.get("middleName");
        headerBirthYear = attributeMap.get("birthYear");
		headerAffiliations = attributeMap.get("affiliations");
		headerCurrentPhoneNumber = attributeMap.get("currentPhoneNumber");
		headerCurrentPostalAddress = attributeMap.get("currentPostalAddress");
        headerCurrentPostalCity = attributeMap.get("currentPostalCity");
        headerCurrentPostalState = attributeMap.get("currentPostalState");
        headerCurrentPostalZip = attributeMap.get("currentPostalZip");
		headerCurrentEmailAddress = attributeMap.get("currentEmailAddress");
		headerPermanentPhoneNumber = attributeMap.get("permanentPhoneNumber");
		headerPermanentPostalAddress = attributeMap.get("permanentPostalAddress");
		headerPermanentEmailAddress = attributeMap.get("permanentEmailAddress");
        headerCurrentDegree = attributeMap.get("currentDegree");
		headerCurrentDepartment = attributeMap.get("currentDepartment");
        headerCurrentCollege = attributeMap.get("currentCollege");
		headerCurrentMajor = attributeMap.get("currentMajor");
        headerCurrentGraduationYear = attributeMap.get("currentGraduationYear");
        headerCurrentGraduationMonth = attributeMap.get("currentGraduationMonth");
        headerStudentID = attributeMap.get("studentID");
	}

    /**
	 * LDAP authentication. Handles both:
     * - authentication against a flat LDAP tree where all users are in the same unit
     *   (if search.user or search.password is not set)
     * - authentication against structured hierarchical LDAP trees of users.
     *   An initial bind is required using a user name and password in order to
     *   search the tree and find the DN of the user. A second bind is then required to
     *   check the credentials of the user by binding directly to their DN.
	 *
	 */
	public AuthenticationResult authenticate(String username, String password,
			Request request) {

		if (username == null || username.length() == 0 || password == null)
			return AuthenticationResult.MISSING_CREDENTIALS;

		// 2. Get required attributes.
		String netid = getSingleAttribute(request, headerNetId);
		String email = getSingleAttribute(request, headerEmail);
		String firstName = getSingleAttribute(request, headerFirstName);
		String lastName = getSingleAttribute(request, headerLastName);

		// 3. Lookup the person based upon the primary identifier
		Person person;
		try {
			context.turnOffAuthorization();
			if (useNetIdAsIdentifier) {
				person = personRepo.findPersonByNetId(netid);
			} else {
				person = personRepo.findPersonByEmail(email);
			}
			if (person == null) {
				// Create the new person
				try {
					person = personRepo.createPerson(netid, email, firstName, lastName, RoleType.STUDENT).save();
				} catch (RuntimeException re) {
					// Unable to create new person, probably because the email or netid already exist.
					Logger.error(re,"Shib: Unable to create new eperson.");
					return AuthenticationResult.BAD_CREDENTIALS;
				}
			} else {
				// Update required fields.
				if (netid != null)
					person.setNetId(netid);
				person.setEmail(email);
				person.setFirstName(firstName);
				person.setLastName(lastName);
			}

			// 4. Update Optional attributes:
			if (headerInstitutionalIdentifier != null) {
				String identifier = getSingleAttribute(request, headerInstitutionalIdentifier);
				if (!isEmpty(identifier))
					person.setInstitutionalIdentifier(identifier);
			}
			if (headerMiddleName != null) {
				String middleName = getSingleAttribute(request, headerMiddleName);
				if (!isEmpty(middleName) && person.getMiddleName() == null)
					person.setMiddleName(middleName);
			}
			if (headerBirthYear != null) {
				String birthYearString = getSingleAttribute(request, headerBirthYear);
				if (!isEmpty(birthYearString) && person.getBirthYear() == null) {
					try {
						Integer birthYear = Integer.valueOf(birthYearString);
						person.setBirthYear(birthYear);
					} catch (NumberFormatException nfe) {
						Logger.warn("Shib: Unable to interpret birth year attribute '"+headerBirthYear+"'='"+birthYearString+"' as an integer.");
					}
				}
			}
			if (headerAffiliations != null) {
				List<String> affiliations = getAttributes(request, headerAffiliations);
				if (affiliations != null && affiliations.size() > 0) {
					person.getAffiliations().clear();
					if (affiliations != null)
						person.getAffiliations().addAll(affiliations);
				}
			}
			if (headerCurrentPhoneNumber != null) {
				String currentPhoneNumber = getSingleAttribute(request, headerCurrentPhoneNumber);
				if (!isEmpty(currentPhoneNumber) && person.getCurrentPhoneNumber() == null)
					person.setCurrentPhoneNumber(currentPhoneNumber);
			}
			if (headerCurrentPostalAddress != null) {
				String currentPostalAddress = getSingleAttribute(request, headerCurrentPostalAddress);
				if (!isEmpty(currentPostalAddress) && person.getCurrentPostalAddress() == null)
					person.setCurrentPostalAddress(currentPostalAddress);
			}
			if (headerCurrentEmailAddress != null) {
				String currentEmailAddress = getSingleAttribute(request, headerCurrentEmailAddress);
				if (!isEmpty(currentEmailAddress) && person.getCurrentEmailAddress() == null)
					person.setCurrentEmailAddress(currentEmailAddress);
			}
			if (headerPermanentPhoneNumber != null) {
				String permanentPhoneNumber = getSingleAttribute(request, headerPermanentPhoneNumber);
				if (!isEmpty(permanentPhoneNumber) && person.getPermanentPhoneNumber() == null)
					person.setPermanentPhoneNumber(permanentPhoneNumber);
			}
			if (headerPermanentPostalAddress != null) {
				String permanentPostalAddress = getSingleAttribute(request, headerPermanentPostalAddress);
				if (!isEmpty(permanentPostalAddress) && person.getPermanentPostalAddress() == null)
					person.setPermanentPostalAddress(permanentPostalAddress);
			}
			if (headerPermanentEmailAddress != null) {
				String permanentEmailAddress = getSingleAttribute(request, headerPermanentEmailAddress);
				if (!isEmpty(permanentEmailAddress) && person.getPermanentEmailAddress() == null)
					person.setPermanentEmailAddress(permanentEmailAddress);
			}
			if (headerCurrentDegree != null) {
				String currentDegree = getSingleAttribute(request, headerCurrentDegree);
				if (!isEmpty(currentDegree) && person.getCurrentDegree() == null)
					person.setCurrentDegree(currentDegree);
			}
			if (headerCurrentDepartment != null) {
				String currentDepartment = getSingleAttribute(request, headerCurrentDepartment);
				if (!isEmpty(currentDepartment) && person.getCurrentDepartment() == null)
					person.setCurrentDepartment(currentDepartment);
			}
			if (headerCurrentCollege != null) {
				String currentCollege = getSingleAttribute(request, headerCurrentCollege);
				if (!isEmpty(currentCollege) && person.getCurrentCollege() == null)
					person.setCurrentCollege(currentCollege);
			}
			if (headerCurrentMajor != null) {
				String currentMajor = getSingleAttribute(request, headerCurrentMajor);
				if (!isEmpty(currentMajor) && person.getCurrentMajor() == null)
					person.setCurrentMajor(currentMajor);
			}
			if (headerCurrentGraduationYear != null) {
				String currentGraduationYearString = getSingleAttribute(request, headerCurrentGraduationYear);
				if (!isEmpty(currentGraduationYearString) && person.getCurrentGraduationYear() == null) {
					try {
						Integer currentGraduationYear = Integer.valueOf(currentGraduationYearString);
						person.setCurrentGraduationYear(currentGraduationYear);
					} catch (NumberFormatException nfe) {
						Logger.warn("Shib: Unable to interpret current graduation year attribute '"+headerCurrentGraduationYear+"'='"+currentGraduationYearString+"' as an integer.");
					}
				}
			}
			if (headerCurrentGraduationMonth != null) {
				String currentGraduationMonthString = getSingleAttribute(request, headerCurrentGraduationMonth);
				if (!isEmpty(currentGraduationMonthString) && person.getCurrentGraduationMonth() == null) {
					try {
						Integer currentGraduationMonth = Integer.valueOf(currentGraduationMonthString);
						person.setCurrentGraduationMonth(currentGraduationMonth);
					} catch (NumberFormatException nfe) {
						Logger.warn("Shib: Unable to interpret current graduation month attribute '"+headerCurrentGraduationMonth+"'='"+currentGraduationMonthString+"' as an integer.");
					} catch (IllegalArgumentException iae) {
						Logger.warn("Shib: Illegal value for current graduation month attribute '"+headerCurrentGraduationMonth+"'='"+currentGraduationMonthString+"', 0=January, 11=Dember. Any values outside this range are illegal.");
					}
				}
			}
			person.save();

		} finally {
			context.restoreAuthorization();
		}

		context.login(person);
		return AuthenticationResult.SUCCESSFULL;
    }
}

/*



	// All the header names.
	    public String headerNetId = "SHIB_netid";
	    public String headerEmail = "SHIB_mail";
	public String headerInstitutionalIdentifier = "SHIB_uin";
	    public String headerFirstName = "SHIB_givenname";
	public String headerMiddleName = "SHIB_initials";
	    public String headerLastName = "SHIB_sn";
	public String headerBirthYear = "SHIB_dateOfBirth";
	    public String headerAffiliations = "SHIB_eduPersonAffilation";
	    public String headerCurrentPhoneNumber = "SHIB_phone";
public String headerCurrentPostalAddress = "SHIB_postal";
	    public String headerCurrentEmailAddress = "SHIB_mail";
	    public String headerPermanentPhoneNumber = "SHIB_permanentPhone";
        public String headerPermanentPostalAddress = "SHIB_permanentPostal";
	public String headerPermanentEmailAddress = "SHIB_permanentMail";
    public String headerCurrentDegree = "SHIB_degree";
	    public String headerCurrentDepartment = "SHIB_department";
public String headerCurrentCollege = "SHIB_college"; // auto-determine from dept or major?
	    public String headerCurrentMajor = "SHIB_major";
	public String headerCurrentGraduationYear = "SHIB_gradYear";
	public String headerCurrentGraduationMonth = "SHIB_gradMonth";

*/
