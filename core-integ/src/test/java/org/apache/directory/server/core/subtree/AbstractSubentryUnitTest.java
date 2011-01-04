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
package org.apache.directory.server.core.subtree;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.apache.directory.ldap.client.api.LdapConnection;
import org.apache.directory.server.constants.ApacheSchemaConstants;
import org.apache.directory.server.core.integ.AbstractLdapTestUnit;
import org.apache.directory.server.core.integ.IntegrationUtils;
import org.apache.directory.shared.ldap.entry.Entry;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.shared.ldap.exception.LdapException;
import org.apache.directory.shared.ldap.ldif.LdifUtils;
import org.apache.directory.shared.ldap.message.AddResponse;
import org.apache.directory.shared.ldap.message.ResultCodeEnum;
import org.junit.After;
import org.junit.Before;

/**
 * Common class for the subentry operation tests
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class AbstractSubentryUnitTest extends AbstractLdapTestUnit
{
    // The shared LDAP admin connection
    protected static LdapConnection adminConnection;

    // The shared LDAP user connection
    protected static LdapConnection userConnection;


    @Before
    public void init() throws Exception
    {
        adminConnection = IntegrationUtils.getAdminConnection( service );
        userConnection = IntegrationUtils.getConnectionAs( service, "cn=testUser,ou=system", "test" );
    }


    @After
    public void shutdown() throws Exception
    {
        adminConnection.close();
        userConnection.close();
    }


    /**
     * Helper methods
     */
    protected Entry getAdminRole( String dn ) throws Exception
    {
        Entry lookup = adminConnection.lookup( dn, "administrativeRole" );

        assertNotNull( lookup );

        return lookup;
    }
    
    
    /**
     * Checks that an entry is absent from the DIT
     */
    protected boolean checkIsAbsent( String dn ) throws LdapException
    {
        Entry entry = adminConnection.lookup( dn );
        
        return entry == null;
    }

    
    /**
     * Checks that an entry is present in the DIT
     */
    protected boolean checkIsPresent( String dn ) throws LdapException
    {
        Entry entry = adminConnection.lookup( dn );
        
        return entry != null;
    }
    
    
    /**
     * Gets the UUIDref
     */
    private String getUuidRef( String apDn, String attributeType ) throws LdapException
    {
        Entry entry = adminConnection.lookup( apDn, attributeType );
        
        if ( entry == null )
        {
            return null;
        }
        
        EntryAttribute attribute = entry.get( attributeType);
        
        if ( attribute == null )
        {
            return null;
        }
        
        return attribute.getString();
    }

    
    
    /**
     * Creates an AAP 
     */
    protected void createAAP( String dn ) throws LdapException
    {
        Entry autonomousArea = LdifUtils.createEntry( 
            dn, 
            "ObjectClass: top",
            "ObjectClass: organizationalUnit", 
            "administrativeRole: autonomousArea"
            );

        // It should succeed
        AddResponse response = adminConnection.add( autonomousArea );

        assertEquals( ResultCodeEnum.SUCCESS, response.getLdapResult().getResultCode() );
    }
    
    
    // ---- AC methods -------------------------------------------------------------------
    
    /**
     * Creates an AC SAP 
     */
    protected void createAcSAP( String dn ) throws LdapException
    {
        Entry sap = LdifUtils.createEntry( 
            dn, 
            "ObjectClass: top",
            "ObjectClass: organizationalUnit", 
            "administrativeRole: accessControlSpecificArea"
            );

        // It should succeed
        AddResponse response = adminConnection.add( sap );

        assertEquals( ResultCodeEnum.SUCCESS, response.getLdapResult().getResultCode() );
    }
    
    
    /**
     * Creates an AC IAP 
     */
    protected void createAcIAP( String dn ) throws LdapException
    {
        Entry iap = LdifUtils.createEntry( 
            dn, 
            "ObjectClass: top",
            "ObjectClass: organizationalUnit", 
            "administrativeRole: accessControlInnerArea"
            );

        // It should succeed
        AddResponse response = adminConnection.add( iap );

        assertEquals( ResultCodeEnum.SUCCESS, response.getLdapResult().getResultCode() );
    }
    
    
    /**
     * Gets the AccessControl seqNumber of a given AP
     */
    protected long getAcSeqNumber( String apDn ) throws LdapException
    {
        Entry entry = adminConnection.lookup( apDn, "AccessControlSeqNumber" );
        
        EntryAttribute attribute = entry.get( ApacheSchemaConstants.ACCESS_CONTROL_SEQ_NUMBER_AT );
        
        if ( attribute == null )
        {
            return Long.MIN_VALUE;
        }
        
        return Long.parseLong( attribute.getString() );
    }


    /**
     * Gets the AccessControl UUIDref
     */
    protected String getAcUuidRef( String apDn ) throws LdapException
    {
        return getUuidRef( apDn, "AccessControlSubentryUuid" );
    }


    // ---- CA methods -------------------------------------------------------------------

    /**
     * Creates a CA SAP 
     */
    protected void createCaSAP( String dn ) throws LdapException
    {
        Entry sap = LdifUtils.createEntry( 
            dn, 
            "ObjectClass: top",
            "ObjectClass: organizationalUnit", 
            "administrativeRole: collectiveAttributeSpecificArea"
            );

        // It should succeed
        AddResponse response = adminConnection.add( sap );

        assertEquals( ResultCodeEnum.SUCCESS, response.getLdapResult().getResultCode() );
    }
    
    
    /**
     * Creates a CA IAP 
     */
    protected void createCaIAP( String dn ) throws LdapException
    {
        Entry iap = LdifUtils.createEntry( 
            dn, 
            "ObjectClass: top",
            "ObjectClass: organizationalUnit", 
            "administrativeRole: collectiveAttributeInnerArea"
            );

        // It should succeed
        AddResponse response = adminConnection.add( iap );

        assertEquals( ResultCodeEnum.SUCCESS, response.getLdapResult().getResultCode() );
    }
    
    
    /**
     * Gets the CollectiveAttribute seqNumber of a given AP
     */
    protected long getCaSeqNumber( String apDn ) throws LdapException
    {
        Entry entry = adminConnection.lookup( apDn, "CollectiveAttributeSeqNumber" );
        
        EntryAttribute attribute = entry.get( ApacheSchemaConstants.COLLECTIVE_ATTRIBUTE_SEQ_NUMBER_AT );
        
        if ( attribute == null )
        {
            return Long.MIN_VALUE;
        }
        
        return Long.parseLong( attribute.getString() );
    }
    
    
    /**
     * Gets the CollectiveAttribute UUID ref
     */
    protected String getCaUuidRef( String apDn ) throws LdapException
    {
        return getUuidRef( apDn, "CollectiveAttributeSubentryUuid" );
    }
    

    /**
     * Creates a CollectiveAttribute subentry
     */
    protected void createCaSubentry( String dn, String subtree ) throws LdapException
    {
        Entry subentry = LdifUtils.createEntry( 
            dn, 
            "ObjectClass: top",
            "ObjectClass: subentry", 
            "ObjectClass: collectiveAttributeSubentry",
            "subtreeSpecification", subtree,
            "c-o: Test Org" );

        AddResponse response = adminConnection.add( subentry );
        assertEquals( ResultCodeEnum.SUCCESS, response.getLdapResult().getResultCode() );
    }

    
    // ---- SS methods -------------------------------------------------------------------

    /**
     * Creates a SS SAP 
     */
    protected void createSsSAP( String dn ) throws LdapException
    {
        Entry sap = LdifUtils.createEntry( 
            dn, 
            "ObjectClass: top",
            "ObjectClass: organizationalUnit", 
            "administrativeRole: subschemaSpecificArea"
            );

        // It should succeed
        AddResponse response = adminConnection.add( sap );

        assertEquals( ResultCodeEnum.SUCCESS, response.getLdapResult().getResultCode() );
    }
    
    
    /**
     * Gets the SubSchema UUID ref
     */
    protected String getSsUuidRef( String apDn ) throws LdapException
    {
        return getUuidRef( apDn, "SubSchemaSubentryUuid" );
    }

    
    /**
     * Gets the SubSchema seqNumber of a given AP
     */
    protected long getSsSeqNumber( String apDn ) throws LdapException
    {
        Entry entry = adminConnection.lookup( apDn, "SubSchemaSeqNumber" );
        
        EntryAttribute attribute = entry.get( ApacheSchemaConstants.SUB_SCHEMA_SEQ_NUMBER_AT );
        
        if ( attribute == null )
        {
            return Long.MIN_VALUE;
        }
        
        return Long.parseLong( attribute.getString() );
    }
    
    
    // ---- TE methods -------------------------------------------------------------------

    /**
     * Creates a TE SAP 
     */
    protected void createTeSAP( String dn ) throws LdapException
    {
        Entry sap = LdifUtils.createEntry( 
            dn, 
            "ObjectClass: top",
            "ObjectClass: organizationalUnit", 
            "administrativeRole: TriggerExecutionSpecificArea"
            );

        // It should succeed
        AddResponse response = adminConnection.add( sap );

        assertEquals( ResultCodeEnum.SUCCESS, response.getLdapResult().getResultCode() );
    }
    
    
    /**
     * Creates a TE IAP 
     */
    protected void createTeIAP( String dn ) throws LdapException
    {
        Entry iap = LdifUtils.createEntry( 
            dn, 
            "ObjectClass: top",
            "ObjectClass: organizationalUnit", 
            "administrativeRole: TriggerExecutionInnerArea"
            );

        // It should succeed
        AddResponse response = adminConnection.add( iap );

        assertEquals( ResultCodeEnum.SUCCESS, response.getLdapResult().getResultCode() );
    }
    
    
    /**
     * Gets the TriggerExecution UUID ref
     */
    protected String getTeUuidRef( String apDn ) throws LdapException
    {
        return getUuidRef( apDn, "TriggerExecutionSubentryUuid" );
    }

    
    /**
     * Gets the TriggerExecution seqNumber of a given AP
     */
    protected long getTeSeqNumber( String apDn ) throws LdapException
    {
        Entry entry = adminConnection.lookup( apDn, "TriggerExecutionSeqNumber" );
        
        EntryAttribute attribute = entry.get( ApacheSchemaConstants.TRIGGER_EXECUTION_SEQ_NUMBER_AT );
        
        if ( attribute == null )
        {
            return Long.MIN_VALUE;
        }
        
        return Long.parseLong( attribute.getString() );
    }
}