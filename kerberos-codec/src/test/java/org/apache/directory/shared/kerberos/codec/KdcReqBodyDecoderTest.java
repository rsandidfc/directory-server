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
package org.apache.directory.shared.kerberos.codec;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.nio.ByteBuffer;

import org.apache.directory.junit.tools.Concurrent;
import org.apache.directory.junit.tools.ConcurrentJunitRunner;
import org.apache.directory.shared.asn1.ber.Asn1Decoder;
import org.apache.directory.shared.asn1.codec.EncoderException;
import org.apache.directory.shared.kerberos.KerberosTime;
import org.apache.directory.shared.kerberos.codec.options.KdcOptions;
import org.apache.directory.shared.kerberos.codec.types.EncryptionType;
import org.apache.directory.shared.kerberos.codec.types.HostAddrType;
import org.apache.directory.shared.kerberos.codec.types.PrincipalNameType;
import org.apache.directory.shared.kerberos.components.EncryptedData;
import org.apache.directory.shared.kerberos.components.HostAddress;
import org.apache.directory.shared.kerberos.components.HostAddresses;
import org.apache.directory.shared.kerberos.components.KdcReqBody;
import org.apache.directory.shared.kerberos.components.PrincipalName;
import org.apache.directory.shared.kerberos.messages.Ticket;
import org.apache.directory.shared.ldap.util.StringTools;
import org.junit.Test;
import org.junit.runner.RunWith;


/**
 * Test the decoder for a KdcReqBody
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
@RunWith(ConcurrentJunitRunner.class)
@Concurrent()
public class KdcReqBodyDecoderTest
{
    /**
     * Test the decoding of a KdcReqBody message
     */
    @Test
    public void testEncodeTicket() throws Exception
    {
        Asn1Decoder kerberosDecoder = new Asn1Decoder();

        ByteBuffer stream = ByteBuffer.allocate( 0x15B );
        
        stream.put( new byte[]
        {
            0x30, (byte)0x82, 0x01, 0x57, 
              (byte)0xA0, 0x07,
                0x03, 0x04, 
                  0x01, 0x02, 0x03, 0x04, 
              (byte)0xA1, 0x13, 
                0x30, 0x11, 
                  (byte)0xA0, 0x03, 
                    0x02, 0x01, 0x0A, 
                  (byte)0xA1, 0x0A, 
                    0x30, 0x08, 
                      0x1B, 0x06, 
                        'c', 'l', 'i', 'e', 'n', 't', 
              (byte)0xA2, 0x0D, 
                0x1B, 0x0B, 
                  'E', 'X', 'A', 'M', 'P', 'L', 'E', '.', 'C', 'O', 'M', 
              (byte)0xA3, 0x13, 
                0x30, 0x11, 
                  (byte)0xA0, 0x03, 
                    0x02, 0x01, 0x0A, 
                  (byte)0xA1, 0x0A, 
                    0x30, 0x08, 
                      0x1B, 0x06, 
                        's', 'e', 'r', 'v', 'e', 'r', 
              (byte)0xA4, 0x11, 
                0x18, 0x0F, 
                  '2', '0', '1', '0', '1', '1', '1', '0', '1', '5', '4', '5', '2', '5', 'Z', 
              (byte)0xA5, 0x11, 
                0x18, 0x0F, 
                  '2', '0', '1', '0', '1', '1', '1', '0', '1', '5', '4', '5', '2', '5', 'Z', 
              (byte)0xA6, 0x11, 
                0x18, 0x0F, 
                  '2', '0', '1', '0', '1', '1', '1', '0', '1', '5', '4', '5', '2', '5', 'Z', 
              (byte)0xA7, 0x04, 
                0x02, 0x02, 
                  0x30, 0x39, 
              (byte)0xA8, 0x0B, 
                0x30, 0x09, 
                  0x02, 0x01, 0x06, 
                  0x02, 0x01, 0x11, 
                  0x02, 0x01, 0x12, 
              (byte)0xA9, 0x2E, 
                0x30, 0x2C, 
                  0x30, 0x14, 
                    (byte)0xA0, 0x03, 
                      0x02, 0x01, 0x02, 
                    (byte)0xA1, 0x0D, 
                      0x04, 0x0B, 
                        '1', '9', '2', '.', '1', '6', '8', '.', '0', '.', '1', 
                  0x30, 0x14, 
                    (byte)0xA0, 0x03, 
                      0x02, 0x01, 0x02, 
                    (byte)0xA1, 0x0D, 
                      0x04, 0x0B, 
                        '1', '9', '2', '.', '1', '6', '8', '.', '0', '.', '2', 
              (byte)0xAA, 0x11, 
                0x30, 0x0F, 
                  (byte)0xA0, 0x03, 
                    0x02, 0x01, 0x11, 
                  (byte)0xA2, 0x08, 
                    0x04, 0x06, 
                      'a', 'b', 'c', 'd', 'e', 'f', 
              (byte)0xAB, (byte)0x81, (byte)0x83, 
                0x30, (byte)0x81, (byte)0x80, 
                  0x61, 0x3E, 
                    0x30, 0x3C, 
                      (byte)0xA0, 0x03, 
                        0x02, 0x01, 0x05, 
                      (byte)0xA1, 0x0D, 
                        0x1B, 0x0B, 
                          'E', 'X', 'A', 'M', 'P', 'L', 'E', '.', 'C', 'O', 'M', 
                      (byte)0xA2, 0x13, 
                        0x30, 0x11, 
                          (byte)0xA0, 0x03, 
                            0x02, 0x01, 0x01, 
                          (byte)0xA1, 0x0A, 
                            0x30, 0x08, 
                              0x1B, 0x06, 
                                'c', 'l', 'i', 'e', 'n', 't', 
                      (byte)0xA3, 0x11, 
                        0x30, 0x0F, 
                          (byte)0xA0, 0x03, 
                            0x02, 0x01, 0x11, 
                          (byte)0xA2, 0x08, 
                            0x04, 0x06, 
                              'a', 'b', 'c', 'd', 'e', 'f', 
                  0x61, 0x3E, 
                    0x30, 0x3C, 
                      (byte)0xA0, 0x03, 
                        0x02, 0x01, 0x05, 
                      (byte)0xA1, 0x0D, 
                        0x1B, 0x0B, 
                          'E', 'X', 'A', 'M', 'P', 'L', 'E', '.', 'C', 'O', 'M',
                      (byte)0xA2, 0x13, 
                        0x30, 0x11, 
                          (byte)0xA0, 0x03, 
                            0x02, 0x01, 0x01, 
                          (byte)0xA1, 0x0A, 
                            0x30, 0x08, 
                              0x1B, 0x06, 
                                's', 'e', 'r', 'v', 'e', 'r', 
                      (byte)0xA3, 0x11, 
                        0x30, 0x0F, 
                          (byte)0xA0, 0x03, 
                            0x02, 0x01, 0x11, 
                          (byte)0xA2, 0x08, 
                            0x04, 0x06, 
                              'a', 'b', 'c', 'd', 'e', 'f', 

        });

        String decodedPdu = StringTools.dumpBytes( stream.array() );
        stream.flip();

        KdcReqBody body = new KdcReqBody();
        
        body.setKdcOptions( new KdcOptions( new byte[]{0x01, 0x02, 0x03, 0x04} ) );
        body.setCName( new PrincipalName( "client", PrincipalNameType.KRB_NT_ENTERPRISE ) );
        body.setRealm( "EXAMPLE.COM" );
        body.setSName( new PrincipalName( "server", PrincipalNameType.KRB_NT_ENTERPRISE ) );
        
        body.setFrom( new KerberosTime( System.currentTimeMillis() ) );
        body.setTill( new KerberosTime( System.currentTimeMillis() ) );
        body.setRtime( new KerberosTime( System.currentTimeMillis() ) );
        body.setNonce( 12345 );
        
        body.addEType( EncryptionType.AES256_CTS_HMAC_SHA1_96 );
        body.addEType( EncryptionType.DES3_CBC_MD5 );
        body.addEType( EncryptionType.AES128_CTS_HMAC_SHA1_96 );
        
        HostAddresses addresses = new HostAddresses();
        addresses.addHostAddress( new HostAddress( HostAddrType.ADDRTYPE_INET, "192.168.0.1".getBytes() ) );
        addresses.addHostAddress( new HostAddress( HostAddrType.ADDRTYPE_INET, "192.168.0.2".getBytes() ) );
        body.setAddresses( addresses );

        EncryptedData encAuthorizationData = new EncryptedData( EncryptionType.AES128_CTS_HMAC_SHA1_96, "abcdef".getBytes() );
        body.setEncAuthorizationData( encAuthorizationData );
        
        Ticket ticket1 = new Ticket();
        ticket1.setTktVno( 5 );
        ticket1.setRealm( "EXAMPLE.COM" );
        ticket1.setSName( new PrincipalName( "client", PrincipalNameType.KRB_NT_PRINCIPAL ) );
        ticket1.setEncPart( new EncryptedData( EncryptionType.AES128_CTS_HMAC_SHA1_96, "abcdef".getBytes() ) );
        
        body.addAdditionalTicket( ticket1 );

        Ticket ticket2 = new Ticket();
        ticket2.setTktVno( 5 );
        ticket2.setRealm( "EXAMPLE.COM" );
        ticket2.setSName( new PrincipalName( "server", PrincipalNameType.KRB_NT_PRINCIPAL ) );
        ticket2.setEncPart( new EncryptedData( EncryptionType.AES128_CTS_HMAC_SHA1_96, "abcdef".getBytes() ) );
        
        body.addAdditionalTicket( ticket2 );
        
        // Check the encoding
        int length = body.computeLength();

        // Check the length
        assertEquals( 0x15B, length );
        
        // Check the encoding
        ByteBuffer encodedPdu = ByteBuffer.allocate( length );
        
        try
        {
            encodedPdu = body.encode( encodedPdu );
    
            // Check the length
            assertEquals( 0x15B, encodedPdu.limit() );
    
            //assertEquals( StringTools.dumpBytes( encodedPdu.array() ), decodedPdu );
        }
        catch ( EncoderException ee )
        {
            fail();
        }
    }
}