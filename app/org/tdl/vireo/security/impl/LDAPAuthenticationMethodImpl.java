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

// adapted starting from org.dspace.authenticate.LDAPAuthentication and several Vireo classes.
/**
 * Authenticates netID and password against LDAP. Retrieves personal information.
 * Selects or creates correct Person. Logs in.
 */
public class LDAPAuthenticationMethodImpl extends
		AbstractAuthenticationMethodImpl.AbstractExplicitAuthenticationMethod implements
		AuthenticationMethod.Explicit {

	/* Injected configuration */

	// URL of server
	private String providerURL = "";

    // context for constructing DN
    private String objectContext = "";
	
    // context for searching for DN and attributes
    private String searchContext = "";

    /**
     * LDAP search scope. May be object scope, single-level, or subtree.
     * Correct value will depend on your LDAP setup.
     */
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

    // search for user DN and attributes anonymously?
    private boolean searchAnonymous = false;

    // if non-anonymous search is required, the login credentials for the search
    private String searchUser = null;
    private String searchPassword = null;

    // added to netid to create an email address if no email address is available.
    private String netIDEmailDomain = null;

    // allow account creation using NetID as filler value for missing name?
    private boolean allowNetIdAsMissingName = false;

    // allow new users to match to existing NetID-free user accounts based on email?
    private boolean allowNewUserEmailMatch = false;

    // boilerplate value for person.institutionalIdentifier, if desired
    private String valueInstitutionalIdentifier = null;

    // value for UserStatus attribute indicating active account, or null to disable checking
    private String valueUserStatusActive = null;

    /**
     * Names for all the Person attributes and user data attributes that can be collected from LDAP.
     * Most correspond to fields in Person.
     */
    // Note: LDAP can include a value for displayName, and Shibboleth includes currentEmail, but setting
    // either of those from an external source seems incompatible with the way the behavior of those
    // attributes was designed.
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

    /**
     * Student ID is stored as a Preference in Person, since there's no dedicated field for it.
     * This is the name of the Preference it's stored in.
     *
     * Pros: doesn't require db schema change; having arbitrary named String data on a student is flexible
     * Cons: this isn't what people/code expects to see in a field called preferences.
     */
    public static final String personPrefKeyForStudentID = "LDAP_STUDENT_ID";

    // mapping from names of fields we can collect to the names used in LDAP for those fields
    private Map <AttributeName, String> ldapFieldNames = new HashMap<AttributeName, String>();
    
	// Whether LDAP responses and authentication will be test stub values or real.
	private boolean testStub = false;

	// map of fake ldap user attributes injected directly as test stub values
    private Map<String, String> stubAttributes;

    // test stub DN expected for authentication match
    private String stubUserDn;

    /**
     * test stub password might as well just be hardcoded.
     */
    public static final String stubPassword = "secret";

    /**
     * This is the url to the institution's ldap server. The /o=myu.edu
     * may or may not be required depending on the LDAP server setup.
     * A server may also require the ldaps:// protocol.
     * Example: ldaps://ldap.myu.edu:636/
     * @param providerURL URL required to talk to LDAP server
     */
    public void setProviderURL(String providerURL) {
        this.providerURL = providerURL;
    }

    /**
     * This is the object context used when authenticating the user.
     *  It is appended to the NetID field and username.
     *  For example uid=username,ou=people,o=myu.edu.
     *  This must match the LDAP server configuration.
     * @param objectContext LDAP object context where users may be found
     */
    public void setObjectContext(String objectContext) {
        this.objectContext = objectContext;
    }

    /**
     * This is the search context used when searching for a user to
     * retrieve their DN and user attributes.
     * Often the searchContext is the same as the objectContext,
     * but this depends on each individual LDAP server configuration.
     * @param searchContext LDAP context used to search for user info
     */
    public void setSearchContext(String searchContext) {
        this.searchContext = searchContext;
    }

    /**
     * This is the optional search scope value for the initial LDAP search.
     * This will depend on your LDAP server setup.
     * @param searchScope one of three valid search scopes
     */
    public void setSearchScope(SearchScope searchScope) {
        this.searchScope = searchScope;
    }

    /**
     * If your users are spread out across a hierarchical tree on your
       LDAP server, or if you want to retrieve attributes about users,
       you will need to search the tree to find the full DN of the user
       who is logging in.
     * If your server allows anonymous search, you may set searchAnonymous
       to true.
     * If not, you will need to specify the full DN and password of a
       user that is allowed to bind in order to search for the users
       using setSearchUser() and setSearchPassword().
     * If neither searchAnonymous is true, nor searchUser/Pass is specified,
       LDAP will not do the hierarchical search for a DN and will assume
       a flat directory structure. Since it cannot search, it will also
       not be able to return user attributes.
     * @param searchAnonymous If true, the initial bind will be performed
     * anonymously to get the correct DN and retrieve attributes.
     * This applies to both flat and hierarchical searches.
     */
    public void setSearchAnonymous(boolean searchAnonymous) {
        this.searchAnonymous = searchAnonymous;
    }

    /**
     * Set credentials of a user allowed to connect to the LDAP server and
     * search for the DN and attributes of the user trying to log in. This is
     * not required if your LDAP may be searched anonymously and searchAnonymous
     * is true, or if you are using a flat directory structure and do not need
     * to search for user attributes.
     * @param searchUser the full DN of the authorized search account
     */
    public void setSearchUser(String searchUser) {
        this.searchUser = searchUser;
    }

    /**
     * Set credentials of a user allowed to connect to the LDAP server and
     * search for the DN and attributes of the user trying to log in. This is
     * not required if your LDAP may be searched anonymously and searchAnonymous
     * is true, or if you are using a flat directory structure and do not need
     * to search for user attributes.
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
     * If you do this, you should probably have a procedure for manually
     * correcting the names later.
     * @param allowNetIdAsMissingName true to allow NetID substitution for lastName, or
     * false to disallow login if no name available.
     */
    public void setAllowNetIdAsMissingName(boolean allowNetIdAsMissingName) {
        this.allowNetIdAsMissingName = allowNetIdAsMissingName;
    }

    /**
     * If user A submits valid NetId credentials and there is no existing Vireo
     * person with that NetID, but there is an existing person B with the email
     * address derived from LDAP for user A and no existing NetID, should user A
     * in be allowed to claim the existing person B account? If so, person B's
     * NetID will be filled in, so this would only happen once per user. This is
     * useful if you are transitioning from password accounts to LDAP accounts,
     * for example.
     *
     * Security note: This seems secure as long as person B wasn't ever able to
     * freely change their email address without verification (in order to
     * capture the new and still retain password access to the account), and as
     * long as user A cannot freely change the email address LDAP reports for
     * them (in order to hijack an person B's existing password account). Both
     * of these conditions seem to be the default case unless you configure
     * things differently.
     * If this still makes you uncomfortable, leave this feature off.
     * @param allowNewUserEmailMatch whether to allow claiming accounts based on email
     */
    public void setAllowNewUserEmailMatch(boolean allowNewUserEmailMatch) {
        this.allowNewUserEmailMatch = allowNewUserEmailMatch;
    }

    /**
     * LDAP is usually single-institution, so if this is set, all users
     * will get this value set as their institutional identifier (as long
     * as they don't already have a different one set).
     * @param valueInstitutionalIdentifier a boilerplate inst. id. to set
     */
    public void setValueInstitutionalIdentifier(String valueInstitutionalIdentifier) {
        this.valueInstitutionalIdentifier = valueInstitutionalIdentifier;
    }

    /**
     * Set value that the UserStatus attribute (if present) must have to
     * permit a user to log into Vireo for the first time. If the attribute
     * is present but differs from what is set here, login will be denied
     * for any user not already in the Vireo system.
     * If you don't want to perform this check, leave this field null.
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
     * @param testStub
     * True if, instead of connecting to a real LDAP, we should instead connect
     * to a table of test values to return for specific queries.
     */
    public void setTestStub(boolean testStub) {
        this.testStub = testStub;
    }

    /**
     * If this authentication method is configured for a test stub LDAP
     * connection then these are the raw LDAP field names and attributes
     * that will be returned from the test stub LDAP service when authenticating.
     *
     * The map should contain LDAP field names as keys and corresponding
     * LDAP attribute data as values.
     *
     * @param stubAttributes
     *            A map of stub LDAP attributes.
     */
    public void setStubAttributes(Map<String, String> stubAttributes) {
        this.stubAttributes = stubAttributes;
    }
    public Map<String, String> getStubAttributes() {
        return stubAttributes;
    }

    /**
     * If this authentication method is configured to use a test stub LDAP
     * connection then this is the DN that needs to be matched for
     * authentication to succeed.
     * @param stubUserDn a stub user DN to match against
     */
    public void setStubUserDn(String stubUserDn) {
        this.stubUserDn = stubUserDn;
    }

    /**
	 * LDAP authentication. Handles both:
     * 1) Authentication against a flat LDAP tree where all users are in the same unit
     *   and searching LDAP for a user's record is not possible.
     *
     *   This method of search cannot return user attributes, so pay attention
     *   to setAllowNetIdAsMissingName() and setNetIdEmailDomain().
     *
     * 2) Authentication against structured hierarchical LDAP trees of users, or
     *   authentication against a flat tree with search.
     *
     *   An initial bind is required (not connected as the logging-in user) in order to
     *   search the tree and find the DN for the given NetID. A second bind is then
     *   required to authenticate the user by binding directly to their searched-for DN
     *   with their supplied password.
     *
     *   This method of search supports returning user attributes.
     *   Required attributes (email and name) are only updated the first time a
     *   user logs in with their netID. Optional attributes (everything else) may be
     *   added from LDAP on subsequent logins, but never modified from LDAP (to protect
     *   local edits).
     *
     * Logs in the correct person, or creates and logs in a new person based on LDAP
     * attributes if required (and possible).
     * 
     * @return whether authentication (and any subsequent steps such as attribute lookup
     *   or creating a new user) and login were successful, given the user credentials and
     *   the configuration settings.
	 */
	public AuthenticationResult authenticate(String netID, String password,
			Request request) {

		if (StringUtils.isBlank(netID) || StringUtils.isBlank(password))
			return AuthenticationResult.MISSING_CREDENTIALS;

        // 1. Get or construct the correct DN for the user.
        //    If not using a flat LDAP structure with no anon access and no admin login,
        //      this will connect to LDAP and also retrieve user attributes.
        Map<AttributeName,String> attributes = new HashMap<AttributeName, String>();
        String dn = ldapUserSearch(netID, attributes);

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
        //     it's required and possibly needed to find the Person.
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
            //  email address as what LDAP reported for this netID (if configured to do this)
            if (person == null && allowNewUserEmailMatch && !StringUtils.isBlank(email)) {
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
            // Maybe that's appropriate for Shibboleth and its multi-institution setup,
            // but it doesn't seem right for the way we use LDAP.

            // 7. Log the user in
            context.login(person);
            Logger.info("ldap: successfully logged in person id="+person.getId()+" netID="+netID);
            return AuthenticationResult.SUCCESSFULL;

        } finally {
            context.restoreAuthorization();
        }
    }

    /**
     * - Bind to LDAP not as user but as either an admin or anon (according
     *   to the configuration of searchAnonymous, searchUser, and searchAnonymous).
     *   Search for the user's record in LDAP to determine the correct DN to use
     *   later when checking that the user credentials will authenticate.
     *   Also retrieve user attributes while we're in there.
     * - Or if no ability to bind for search, just construct a DN for a flat-directory
     *   system, and return no attributes.
     * @param netID the id of the user to search for
     * @param attributes a caller-supplied empty Map into which we will add, for each retrieved
     *      LDAP value for the specified user, whose field name matched a value configured in
     *      ldapFieldNames, a KVP consisting of the Vireo attribute name and the raw value
     *      from LDAP.
     * @return the correct DN to attempt to bind to to authenticate the user whose
     *      netID was provided, or null if not found or an error occurred
     */
    protected String ldapUserSearch(String netID, Map<AttributeName, String> attributes) {

        // Set up environment for creating initial context
        Hashtable<String, String> env = new Hashtable<String, String>(11);

        // set up authentication
        if (!StringUtils.isBlank(searchUser) && !StringUtils.isBlank(searchPassword)) {
            // Use admin credentials for search authentication
            env.put(javax.naming.Context.SECURITY_AUTHENTICATION, "simple");
            env.put(javax.naming.Context.SECURITY_PRINCIPAL, searchUser);
            env.put(javax.naming.Context.SECURITY_CREDENTIALS, searchPassword);
        } else {
            // No credentials; must use anonymous authentication
            if (searchAnonymous) {
                // Anonymous search is allowed. Good.
                env.put(javax.naming.Context.SECURITY_AUTHENTICATION, "none");
            } else {
                // Anonymous search not allowed. We have no way left to search.
                // Just construct the DN for a flat directory structure instead of searching for it.
                // Attributes will not be available.
                return ldapFieldNames.get(AttributeName.NetID) + "=" + netID + "," + objectContext;
            }
        }

        // set up connection
        env.put(javax.naming.Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(javax.naming.Context.PROVIDER_URL, providerURL);

        // set up search scope
        SearchControls controls = new SearchControls();
        controls.setSearchScope(searchScope.code());

        DirContext ctx = null;
        try {
            SearchResult sr = null;

            if (!testStub) {
                // do search, filtering on netID, with configured search setup
                ctx = new InitialDirContext(env);
                NamingEnumeration<SearchResult> answer = ctx.search(
                        providerURL + searchContext,
                        "(&({0}={1}))", new Object[] { ldapFieldNames.get(AttributeName.NetID), netID },
                        controls);

                // if one user matched, save it.
                if (answer.hasMoreElements()) {
                    sr = answer.next();
                }
                // if more than one user matched the same netID, well, um... Oh dear. Config error?
                if (answer.hasMoreElements()) {
                    Logger.error("ldap.search: more than one user in LDAP for netID=" + netID+"! Using the first one.");
                }
            } else {
                // for connection to stub test, save the canned results if the given NetID matches the stub record's NetID field.
                if (netID.equals(stubAttributes.get(ldapFieldNames.get(AttributeName.NetID)))) {
                    // for test, make a fake SearchResult using given stub attributes
                    Attributes attrs = new BasicAttributes();
                    for (Map.Entry<String, String> entry : stubAttributes.entrySet()) {
                        attrs.put(entry.getKey(), entry.getValue());
                    }
                    // This is not a DN. This is just the raw node name as LDAP
                    // would return it but before further processing by this method.
                    String stubName = ldapFieldNames.get(AttributeName.NetID)+"="+netID;
                    sr = new SearchResult(stubName, null, attrs, true);
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
            if (!StringUtils.isEmpty(objectContext)) {
                dn += ("," + objectContext);
            }
            Logger.debug("ldap.search: found user with dn=" + dn + " for netID=" + netID);

            // For each attribute we're configured for, check the LDAP data for its
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

        // set up environment for creating initial context
        Hashtable<String, String> env = new Hashtable<String, String>();
        env.put(javax.naming.Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(javax.naming.Context.PROVIDER_URL, providerURL);

        // set up authentication credentials
        env.put(javax.naming.Context.SECURITY_AUTHENTICATION, "Simple");
        env.put(javax.naming.Context.SECURITY_PRINCIPAL, dn);
        env.put(javax.naming.Context.SECURITY_CREDENTIALS, password);
        env.put(javax.naming.Context.AUTHORITATIVE, "true");
        env.put(javax.naming.Context.REFERRAL, "follow");

        DirContext ctx = null;
        try {
            if (!testStub) {
                // Try to bind
                ctx = new InitialDirContext(env);
            } else {
                // for test stub connection, check that DN and password match stub values
                if (StringUtils.isBlank(stubUserDn) || !stubUserDn.equals(dn)) {
                    throw new NamingException("ldap.stubserver: user DN doesn't match required DN="+((stubUserDn ==null)?"null": stubUserDn));
                }
                if (StringUtils.isBlank(stubPassword) || !stubPassword.equals(password)) {
                    throw new NamingException("ldap.stubserver: incorrect password");
                }
            }
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
     *  CurrentPostalAddress, City, State, and Zip will be combined into CurrentPostalAddress
     *    if present. This does not apply to PermanentPostalAddress since I've never seen
     *    an LDAP that broke down the permanent address, only the current one.
     *  PermanentPostalAddress: "$" is synonymous with newline (and therefore a single "$"
     *    is interpreted as blank). Some LDAP systems use this convention, and it shouldn't
     *    hurt on those that don't.
     *  BirthYear, CurrentGraduationMonth, CurrentGraduationYear: integer values must be
     *    parsed successfully. If not, the value will be ignored. In addition,
     *    grad month must be within the valid range for person.setGraduationMonth() or it
     *    will be ignored.
     *  PermanentEmailAddress must pass format validation. If not, the value will be ignored.
     *  StudentID will be stored as a user preference with key personPrefKeyForStudentID, since
     *    there is no place else in Person to store that field.
     *  UserStatus is not saved to the Person; it is only consulted when logging in and is
     *    ignored here.
     *  InstitutionalIdentifier is saved into the Person from configuration value if
     *    present, not from attributes, since it isn't typically in LDAP.
     *  CurrentPhoneNumber and PermanentPhoneNumber: "-" is synonymous with null. Some
     *    LDAP systems use this convention, and it shouldn't hurt on those that don't.
     *  CurrentMajor: "Undeclared" is synonymous with null. If we saved the "undeclared"
     *    value, it would block a later actual choice of majors from overwriting.
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
                Logger.warn("ldap: unable to interpret current graduation year attribute '"
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
                Logger.warn("ldap: unable to interpret current graduation month attribute '"
                        + attributes.get(AttributeName.CurrentGraduationMonth)
                        + "'='" + value + "' as an integer.");
            } catch (IllegalArgumentException iae) {
                Logger.warn("ldap: unable to set current graduation month attribute '"
                        + attributes.get(AttributeName.CurrentGraduationMonth)
                        + "'='" + value + "' because "+iae.getMessage());
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

    /*
     * Not very useful for this auth method--we cannot statelessly tell anything
     * about what the failure was from the information provided. And stashing it in the
     * request object seems tacky. Ideally, AuthenticationResult would have a message
     * field where we could put some details if it were important to report anything
     * more than a mere "failed".
     * This works better for Shibboleth because
     * 1) as an implicit auth method, shib can already assume auth succeeded, and its job
     *    is just to set attributes and deal with Person objects. LDAP has to deal with
     *    the possibility that the NetID or password was simply wrong. But there's no
     *    was to tell those situations apart--they're both covered by BAD_CREDENTIALS.
     * 2) all the information to authenticate the shib request was in the Request,
     *    which this method here still has access to. For LDAP, the server response is gone.
    @Override
    public String getFailureMessage(Request request, AuthenticationResult result) {
    }
    */
}
