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


import javax.naming.NamingException;

import org.apache.ldap.common.schema.MatchingRule;


/**
 * An adapter for a MatchingRuleRegistryMonitor.
 *
 * @author <a href="mailto:directory-dev@incubator.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public class MatchingRuleRegistryMonitorAdapter
    implements MatchingRuleRegistryMonitor
{
    /**
     * @see org.apache.ldap.server.schema.MatchingRuleRegistryMonitor#registered(
     * org.apache.ldap.common.schema.MatchingRule)
     */
    public void registered( MatchingRule rule )
    {
    }
    

    /**
     * @see org.apache.ldap.server.schema.MatchingRuleRegistryMonitor#lookedUp(
     * org.apache.ldap.common.schema.MatchingRule)
     */
    public void lookedUp( MatchingRule rule )
    {
    }

    
    /**
     * @see org.apache.ldap.server.schema.MatchingRuleRegistryMonitor#lookupFailed(
     * java.lang.String, javax.naming.NamingException)
     */
    public void lookupFailed( String oid, NamingException fault )
    {
        if ( fault != null )
        {
            fault.printStackTrace();
        }
    }

    
    /**
     * @see org.apache.ldap.server.schema.MatchingRuleRegistryMonitor#registerFailed(
     * org.apache.ldap.common.schema.MatchingRule, javax.naming.NamingException)
     */
    public void registerFailed( MatchingRule rule, NamingException fault )
    {
        if ( fault != null )
        {
            fault.printStackTrace();
        }
    }
}
