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
package org.apache.directory.server.core.shared.txn;

import org.apache.directory.server.core.api.partition.index.Serializer;
import org.apache.directory.server.core.txn.logedit.TxnStateChange;
import org.apache.directory.server.core.log.LogAnchor;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;


import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.directory.server.core.log.UserLogRecord;

import java.io.IOException;


/**
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
/** Package protected */ class DefaultTxnManager<ID> implements  TxnManagerInternal<ID>
{
    /** wal log manager */
    private TxnLogManager<ID> txnLogManager;
    
    /** List of committed txns in commit LSN order */
    private ConcurrentLinkedQueue<ReadWriteTxn<ID>> committedQueue = new ConcurrentLinkedQueue<ReadWriteTxn<ID>>();
    
    /** Verify lock under which txn verification is done */
    private Lock verifyLock = new ReentrantLock();
    
    /** Used to assign start and commit version numbers to writeTxns */
    private Lock writeTxnsLock = new ReentrantLock();
    
    /** Latest committed txn on which read only txns can depend */
    private AtomicReference<ReadWriteTxn<ID>> latestCommittedTxn = new AtomicReference<ReadWriteTxn<ID>>();
    
    /** Latest verified write txn */
    private AtomicReference<ReadWriteTxn<ID>> latestVerifiedTxn = new AtomicReference<ReadWriteTxn<ID>>();
    
    /** Latest flushed txn's logical commit time */
    private AtomicLong latestFlushedTxnLSN = new AtomicLong( LogAnchor.UNKNOWN_LSN );
    
    /** ID comparator */
    private Comparator<ID> idComparator;
    
    /** ID serializer */
    private Serializer idSerializer ;
    
    /** Per thread txn context */
    static final ThreadLocal < Transaction > txnVar = 
         new ThreadLocal < Transaction > () 
         {
             @Override 
             protected Transaction initialValue()
             {
                 return null;
             }
        };
    
    /**
     * TODO : doco
     * @param txnLogManager
     * @param idComparator
     * @param idSerializer
     */
    public void init( TxnLogManager<ID> txnLogManager, Comparator<ID> idComparator, Serializer idSerializer )
    {
        this.txnLogManager = txnLogManager;
        this.idComparator = idComparator;
        this.idSerializer = idSerializer;
    }
    
    
    /**
     * {@inheritDoc}
     */
    public Comparator<ID> getIDComparator()
    {
        return idComparator;
    }
    
    
    /**
     * {@inheritDoc}
     */
    public Serializer getIDSerializer()
    {
        return idSerializer;
    }
    
    
    /**
     * {@inheritDoc}
     */  
    public void beginTransaction( boolean readOnly ) throws IOException
    {
        Transaction<ID> curTxn = getCurTxn();
        
        if ( curTxn != null )
        {
            throw new IllegalStateException("Cannot begin a txn when txn is already running: " + 
                curTxn);
        }
        
        if ( readOnly )
        {
            beginReadOnlyTxn();
        }
        else
        {
            beginReadWriteTxn();
        }
    }

    
    /**
     * {@inheritDoc}
     */
    public void commitTransaction() throws IOException, TxnConflictException
    {
        Transaction<ID> txn = getCurTxn();
        
        if ( txn == null )
        {
            throw new IllegalStateException(" trying to commit non existent txn ");
        }
        
        prepareForEndingTxn( txn );
        
        if ( txn instanceof ReadOnlyTxn )
        {
            txn.commitTxn( txn.getStartTime() );
        }
        else
        {
            commitReadWriteTxn( (ReadWriteTxn<ID>)txn );
        }
        
        txnVar.set( null );
    }
    
    
    /**
     * {@inheritDoc}
     */
    public void abortTransaction() throws IOException
    {
        Transaction<ID> txn = getCurTxn();
        
        if ( txn == null )
        {
            // this is acceptable
            return;
        }
        
        prepareForEndingTxn( txn );
        
        if ( txn instanceof ReadWriteTxn )
        {
            abortReadWriteTxn( (ReadWriteTxn<ID>)txn );
        }
        
        txn.abortTxn();
        txnVar.set( null );
    }
    
    
    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    public Transaction<ID> getCurTxn()
    {
       return (Transaction<ID>)txnVar.get(); 
    }
    
    
    /**
     * Begins a read only txn. A read only txn does not put any log edits
     * to the txn log.Its start time is the latest committed txn's commit time. 
     */
    private void beginReadOnlyTxn()
    {
        ReadOnlyTxn<ID> txn = new ReadOnlyTxn<ID>();
        ReadWriteTxn<ID> lastTxnToCheck = null;

        /*
         * Set the start time as the latest committed txn's commit time. We need to make sure that
         * any change after our start time is not flushed to the partitions. Say we have txn1 as the
         * lastest committed txn. There is a small window where we get ref to txn1, txn2 commits and
         * becomes the latest committed txn, txn1's ref count becomes zero before we bump its ref
         * count and changes to txn2 are flushed to partitions. Below we loop until we make sure
         * that the txn for which we bumped up the ref count is indeed the latest committed txn.
         */

        do
        {
            if ( lastTxnToCheck != null )
            {
                lastTxnToCheck.getRefCount().decrementAndGet();
            }

            lastTxnToCheck = latestCommittedTxn.get();

            if ( lastTxnToCheck != null )
            {
                lastTxnToCheck.getRefCount().getAndIncrement();
            }

        }
        while ( lastTxnToCheck != latestCommittedTxn.get() );

        // Determine start time
        long startTime;

        if ( lastTxnToCheck != null )
        {
            startTime = lastTxnToCheck.getCommitTime();
        }
        else
        {
            startTime = LogAnchor.UNKNOWN_LSN;
        }

        txn.startTxn( startTime );

        buildCheckList( txn, lastTxnToCheck );
        txnVar.set( txn );
    }
    

    /**
     * Begins a read write txn. A start txn marker is inserted
     * into the txn log and the lsn of that log record is the
     * start time.
     */
    private void beginReadWriteTxn() throws IOException
    {

        ReadWriteTxn<ID> txn = new ReadWriteTxn<ID>();
        UserLogRecord logRecord = txn.getUserLogRecord();

        TxnStateChange<ID> txnRecord = new TxnStateChange<ID>( LogAnchor.UNKNOWN_LSN,
            TxnStateChange.State.TXN_BEGIN );
        ObjectOutputStream out = null;
        ByteArrayOutputStream bout = null;
        byte[] data;

        try
        {
            bout = new ByteArrayOutputStream();
            out = new ObjectOutputStream( bout );
            out.writeObject( txnRecord );
            out.flush();
            data = bout.toByteArray();
        }
        finally
        {
            if ( bout != null )
            {
                bout.close();
            }

            if ( out != null )
            {
                out.close();
            }
        }

        logRecord.setData( data, data.length );

        /*
         * Get the start time and last txn to depend on
         * when mergin data under te writeTxnLock.
         */

        ReadWriteTxn<ID> lastTxnToCheck = null;
        writeTxnsLock.lock();

        try
        {
            txnLogManager.log( logRecord, false );
            txn.startTxn( logRecord.getLogAnchor().getLogLSN() );

            do
            {
                if ( lastTxnToCheck != null )
                {
                    lastTxnToCheck.getRefCount().decrementAndGet();
                }

                lastTxnToCheck = latestVerifiedTxn.get();

                if ( lastTxnToCheck != null )
                {
                    lastTxnToCheck.getRefCount().incrementAndGet();
                }

            }
            while ( lastTxnToCheck != latestVerifiedTxn.get() );

        }
        finally
        {
            writeTxnsLock.unlock();
        }

        // Finally build the check list
        buildCheckList( txn, lastTxnToCheck );

        txnVar.set( txn );
    }
    

    /**
     * Builds the list of txns which the given txn should check while mergin what it read from
     * the partitions with the changes in the txn log. These are the txns that committed before
     * the start of the give txn and for which the changes are not flushed to the partitions yet.
     * Note that, for some of these txns, flush to partitions could go on in parallel.
     *
     * @param txn txn for which we will build the check list
     * @param lastTxnToCheck latest txn to check
     */
    private void buildCheckList( Transaction<ID> txn, ReadWriteTxn<ID> lastTxnToCheck )
    {
        if ( lastTxnToCheck != null )
        {
            long lastLSN = lastTxnToCheck.getCommitTime();
            ReadWriteTxn<ID> toAdd;

            List<ReadWriteTxn<ID>> toCheckList = txn.getTxnsToCheck();
            Iterator<ReadWriteTxn<ID>> it = committedQueue.iterator();

            while ( it.hasNext() )
            {
                toAdd = it.next();

                if ( toAdd.getCommitTime() > lastLSN )
                {
                    break;
                }

                toCheckList.add( toAdd );
            }

            /*
             * Get latest flushed lsn and eliminate already flushed txn from the check list.
             */
            long flushedLSN = latestFlushedTxnLSN.get();

            it = toCheckList.iterator();
            ReadWriteTxn<ID> toCheck;

            while ( it.hasNext() )
            {
                toCheck = it.next();

                if ( toCheck.commitTime <= flushedLSN )
                {
                    it.remove();
                }
            }
        }

        // A read write txn, always has to check its changes
        if ( txn instanceof ReadWriteTxn )
        {
            txn.getTxnsToCheck().add( ( ReadWriteTxn<ID> ) txn );
        }
    }
    
    
    /**
     * Called before ending a txn. Txn for which this txn bumped 
     * up the ref count is gotten and its ref count is decreased.
     *
     * @param txn txn which is about to commit or abort
     */
    private void prepareForEndingTxn( Transaction<ID> txn )
    {
        List<ReadWriteTxn<ID>> toCheck = txn.getTxnsToCheck();
        
        // A read write txn, always has to check its changes
        if ( txn instanceof ReadWriteTxn )
        {

            if ( toCheck.size() <= 0 )
            {
                throw new IllegalStateException(
                    " prepareForEndingTxn: a read write txn should at least depend on itself:" + txn );
            }

            txn.getTxnsToCheck().remove( ( ReadWriteTxn<ID> ) txn );
        }
        
        if ( toCheck.size() > 0 )
        {
            ReadWriteTxn<ID> lastTxnToCheck = toCheck.get( toCheck.size() - 1 );
            
            if ( lastTxnToCheck.commitTime > txn.getStartTime() )
            {
                throw new IllegalStateException( " prepareForEndingTxn: txn has unpexptected start time " + 
                    txn + " expected: " + lastTxnToCheck );
            }
            
            if ( lastTxnToCheck.getRefCount().get() <= 0 )
            {
                throw new IllegalStateException( " prepareForEndingTxn: lastTxnToCheck has unexpected ref cnt " + 
                    txn + " expected: " + lastTxnToCheck );
            }
            
            lastTxnToCheck.getRefCount().decrementAndGet();
        }
    }
    
    
    /**
     * Tries to commit the given read write txn. Before a read write txn can commit, it is
     * verified against the txns that committed after this txn started. If a conflicting change is
     * found, a conflict exception is thrown. 
     * 
     * If a txn can commit, a commit record is inserted into the txn log. The lsn of the commit record
     * is the commit time of the txn.
     * 
     * Note that, a txn is not committed until its commit record is synced to the underlying media. Say we haveread write txns rw1 and 
     * rw2 and that rw1 and rw2 is verified and their commit record are in the log but not synced to underlying media yet. A new read 
     * write txn rw3 and a read only txn r1 comes along. Since rw1 and rw2 wont be acked until they commit, r1 should not depend on rw1 and rw2 and can have a view 
     * as of a commit time before rw1 and rw2's commit time. If r1 depended rw1 or rw2 and we crashed before sycning rw1 and rw2's to the underlying media, 
     * r1 would have depended on a change that actually doesnt exist in the database. However, rw3 either has to depend on rw1 and rw2) or has to verify 
     * its changeset against rw1 and rw2 when it tries to commit. Whether the first thing(depending on rw1, rw2 and merging its changeset) or the second
     * thing ( verifiying its change set against rw1 and rw2) is determined by the order of the lsns of the commit record of rw1 and rw2  and start record of rw3.
     * Lets say we have this order in the txn log:
     *              commit record rw1, start record rw3, commit record rw2
     * then rw3 will merge its changes with that of rw1 and will verify its changes against rw2. When rw3 is merging its changeset with that of rw1, rw1 might not have
     * committed yet as its commit record might not have made it to the underlying media but this is OK as rw3 cannot commit before rw1 because of the log.
     *
     * @param txn txn to commit.
     * @throws IOException
     * @throws TxnConflictException
     */
    private void commitReadWriteTxn( ReadWriteTxn<ID> txn ) throws IOException, TxnConflictException
    {
        UserLogRecord logRecord = txn.getUserLogRecord();

        TxnStateChange<ID> txnRecord = new TxnStateChange<ID>( txn.getStartTime(),
            TxnStateChange.State.TXN_COMMIT );
        ObjectOutputStream out = null;
        ByteArrayOutputStream bout = null;
        byte[] data;

        try
        {
            bout = new ByteArrayOutputStream();
            out = new ObjectOutputStream( bout );
            out.writeObject( txnRecord );
            out.flush();
            data = bout.toByteArray();
        }
        finally
        {
            if ( bout != null )
            {
                bout.close();
            }

            if ( out != null )
            {
                out.close();
            }
        }

        logRecord.setData( data, data.length );
        
        verifyLock.lock();
       
        //Verify txn and throw conflict exception if necessary
        Iterator<ReadWriteTxn<ID>> it = committedQueue.iterator();
        ReadWriteTxn<ID> toCheckTxn;
        long startTime = txn.getStartTime();
        
        while ( it.hasNext() )
        {
            toCheckTxn = it.next();

            // Check txns that committed after we started 
            if ( toCheckTxn.getCommitTime() < startTime )
            {
                continue;
            }

            if ( txn.hasConflict( toCheckTxn ) )
            {
                verifyLock.unlock();
                throw new TxnConflictException();
            }
        }
        
        writeTxnsLock.lock();
        
        try
        {
           // TODO sync of log can be done outside the locks. 
           txnLogManager.log( logRecord, true );
           txn.commitTxn( logRecord.getLogAnchor().getLogLSN() );
           
           latestVerifiedTxn.set( txn );
           committedQueue.offer( txn );
           
           // TODO when sync is done outside the locks, advance latest commit outside the locks
           latestCommittedTxn.set( txn );
        }
        finally
        {
            writeTxnsLock.unlock();
            verifyLock.unlock();
        }
    }
    

    /**
     * Aborts a read write txn. An abort record is inserted into the txn log.
     *
     * @param txn txn to abort
     * @throws IOException
     */
    private void abortReadWriteTxn( ReadWriteTxn<ID> txn ) throws IOException
    {
        UserLogRecord logRecord = txn.getUserLogRecord();

        TxnStateChange<ID> txnRecord = new TxnStateChange<ID>( txn.getStartTime(),
            TxnStateChange.State.TXN_ABORT );
        ObjectOutputStream out = null;
        ByteArrayOutputStream bout = null;
        byte[] data;

        try
        {
            bout = new ByteArrayOutputStream();
            out = new ObjectOutputStream( bout );
            out.writeObject( txnRecord );
            out.flush();
            data = bout.toByteArray();
        }
        finally
        {
            if ( bout != null )
            {
                bout.close();
            }

            if ( out != null )
            {
                out.close();
            }
        }

        logRecord.setData( data, data.length );
        txnLogManager.log( logRecord, false );
    }
}
