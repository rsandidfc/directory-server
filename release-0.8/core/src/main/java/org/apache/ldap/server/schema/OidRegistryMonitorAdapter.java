/*
 *   Copyright 2004 The Apache Software Foundation
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */
package org.apache.ldap.server.schema;

import java.util.List;
import javax.naming.NamingException;


/**
 * An adapter for an OidRegistryMonitor.
 *
 * @author <a href="mailto:directory-dev@incubator.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public class OidRegistryMonitorAdapter implements OidRegistryMonitor
{

    /* (non-Javadoc)
     * @see org.apache.ldap.server.schema.OidRegistryMonitor#getOidWithOid(
     * java.lang.String)
     */
    public void getOidWithOid( String oid )
    {
    }

    
    /* (non-Javadoc)
     * @see org.apache.ldap.server.schema.OidRegistryMonitor#oidResolved(
     * java.lang.String, java.lang.String)
     */
    public void oidResolved( String name, String oid )
    {
    }
    

    /* (non-Javadoc)
     * @see org.apache.ldap.server.schema.OidRegistryMonitor#oidResolved(
     * java.lang.String, java.lang.String, java.lang.String)
     */
    public void oidResolved( String name, String normalized, String oid )
    {
    }

    
    /* (non-Javadoc)
     * @see org.apache.ldap.server.schema.OidRegistryMonitor#oidResolutionFailed(
     * java.lang.String, javax.naming.NamingException)
     */
    public void oidResolutionFailed( String name, NamingException fault )
    {
        if ( fault != null )
        {
            fault.printStackTrace();
        }
    }

    
    /* (non-Javadoc)
     * @see org.apache.ldap.server.schema.OidRegistryMonitor#oidDoesNotExist(
     * java.lang.String, javax.naming.NamingException)
     */
    public void oidDoesNotExist( String oid, NamingException fault )
    {
        if ( fault != null )
        {
            fault.printStackTrace();
        }
    }

    
    /* (non-Javadoc)
     * @see org.apache.ldap.server.schema.OidRegistryMonitor#nameResolved(
     * java.lang.String, java.lang.String)
     */
    public void nameResolved( String oid, String name )
    {
    }

    
    /* (non-Javadoc)
     * @see org.apache.ldap.server.schema.OidRegistryMonitor#namesResolved(
     * java.lang.String, java.util.List)
     */
    public void namesResolved( String oid, List names )
    {
    }

    
    /* (non-Javadoc)
     * @see org.apache.ldap.server.schema.OidRegistryMonitor#registered(
     * java.lang.String, java.lang.String)
     */
    public void registered( String name, String oid )
    {
    }
}
