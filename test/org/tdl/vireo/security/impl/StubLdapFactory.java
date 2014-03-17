package org.tdl.vireo.security.impl;

import org.apache.commons.lang.StringUtils;
import play.Logger;

import javax.naming.*;
import javax.naming.directory.*;
import javax.naming.spi.InitialContextFactory;
import java.net.UnknownHostException;
import java.util.Hashtable;
import java.util.Map;
import java.util.NoSuchElementException;

public class StubLdapFactory implements InitialContextFactory{
    // map of fake ldap user attributes injected directly as test stub values
    public static Map<String, String> attributes;

    public static String serverName="ldapstub:test";

    // added to the end of "uid=bbthornton" to get his full user DN
    public static String objectContext = "OU=people,DC=myu,DC=edu";
    // provided when searching for "uid=bbthornton" to specify where to search
    public static String searchContext = "OU=people,DC=myu,DC=edu";
    // password expected for user login authentication match with whatever NetID is provided
    public static String userPass = "secret";

    // username DN expected for admin search user authentication match
    public static String adminDn = "uid=admin,OU=Service Accounts,DC=myu,DC=edu";
    // password expected for admin search user authentication match
    public static String adminPass = "adminsecret";

    // ldap field name used as key to NetID value
    public static String netIdLdapFieldName = "uid";

    @Override
    // This is where the StubInitialDirContext is returned to the code being tested.
    public Context getInitialContext(Hashtable<?, ?> environment) throws NamingException {
        return new StubDirContext(environment);
    }

    // This is what's returned when a matching search() is called on a StubDirContext.
    // The enumeration holds only one result, which returns a result based on stubAttributes.
    public class SingleStubNamingEnumeration<T> implements NamingEnumeration<T> {
        boolean delivered = false;
        boolean closed = false;

        public void close() throws NamingException { closed = true; }
        public boolean hasMore() throws NamingException { return hasMoreElements(); }
        public T next() throws NamingException { return nextElement(); }

        public boolean hasMoreElements() {
            if (closed) {
                throw new IllegalStateException("Can't query a closed search result");
            }
            return !delivered;
        }

        public T nextElement() {
            if (closed) {
                throw new IllegalStateException("Can't query a closed search result");
            }
            if (delivered) {
                throw new NoSuchElementException("This stub has exactly one element.");
            }
            delivered = true;

            // make a fake SearchResult using stubAttributes
            final Attributes attrs = new BasicAttributes();
            for (Map.Entry<String, String> entry : attributes.entrySet()) {
                attrs.put(entry.getKey(), entry.getValue());
            }
            final String netId = attributes.get(netIdLdapFieldName);
            final String name = netIdLdapFieldName+'='+netId;
            return (T)new SearchResult(name, null, attrs, true);
        }
    }

    // This is what's returned when a non-matching search() is called on a StubDirContext.
    public static class EmptyNamingEnumeration<T> implements NamingEnumeration<T> {
        public static EmptyNamingEnumeration<SearchResult> single = new EmptyNamingEnumeration<SearchResult>();
        private EmptyNamingEnumeration(){ }
        public void close() throws NamingException { }
        public boolean hasMore() throws NamingException { return hasMoreElements(); }
        public T next() throws NamingException { return nextElement(); }

        public boolean hasMoreElements() {
            return false;
        }
        public T nextElement() {
            throw new NoSuchElementException("This stub never had any elements.");
        }
    }

    // This has implementations of search() and close(). That's it, out of 55(!) required methods.
    public class StubDirContext implements DirContext {
        public boolean closed = false;
        public boolean authUser = false;
        public boolean authAdmin = false;

        public StubDirContext(Hashtable<?, ?> environment) throws NamingException {
            final String providerUrl = (String)environment.get(Context.PROVIDER_URL);
            if (providerUrl==null || !providerUrl.equals(serverName)) {
                Logger.debug("ldap.stubserver: Failed connection to wrong provider url '"+providerUrl+'\'');
                NamingException e = new NamingException("Wrapped exception; that's how LDAP rolls.");
                e.setRootCause(new UnknownHostException("Connection error. The server '"+providerUrl+"' does not exist."));
                throw e;
            }
            final String authType = (String)environment.get(Context.SECURITY_AUTHENTICATION);
            if (authType==null) {
                throw new NamingException("ldap.stub: environment didn't include authtype");
            } else if (authType.toLowerCase().equals("none")) {
                // authtype none: make sure anonymous login is really allowed
                if (userPass==null) {
                    authUser = true;
                    Logger.debug("ldap.stubserver: user bind anonymous success");
                }
                if (adminDn==null && adminPass==null) {
                    authAdmin = true;
                    Logger.debug("ldap.stubserver: admin bind anonymous success");
                }
                if (!authAdmin && !authUser) {
                    Logger.debug("ldap.stubserver: bind failed: anonymous auth not allowed");
                    throw new NamingException("ldap.stub: anonymous auth not allowed");
                }
            } else if (authType.toLowerCase().equals("simple")) {
                // authtype simple: check that DN and password match stub values
                final String authDn = (String)environment.get(Context.SECURITY_PRINCIPAL);
                final String authPass = (String)environment.get(Context.SECURITY_CREDENTIALS);
                if (!StringUtils.isBlank(authDn) && !StringUtils.isBlank(authPass)) {
                    // To match authDN against, build user's DN from netID and object context
                    String userDn = netIdLdapFieldName+'='+attributes.get(netIdLdapFieldName)+','+objectContext;
                    if  (authDn.equals(userDn) && authPass.equals(userPass)) {
                        authUser = true;
                        Logger.debug("ldap.stubserver: user bind success: '" + userDn+'\'');
                    } else if  (authDn.equals(adminDn) && authPass.equals(adminPass)) {
                        authAdmin = true;
                        Logger.debug("ldap.stubserver: admin bind success: '" + adminDn+'\'');
                    } else {
                        Logger.debug("ldap.stubserver: incorrect username or password");
                        throw new NamingException("ldap.stubserver: incorrect username/password");
                    }
                } else {
                    throw new NamingException("ldap.stubserver: blank username or password");
                }
            } else {
                throw new NamingException("ldap.stub: unrecognized authtype '"+authType+'\'');
            }
        }

        @Override
        public Attributes getAttributes(Name name) throws NamingException {
            throw new RuntimeException("getAttributes(Name name)");
            ///return null;
        }

        @Override
        public Attributes getAttributes(String s) throws NamingException {
            throw new RuntimeException("getAttributes(String s)");
            //return null;
        }

        @Override
        public Attributes getAttributes(Name name, String[] strings) throws NamingException {
            throw new RuntimeException("getAttributes(Name name, String[] strings)");
            //return null;
        }

        @Override
        public Attributes getAttributes(String s, String[] strings) throws NamingException {
            throw new RuntimeException("getAttributes(String s, String[] strings)");
            //return null;
        }

        @Override
        public void modifyAttributes(Name name, int i, Attributes attributes) throws NamingException {
            throw new RuntimeException("modifyAttributes(Name name, int i, Attributes attributes)");
        }

        @Override
        public void modifyAttributes(String s, int i, Attributes attributes) throws NamingException {
            throw new RuntimeException("modifyAttributes(String s, int i, Attributes attributes)");
        }

        @Override
        public void modifyAttributes(Name name, ModificationItem[] modificationItems) throws NamingException {
            throw new RuntimeException("modifyAttributes(Name name, ModificationItem[] modificationItems)");
        }

        @Override
        public void modifyAttributes(String s, ModificationItem[] modificationItems) throws NamingException {
            throw new RuntimeException("modifyAttributes(String s, ModificationItem[] modificationItems)");
        }

        @Override
        public void bind(Name name, Object o, Attributes attributes) throws NamingException {
            Logger.debug("bind(Name name, Object o, Attributes attributes)");
        }

        @Override
        public void bind(String s, Object o, Attributes attributes) throws NamingException {
            Logger.debug("bind(String s, Object o, Attributes attributes)");
        }

        @Override
        public void rebind(Name name, Object o, Attributes attributes) throws NamingException {
            throw new RuntimeException("rebind(Name name, Object o, Attributes attributes)");
        }

        @Override
        public void rebind(String s, Object o, Attributes attributes) throws NamingException {
            throw new RuntimeException("rebind(String s, Object o, Attributes attributes)");
        }

        @Override
        public DirContext createSubcontext(Name name, Attributes attributes) throws NamingException {
            throw new RuntimeException("createSubcontext(Name name, Attributes attributes)");
            //return null;
        }

        @Override
        public DirContext createSubcontext(String s, Attributes attributes) throws NamingException {
            throw new RuntimeException("createSubcontext(String s, Attributes attributes)");
            //return null;
        }

        @Override
        public DirContext getSchema(Name name) throws NamingException {
            throw new RuntimeException("getSchema(Name name)");
            //return null;
        }

        @Override
        public DirContext getSchema(String s) throws NamingException {
            throw new RuntimeException("getSchema(String s)");
            //return null;
        }

        @Override
        public DirContext getSchemaClassDefinition(Name name) throws NamingException {
            throw new RuntimeException("getSchemaClassDefinition(Name name)");
            //return null;
        }

        @Override
        public DirContext getSchemaClassDefinition(String s) throws NamingException {
            throw new RuntimeException("getSchemaClassDefinition(String s)");
            //return null;
        }

        @Override
        public NamingEnumeration<SearchResult> search(Name name, Attributes attributes, String[] strings) throws NamingException {
            throw new RuntimeException("search(Name name, Attributes attributes, String[] strings)");
            //return null;
        }

        @Override
        public NamingEnumeration<SearchResult> search(String s, Attributes attributes, String[] strings) throws NamingException {
            throw new RuntimeException("search(String s, Attributes attributes, String[] strings)");
            //return null;
        }

        @Override
        public NamingEnumeration<SearchResult> search(Name name, Attributes attributes) throws NamingException {
            throw new RuntimeException("search(Name name, Attributes attributes)");
            //return null;
        }

        @Override
        public NamingEnumeration<SearchResult> search(String s, Attributes attributes) throws NamingException {
            throw new RuntimeException("search(String s, Attributes attributes)");
            //return null;
        }

        @Override
        public NamingEnumeration<SearchResult> search(Name name, String s, SearchControls searchControls) throws NamingException {
            throw new RuntimeException("search(Name name, String s, SearchControls searchControls)");
            //return null;
        }

        @Override
        public NamingEnumeration<SearchResult> search(String s, String s1, SearchControls searchControls) throws NamingException {
            throw new RuntimeException("search(String s, String s1, SearchControls searchControls)");
            //return null;
        }

        @Override
        public NamingEnumeration<SearchResult> search(Name name, String s, Object[] objects, SearchControls searchControls) throws NamingException {
            throw new RuntimeException("search(Name name, String s, Object[] objects, SearchControls searchControls)");
            //return null;
        }

        @Override
        // returns the configured stub answer if the requested NetID matches
        public NamingEnumeration<SearchResult> search(String name, String filterExpr, Object[] filterArgs, SearchControls searchControls) throws NamingException {

            // First, find all the ways we can reject the request. Just like a real LDAP server :)
            if (closed) {
                throw new IllegalStateException("Can't query a closed connection");
            }
            if (!authAdmin) {
                Logger.debug("ldap.stubserver: refused search due to lack of admin auth");
                throw new IllegalStateException("Can't search on dirContext without admin authentication");
            }
            if (!filterExpr.equals("(&({0}={1}))") || filterArgs.length!=2) {
                Logger.debug("ldap.stubserver: invalid search filters");
                throw new InvalidSearchFilterException("unexpected or invalid search filters");
            }
            if (name==null || !name.startsWith(serverName)) {
                Logger.debug("ldap.stubserver: client has wrong server name in search context. No results will be found.");
                Logger.debug("ldap.stubserver: client search context does not start with correct server name. No results fill be found.");
                return EmptyNamingEnumeration.single;
            }
            if (!StringUtils.substringAfter(name, serverName).equals(searchContext)) {
                Logger.debug("ldap.stubserver: client search context does not match. No results fill be found.");
                return EmptyNamingEnumeration.single;
            }
            if (filterArgs.length<1 || !filterArgs[0].equals(netIdLdapFieldName)) {
                Logger.debug("ldap.stubserver: client searched on a field '"+(filterArgs[0]==null?"":filterArgs[0])+"' that wasn't the NetID field. No results will be found.");
                return EmptyNamingEnumeration.single;
            }
            if (filterArgs.length<2 || StringUtils.isBlank((String)filterArgs[1])) {
                Logger.debug("ldap.stubserver: blank NetID search value. No results will be found.");
                return EmptyNamingEnumeration.single;
            }

            // Actually see if we have it
            final String requestedNetId = (String)filterArgs[1];
            if (requestedNetId.equals(attributes.get(netIdLdapFieldName))) {
                // found results
                Logger.debug("ldap.stubserver: found records match for user "+requestedNetId);
                return new SingleStubNamingEnumeration<SearchResult>();
            } else {
                // no results
                Logger.debug("ldap.stubserver: no records match for user "+requestedNetId);
                return EmptyNamingEnumeration.single;
            }
        }

        @Override
        public Object lookup(Name name) throws NamingException {
            throw new RuntimeException("lookup(Name name)");
            //return null;
        }

        @Override
        public Object lookup(String s) throws NamingException {
            throw new RuntimeException("lookup(String s)");
            //return null;
        }

        @Override
        public void bind(Name name, Object o) throws NamingException {
            Logger.debug("name="+name+" o="+o);
            throw new RuntimeException("bind(Name name, Object o)");
        }

        @Override
        public void bind(String s, Object o) throws NamingException {
            Logger.debug("string="+s+" o="+o);
            throw new RuntimeException("bind(String s, Object o)");
        }

        @Override
        public void rebind(Name name, Object o) throws NamingException {
            throw new RuntimeException("rebind(Name name, Object o)");
        }

        @Override
        public void rebind(String s, Object o) throws NamingException {
            throw new RuntimeException("rebind(String s, Object o)");
        }

        @Override
        public void unbind(Name name) throws NamingException {
            throw new RuntimeException("unbind(Name name)");
        }

        @Override
        public void unbind(String s) throws NamingException {
            throw new RuntimeException("unbind(String s)");
        }

        @Override
        public void rename(Name name, Name name1) throws NamingException {
            throw new RuntimeException("rename(Name name, Name name1)");
        }

        @Override
        public void rename(String s, String s1) throws NamingException {
            throw new RuntimeException("rename(String s, String s1)");
        }

        @Override
        public NamingEnumeration<NameClassPair> list(Name name) throws NamingException {
            throw new RuntimeException("list(Name name)");
            //return null;
        }

        @Override
        public NamingEnumeration<NameClassPair> list(String s) throws NamingException {
            throw new RuntimeException("list(String s)");
            //return null;
        }

        @Override
        public NamingEnumeration<Binding> listBindings(Name name) throws NamingException {
            throw new RuntimeException("listBindings(Name name)");
            //return null;
        }

        @Override
        public NamingEnumeration<Binding> listBindings(String s) throws NamingException {
            throw new RuntimeException("listBindings(String s)");
            //return null;
        }

        @Override
        public void destroySubcontext(Name name) throws NamingException {
            throw new RuntimeException("destroySubcontext(Name name)");
        }

        @Override
        public void destroySubcontext(String s) throws NamingException {
            throw new RuntimeException("destroySubcontext(String s)");
        }

        @Override
        public Context createSubcontext(Name name) throws NamingException {
            throw new RuntimeException("createSubcontext(Name name)");
            //return null;
        }

        @Override
        public Context createSubcontext(String s) throws NamingException {
            throw new RuntimeException("createSubcontext(String s)");
            //return null;
        }

        @Override
        public Object lookupLink(Name name) throws NamingException {
            throw new RuntimeException("lookupLink(Name name)");
            //return null;
        }

        @Override
        public Object lookupLink(String s) throws NamingException {
            throw new RuntimeException("lookupLink(String s)");
            //return null;
        }

        @Override
        public NameParser getNameParser(Name name) throws NamingException {
            throw new RuntimeException("getNameParser(Name name)");
            //return null;
        }

        @Override
        public NameParser getNameParser(String s) throws NamingException {
            throw new RuntimeException("getNameParser(String s)");
            //return null;
        }

        @Override
        public Name composeName(Name name, Name name1) throws NamingException {
            throw new RuntimeException("composeName(Name name, Name name1)");
            //return null;
        }

        @Override
        public String composeName(String s, String s1) throws NamingException {
            throw new RuntimeException("composeName(String s, String s1)");
            //return null;
        }

        @Override
        public Object addToEnvironment(String s, Object o) throws NamingException {
            throw new RuntimeException("addToEnvironment(String s, Object o)");
            //return null;
        }

        @Override
        public Object removeFromEnvironment(String s) throws NamingException {
            throw new RuntimeException("removeFromEnvironment(String s)");
            //return null;
        }

        @Override
        public Hashtable<?, ?> getEnvironment() throws NamingException {
            throw new RuntimeException("getEnvironment()");
            //return null;
        }

        @Override
        public void close() throws NamingException {
            closed = true;
        }

        @Override
        public String getNameInNamespace() throws NamingException {
            throw new RuntimeException("getNameInNamespace()");
            //return null;
        }
    };
}
