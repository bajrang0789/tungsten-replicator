/**
 * Tungsten Scale-Out Stack
 * Copyright (C) 2011-2013 Continuent Inc.
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

package com.continuent.tungsten.replicator.catalog;

import org.apache.log4j.Logger;

import com.continuent.tungsten.replicator.ReplicatorException;
import com.continuent.tungsten.replicator.database.Database;

/**
 * Implements a catalog based on a relational DBMS. This supersedes class
 * com.continuent.tungsten.replicator.thl.CatalogManager.
 */
public class SqlCatalog implements Catalog
{
    private static Logger logger   = Logger.getLogger(SqlCatalog.class);

    // Properties.
    private String        serviceName;
    private int           channels = 1;
    private String        url;
    private String        user;
    private String        password;
    private String        tableType;
    private boolean       privilegedSlaveUpdate;
    private String        schema;

    // Catalog tables.
    SqlCommitSeqno        commitSeqno;

    // SQL connection manager.
    SqlConnectionManager  connectionManager;

    /** Create new instance. */
    public SqlCatalog()
    {
    }

    public String getUrl()
    {
        return url;
    }

    public void setUrl(String url)
    {
        this.url = url;
    }

    public String getUser()
    {
        return user;
    }

    public void setUser(String user)
    {
        this.user = user;
    }

    public String getPassword()
    {
        return password;
    }

    public void setPassword(String password)
    {
        this.password = password;
    }

    public String getTableType()
    {
        return tableType;
    }

    public void setTableType(String tableType)
    {
        this.tableType = tableType;
    }

    public boolean isPrivilegedSlaveUpdate()
    {
        return privilegedSlaveUpdate;
    }

    public void setPrivilegedSlaveUpdate(boolean privilegedSlaveUpdate)
    {
        this.privilegedSlaveUpdate = privilegedSlaveUpdate;
    }

    public String getSchema()
    {
        return schema;
    }

    public void setSchema(String schema)
    {
        this.schema = schema;
    }

    public String getServiceName()
    {
        return serviceName;
    }

    public int getChannels()
    {
        return channels;
    }

    // CATALOG API

    /**
     * {@inheritDoc}
     * 
     * @see com.continuent.tungsten.replicator.catalog.Catalog#setServiceName(java.lang.String)
     */
    public void setServiceName(String serviceName)
    {
        this.serviceName = serviceName;
    }

    /**
     * {@inheritDoc}
     * 
     * @see com.continuent.tungsten.replicator.catalog.Catalog#setChannels(int)
     */
    public void setChannels(int channels)
    {
        this.channels = channels;
    }

    /**
     * Instantiate and configure all catalog tables.
     */
    @Override
    public void configure() throws ReplicatorException, InterruptedException
    {
        // Configure connection manager.
        connectionManager = new SqlConnectionManager();
        connectionManager.setUrl(url);
        connectionManager.setUser(user);
        connectionManager.setPassword(password);
        connectionManager.setPrivilegedSlaveUpdate(privilegedSlaveUpdate);
        connectionManager.setTableType(tableType);

        // Configure tables.
        commitSeqno = new SqlCommitSeqno(connectionManager, schema, tableType);
        commitSeqno.setChannels(channels);
    }

    /**
     * Prepare all catalog tables for use.
     */
    @Override
    public void prepare() throws ReplicatorException, InterruptedException
    {
        // First of course prepare the connection manager.
        connectionManager.prepare();

        // Now prepare all tables.
        commitSeqno.prepare();
    }

    /**
     * Release all catalog tables.
     */
    @Override
    public void release() throws ReplicatorException, InterruptedException
    {
        // Release tables first...
        commitSeqno.release();

        // Followed by the connection manager.
        connectionManager.release();
    }

    /**
     * Ensure all tables are ready for use, creating them if necessary.
     */
    @Override
    public void initialize() throws ReplicatorException, InterruptedException
    {
        logger.info("Initializing catalog tables: service=" + serviceName
                + " schema=" + schema);
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
     * @see com.continuent.tungsten.replicator.catalog.Catalog#getCommitSeqno()
     */
    @Override
    public CommitSeqno getCommitSeqno()
    {
        return commitSeqno;
    }

    /**
     * {@inheritDoc}
     * 
     * @see com.continuent.tungsten.replicator.catalog.Catalog#getConnection()
     */
    public Database getConnection() throws ReplicatorException
    {
        return connectionManager.getWrappedConnection();
    }

    /**
     * {@inheritDoc}
     * 
     * @see com.continuent.tungsten.replicator.catalog.Catalog#releaseConnection(com.continuent.tungsten.replicator.database.Database)
     */
    public void releaseConnection(Database conn)
    {
        connectionManager.releaseWrappedConnection(conn);
    }
}