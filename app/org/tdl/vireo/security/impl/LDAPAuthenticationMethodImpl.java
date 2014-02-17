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
    private SearchScope searchScope = SearchScope.OBJECT;

    // If true, the initial bind will be performed anonymously.
    private boolean searchAnonymous = false;

    // if hierarchical LDAP search is required, the login credentials for the search
    private String searchUser = null;
    private String searchPassword = null;

    // added to netid to create an email address if email not explicitly specified.
    private String netIDEmailDomain = null;

    // boilerplate value for person.institutionalIdentifier, if desired
    private String valueInstitutionalIdentifier = null;

    // value for UserStatus attribute indicating active account
    private String valueUserStatusActive = null;
    
    // the names of all the Person attributes and user data attributes that can be collected from LDAP
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
        StudentID,
        UserStatus
    }

    // Student ID is stored as a preference in Person, since there's no dedicated field for it.
    public final String STUDENT_ID_PREF_NAME = "LDAP_STUDENT_ID";

    // mapping from names of fields we can collect to the names used in LDAP for those fields
    private Map <AttributeName, String> ldapFieldNames = new HashMap<AttributeName, String>();
    
	// Whether LDAP responses and authentication will be mocked or real.
	private boolean mock = (Play.mode == Play.Mode.DEV);

	// map of fake attributes injected directly for mock
	private Map<AttributeName,String> mockAttributes = new HashMap<AttributeName, String>();

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
     * Set value that the UserStatus attribute should have if student is to
     * be allowed to log into Vireo for the first time. If you don't want to
     * perform this check, leave this field null.
     * @param valueUserStatusActive value indicating user is active
     */
    public void setValueUserStatusActive(String valueUserStatusActive) {
        this.valueUserStatusActive = valueUserStatusActive;
    }

	/**
	 * Set the LDAP header mapping to Vireo attributes. The keys are the names of all the
     * attributes Vireo can be configured to look for. Then the value of that key
	 * must be the expected LDAP header name for that particular attribute. For a
	 * definition of what most attributes are, see the Person model.
	 *
	 * Required Mapping: netID, and also email if no netIDEmailDomain is set.
     * All other mappings are optional.
	 *
	 * @param ldapFieldNames map from Vireo attribute names to LDAP field names.
     *  mappings to blank values will be ignored.
	 */
	public void setLdapFieldNames(Map<AttributeName, String> ldapFieldNames) {
        if (StringUtils.isBlank(ldapFieldNames.get(AttributeName.NetID))) {
            throw new IllegalArgumentException("ldap: missing required attribute NetID in the provided ldapFieldNames.");
        }

        this.ldapFieldNames.clear();
        // save all non-blank mappings. (some may be present but blank depending on Spring/config setup)
        for (Map.Entry<AttributeName, String> entry : ldapFieldNames.entrySet()) {
            if (!StringUtils.isBlank(entry.getValue())) {
                this.ldapFieldNames.put(entry.getKey(), entry.getValue());
            }
        }
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
        this.mockAttributes = mockAttributes;
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
        Map<AttributeName,String> attributes = new HashMap<AttributeName, String>();

        if (!searchAnonymous && (StringUtils.isBlank(searchUser) || StringUtils.isBlank(searchPassword))) {
            // Anonymous search not allowed, and no admin user to search as.
            // Just construct the DN for a flat directory structure instead of searching for it.
            // Attributes will not be available.
            dn = ldapFieldNames.get(AttributeName.NetID) + "=" + netID + "," + objectContext;
        } else {
            // Search LDAP, hierarchically if needed, to find the correct DN for the user.
            // This also looks up and stores the user's attributes.
            dn = ldapUserSearch(netID, attributes);
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

            // 5. If no existing Person, create one.
            if (person == null) {

                // check if configured to deny new accounts to students who have gone inactive.
                if (valueUserStatusActive != null
                    && !StringUtils.isBlank(attributes.get(AttributeName.UserStatus))
                    && !attributes.get(AttributeName.UserStatus).toLowerCase().equals(valueUserStatusActive))
                {
                    Logger.error("ldap: refused new account to inactive user with netID " + netID);
                    return AuthenticationResult.BAD_CREDENTIALS;
                }

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
            }

            // 6. Add any optional attributes from LDAP.
            // Note: This will not overwrite any values that already existed in the Person,
            //   which may have already been QC'ed by the student of Graduate Studies office.
            // This will also not remove any pre-existing Person attributes which have been
            //   removed from LDAP, for example when a student changes to inactive status
            //   before their thesis is final and many LDAP attributes go away.
            updatePersonWithAttributes(person, attributes);


            context.login(person);
            return AuthenticationResult.SUCCESSFULL;

        } finally {
            context.restoreAuthorization();
        }
    }

    /**
     * Bind to LDAP not as user but as either an admin or anon (according
     * to the configuration of searchAnonymous, searchUser, and searchAnonymous).
     * Search for the user's record in LDAP to determine the correct DN to use
     * later when checking that the user credentials will authenticate.
     * Also retrieve user attributes while we're in there.
     * @param netID the id of the user to search for
     * @param attributes a caller-supplied empty Map into which we will add, for each retrieved
     *      LDAP value for the specified user, whose field name matched a value configured in
     *      ldapFieldNames, a KVP consisting of the Vireo attribute name and the value from LDAP.
     * @return the correct DN to specify the user whose netID was provided, or null if
     *      not found or an error occurred
     */
    protected String ldapUserSearch(String netID, Map<AttributeName, String> attributes) {

        // for mock, use pre-injected attributes and make up a plausible flat-directory DN
        if (mock) {
            attributes.putAll(mockAttributes);
            return ldapFieldNames.get(AttributeName.NetID) + "=" + netID + "," + objectContext;
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
            SearchControls controls = new SearchControls();
            controls.setSearchScope(searchScope.code());

            NamingEnumeration<SearchResult> answer = ctx.search(
                    providerURL + searchContext,
                    "(&({0}={1}))", new Object[] { ldapFieldNames.get(AttributeName.NetID), netID },
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
                Attributes ldapFields = sr.getAttributes();
                for (AttributeName attributeName : ldapFieldNames.keySet()) {
                    String fieldName = ldapFieldNames.get(attributeName);
                    Attribute ldapField = ldapFields.get(fieldName);
                    if (ldapField != null) {
                        String value = (String)ldapField.get();
                        if (!StringUtils.isBlank(value)) {
                            attributes.put(attributeName, value);
                        }
                    }
                }

                if (answer.hasMoreElements()) {
                    // Oh dear - more than one user match. Config error?
                    Logger.error("ldap: more than one user in LDAP for netID " + netID);
                }

                return dn;
            }
        }
        catch (NamingException e) {
            // if we won't be returning a DN, don't return attributes either.
            attributes.clear();
            Logger.warn(e, "ldap: failed search auth or execution");
        }
        finally {
            // Close the context when we're done
            try {
                if (ctx != null) {
                    ctx.close();
                }
            }
            catch (NamingException e) {
                //
            }
        }

        // No DN match found, or error doing the search
        return null;
    }

    /**
     * Contact the ldap server and attempt to bind as user.
     * @param dn user's DN, constructed or searched for as appropriate
     * @param password user's password
     * @return whether the bind was successfully authenticated
     */
    protected boolean ldapAuthenticate(String dn, String password) {

        // Mock authentication always succeeds.
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
                //
            }
        }

        return true;
    }

    /**
     * Updates supplied Person with attributes from given map, only for fields that do not
     * already contain a value.
     * @param person the person to add attributes to
     * @param attributes values for the attributes, mapped to attribute names.
     *  Certain attributes get special treatment:
     *  CurrentPostalAddress, City, State, and Zip will be combined into CurrentPostalAddress if present.
     *  Attributes that are integer values will be parsed, of course.
     *  StudentID will be stored as a user preference with key STUDENT_ID_PREF_NAME, since
     *    there is no place else to store that datum.
     *  UserStatus is not saved in the Person; it is only consulted when logging in.
     *  InstitutionalIdentifier is saved into the Person from configuration value if
     *    present, not from attributes, since it isn't typically in LDAP.
     *  For phone numbers, "-" is synonymous with null.
     *  For Addresses, "$" is synonymous with newline (and therefore a single "$" is
     *    interpreted as blank)
     *  For CurrentMajor, "Undeclared" is synonymous with null
     */
    void updatePersonWithAttributes(Person person, Map<AttributeName, String> attributes) {
        String value;
        boolean modified = false;

        value = attributes.get(AttributeName.MiddleName);
        if (!StringUtils.isBlank(value) && StringUtils.isBlank(person.getMiddleName())) {
            person.setMiddleName(value);
            modified = true;
        }

        value = attributes.get(AttributeName.BirthYear);
        if (!StringUtils.isBlank(value) && person.getBirthYear() == null) {
            try {
                Integer birthYear = Integer.valueOf(value);
                person.setBirthYear(birthYear);
                modified = true;
            } catch (NumberFormatException nfe) {
                Logger.warn("ldap: Unable to interpret birth year attribute '"
                        + attributes.get(AttributeName.BirthYear)
                        + "'='" + value + "' as an integer.");
            }
        }

        value = attributes.get(AttributeName.DisplayName);
        if (!StringUtils.isBlank(value) && StringUtils.isBlank(person.getDisplayName())) {
            person.setDisplayName(value);
            modified = true;
        }

        value = attributes.get(AttributeName.Affiliations);
        if (!StringUtils.isBlank(value) && person.getAffiliations().size() == 0) {
            person.addAffiliation(value);
            modified = true;
        }

        value = attributes.get(AttributeName.CurrentPhoneNumber);
        if (!StringUtils.isBlank(value) && StringUtils.isBlank(person.getCurrentPhoneNumber())
                && !value.trim().equals("-")) {
            person.setCurrentPhoneNumber(value);
            modified = true;
        }

        value = attributes.get(AttributeName.CurrentPostalAddress);
        if (!StringUtils.isBlank(value) && StringUtils.isBlank(person.getCurrentPostalAddress())) {
            value = value.replace("$", "\n");
            if (!StringUtils.isBlank(value)) {
                String city = attributes.get(AttributeName.CurrentPostalCity);
                if (!StringUtils.isBlank(city)) {
                    city = city.replace("$", "/n");
                    if (!StringUtils.isBlank(city))
                        value += "\n " + city;
                }
                String state = attributes.get(AttributeName.CurrentPostalState);
                if (!StringUtils.isBlank(state)) {
                    state = state.replace("$", "/n");
                    if (!StringUtils.isBlank(state))
                        value += ", " + state;
                }
                String zip = attributes.get(AttributeName.CurrentPostalZip);
                if (!StringUtils.isBlank(zip)) {
                    zip = zip.replace("$", "/n");
                    if (!StringUtils.isBlank(zip))
                        value += " " + zip;
                }

                person.setCurrentPostalAddress(value);
                modified = true;
            }
        }

        value = attributes.get(AttributeName.CurrentEmailAddress);
        if (!StringUtils.isBlank(value) && StringUtils.isBlank(person.getCurrentEmailAddress())) {
            person.setCurrentEmailAddress(value);
            modified = true;
        }

        value = attributes.get(AttributeName.PermanentPhoneNumber);
        if (!StringUtils.isBlank(value) && StringUtils.isBlank(person.getPermanentPhoneNumber())
                && !value.trim().equals("-")) {
            person.setPermanentPhoneNumber(value);
            modified = true;
        }

        value = attributes.get(AttributeName.PermanentPostalAddress);
        if (!StringUtils.isBlank(value) && StringUtils.isBlank(person.getPermanentPostalAddress())) {
            value = value.replace("$", "\n");
            if (!StringUtils.isBlank(value)) {
                person.setPermanentPostalAddress(value);
                modified = true;
            }
        }

        value = attributes.get(AttributeName.PermanentEmailAddress);
        if (!StringUtils.isBlank(value) && StringUtils.isBlank(person.getPermanentEmailAddress())) {
            person.setPermanentEmailAddress(value);
            modified = true;
        }

        value = attributes.get(AttributeName.CurrentDegree);
        if (!StringUtils.isBlank(value) && StringUtils.isBlank(person.getCurrentDegree())) {
            person.setCurrentDegree(value);
            modified = true;
        }

        value = attributes.get(AttributeName.CurrentDepartment);
        if (!StringUtils.isBlank(value) && StringUtils.isBlank(person.getCurrentDepartment())) {
            person.setCurrentDepartment(value);
            modified = true;
        }

        value = attributes.get(AttributeName.CurrentCollege);
        if (!StringUtils.isBlank(value) && StringUtils.isBlank(person.getCurrentCollege())) {
            person.setCurrentCollege(value);
            modified = true;
        }

        value = attributes.get(AttributeName.CurrentMajor);
        if (!StringUtils.isBlank(value) && StringUtils.isBlank(person.getCurrentMajor())) {
            if (!value.toLowerCase().equals("Undeclared")) {
                person.setCurrentMajor(value);
                modified = true;
            }
        }

        value = attributes.get(AttributeName.CurrentGraduationYear);
        if (!StringUtils.isBlank(value) && person.getCurrentGraduationYear() == null) {
            try {
                Integer currentGraduationYear = Integer.valueOf(value);
                person.setCurrentGraduationYear(currentGraduationYear);
                modified = true;
            } catch (NumberFormatException nfe) {
                Logger.warn("ldap: Unable to interpret current graduation year attribute '"
                        + attributes.get(AttributeName.CurrentGraduationYear)
                        + "'='" + value + "' as an integer.");
            }
        }

        value = attributes.get(AttributeName.CurrentGraduationMonth);
        if (!StringUtils.isBlank(value) && person.getCurrentGraduationMonth() == null) {
            try {
                Integer currentGraduationMonth = Integer.valueOf(value);
                person.setCurrentGraduationMonth(currentGraduationMonth);
                modified = true;
            } catch (NumberFormatException nfe) {
                Logger.warn("ldap: Unable to interpret current graduation month attribute '"
                        + attributes.get(AttributeName.CurrentGraduationMonth)
                        + "'='" + value + "' as an integer.");
            }
        }

        value = attributes.get(AttributeName.StudentID);
        if (!StringUtils.isBlank(value) && person.getPreference(STUDENT_ID_PREF_NAME) == null) {
            person.addPreference(STUDENT_ID_PREF_NAME, value);
            modified = true;
        }

        if (valueInstitutionalIdentifier != null && StringUtils.isBlank(person.getInstitutionalIdentifier())) {
            person.setInstitutionalIdentifier(valueInstitutionalIdentifier);
            modified = true;
        }

        if (modified) {
            Logger.info("ldap: updated attributes for user with netID " + person.getNetId());
            person.save();
        }
    }
}