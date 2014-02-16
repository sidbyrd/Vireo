package org.tdl.vireo.security.impl;

import org.apache.commons.lang.StringUtils;
import org.tdl.vireo.model.Person;
import org.tdl.vireo.model.RoleType;
import org.tdl.vireo.security.AuthenticationMethod;
import org.tdl.vireo.security.AuthenticationResult;

import play.Logger;
import play.Play;
import play.mvc.Http.Request;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.*;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

/**
 * Authenticates netID and password against LDAP. Retrieves personal information.
 */
public class LDAPAuthenticationMethodImpl extends
		AbstractAuthenticationMethodImpl.AbstractExplicitAuthenticationMethod implements
		AuthenticationMethod.Explicit {

	/* Injected configuration */

	// This is the url to the institution's ldap server. The /o=myu.edu
    // may or may not be required depending on the LDAP server setup.
    // A server may also require the ldaps:// protocol.
	private String providerURL = "ldaps://ldap.myu.edu:636/";

    // This is the object context used when authenticating the
    // user.  It is appended to the id_field and username.
    // For example uid=username,ou=people,o=myu.edu.  This must match
    // the LDAP server configuration.
    // objectContext = ou=people,o=myu.edu
    private String objectContext = "OU=people,DC=myu,DC=edu";
	
    // This is the search context used when looking up a user's
    // LDAP object to retrieve their data for autoregistering.
    // With autoregister turned on, when a user authenticates
    // without an EPerson object, a search on the LDAP directory to
    // get their name and email address is initiated so that DSpace
    // can create a EPerson object for them.  So after we have authenticated against
    // uid=username,ou=people,o=byu.edu we now search in ou=people
    // for filtering on [uid=username].  Often the
    // searchContext is the same as the objectContext
    // parameter.  But again this depends on each individual LDAP server
    // configuration.
    private String searchContext = "OU=people,DC=myu,DC=edu";

    public enum SearchScope {
        /**
         * object level LDAP search scope
         */
        OBJECT (0),
        /**
         * single level LDAP search scope
         */
        SINGLE (1),
        /**
         * subtree LDAP search scope
         */
        SUBTREE (2);

        int code;
        SearchScope(int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }
    }
    private SearchScope searchScope = SearchScope.SUBTREE;

    // If true, the initial bind will be performed anonymously.
    private boolean searchAnonymous = false;

    // if hierarchical LDAP search is required, the login credentials for the search
    private String searchUser = null;
    private String searchPassword = null;

    // added to netid to create an email address if email not explicitly specified.
    private String netIDEmailDomain = null;

    /// the names of all the Person attributes and user data attributes that can be collected from LDAP
    public enum AttributeName {
        NetID,
        Email,
        FirstName,
        MiddleName,
        LastName,
        BirthYear,
        DisplayName,
        Affiliations,
        CurrentPhoneNumber,
        CurrentPostalAddress,
        CurrentPostalCity,
        CurrentPostalState,
        CurrentPostalZip,
        CurrentEmailAddress,
        PermanentPhoneNumber,
        PermanentPostalAddress,
        PermanentEmailAddress,
        CurrentDegree,
        CurrentDepartment,
        CurrentCollege,
        CurrentMajor,
        CurrentGraduationYear,
        CurrentGraduationMonth,
        StudentID
    }

    public final String STUDENT_ID_PREF_NAME = "student_id";

    // mapping from names of fields we can collect to the names used in LDAP for those fields
    private Map <AttributeName, String> attributeNameMap;
    
    /*private String headerNetID = "uid";
    private String headerEmail = "mail";
    private String headerFirstName = "givenName";
    private String headerMiddleName = null;
    private String headerLastName = "sn";
    private String headerBirthYear = null;
    private String headerDisplayName = "displayName"; // does this go into Person OK?
    private String headerAffiliations = "eduPersonPrimaryAffiliation"; // optional advisor credential, may be absent
    private String headerCurrentPhoneNumber = "telephoneNumber"; // "-" means null
    private String headerCurrentPostalAddress = "postalAddress"; // assemble—all parts optional. this is on-campus dept/office mail
    private String headerCurrentPostalCity = "l";
    private String headerCurrentPostalState = "st";
    private String headerCurrentPostalZip = "postalCode";
    private String headerCurrentEmailAddress = "mail";
    private String headerPermanentPhoneNumber = "myuPermPhone"; // "-" means null
    private String headerPermanentPostalAddress = "myuPermAddress"; // "$" is newline, or solo "$" means null
    private String headerPermanentEmailAddress = "mail";
    private String headerCurrentDegree = null;
    private String headerCurrentDepartment = "myuOrg"; // or myuDistOrg or ou: ours seem all the same
    private String headerCurrentCollege = null;
    private String headerCurrentMajor = "myuCurriculum"; // "Undeclared" is null
    private String headerCurrentGraduationYear = "SHIB_gradYear";
    private String headerCurrentGraduationMonth = "SHIB_gradMonth";
    private String headerStudentID = "myuID"; /// stored in Vireo as a user pref, since no dedicated field. Used for OnBase export
*/
    private String valueInstitutionalIdentifier = null;

	// Whether LDAP responses and authentication will be mocked or real.
	private boolean mock = (Play.mode == Play.Mode.DEV);

	// map of attributes about the user returned by LDAP search or injected directly as mock
	private Map<AttributeName,String> attributes = new HashMap<AttributeName, String>();

    /**
     * This is the url to the institution's ldap server. The /o=myu.edu
     * may or may not be required depending on the LDAP server setup.
     * A server may also require the ldaps:// protocol.
     * @param providerURL URL required to talk to LDAP server
     */
    public void setProviderURL(String providerURL) {
        this.providerURL = providerURL;
    }

    /**
     * This is the object context used when authenticating the user.
     *  It is appended to the id_field and username.
     *  For example uid=username,ou=people,o=myu.edu.
     *  This must match the LDAP server configuration.
     * @param objectContext LDAP object context where users may be found
     */
    public void setObjectContext(String objectContext) {
        this.objectContext = objectContext;
    }

    /**
     * This is the search context used when looking up a user's
     * LDAP object to retrieve their data for new user registration.
     * When a user authenticates without a Person object, a search on
     * the LDAP directory to get their name and email address is initiated
     * so that Vireo can create a Person object for them.  So after we
     * have authenticated against uid=username,ou=people,o=byu.edu we now
     * search in ou=people for filtering on [uid=username].  Often the
     * searchContext is the same as the objectContext.
     * But again this depends on each individual LDAP server configuration.
     * @param searchContext LDAP context used to search for user info
     */
    public void setSearchContext(String searchContext) {
        this.searchContext = searchContext;
    }

    /**
     * This is the optional search scope value for the LDAP search during
     * user creation. This will depend on your LDAP server setup.
     * @param searchScope one of three valid search scopes
     */
    public void setSearchScope(SearchScope searchScope) {
        this.searchScope = searchScope;
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
     * @param searchAnonymous If true, the initial bind will be performed
     * anonymously to get the correct DN and retrieve attributes.
     *  This applies to both flat and hierarchical searches.
     */
    public void setSearchAnonymous(boolean searchAnonymous) {
        this.searchAnonymous = searchAnonymous;
    }

    /**
     * Set credentials of a user allowed to connect to the LDAP server and
     * search hierarchically for the DN and attributes of the user trying to log in.
     * @param searchUser the full DN of the authorized search account
     */
    public void setSearchUser(String searchUser) {
        this.searchUser = searchUser;
    }

    /**
     * Set credentials of a user allowed to connect to the LDAP server and
     * search hierarchically for the DN and attributes of the user trying to log in.
     * @param searchPassword the plaintext password of the authorized search account
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
     * @param netIDEmailDomain domain to append
     */
    public void setNetIDEmailDomain(String netIDEmailDomain) {
        this.netIDEmailDomain = netIDEmailDomain;
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
	 * Set the LDAP header mapping to Vireo attributes. The keys are the names of all the
     * attributes Vireo can be configured to look for. Then the value of that key
	 * must be the expected LDAP header name for that particular attribute. For a
	 * definition of what most attributes are, see the Person model.
     *
     * If data values are found in mapped fields for currentPostal City|State|Zip, they
     * will be appended to the value for currentPostalAddress.
     *
     * If a data value is found in mapped field for studentID, it will be stored as a
     * user preference, since there is no dedicated Person field for that info.
	 *
	 * Required Mapping: netID, and also email if no netIDEmailDomain is set.
     * All other mappings are optional.
	 *
	 * @param attributeNameMap map from Vireo attribute names to LDAP field names
	 */
	public void setAttributeNameMap(Map<AttributeName,String> attributeNameMap) {
        this.attributeNameMap = attributeNameMap;
    }

    /**
     * @param mock
     *            True if LDAP authentication should be mocked with test
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
    public void setMockAttributes(Map<AttributeName,String> mockAttributes) {
        this.attributes = mockAttributes;
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
	public AuthenticationResult authenticate(String netID, String password,
			Request request) {

		if (StringUtils.isBlank(netID) || password == null)
			return AuthenticationResult.MISSING_CREDENTIALS;

        // 1. Get or construct the correct DN for the user.
        //    If not using a flat LDAP structure with no anon access and no admin login,
        //      this will connect to LDAP and also retrieve user attributes.
        String dn;
        if (!searchAnonymous && (StringUtils.isBlank(searchUser) || StringUtils.isBlank(searchPassword))) {
            // Anonymous search not allowed, and no admin user to search as.
            // Just construct the DN for a flat directory structure instead of searching for it.
            // Attributes will not be available.
            dn = attributeNameMap.get(AttributeName.NetID) + "=" + netID + "," + objectContext;
        } else {
            // Search LDAP, hierarchically if needed, to find the correct DN for the user.
            // This also looks up and stores the user's attributes.
            dn = ldapLookupUser(netID);
        }

        if (!StringUtils.isBlank(dn))
        {
            // The given netID does not exist in LDAP, or search failed
            Logger.info("ldap: failed login (DN search) for user " + netID);
            return AuthenticationResult.BAD_CREDENTIALS;
        }

        // 2. try to authenticate against LDAP with the user's supplied credentials
        if (!ldapAuthenticate(dn, password)) {
            // The netID is not in LDAP and we didn't search the DN, or the password was wrong
            Logger.info("ldap: failed login (auth) for user " + netID + " with DN=" + dn);
            return AuthenticationResult.BAD_CREDENTIALS;
        }

        // 3. Make sure we have the right email address from LDAP for the user, since
        //     it's required and possibly needed next.
        String email = attributes.remove(AttributeName.Email);
            if (StringUtils.isBlank(email) && !StringUtils.isBlank(netIDEmailDomain)) {
                email = netID + netIDEmailDomain;
        }
        if (StringUtils.isBlank(email)) {
            // still no email? Must make a fake one, or creating person will cause exception.
            // User will need to review account and fix it.
            Logger.error("ldap: no email address found for user with netID " + netID);
            email = "nobody@fake.com";
        }
        
        Person person;
        try {
            context.turnOffAuthorization();

            // 4. Check if Vireo Person already exists
            person = personRepo.findPersonByNetId(netID);
            if (person == null) {
                // if no person found, check if there's one with the same
                //  email address as this user's email address from LDAP
                if (!StringUtils.isBlank(email)) {
                    person = personRepo.findPersonByEmail(email);
                    if (person != null && person.getNetId() == null) {
                        // found existing user with no existing netID who
                        // presumably had not logged in via LDAP before.
                        // Add netID to this user.
                        Logger.warn("ldap: added netID " + netID + " to existing person with email " + email);
                        person.setNetId(netID);
                        person.save();
                    }
                }
            }

            // 5. if no existing Person, create one and pre-fill fields when possible
            if (person == null) {

                // get first and last name fields, since they're required.
                String firstName = attributes.remove(AttributeName.FirstName);
                String lastName = attributes.remove(AttributeName.LastName);
                if (StringUtils.isBlank(firstName+lastName)) {
                    // one of these being non-null is required to avoid an exception creating Person
                    // User will need to review account and fix it.
                    Logger.warn("ldap: no first or last name found for user with netID " + netID);
                    lastName = netID;
                }

				// Create the new person
				try {
					person = personRepo.createPerson(netID, email, firstName, lastName, RoleType.STUDENT).save();
				} catch (RuntimeException re) {
					// Unable to create new person, possibly because the email or netID already exist.
					Logger.error(re,"ldap: unable to create new person with netID " + netID);
					return AuthenticationResult.BAD_CREDENTIALS;
				}
                Logger.info("ldap: created person for netID " + netID);

                // Fill in the optional attributes from LDAP
                updatePersonWithAttributes(person, attributes);
            }

            // 6. Else if person already existed, we're done.
            //    Don't try to overwrite local info with newer from LDAP, because
            //    a. LDAP removes a bunch of fields and changes others when a student
            //       goes inactive, which may be before thesis is final, and
            //    b. the student or Graduate Studies office may have QC'ed fields
            //       and improved over whatever was originally from LDAP.

            context.login(person);
            return AuthenticationResult.SUCCESSFULL;

        } finally {
            context.restoreAuthorization();
        }
    }

    protected String ldapLookupUser(String netID) {

        if (mock) {
            // mock attributes should have already been set in this.attributes using setMockAttributes().
            return attributeNameMap.get(AttributeName.NetID) + "=" + netID + "," + objectContext;
        }

        // Set up environment for creating initial context
        Hashtable<String, String> env = new Hashtable<String, String>(11);
        env.put(javax.naming.Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(javax.naming.Context.PROVIDER_URL, providerURL);

        if (!StringUtils.isBlank(searchUser) && !StringUtils.isBlank(searchPassword)) {
            // Use admin credentials for search authentication
            env.put(javax.naming.Context.SECURITY_AUTHENTICATION, "simple");
            env.put(javax.naming.Context.SECURITY_PRINCIPAL, searchUser);
            env.put(javax.naming.Context.SECURITY_CREDENTIALS, searchPassword);
        } else {
            // Use anonymous authentication
            env.put(javax.naming.Context.SECURITY_AUTHENTICATION, "none");
        }

        DirContext ctx = null;
        try {
            // Create initial context
            ctx = new InitialDirContext(env);

            // look up attributes
            try {
                SearchControls controls = new SearchControls();
                controls.setSearchScope(searchScope.code());

                NamingEnumeration<SearchResult> answer = ctx.search(
                        providerURL + searchContext,
                        "(&({0}={1}))", new Object[] { attributeNameMap.get(AttributeName.NetID), netID },
                        controls);

                // LDAP returns a list—hopefully, there's only one user in it.
                if (answer.hasMoreElements()) {
                    SearchResult sr = answer.next();

                    // the most important thing we came for is the DN
                    String dn = sr.getName();
                    if (!StringUtils.isEmpty(searchContext)) {
                        dn += ("," + searchContext);
                    }
                    Logger.info("ldap: found user with dn " + dn + " for netID " + netID);

                    // For each attribute we're configured for, check LDAP for its corresponding field.
                    // If anything is found, save the result.
                    Attributes ldapAttributes = sr.getAttributes();
                    for (AttributeName name : attributeNameMap.keySet()) {
                        Attribute attribute = ldapAttributes.get(attributeNameMap.get(name));
                        if (attribute != null) {
                            String value = (String)attribute.get();
                            if (!StringUtils.isBlank(value)) {
                                attributes.put(name, value);
                            }
                        }
                    }

                    if (answer.hasMoreElements()) {
                        // Oh dear - more than one match. Config error?
                        Logger.error("ldap: more than one user in LDAP for netID " + netID);
                    }

                    return dn;
                }
            }
            catch (NamingException e) {
                Logger.warn(e, "ldap: failed search execution");
            }
        }
        catch (NamingException e) {
            Logger.warn(e, "ldap: failed search auth");
        }
        finally {
            // Close the context when we're done
            try {
                if (ctx != null) {
                    ctx.close();
                }
            }
            catch (NamingException e) {
            }
        }

        // No DN match found
        return null;
    }

    /**
     * Contact the ldap server and attempt to bind as user.
     * @param dn user's DN, constructed or searched for as appropriate
     * @param password user's password
     * @return whether the bind was successfully authenticated
     */
    protected boolean ldapAuthenticate(String dn, String password) {
        if (mock)
            return true;
        
        // Set up environment for creating initial context
        Hashtable<String, String> env = new Hashtable<String, String>();
        env.put(javax.naming.Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(javax.naming.Context.PROVIDER_URL, providerURL);

        // Authenticate
        env.put(javax.naming.Context.SECURITY_AUTHENTICATION, "Simple");
        env.put(javax.naming.Context.SECURITY_PRINCIPAL, dn);
        env.put(javax.naming.Context.SECURITY_CREDENTIALS, password);
        env.put(javax.naming.Context.AUTHORITATIVE, "true");
        env.put(javax.naming.Context.REFERRAL, "follow");

        DirContext ctx = null;
        try {
            // Try to bind
            ctx = new InitialDirContext(env);
        } catch (NamingException e) {
            return false;
        } finally {
            // Close the context when we're done
            try {
                if (ctx != null) {
                    ctx.close();
                }
            } catch (NamingException e) {
            }
        }

        return true;
    }


/*
        StudentID
 */
    void updatePersonWithAttributes(Person person, Map<AttributeName, String> attributes) {
        String value;

        value = attributes.get(AttributeName.MiddleName);
        if (!StringUtils.isBlank(value)) {
            person.setMiddleName(value);
        }

        value = attributes.get(AttributeName.BirthYear);
        if (!StringUtils.isBlank(value)) {
            try {
                Integer birthYear = Integer.valueOf(value);
                person.setBirthYear(birthYear);
            } catch (NumberFormatException nfe) {
                Logger.warn("ldap: Unable to interpret birth year attribute '"
                        + attributes.get(AttributeName.BirthYear)
                        + "'='" + value + "' as an integer.");
            }
        }

        value = attributes.get(AttributeName.DisplayName);
        if (!StringUtils.isBlank(value)) {
            person.setDisplayName(value);
        }

        value = attributes.get(AttributeName.Affiliations);
        if (!StringUtils.isBlank(value)) {
            person.addAffiliation(value);
        }

        value = attributes.get(AttributeName.CurrentPhoneNumber);
        if (!StringUtils.isBlank(value)) {
            person.setCurrentPhoneNumber(value);
        }

        value = attributes.get(AttributeName.CurrentPostalAddress);
        if (!StringUtils.isBlank(value)) {
            String city = attributes.get(AttributeName.CurrentPostalCity);
            if (!StringUtils.isBlank(city))
                value += "\n " + city;
            String state = attributes.get(AttributeName.CurrentPostalState);
            if (!StringUtils.isBlank(state))
                value += ", " + state;
            String zip = attributes.get(AttributeName.CurrentPostalZip);
            if (!StringUtils.isBlank(zip))
                value += " " + zip;

            person.setCurrentPostalAddress(value);
        }

        value = attributes.get(AttributeName.CurrentEmailAddress);
        if (!StringUtils.isBlank(value)) {
            person.setCurrentEmailAddress(value);
        }

        value = attributes.get(AttributeName.PermanentPhoneNumber);
        if (!StringUtils.isBlank(value)) {
            person.setPermanentPhoneNumber(value);
        }

        value = attributes.get(AttributeName.PermanentPostalAddress);
        if (!StringUtils.isBlank(value)) {
            person.setPermanentPostalAddress(value);
        }

        value = attributes.get(AttributeName.PermanentEmailAddress);
        if (!StringUtils.isBlank(value)) {
            person.setPermanentEmailAddress(value);
        }

        value = attributes.get(AttributeName.CurrentDegree);
        if (!StringUtils.isBlank(value)) {
            person.setCurrentDegree(value);
        }

        value = attributes.get(AttributeName.CurrentDepartment);
        if (!StringUtils.isBlank(value)) {
            person.setCurrentDepartment(value);
        }

        value = attributes.get(AttributeName.CurrentCollege);
        if (!StringUtils.isBlank(value)) {
            person.setCurrentCollege(value);
        }

        value = attributes.get(AttributeName.CurrentMajor);
        if (!StringUtils.isBlank(value)) {
            person.setCurrentMajor(value);
        }

        value = attributes.get(AttributeName.CurrentGraduationYear);
        if (!StringUtils.isBlank(value)) {
            try {
                Integer currentGraduationYear = Integer.valueOf(value);
                person.setCurrentGraduationYear(currentGraduationYear);
            } catch (NumberFormatException nfe) {
                Logger.warn("ldap: Unable to interpret current graduation year attribute '"
                        + attributes.get(AttributeName.CurrentGraduationYear)
                        + "'='" + value + "' as an integer.");
            }
        }

        value = attributes.get(AttributeName.CurrentGraduationMonth);
        if (!StringUtils.isBlank(value)) {
            try {
                Integer currentGraduationMonth = Integer.valueOf(value);
                person.setCurrentGraduationMonth(currentGraduationMonth);
            } catch (NumberFormatException nfe) {
                Logger.warn("ldap: Unable to interpret current graduation month attribute '"
                        + attributes.get(AttributeName.CurrentGraduationMonth)
                        + "'='" + value + "' as an integer.");
            }
        }

        value = attributes.get(AttributeName.StudentID);
        if (!StringUtils.isBlank(value)) {
            person.addPreference(STUDENT_ID_PREF_NAME, value);
        }

        if (valueInstitutionalIdentifier != null) {
            person.setInstitutionalIdentifier(valueInstitutionalIdentifier);
        }

        person.save();
        Logger.info("ldap: updated attributes for user with netID " + person.getNetId());
    }
}