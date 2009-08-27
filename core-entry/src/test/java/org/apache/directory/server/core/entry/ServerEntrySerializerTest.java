/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *  
 *    http://www.apache.org/licenses/LICENSE-2.0
 *  
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License. 
 *  
 */
package org.apache.directory.server.core.entry;


import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.directory.server.schema.bootstrap.ApacheSchema;
import org.apache.directory.server.schema.bootstrap.ApachemetaSchema;
import org.apache.directory.server.schema.bootstrap.BootstrapSchemaLoader;
import org.apache.directory.server.schema.bootstrap.CoreSchema;
import org.apache.directory.server.schema.bootstrap.CosineSchema;
import org.apache.directory.server.schema.bootstrap.InetorgpersonSchema;
import org.apache.directory.shared.ldap.schema.registries.Schema;
import org.apache.directory.server.schema.bootstrap.SystemSchema;
import org.apache.directory.server.schema.registries.DefaultRegistries;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.apache.directory.shared.ldap.schema.normalizers.DeepTrimToLowerNormalizer;
import org.apache.directory.shared.ldap.schema.normalizers.OidNormalizer;
import org.apache.directory.shared.ldap.schema.registries.OidRegistry;
import org.apache.directory.shared.ldap.schema.registries.Registries;
import org.apache.directory.shared.ldap.util.StringTools;

import static org.junit.Assert.assertEquals;
import org.junit.BeforeClass;
import org.junit.Test;


/**
 * Test the ServerEntry serialization/deserialization class
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class ServerEntrySerializerTest
{
    private static BootstrapSchemaLoader loader;
    private static Registries registries;
    private static OidRegistry oidRegistry;
    private static Map<String, OidNormalizer> oids;
    private static Map<String, OidNormalizer> oidOids;

    /**
     * Initialize the registries once for the whole test suite
     */
    @BeforeClass
    public static void setup() throws Exception
    {
        loader = new BootstrapSchemaLoader();
        oidRegistry = new OidRegistry();
        registries = new DefaultRegistries( "bootstrap", loader, oidRegistry );
        
        // load essential bootstrap schemas 
        Set<Schema> bootstrapSchemas = new HashSet<Schema>();
        bootstrapSchemas.add( new ApachemetaSchema() );
        bootstrapSchemas.add( new ApacheSchema() );
        bootstrapSchemas.add( new CoreSchema() );
        bootstrapSchemas.add( new SystemSchema() );
        bootstrapSchemas.add( new InetorgpersonSchema() );
        bootstrapSchemas.add( new CosineSchema() );
        loader.loadWithDependencies( bootstrapSchemas, registries );
        
        oids = new HashMap<String, OidNormalizer>();

        // DC normalizer
        OidNormalizer dcOidNormalizer = new OidNormalizer( "dc",
            new DeepTrimToLowerNormalizer( SchemaConstants.DOMAIN_COMPONENT_AT_OID ) );
        
        oids.put( "dc", dcOidNormalizer );
        oids.put( "domaincomponent", dcOidNormalizer );
        oids.put( "0.9.2342.19200300.100.1.25", dcOidNormalizer );

        // OU normalizer
        OidNormalizer ouOidNormalizer = new OidNormalizer( "ou",
            new DeepTrimToLowerNormalizer( SchemaConstants.OU_AT_OID ) );
        
        oids.put( "ou", ouOidNormalizer );
        oids.put( "organizationalUnitName",ouOidNormalizer );
        oids.put( "2.5.4.11", ouOidNormalizer );
    
        // Another map where we store OIDs instead of names.
        oidOids = new HashMap<String, OidNormalizer>();

        oidOids.put( "dc", dcOidNormalizer );
        oidOids.put( "domaincomponent", dcOidNormalizer );
        oidOids.put( "0.9.2342.19200300.100.1.25", dcOidNormalizer );

        oidOids.put( "ou", ouOidNormalizer );
        oidOids.put( "organizationalUnitName",ouOidNormalizer );
        oidOids.put( "2.5.4.11", ouOidNormalizer );
    }

    
    @Test public void testSerializeEmtpyServerEntry() throws Exception
    {
        LdapDN dn = LdapDN.EMPTY_LDAPDN;
        ServerEntry entry = new DefaultServerEntry( registries, dn );

        ServerEntrySerializer ses = new ServerEntrySerializer( registries );
        
        byte[] data = ses.serialize( entry );
        
        ServerEntry result = (ServerEntry)ses.deserialize( data );
        
        assertEquals( entry, result );
    }


    @Test public void testSerializeDNServerEntry() throws Exception
    {
        LdapDN dn = new LdapDN( "cn=text, dc=example, dc=com" );
        dn.normalize( oids );
        
        ServerEntry entry = new DefaultServerEntry( registries, dn );

        ServerEntrySerializer ses = new ServerEntrySerializer( registries );
        
        byte[] data = ses.serialize( entry );
        
        ServerEntry result = (ServerEntry)ses.deserialize( data );
        
        assertEquals( entry, result );
    }


    @Test public void testSerializeServerEntryOC() throws Exception
    {
        LdapDN dn = new LdapDN( "cn=text, dc=example, dc=com" );
        dn.normalize( oids );
        
        ServerEntry entry = new DefaultServerEntry( registries, dn );
        entry.add( "objectClass", "top", "person", "inetOrgPerson", "organizationalPerson" );

        ServerEntrySerializer ses = new ServerEntrySerializer( registries );

        byte[] data = ses.serialize( entry );
        
        ServerEntry result = (ServerEntry)ses.deserialize( data );
        
        assertEquals( entry, result );
    }


    @Test public void testSerializeServerEntry() throws Exception
    {
        LdapDN dn = new LdapDN( "cn=text, dc=example, dc=com" );
        dn.normalize( oids );
        
        ServerEntry entry = new DefaultServerEntry( registries, dn );
        entry.add( "objectClass", "top", "person", "inetOrgPerson", "organizationalPerson" );
        entry.add( "cn", "text", "test" );
        entry.add( "SN", (String)null );
        entry.add( "userPassword", StringTools.getBytesUtf8( "password" ) );

        ServerEntrySerializer ses = new ServerEntrySerializer( registries );
        
        byte[] data = ses.serialize( entry );
        
        ServerEntry result = (ServerEntry)ses.deserialize( data );
        
        assertEquals( entry, result );
    }


    @Test public void testSerializeServerEntryWithEmptyDN() throws Exception
    {
        LdapDN dn = new LdapDN( "" );
        dn.normalize( oids );
        
        ServerEntry entry = new DefaultServerEntry( registries, dn );
        entry.add( "objectClass", "top", "person", "inetOrgPerson", "organizationalPerson" );
        entry.add( "cn", "text", "test" );
        entry.add( "SN", (String)null );
        entry.add( "userPassword", StringTools.getBytesUtf8( "password" ) );

        ServerEntrySerializer ses = new ServerEntrySerializer( registries );
        
        byte[] data = ses.serialize( entry );
        
        ServerEntry result = (ServerEntry)ses.deserialize( data );
        
        assertEquals( entry, result );
    }

    
    @Test public void testSerializeServerEntryWithNoAttributes() throws Exception
    {
        LdapDN dn = new LdapDN( "" );
        dn.normalize( oids );
        
        ServerEntry entry = new DefaultServerEntry( registries, dn );

        ServerEntrySerializer ses = new ServerEntrySerializer( registries );
        
        byte[] data = ses.serialize( entry );
        
        ServerEntry result = (ServerEntry)ses.deserialize( data );
        
        assertEquals( entry, result );
    }
    
    
    @Test public void testSerializeServerEntryWithAttributeNoValue() throws Exception
    {
        LdapDN dn = new LdapDN( "" );
        dn.normalize( oids );
        
        ServerEntry entry = new DefaultServerEntry( registries, dn );

        ServerEntrySerializer ses = new ServerEntrySerializer( registries );
        EntryAttribute oc = new DefaultServerAttribute( "ObjectClass", registries.getAttributeTypeRegistry().lookup( "objectclass" ) );
        entry.add( oc );
        
        byte[] data = ses.serialize( entry );
        
        ServerEntry result = (ServerEntry)ses.deserialize( data );
        
        assertEquals( entry, result );
    }


    @Test public void testSerializeServerEntryWithAttributeStringValue() throws Exception
    {
        LdapDN dn = new LdapDN( "" );
        dn.normalize( oids );
        
        ServerEntry entry = new DefaultServerEntry( registries, dn );

        ServerEntrySerializer ses = new ServerEntrySerializer( registries );
        entry.add( "ObjectClass", "top", "person" );
        
        byte[] data = ses.serialize( entry );
        
        ServerEntry result = (ServerEntry)ses.deserialize( data );
        
        assertEquals( entry, result );
    }


    @Test public void testSerializeServerEntryWithAttributeBinaryValue() throws Exception
    {
        LdapDN dn = new LdapDN( "" );
        dn.normalize( oids );
        
        ServerEntry entry = new DefaultServerEntry( registries, dn );

        ServerEntrySerializer ses = new ServerEntrySerializer( registries );
        entry.add( "userPassword", StringTools.getBytesUtf8( "secret" ) );
        
        byte[] data = ses.serialize( entry );
        
        ServerEntry result = (ServerEntry)ses.deserialize( data );
        
        assertEquals( entry, result );
    }
}
