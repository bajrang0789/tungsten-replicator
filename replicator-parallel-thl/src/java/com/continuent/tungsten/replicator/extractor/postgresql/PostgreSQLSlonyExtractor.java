/**
 * Tungsten Scale-Out Stack
 * Copyright (C) 2011 Continuent Inc.
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
 * Initial developer(s): Linas Virbalas
 * Contributor(s): 
 */

package com.continuent.tungsten.replicator.extractor.postgresql;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import org.apache.log4j.Logger;

import com.continuent.tungsten.replicator.ReplicatorException;
import com.continuent.tungsten.replicator.conf.FailurePolicy;
import com.continuent.tungsten.replicator.conf.ReplicatorRuntime;
import com.continuent.tungsten.replicator.database.Database;
import com.continuent.tungsten.replicator.database.DatabaseFactory;
import com.continuent.tungsten.replicator.dbms.DBMSData;
import com.continuent.tungsten.replicator.dbms.StatementData;
import com.continuent.tungsten.replicator.event.DBMSEvent;
import com.continuent.tungsten.replicator.event.ReplOptionParams;
import com.continuent.tungsten.replicator.extractor.ExtractorException;
import com.continuent.tungsten.replicator.extractor.RawExtractor;
import com.continuent.tungsten.replicator.plugin.PluginContext;

/**
 * Class extracting events from Slony's log tables, generated by triggers. No
 * running Slony deamons required.
 * 
 * @author <a href="mailto:linas.virbalas@continuent.com">Linas Virbalas</a>
 * @version 1.0
 */
public class PostgreSQLSlonyExtractor implements RawExtractor
{
    private static Logger                   logger                = Logger.getLogger(PostgreSQLSlonyExtractor.class);

    private ReplicatorRuntime               runtime               = null;
    private String                          host                  = "localhost";
    private int                             port                  = 5432;
    private String                          database              = null;
    private String                          user                  = "postgres";
    private String                          password              = "";

    private String                          slonySchema           = "_slonytest";

    private String                          url;

    Database                                conn                  = null;

    /** Cursor to current Slony log position. */
    private String                          currentTxId           = null;

    public String getHost()
    {
        return host;
    }

    public void setHost(String host)
    {
        this.host = host;
    }

    public int getPort()
    {
        return port;
    }

    public void setPort(int port)
    {
        this.port = port;
    }

    public String getDatabase()
    {
        return database;
    }

    /**
     * PostgreSQL database to connect to. Important to specify, as Slony logs
     * are installed on per database basis.
     */
    public void setDatabase(String database)
    {
        this.database = database;
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

    /**
     * Put back together all Slony log's variables and build a valid SQL query.
     * 
     * @param cmdType Query type: "I" for INSERT, "U" for UPDATE or "D" for
     *            DELETE.
     * @param cmdData Value of Slony log's "log_cmddata" column (part of the
     *            query).
     * @param tableSchemaName Fully qualified table name (with schema).
     * @return Executable SQL statement.
     * @throws ExtractorException If unrecognized cmdType is hit.
     */
    private String rebuildSlonyQuery(String cmdType, String cmdData,
            String tableSchemaName) throws ExtractorException
    {
        StringBuffer sb = new StringBuffer();

        if (cmdType.compareTo("I") == 0)
        {
            sb.append("INSERT INTO ");
            sb.append(tableSchemaName);
            sb.append(" ");
        }
        else if (cmdType.compareTo("U") == 0)
        {
            sb.append("UPDATE ONLY ");
            sb.append(tableSchemaName);
            sb.append(" SET ");
        }
        else if (cmdType.compareTo("D") == 0)
        {
            sb.append("DELETE FROM ONLY ");
            sb.append(tableSchemaName);
            sb.append(" WHERE ");
        }
        else
            throw new ExtractorException("Unrecognized command type: "
                    + cmdType);

        sb.append(cmdData);

        return sb.toString();
    }
    
    /**
     * Retrieves log_txid of the next event after afterTxId.
     * 
     * @param afterTxId log_txid of the transaction to see for an event after.
     * @return log_txid if there is an event; null, if there is not.
     * @throws ExtractorException If SQLException happened.
     */
    private Long getNextEventId(String afterTxId) throws ExtractorException
    {
        Statement st = null;
        ResultSet rs = null;

        try
        {
            conn = getDBConnection();
            st = conn.createStatement();

            StringBuffer sb = new StringBuffer();
            sb.append("SELECT log_txid FROM (");
            sb.append(" SELECT log_txid FROM ");
            sb.append(getSlLogTable(1));
            sb.append(" WHERE log_txid > ");
            sb.append(afterTxId);
            sb.append(" GROUP BY log_txid");
            sb.append(" UNION ALL");
            sb.append(" SELECT log_txid FROM ");
            sb.append(getSlLogTable(2));
            sb.append(" WHERE log_txid > ");
            sb.append(afterTxId);
            sb.append(" GROUP BY log_txid");
            sb.append(" ) AS log_union");
            sb.append(" ORDER BY log_txid LIMIT 1");

            rs = st.executeQuery(sb.toString());
            if (rs.next())
            {
                return rs.getLong(1);
            }
            else
            {
                return null;
            }
        }
        catch (SQLException e)
        {
            logger.info("URL: " + url + " User: " + user);
            throw new ExtractorException(
                    "Unable to retrieve log_txid of the next event", e);
        }
        finally
        {
            cleanUpDatabaseResources(null, st, rs);
        }
    }

    /**
     * Extract a single event from the Slony log. A single event represents one
     * transaction, thus may contain more than one statement (action).<br/>
     * 
     * @param afterTxId log_txid of the event to extract *after*.
     */
    private DBMSEvent extractEvent(String afterTxId) throws ExtractorException,
            InterruptedException
    {
        DBMSEvent dbmsEvent = null;
        ArrayList<DBMSData> dataArray = new ArrayList<DBMSData>();

        Database conn = null;
        Statement st = null;
        ResultSet rs = null;

        try
        {
            if (logger.isDebugEnabled())
                logger.debug("Determining next event after: " + afterTxId);

            // "Tail" the Slony log tables.
            while (true)
            {
                Long txId = getNextEventId(afterTxId);
                if (txId == null)
                {
                    // Next transaction not yet exists, search again.
                    // Sleep for a while.
                    Thread.sleep(10);
                }
                else
                {
                    // Next event retrieved.
                    if (logger.isDebugEnabled())
                        logger.debug("Extracting event: " + txId);

                    conn = getDBConnection();
                    st = conn.createStatement();

                    // Retrieve ordered statements belonging to this
                    // transaction.
                    StringBuffer sb = new StringBuffer();
                    sb.append("SELECT * FROM (");
                    sb.append(" SELECT log_origin, log_txid, log_tableid, log_actionseq, log_cmdtype, log_cmddata, tab_nspname, tab_relname FROM ");
                    sb.append(getSlLogTable(1));
                    sb.append(" LEFT OUTER JOIN ");
                    sb.append(getSlTableTable());
                    sb.append(" ON ");
                    sb.append(getSlLogTable(1));
                    sb.append(".log_tableid = ");
                    sb.append(getSlTableTable());
                    sb.append(".tab_id");
                    sb.append(" WHERE log_txid = ");
                    sb.append(txId);
                    sb.append(" UNION ALL");
                    sb.append(" SELECT log_origin, log_txid, log_tableid, log_actionseq, log_cmdtype, log_cmddata, tab_nspname, tab_relname FROM ");
                    sb.append(getSlLogTable(2));
                    sb.append(" LEFT OUTER JOIN ");
                    sb.append(getSlTableTable());
                    sb.append(" ON ");
                    sb.append(getSlLogTable(2));
                    sb.append(".log_tableid = ");
                    sb.append(getSlTableTable());
                    sb.append(".tab_id");
                    sb.append(" WHERE log_txid = ");
                    sb.append(txId);
                    sb.append(") AS log_union ");
                    sb.append("ORDER BY log_actionseq");

                    rs = st.executeQuery(sb.toString());

                    // Loop through statements of this transaction.
                    while (rs.next())
                    {
                        Long logOrigin = rs.getLong(1);
                        Long actionSeq = rs.getLong(4);
                        String cmdType = rs.getString(5);
                        String cmdData = rs.getString(6);
                        String tableSchema = rs.getString(7);
                        String tableName = rs.getString(8);

                        String queryString = rebuildSlonyQuery(cmdType,
                                cmdData, tableSchema + "." + tableName);

                        StatementData statement = new StatementData(queryString);
                        statement.addOption(ReplOptionParams.SERVICE,
                                logOrigin.toString());
                        statement.addOption("log_actionseq",
                                actionSeq.toString());
                        dataArray.add(statement);
                    }

                    // Create DBMSEvent only if transaction existed.
                    if (!dataArray.isEmpty())
                    {
                        dbmsEvent = new DBMSEvent(txId.toString(), dataArray,
                                null /*
                                      * TODO : startTime ?
                                      */);

                        // Move to next event.
                        if (logger.isDebugEnabled())
                            logger.debug("Moving cursor to next transaction: "
                                    + txId);
                        this.setLastEventId(txId.toString());

                        break;
                    }
                    else
                    {
                        // Unexpected, event should have been here.
                        logger.error("Extracted an empty event " + txId
                                + ", which is unexpected");
                        if (runtime.getExtractorFailurePolicy() == FailurePolicy.STOP)
                            throw new ExtractorException(
                                    "Unexpectedly extracted an empty event "
                                            + txId + " after event "
                                            + afterTxId);
                    }
                }
            }
        }
        catch (ExtractorException e)
        {
            logger.error("Failed to extract after " + afterTxId, e);
            if (runtime.getExtractorFailurePolicy() == FailurePolicy.STOP)
                throw e;
        }
        catch (InterruptedException e)
        {
            // We just pass this up the stack as we are being canceled.
            throw e;
        }
        catch (Exception e)
        {
            logger.error("Unexpected failure while extracting after event "
                    + afterTxId, e);
            if (runtime.getExtractorFailurePolicy() == FailurePolicy.STOP)
                throw new ExtractorException(
                        "Unexpected failure while extracting after event "
                                + afterTxId, e);
        }
        finally
        {
            cleanUpDatabaseResources(null, st, rs);
        }

        return dbmsEvent;
    }

    /**
     * Extracts the next event.<br/>
     * <br/> {@inheritDoc}
     * 
     * @see com.continuent.tungsten.replicator.extractor.RawExtractor#extract()
     */
    public synchronized DBMSEvent extract() throws InterruptedException,
            ExtractorException
    {
        return extractEvent(currentTxId);
    }

    /**
     * {@inheritDoc}
     * 
     * @see com.continuent.tungsten.replicator.extractor.RawExtractor#extract(java.lang.String)
     */
    public DBMSEvent extract(String eventId) throws InterruptedException,
            ExtractorException
    {
        setLastEventId(eventId);
        return extract();
    }

    /**
     * {@inheritDoc}
     * 
     * @see com.continuent.tungsten.replicator.extractor.RawExtractor#setLastEventId(java.lang.String)
     */
    public void setLastEventId(String eventId) throws ExtractorException
    {
        if (eventId != null)
        {
            this.currentTxId = eventId;
        }
        else
        {
            this.currentTxId = getCurrentResourceEventId();
        }
    }

    /**
     * {@inheritDoc}
     * 
     * @see com.continuent.tungsten.replicator.plugin.ReplicatorPlugin#configure(com.continuent.tungsten.replicator.plugin.PluginContext)
     */
    public void configure(PluginContext context) throws ReplicatorException
    {
        runtime = (ReplicatorRuntime) context;

        // Compute our MySQL dbms URL.
        StringBuffer sb = new StringBuffer();
        sb.append("jdbc:postgresql://");
        sb.append(host);
        sb.append(":");
        sb.append(port);
        sb.append("/");
        if (database != null)
            sb.append(database);
        url = sb.toString();
    }

    /**
     * Ensures a connected Database instance is returned.
     */
    private Database getDBConnection() throws SQLException
    {
        // TODO: add reconnect if connection lost?
        if (conn == null)
        {
            conn = DatabaseFactory.createDatabase(url, user, password);
            conn.connect();
        }
        return conn;
    }
    
    /**
     * {@inheritDoc}
     * 
     * @see com.continuent.tungsten.replicator.plugin.ReplicatorPlugin#prepare(com.continuent.tungsten.replicator.plugin.PluginContext)
     */
    public void prepare(PluginContext context) throws ReplicatorException
    {
        try
        {
            // Establish initial connection to the database.
            getDBConnection();
        }
        catch (SQLException e)
        {
            String message = "Unable to connect to PostgreSQL server while preparing extractor; is server available?";
            logger.error(message);
            logger.info("URL: " + url + " User: " + user);
            throw new ExtractorException(message, e);
        }
    }

    /**
     * {@inheritDoc}
     * 
     * @see com.continuent.tungsten.replicator.plugin.ReplicatorPlugin#release(com.continuent.tungsten.replicator.plugin.PluginContext)
     */
    public void release(PluginContext context) throws ReplicatorException
    {
        cleanUpDatabaseResources(conn, null, null);
    }

    /**
     * Generates fully qualified Slony's log table name to be used in queries.
     * 
     * @param i 1 or 2 - determines which table to generate, eg. sl_log_1 or
     *            sl_log_2.
     */
    private String getSlLogTable(int i)
    {
        return slonySchema + ".sl_log_" + i;
    }

    /**
     * Generates fully qualified name to be used in queries for Slony's table
     * definitions table.
     */
    private String getSlTableTable()
    {
        return slonySchema + ".sl_table";
    }

    /**
     * Determines last transaction (log_txid) in the Slony log.<br/>
     * <br/> {@inheritDoc}
     * 
     * @see com.continuent.tungsten.replicator.extractor.RawExtractor#getCurrentResourceEventId()
     */
    public String getCurrentResourceEventId() throws ExtractorException
    {
        Database conn = null;
        Statement st = null;
        ResultSet rs = null;
        try
        {
            conn = DatabaseFactory.createDatabase(url, user, password);
            conn.connect();
            st = conn.createStatement();

            logger.debug("Determining last position in Slony log");
            rs = st.executeQuery("SELECT MAX(max_log_txid) FROM ("
                    + "SELECT MAX(log_txid) AS max_log_txid FROM "
                    + getSlLogTable(1) + " UNION ALL "
                    + "SELECT MAX(log_txid) AS max_log_txid FROM "
                    + getSlLogTable(2) + ") AS max_log_union;");
            if (!rs.next())
                throw new ExtractorException(
                        "Error getting max log_txid from Slony's log tables; are Slony triggers installed?");

            Long txId = rs.getLong(1);

            if (logger.isDebugEnabled())
                logger.debug("Last position in Slony log: " + txId);

            return txId.toString();
        }
        catch (SQLException e)
        {
            logger.info("URL: " + url + " User: " + user);
            throw new ExtractorException(
                    "Unable to run SELECT MAX(log_txid) to find log position",
                    e);
        }
        finally
        {
            cleanUpDatabaseResources(conn, st, rs);
        }
    }

    /**
     * Utility method to close result, statement, and connection objects.
     */
    private void cleanUpDatabaseResources(Database conn, Statement st,
            ResultSet rs)
    {
        if (rs != null)
        {
            try
            {
                rs.close();
            }
            catch (SQLException ignore)
            {
            }
        }
        if (st != null)
        {
            try
            {
                st.close();
            }
            catch (SQLException ignore)
            {
            }
        }
        if (conn != null)
            conn.close();
    }
}
