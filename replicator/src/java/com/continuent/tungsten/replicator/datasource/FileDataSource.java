/**
 * Tungsten Scale-Out Stack
 * Copyright (C) 2013 Continuent Inc.
 * Contact: tungsten@continuent.org
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of version 2 of the GNU General Public License as
 * published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA
 *
 * Initial developer(s): Robert Hodges
 * Contributor(s): 
 */

package com.continuent.tungsten.replicator.datasource;

import org.apache.log4j.Logger;

import com.continuent.tungsten.common.file.FileIO;
import com.continuent.tungsten.common.file.FilePath;
import com.continuent.tungsten.common.file.JavaFileIO;
import com.continuent.tungsten.replicator.ReplicatorException;

/**
 * Implements a data source that stores data on a file system.
 */
public class FileDataSource implements UniversalDataSource
{
    private static Logger logger   = Logger.getLogger(FileDataSource.class);

    // Properties.
    private String        serviceName;
    private int           channels = 1;
    private String        directory;

    // Catalog tables.
    FileCommitSeqno       commitSeqno;

    // File IO-related variables.
    FilePath              rootDir;
    FilePath              serviceDir;
    JavaFileIO            javaFileIO;

    /** Create new instance. */
    public FileDataSource()
    {
    }

    public String getDirectory()
    {
        return directory;
    }

    public void setDirectory(String directory)
    {
        this.directory = directory;
    }

    /**
     * {@inheritDoc}
     * 
     * @see com.continuent.tungsten.replicator.datasource.UniversalDataSource#setServiceName(java.lang.String)
     */
    public void setServiceName(String serviceName)
    {
        this.serviceName = serviceName;
    }

    /**
     * {@inheritDoc}
     * 
     * @see com.continuent.tungsten.replicator.datasource.UniversalDataSource#getServiceName()
     */
    public String getServiceName()
    {
        return serviceName;
    }

    /**
     * {@inheritDoc}
     * 
     * @see com.continuent.tungsten.replicator.datasource.UniversalDataSource#setChannels(int)
     */
    public void setChannels(int channels)
    {
        this.channels = channels;
    }

    /**
     * {@inheritDoc}
     * 
     * @see com.continuent.tungsten.replicator.datasource.UniversalDataSource#getChannels()
     */
    public int getChannels()
    {
        return channels;
    }

    /**
     * Instantiate and configure all data source tables.
     */
    @Override
    public void configure() throws ReplicatorException, InterruptedException
    {
        // Configure file paths.
        rootDir = new FilePath(directory);
        serviceDir = new FilePath(rootDir, serviceName);

        // Create a new Java file IO instance.
        javaFileIO = new JavaFileIO();

        // Configure tables.
        commitSeqno = new FileCommitSeqno(javaFileIO);
        commitSeqno.setServiceName(serviceName);
        commitSeqno.setChannels(channels);
        commitSeqno.setServiceDir(serviceDir);
    }

    /**
     * Prepare all data source tables for use.
     */
    @Override
    public void prepare() throws ReplicatorException, InterruptedException
    {
        // Ensure the service directory is ready for use.
        FileIO fileIO = new JavaFileIO();
        if (!fileIO.exists(serviceDir))
        {
            logger.info("Service directory does not exist, creating: "
                    + serviceDir.toString());
            fileIO.mkdirs(serviceDir);
        }

        // Ensure everything exists now.
        if (!fileIO.readable(serviceDir))
        {
            throw new ReplicatorException(
                    "Service directory does not exist or is not readable: "
                            + serviceDir.toString());
        }
        else if (!fileIO.writable(serviceDir))
        {
            throw new ReplicatorException("Service directory is not writable: "
                    + serviceDir.toString());
        }

        // Prepare all tables.
        commitSeqno.prepare();
    }

    /**
     * Release all data source tables.
     */
    @Override
    public void release() throws ReplicatorException, InterruptedException
    {
        commitSeqno.release();
    }

    /**
     * Ensure all tables are ready for use, creating them if necessary.
     */
    @Override
    public void initialize() throws ReplicatorException, InterruptedException
    {
        logger.info("Initializing data source files: service=" + serviceName
                + " directory=" + directory);
        commitSeqno.initialize();
    }

    @Override
    public void clear() throws ReplicatorException, InterruptedException
    {
        commitSeqno.clear();
    }

    /**
     * {@inheritDoc}
     * 
     * @see com.continuent.tungsten.replicator.datasource.UniversalDataSource#getCommitSeqno()
     */
    @Override
    public CommitSeqno getCommitSeqno()
    {
        return commitSeqno;
    }

    /**
     * {@inheritDoc}
     * 
     * @see com.continuent.tungsten.replicator.datasource.UniversalDataSource#getConnection()
     */
    public UniversalConnection getConnection() throws ReplicatorException
    {
        return new FileConnection();
    }

    /**
     * {@inheritDoc}
     * 
     * @see com.continuent.tungsten.replicator.datasource.UniversalDataSource#releaseConnection(com.continuent.tungsten.replicator.datasource.UniversalConnection)
     */
    public void releaseConnection(UniversalConnection conn)
    {
        conn.close();
    }
}