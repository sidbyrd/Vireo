package org.tdl.vireo.security.impl;

import org.apache.commons.lang.StringUtils;
import org.tdl.vireo.model.Person;
import org.tdl.vireo.model.RoleType;
import org.tdl.vireo.security.AuthenticationMethod;
import org.tdl.vireo.security.AuthenticationResult;
import play.Logger;
import play.mvc.Http.Request;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
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
	public String providerURL = "ldaps://ldap.myu.edu:636/";

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
        @SuppressWarnings({"UnusedDeclaration"})
        SINGLE (1),
        /**
         * subtree LDAP search scope
         */
        @SuppressWarnings({"UnusedDeclaration"})
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

    // added to netid to create an email address if no email address is available.
    private String netIDEmailDomain = null;

    // whether to allow account creation using NetID as filler value for missing name
    private boolean allowNetIdAsMissingName = false;

    // boilerplate value for person.institutionalIdentifier, if desired
    private String valueInstitutionalIdentifier = null;

    // value for UserStatus attribute indicating active account
    private String valueUserStatusActive = null;

    // The names of all the Person attributes and user data attributes that can be collected from LDAP
    //
    // Would've included DisplayName and CurrentEmailAddress as attribute names, but both those fields in
    // the Person object suffer from logic that lies about whether a real value is present (and not just
    // a derived value), and code exists that can recycle derived values back and store them as if they were
    // real values. That is incompatible with a login model that needs to know whether a real, non-derived
    // value is present in order to know whether to update the field or not.
    public enum AttributeName {
        NetID,
        Email,
        FirstName,
        MiddleName,
        LastName,
        BirthYear,
        Affiliations,
        CurrentPhoneNumber,
        CurrentPostalAddress,
        CurrentPostalCity,
        CurrentPostalState,
        CurrentPostalZip,
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
    // Pros: doesn't require db schema change, having arbitrary named String data on a student is flexible
    // Cons: this isn't what people expect to see in a field called preferences.
    public static final String personPrefKeyForStudentID = "LDAP_STUDENT_ID";

    // mapping from names of fields we can collect to the names used in LDAP for those fields
    private Map <AttributeName, String> ldapFieldNames = new HashMap<AttributeName, String>();
    
	// Whether LDAP responses and authentication will be mocked or real.
	private boolean mock = false;

	// map of fake attributes injected directly for mock
    private Map<String, String> mockAttributes;

    // mock DN expected for authentication
    private String mockUserDn;

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
     * If your LDAP server does not hold an email address for a user, or if it
     * does not allow searching for user attributes, you can specify your email
     * domain. This value is appended to the NetID in order to make an email
     * address. E.g. a NetID of 'user' and value of '@example.com' makes the email
     * 'user@example.com'.
     * If there is no email address available and this is null, login will
     * be rejected.
     * @param netIDEmailDomain domain to append, or null to disallow
     */
    public void setNetIDEmailDomain(String netIDEmailDomain) {
        this.netIDEmailDomain = netIDEmailDomain;
    }

    /**
     * If your LDAP server does not hold at least one of firstName or lastName for
     * a user, or is it does not allow searching for user attributes, you can
     * set this to true to allow using the NetID as a substitute value for the
     * lastName field, in order to satisfy Vireo's required Person fields.
     * Otherwise if there is no name available, login will be rejected.
     * @param allowNetIdAsMissingName true to allow NetID substitution for lastName, or
     * false to disallow login if no name available.
     */
    public void setAllowNetIdAsMissingName(boolean allowNetIdAsMissingName) {
        this.allowNetIdAsMissingName = allowNetIdAsMissingName;
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
    public Map<AttributeName, String> getLdapFieldNames() {
        return ldapFieldNames;
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
     * The map should contain LDAP field names as keys and corresponding
     * LDAP attribute data as values.
     *
     * @param mockAttributes
     *            A map of mock LDAP attributes.
     */
    public void setMockAttributes(Map<String, String> mockAttributes) {
        this.mockAttributes = mockAttributes;
    }
    public Map<String, String> getMockAttributes() {
        return mockAttributes;
    }

    /**
     * If this authentication method is configured to Mock an LDAP
     * connection then this is the DN that needs to be matched for
     * authentication to succeed.
     * @param mockUserDn a mock user DN to match against
     */
    public void setMockUserDn(String mockUserDn) {
        this.mockUserDn = mockUserDn;
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
     * Also looks up user attributes and applies them if configured.
     * Required attributes (email and name) are only updated the first time a
     * user logs in with their netID. Optional attributes (everything else) may be
     * added from LDAP on subsequent logins, but never modified from LDAP (to protect
     * local edits).
	 */
	public AuthenticationResult authenticate(String netID, String password,
			Request request) {

		if (StringUtils.isBlank(netID) || StringUtils.isBlank(password))
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

        if (StringUtils.isBlank(dn)) {
            // The given netID does not exist in LDAP, or search failed
            Logger.info("ldap: could not find user netID=" + netID);
            return AuthenticationResult.BAD_CREDENTIALS;
        }
        // sanity check -- did the search return the user we asked for?
        String foundNetId = attributes.get(AttributeName.NetID); // will be null if we couldn't search
        if(foundNetId != null && !foundNetId.equals(netID)) {
            Logger.error("ldap: search returned record with netID="+foundNetId+" but request was for netID="+netID);
            return AuthenticationResult.UNKNOWN_FAILURE;
        }

        // 2. try to authenticate against LDAP with the user's supplied credentials
        if (!ldapAuthenticate(dn, password)) {
            // rejected
            Logger.info("ldap: could not authenticate user netID=" + netID);
            return AuthenticationResult.BAD_CREDENTIALS;
        }

        // 3. Make sure we have the right email address from LDAP for the user, since
        //     it's required and possibly needed next.
        String email = attributes.remove(AttributeName.Email);
        if (StringUtils.isBlank(email) && !StringUtils.isBlank(netIDEmailDomain)) {
            email = netID + netIDEmailDomain;
        }

        try {
            // 4. Check if person already exists in Vireo,
            // first by NetID, if they've logged in with LDAP before
            context.turnOffAuthorization();
            Person person = personRepo.findPersonByNetId(netID);

            // if no person found based on netID, check if there's one with the same
            //  email address as what LDAP reported for this netID
            if (person == null && !StringUtils.isBlank(email)) {
                person = personRepo.findPersonByEmail(email);
                if (person != null) {
                    // An existing person is already using this email address.
                    // Do they already have a netID assigned?
                    if (person.getNetId() == null) {
                        // Found existing user with no existing netID who
                        // presumably had not logged in via LDAP before.
                        // Add netID to this user.
                        Logger.warn("ldap: added netID=" + netID + " to existing person id="+person.getId()+" email=" + email);
                        person.setNetId(netID);
                        person.save();
                    } else {
                        // If we didn't take the match due to netID conflict, reject the whole login
                        Logger.warn("ldap: rejected new user with netid="+netID+" trying to log in with same email="
                            + email + " as existing person id="+person.getId()+" netID=" + person.getNetId());
                        return AuthenticationResult.BAD_CREDENTIALS;
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
                    Logger.warn("ldap: refused new account to inactive user with netID=" + netID);
                    return AuthenticationResult.BAD_CREDENTIALS;
                }

                // required field for new Person: first and last name
                String firstName = attributes.remove(AttributeName.FirstName);
                String lastName = attributes.remove(AttributeName.LastName);
                if (StringUtils.isBlank(firstName) && StringUtils.isBlank(lastName)) {
                    // at least one of these being non-null is required
                    if (allowNetIdAsMissingName) {
                        Logger.warn("ldap: no first or last name found for user netID=" + netID);
                        lastName = netID;
                    } else {
                        Logger.warn("ldap: refused new account for netID="+netID+" because no name was available");
                        return AuthenticationResult.BAD_CREDENTIALS;
                    }
                }

                // required field for new Person: email address must be present and valid.
                // email validation is done when registering a new Password login, so it
                // seems consistent to do it for LDAP.
                try {
                    if (email != null) {
                        new InternetAddress(email).validate();
                    }
                } catch (AddressException ae) {
                    Logger.warn("ldap: rejected invalid email='"+email+"' because " +ae.getMessage());
                    email = null;
                }
                if (email == null) {
                    // Either email is blank (and no netIdEmailDomain was set), or it was invalid
                    Logger.warn("ldap: refused new account for netID="+netID+" because no valid email address was available");
                    return AuthenticationResult.BAD_CREDENTIALS;                    
                }

				// Create the new person
				try {
					person = personRepo.createPerson(netID, email, firstName, lastName, RoleType.STUDENT).save();
				} catch (RuntimeException re) {
					// Unable to create new person.
                    // We already checked that netID doesn't match and email doesn't match, so it must
                    // be some other sort of error.
					Logger.error(re,"ldap: attempted and failed to create new person with netID="+netID+" email="+email);
					return AuthenticationResult.BAD_CREDENTIALS;
				}

                // We have a new user
                Logger.info("ldap: created person id="+person.getId()+" netID="+netID+" email="+email);

                // MiddleName should be set at the same time as first and last, i.e. treated
                // as a semi-required attribute and set only at account creation, but the method
                // for doing the setting in code is as if it were an optional attribute.
                String middleName = attributes.remove(AttributeName.MiddleName);
                if (!StringUtils.isBlank(middleName)) {
                    person.setMiddleName(middleName);
                    person.save();
                }
            }

            // 6. Add any optional attributes from LDAP that are not already present.
            //    Do this every time the user logs in, not just the first time.
            addOptionalAttributes(person, attributes);

            // 6.5 Bonus note: why not update required attributes at every login?
            // These are required fields, so any update is necessarily an overwrite, not
            // merely an addition. (For name, only 1 of first,middle,last must be present,
            // but it makes no sense to update one without treating all three together.)
            // Email: if a student goes inactive and has to switch to a gmail account,
            //      campus LDAP won't reflect that. We should leave the student's choice
            //      in place.
            // Name: sometimes a student wants to publish under a name that isn't exactly
            //      how they registered with the university. We should not overwrite
            //      local edits here either.

            // 7. Log the user in
            context.login(person);
            Logger.info("ldap: successfully logged in person id="+person.getId()+" netID="+netID);
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
            SearchResult sr = null;

            if (! mock) {
                // Create initial context
                ctx = new InitialDirContext(env);

                // look up attributes
                SearchControls controls = new SearchControls();
                controls.setSearchScope(searchScope.code());
                NamingEnumeration<SearchResult> answer = ctx.search(
                        providerURL + searchContext,
                        "(&({0}={1}))", new Object[] { ldapFieldNames.get(AttributeName.NetID), netID },
                        controls);

                // if there was one match, save it.
                if (answer.hasMoreElements()) {
                    sr = answer.next();
                }
                // if there was more than one result, well, um... Oh dear. Config error?
                if (answer.hasMoreElements()) {
                    Logger.error("ldap.search: more than one user in LDAP for netID=" + netID+"! Using the first one.");
                }
                
            } else {
                // for mock, return the mock results if the given NetID matches the mock NetID.
                if (netID.equals(mockAttributes.get(ldapFieldNames.get(AttributeName.NetID)))) {
                    // for mock, make a fake SearchResult using given mock attributes and a phony DN
                    Attributes attrs = new BasicAttributes();
                    for (Map.Entry<String, String> entry : mockAttributes.entrySet()) {
                        attrs.put(entry.getKey(), entry.getValue());
                    }
                    // this is not mockUserDN. That's the "correct" value for checking against
                    // when we authenticate. This is just the node name as LDAP would return it.
                    // How it comes to match (or not match) mockUserDN is config dependent and
                    // part of what mock it meant to test.
                    String mockName = ldapFieldNames.get(AttributeName.NetID)+"="+netID;
                    sr = new SearchResult(mockName, null, attrs, true);
                }
            }

            // if no user was found, we're done.
            if (sr == null) {
                Logger.debug("ldap.search: user with netid=" + netID + " not found");
                return null;
            }

            // OK, we have a result.
            // The most important thing we came for is the DN.
            String dn = sr.getName();
            if (!StringUtils.isEmpty(searchContext)) {
                dn += ("," + searchContext);
            }
            Logger.debug("ldap.search: found user with dn=" + dn + " for netID=" + netID);

            // For each attribute we're configured for, check the result for its
            //   corresponding LDAP field.
            // Save anything found in the empty Map this method was passed.
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

            return dn;
        }
        catch (NamingException e) {
            // if we won't be returning a DN, don't return attributes either.
            attributes.clear();
            Logger.debug(e, "ldap.search: failed auth or execution");
            return null;
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
    }

    /**
     * Contact the ldap server and attempt to bind as user.
     * @param dn user's DN, constructed or searched for as appropriate
     * @param password user's password
     * @return whether the bind was successfully authenticated
     */
    protected boolean ldapAuthenticate(String dn, String password) {

        // Mock authentication always succeeds if the DN is correct -- no
        //  password check.
        if (mock)
            return dn.equals(mockUserDn);
        
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
            Logger.debug("ldap.auth: server refused to authenticate DN=" + dn + " due to " + e.getExplanation());
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

        Logger.debug("ldap.auth: successfully authenticated with DN=" + dn);
        return true;
    }

    /**
     * Updates supplied Person with attributes from given map, only for fields that do not
     * already contain a value.
     * Does not apply to NetID, Email, or *Name
     * @param person the person to add attributes to
     * @param attributes values for the attributes, mapped as values to attribute name keys.
     *  Certain attributes get special treatment:
     *  CurrentPostalAddress, City, State, and Zip will be combined into CurrentPostalAddress if present.
     *  Attributes that are integer values will be parsed, of course.
     *  PermanentEmailAddress must pass format validation.
     *  StudentID will be stored as a user preference with key personPrefKeyForStudentID, since
     *    there is no place else to store that datum.
     *  UserStatus is not saved in the Person; it is only consulted when logging in.
     *  InstitutionalIdentifier is saved into the Person from configuration value if
     *    present, not from attributes, since it isn't typically in LDAP.
     *  For phone numbers, "-" is synonymous with null.
     *  For Addresses, "$" is synonymous with newline (and therefore a single "$" is
     *    interpreted as blank)
     *  For CurrentMajor, "Undeclared" is synonymous with null
     */
    void addOptionalAttributes(Person person, Map<AttributeName, String> attributes) {
        String value;
        boolean modified = false;

        // treat MiddleName the same as FirstName and LastName, for consistency
        //value = attributes.get(AttributeName.MiddleName);
        //if (!StringUtils.isBlank(value) && StringUtils.isBlank(person.getMiddleName())) {
        //    person.setMiddleName(value);
        //    modified = true;
        //}

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
                        value += "\n" + city;
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
            try {
                new InternetAddress(value).validate();
                person.setPermanentEmailAddress(value);
                modified = true;
            } catch (AddressException ae) {
                Logger.warn("ldap: rejected invalid permanent email='"+value+"' because " +ae.getMessage());
            }
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
            if (!value.toLowerCase().equals("undeclared")) {
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
        if (!StringUtils.isBlank(value) && person.getPreference(personPrefKeyForStudentID) == null) {
            person.addPreference(personPrefKeyForStudentID, value);
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