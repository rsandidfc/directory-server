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
package org.apache.directory.server.core.shared.log;

import java.nio.ByteBuffer;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.Adler32;
import java.util.zip.Checksum;


import java.io.IOException;

import org.apache.directory.server.core.log.LogFileManager.LogFileWriter;
import org.apache.directory.server.i18n.I18n;

/**
 * Manages the flushing of log to media and scanning of logs. All appends to the log file go through this class. 
 *
 * Internally it manages a circular  buffer where appends initially go. Appends are first 
 * appended to this in memory circular log. As the in memory circular log fills up or as the user requests
 * memory buffer is flushed to the underlying media.  
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
/* Package protected */ class LogFlushManager
{
    /** Ever increasing logical log sequence number assigned to user log records. Bumped up under append lock */
    private long logLSN = Long.MIN_VALUE + 1;
       
    /** Memory buffer size in bytes */
    private final int logBufferSize;
        
    /** Synchronizes appends */
    private final Lock appendLock = new ReentrantLock();
    
    /** Synchronizes flushes to media */
    private final Lock flushLock = new ReentrantLock();
    
    /** Used to wait on ongoing flush */
    private final Condition flushCondition = flushLock.newCondition();
     
    /** In memory LogBuffer */
    private LogBuffer logBuffer;
    
    /** Flush status */
    private FlushStatus flushStatus = new FlushStatus(); 
       
    /** Current LogFile appends go to */
    private LogFileManager.LogFileWriter currentLogFile;

    /** Log manager */
    private LogManager logManager;

    /** Size of data appended to the currentLogFile so far */
    private long appendedSize;
    
    /** Sof limit on the log file size */
    private long targetLogFileSize;
    
    /** If logging cannot succeed, then loggingFailed is set to true and further logging is prevented */
    private boolean logFailed;

    /** The Checksum used */
    private Checksum checksum = new Adler32();

    /**
     * Creates a LogFlushManager instance. We define the memory buffer size, and the default maximum
     * size for each Log file (this maximul size may be exceeded, if one user record is bigger than 
     * this maximum size. Log file may be smaller too.
     * 
     * @param logManager The associated LogManager
     * @param logMemoryBufferSize The buffer size
     * @param logFileSize The default max size for each Log file.
     */
    public LogFlushManager( LogManager logManager, int logMemoryBufferSize, long logFileSize )
    {
        if ( ( logMemoryBufferSize < 0 ) || ( logFileSize < 0 ) )
        {
            throw new IllegalArgumentException( I18n.err( I18n.ERR_748, logMemoryBufferSize, logFileSize ) );
        }
        
        logBufferSize = logMemoryBufferSize;
        targetLogFileSize = logFileSize;
        this.logManager = logManager;
        
        logBuffer = new LogBuffer( logBufferSize, currentLogFile );
    }
    
    
    /**
     * Appends the given user record to the log. Position where the record is appended is returned as part of
     * userRecord.
     *
     * @param userLogRecord provides the user data to be appended to the log
     * @param sync if true, this calls returns after making sure that the appended data is reflected to the underlying file
     * @throws IOException If we had an issue while appending some record in the file
     * @throws InvalidLogException If the log system is is declared as invalid, due to a previous error
     */
    public void append( UserLogRecord userRecord, boolean sync ) throws IOException, InvalidLogException
    {
        boolean appendedRecord = false;
        byte[] userBuffer = userRecord.getDataBuffer();
        int length  = userRecord.getDataLength();
        LogAnchor userLogAnchor = userRecord.getLogAnchor(); 
        
        int recordSize = LogFileRecords.RECORD_HEADER_SIZE + length + LogFileRecords.RECORD_FOOTER_SIZE;
        
        // The addition of a record is done in a protected section
        appendLock.lock();
        
        // Get out immediately if the log system is invalid
        if ( logFailed )
        {
            appendLock.unlock();
            throw new InvalidLogException( I18n.err( I18n.ERR_750 ) );
        }
        
        // Get a new sequence number for the logged data
        long lsn = logLSN++;

        try
        {
            // Compute the checksum for the user record
            checksum.reset();
            checksum.update( userBuffer, 0, length );
            
            if ( currentLogFile == null )
            {
                // We are just starting, get the current log file
                currentLogFile = logManager.switchToNextLogFile( null );
                appendedSize = currentLogFile.getLength();
            }

            // If we try to store more data that what can be hold by the current file,
            // we have to switch to the next file
            if ( appendedSize > targetLogFileSize )
            {
                // Make sure everything outstanding goes to the current log file
                flush( lsn, null, 0, 0, true);
                
                currentLogFile = logManager.switchToNextLogFile( currentLogFile );
                appendedSize = currentLogFile.getLength();
            }
            
            if ( recordSize <= logBufferSize )
            {
                ByteBuffer writeHead = logBuffer.writeHead;
                
                while ( !appendedRecord )
                {
                    // First get the rewind count then the position to which the readhead advanced
                    int readHeadRewindCount = logBuffer.readHeadRewindCount.get();
                    int readHeadPosition = logBuffer.readHeadPosition;
                    
                    if ( ( logBuffer.writeHeadRewindCount == readHeadRewindCount ) || 
                        ( ( logBuffer.writeHeadRewindCount == readHeadRewindCount + 1 ) && 
                            ( readHeadPosition < writeHead.position() ) ) )
                    {
                        if ( writeHead.remaining() >= recordSize )
                        {
                            // Write the header
                            writeHeader( writeHead, recordSize, lsn );
                            
                            // Write the data
                            writeHead.put( userBuffer, 0, length );
                            
                            // Write the footeer
                            writeFooter( writeHead, (int)checksum.getValue() );
                            
                            appendedRecord = true;
                        }
                        else // ( writeHead.remaining() < recordSize )
                        {
                            if ( writeHead.remaining() >= LogFileRecords.RECORD_HEADER_SIZE )
                            {
                                // Write a skip record
                                writeHeader( writeHead, -1, -1 );
                            }
                            
                            // rewind buffer now
                            writeHead.rewind();
                            logBuffer.writeHeadRewindCount++;
                        }
                    }
                    else 
                    {
                        if ( logBuffer.writeHeadRewindCount != ( readHeadRewindCount + 1 )  )
                        {
                            throw new IllegalStateException( "Unexpected sequence number for read/write heads:" + logBuffer.writeHeadRewindCount +
                                    " " + readHeadRewindCount );
                        }
                        
                        if ( ( readHeadPosition - writeHead.position() ) > recordSize )
                        {
                            // Write the header
                            writeHeader( writeHead, recordSize, lsn );
                            
                            // Write the data
                            writeHead.put( userBuffer, 0, length );

                            // Write the footer
                            writeFooter( writeHead, (int)checksum.getValue() );

                            appendedRecord = true;
                        }
                        else
                        {
                            flush( lsn, null, 0, 0, true);
                        }
                    }
                }
            }
            else
            {
                flush( lsn, userBuffer, 0, length, true );
            }
            
            userLogAnchor.resetLogAnchor( currentLogFile.logFileNumber(), appendedSize, lsn );
            appendedSize += recordSize;
        }
        catch( IOException e )
        {
            e.printStackTrace();
            logFailed = true; // Mark log subsytem failed
        }
        catch( InvalidLogException e )
        {
            e.printStackTrace();
            logFailed = true; // Mark log subsystem failed
        }
        finally
        {
            appendLock.unlock();
        }
        
        if ( sync )
        { 
            flush( lsn, null, 0, 0, false );
        }
    }
    
    
    /**
     * Syncs the log upto the given lsn. If lsn is equal to unknow lsn, then the log is 
     * flushed upto the latest logged lsn.
     *
     * @param uptoLSN lsn to flush upto. Unkown lsn if caller just wants to sync the log upto the latest logged lsn.
     * @throws IOException If we had an issue while flushing some record in the file
     * @throws InvalidLogException If the log system is is declared as invalid, due to a previous error
     */
    void sync( long uptoLSN ) throws IOException, InvalidLogException
    {
       if ( uptoLSN == LogAnchor.UNKNOWN_LSN )
       {
           appendLock.lock();
           uptoLSN = logLSN - 1;
           appendLock.unlock();
       }
       
       // If nothing to flush, then just return
       if ( uptoLSN == LogAnchor.UNKNOWN_LSN )
       {
           
           return;
       }
       
       flush( uptoLSN, null, 0, 0, false );
    }
    
    /**
     * Flushes the changes in the log buffer upto the given point. The given point is determined as follows:
     * appendLock is held: flushLSN is the highest lsn generated by the logging system and no more appends can
     * proceed. In this case log is flushed until where the write head is.Log record with the flushLSN might not
     * have been appended yet.
     * 
     * Otherwise: Given flushLSN is appended to the log already. Log is flushed upto max(flushLSN, current flashSatus.uptoLSN)
     * 
     * Also userBuffer != null => appendLockHeld == true
     * 
     * Only one thread can do flush. Once a thread find out that a flush is already going on, it waits for the ongoing flush
     * and is woken up to do its flush.
     * 
     * flushStatus.uptoLSN represents the highest lsn that any thread wanted to sync. If a couple of threads wait on sync to
     * complete, the thread that wakes up and does the sync will take it for the team and sync upto flushStatus.uptoLSN so 
     * that logging is more efficient.
     * 
     * @param flushLSN max LSN the calling thread wants to sync upto
     * @param userBuffer if not null, user buffer is appended to the log without any buffering
     * @param offset offset of data in user buffer
     * @param length length of user data
     * @param appendLockHeld true if append lock is held
     * @throws IOException If we had an issue while flushing some record in the file
     * @throws InvalidLogException If the log system is is declared as invalid, due to a previous error
     */
    private void flush( long flushLSN, byte[] userBuffer, int offset, int length, 
                        boolean appendLockHeld ) throws IOException, InvalidLogException
    {
        long uptoLSN = flushLSN;
       
        if ( appendLockHeld == true )
        {
            uptoLSN--;
        }
        
        flushLock.lock();
        
        // Update max requested lsn if necessary
        if ( uptoLSN > flushStatus.uptoLSN )
        {
            flushStatus.uptoLSN = uptoLSN;
        }
        
        /*
         * Check if we need to do flush and wait for ongoing flush if
         * necessary
         */
        while ( true )
        {
            if ( logFailed )
            {
                flushLock.unlock();
                throw new InvalidLogException( I18n.err( I18n.ERR_750 ) );
            }
            
            if ( ( flushStatus.flushedLSN >= uptoLSN ) && ( appendLockHeld == false ) )
            {
                flushLock.unlock();
                return;
            }
            
            if ( flushStatus.flushInProgress == false )
            {
                break;
            }
            
            flushStatus.numWaiters++;
            flushCondition.awaitUninterruptibly();
            flushStatus.numWaiters--;
        }
        
        // Mark flush in progress and do the flush
        flushStatus.flushInProgress = true;
        
        // If not appendlock held, adjust uptoLSN with the max one requested by any thread
        if ( appendLockHeld == false )
        {
            uptoLSN = flushStatus.uptoLSN;
        }
        else
        {
            uptoLSN = flushLSN;
        }
        
        flushLock.unlock();
        
        long flushedLSN = LogAnchor.UNKNOWN_LSN;
        
        try
        {
            flushedLSN = doFlush( uptoLSN, appendLockHeld );
            
            // Now if there is a user buffer, flush from that
            if ( userBuffer != null )
            {
                ByteBuffer headerFooterHead = logBuffer.headerFooterHead;
                int recordSize = LogFileRecords.RECORD_HEADER_SIZE + LogFileRecords.RECORD_FOOTER_SIZE + length;
                
                headerFooterHead.rewind();
                writeHeader( headerFooterHead, recordSize, flushLSN );
                currentLogFile.append( logBuffer.headerFooterBuffer, 0, LogFileRecords.RECORD_HEADER_SIZE );
                
                currentLogFile.append( userBuffer, offset, length );   
                
                headerFooterHead.rewind();
                writeFooter( headerFooterHead, (int)checksum.getValue() );
                currentLogFile.append( logBuffer.headerFooterBuffer, 0, LogFileRecords.RECORD_FOOTER_SIZE );
    
                flushedLSN = flushLSN;
            }
            
            currentLogFile.sync();
        }
        catch( IOException e )
        {
            // Mark the logger invalid, wakeup any waiters and return
            flushLock.lock();
            logFailed = true;
            flushStatus.flushInProgress = false;
            
            if ( flushStatus.numWaiters != 0 )
            {
                flushCondition.signalAll();
            }
            
            flushLock.unlock();
            
            throw e;
        }
        
        flushLock.lock();
        
        if ( flushedLSN != LogAnchor.UNKNOWN_LSN )
        {
            flushStatus.flushedLSN = flushedLSN;
            
            if ( flushStatus.flushedLSN > flushStatus.uptoLSN )
            {
                // This should only happen with append lock held
                if ( appendLockHeld == false )
                {
                    throw new IllegalStateException( "FlushedLSN went ahead of uptoLSN while appendlock is not held: " + 
                        flushStatus.flushedLSN + "  " + flushStatus.uptoLSN);
                }
                
                flushStatus.uptoLSN = flushStatus.flushedLSN;
            }
        }
        
        flushStatus.flushInProgress = false;
        
        if ( flushStatus.numWaiters != 0 )
        {
            flushCondition.signalAll();
        }
        
        flushLock.unlock();
    }
    
    
    /**
     * Walks the log buffer and writes it to the underlying log file until the uptoLSN or current write head.
     *
     * @param uptoLSN max LSN until where log is flushed
     * @param appendLockHeld true if appendlock held.
     * @return lsn upto which flush is done. UNKNOWN_LSN if no flushing is done.
     * @throws IOException
     */
    private long doFlush( long uptoLSN, boolean appendLockHeld  ) throws IOException
    {
        ByteBuffer readHead = logBuffer.readHead;
        ByteBuffer writeHead = logBuffer.writeHead;
        boolean done = false;
        
        int magicNumber;
        int length;
        long lsn = LogAnchor.UNKNOWN_LSN;
        
        while ( !done )
        {
            int totalLength = 0;
            
            while( true )
            {
                /*
                 * If append lock is held, we might hit write head. We can read
                 * the write head here when append lock is held
                 */
                if ( appendLockHeld )
                {
                    if ( ( writeHead.position() == readHead.position() ) &&
                            ( logBuffer.writeHeadRewindCount == logBuffer.readHeadRewindCount.get() ) )
                    {
                        done = true;
                        break;
                    }
                }
                
                // If less than header length left to process, then break and flush whatever we got so far
                if ( readHead.remaining() < LogFileRecords.RECORD_HEADER_SIZE )
                {
                    break;
                }
                
                magicNumber = readHead.getInt();
                
                if ( magicNumber != LogFileRecords.RECORD_HEADER_MAGIC_NUMBER )
                {
                    throw new IllegalStateException( " Record header magic " +
                        "number does not match " + magicNumber + " expected "+ 
                        LogFileRecords.RECORD_HEADER_MAGIC_NUMBER );
                }
                
                length = readHead.getInt();
                
                // Did we hit a skip record at the end of the buffer?
                if ( length == LogBuffer.SKIP_RECORD_LENGTH )
                {
                    break;
                }
                
                // Sanitize length, it includes header and footer overhead
                if ( length <= ( LogFileRecords.RECORD_HEADER_SIZE + LogFileRecords.RECORD_FOOTER_SIZE) )
                {
                    throw new IllegalStateException( "Record length doesnt make sense:" + length + " expected:" +
                        ( LogFileRecords.RECORD_HEADER_MAGIC_NUMBER + LogFileRecords.RECORD_FOOTER_MAGIC_NUMBER) );
                }
                
                // Add to the total length
                totalLength += length;
                
                lsn = readHead.getLong();
                
                // Move to the next record, we processed 16 bytes already
                readHead.position( readHead.position() + length - 16 );
                
                if ( lsn >= uptoLSN )
                {
                    done = true;
                    break;
                }
            }
            
            // If there is something to flush, then do it now
            if ( totalLength > 0 )
            {
                int offset;
                offset = logBuffer.readHeadPosition;
                
                currentLogFile.append( logBuffer.buffer, offset, totalLength );
                       
                //move the position to the next record
                logBuffer.readHeadPosition = readHead.position();
            }
            
            if ( !done )
            {
                // this means we need to rewind and keep flushing
                logBuffer.readHeadPosition = 0;
                readHead.rewind();
                logBuffer.readHeadRewindCount.incrementAndGet();
            }
        }
        
        return lsn;
    }
    
    
    /**
     * Write the log file header 
     */
    private void writeHeader( ByteBuffer buffer, int length, long lsn )
    {
        buffer.putInt( LogFileRecords.RECORD_HEADER_MAGIC_NUMBER );
        buffer.putInt( length );
        buffer.putLong( lsn );
        buffer.putLong( length ^ lsn ); 
    }
    
    
    /**
     * Write the log file footer 
     */
    private void writeFooter( ByteBuffer buffer, int checksum )
    {
        buffer.putInt( checksum );
        buffer.putInt( LogFileRecords.RECORD_FOOTER_MAGIC_NUMBER );
    }
    
 
    /**
     * Used to group the memory buffer data together 
     */
    private static class LogBuffer
    {
        /** In memory buffer */
        private byte buffer[];
        
        /** Used to scan the buffer while reading it to flush */
        private ByteBuffer readHead;
        
        /** Advanced as readHead flushes data */
        private int readHeadPosition;
        
        /** Rewind count of readHead. Used to avoid overwriting non flushed data */
        private AtomicInteger readHeadRewindCount;
        
        /** Used to scan the buffer while appending records into it */
        private ByteBuffer writeHead;
        
        /** Rewind count of writeHead. used to avoid overwriting non flushed data */
        private int writeHeadRewindCount;
        
        /** Used to mark records that should be skipped at the end of the log buffer */
        private final static int SKIP_RECORD_LENGTH = -1;
        
        /** Header footer buffer used when writing user buffers directly */
        private byte headerFooterBuffer[];
        
        /** Used to format header footer buffer */
        private ByteBuffer headerFooterHead;
        
        /**
         * Create a new instance of a LogBuffer
         */
        private LogBuffer( int bufferSize, LogFileWriter currentLogFile )
        {
            buffer = new byte[bufferSize];
            readHead = ByteBuffer.wrap( buffer );
            
            readHeadRewindCount = new AtomicInteger( 0 );
            
            writeHead = ByteBuffer.wrap( buffer );
            
            headerFooterBuffer = new byte[LogFileRecords.MAX_MARKER_SIZE];
            headerFooterHead = ByteBuffer.wrap( headerFooterBuffer );
        }
    }
    
    /**
     * Used to group the flush related data together
     */
    private static class FlushStatus
    {
        /** whether flush is going on */
        boolean flushInProgress;
        
        /** Current flush request */
        long uptoLSN = LogAnchor.UNKNOWN_LSN;
        
        /** Current flushed lsn */
        long flushedLSN = LogAnchor.UNKNOWN_LSN;
        
        /** Keeps track of the number of waiters */
        int numWaiters;
    }
}
