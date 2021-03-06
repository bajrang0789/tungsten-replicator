/**
 * Tungsten: An Application Server for uni/cluster.
 * Copyright (C) 2007-2014 Continuent Inc.
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
 * Contributor(s): Stephane Giron, Scott Martin
 */

package com.continuent.tungsten.replicator.database;

import java.util.LinkedList;
import java.util.List;

/**
 * Summarizes an operation on a named SQL object.
 * 
 * @author <a href="mailto:robert.hodges@continuent.com">Robert Hodges</a>
 * @version 1.0
 */
public class SqlOperation
{
    // Unrecognized object or operation.
    public static int UNRECOGNIZED        = 0;

    // Object types.
    public static int SCHEMA              = 1;
    public static int TABLE               = 2;
    public static int SESSION             = 3;
    public static int PROCEDURE           = 4;
    public static int FUNCTION            = 5;
    public static int TRANSACTION         = 6;
    public static int BLOCK               = 7;
    public static int VIEW                = 8;
    public static int INDEX               = 9;
    public static int DBMS                = 10;
    public static int TRIGGER             = 11;

    // Operation types.
    public static int CREATE              = 1;
    public static int DROP                = 2;
    public static int INSERT              = 3;
    public static int UPDATE              = 4;
    public static int DELETE              = 5;
    public static int REPLACE             = 6;
    public static int TRUNCATE            = 7;
    public static int LOAD_DATA           = 8;
    public static int SET                 = 9;
    public static int BEGIN               = 10;
    public static int COMMIT              = 11;
    public static int BEGIN_END           = 12;
    public static int SELECT              = 13;
    public static int ALTER               = 14;
    public static int ROLLBACK            = 15;
    public static int RENAME              = 16;

    // Specialized operation types for MySQL.
    public static int FLUSH_TABLES        = 101;

    int               objectType;
    int               operation;
    String            schema;
    String            name;

    List<SqlObject>   moreDatabaseObjects = new LinkedList<SqlObject>();

    boolean           autoCommit          = true;
    boolean           bidiUnsafe;
    private String    sqlCommand;

    /** Instantiate a SQL operation with default values. */
    public SqlOperation()
    {
        this(UNRECOGNIZED, UNRECOGNIZED, null, null, false);
    }

    /** Instantiate an auto-commit operation. */
    public SqlOperation(int object, int operation, String schema, String name)
    {
        this(object, operation, schema, name, true);
    }

    public SqlOperation(String command, int object, int operation,
            String schema, String name)
    {
        this(command, object, operation, schema, name, true);
    }

    /** Instantiate a SQL operation with full metadata. */
    public SqlOperation(int object, int operation, String schema, String name,
            boolean autoCommit)
    {
        this(null, object, operation, schema, name, autoCommit);
    }

    public SqlOperation(String command, int object, int operation,
            String schema, String name, boolean autoCommit)
    {
        this.sqlCommand = command;
        this.objectType = object;
        this.operation = operation;
        this.schema = extractObjectName(schema);
        this.name = extractObjectName(name);
        this.autoCommit = autoCommit;
    }

    public String getSqlCommand()
    {
        return sqlCommand;
    }

    public int getObjectType()
    {
        return objectType;
    }

    public int getOperation()
    {
        return operation;
    }

    public String getSchema()
    {
        return schema;
    }

    public String getName()
    {
        return name;
    }

    public boolean isAutoCommit()
    {
        return autoCommit;
    }

    public boolean isBidiUnsafe()
    {
        return bidiUnsafe;
    }

    public boolean isGlobal()
    {
        return this.objectType == DBMS;
    }

    public void setObjectType(int object)
    {
        this.objectType = object;
    }

    public void setOperation(int operation)
    {
        this.operation = operation;
    }

    public void setSchema(String schema)
    {
        this.schema = schema;
    }

    public void setName(String name)
    {
        this.name = extractObjectName(name);
    }

    public void setAutoCommit(boolean autoCommit)
    {
        this.autoCommit = autoCommit;
    }

    public void setBidiUnsafe(boolean bidiUnsafe)
    {
        this.bidiUnsafe = bidiUnsafe;
    }

    /** Set name when optional qualifier is present. */
    public void setQualifiedName(String qName)
    {
        int dotIndex = qName.indexOf('.');
        if (dotIndex == -1)
        {
            setName(qName);
            setSchema(null);
        }
        else
        {
            String schemaPart = qName.substring(0, dotIndex);
            String namePart = qName.substring(dotIndex + 1);
            this.setSchema(schemaPart);
            this.setName(namePart);
        }
    }

    // Utility methods.
    public boolean createDatabase()
    {
        return objectType == SCHEMA && operation == CREATE;
    }

    public boolean dropDatabase()
    {
        return objectType == SCHEMA && operation == DROP;
    }

    public boolean dropTable()
    {
        return operation == DROP && objectType == TABLE;
    }

    private String extractObjectName(String name)
    {
        if (name == null)
        {
            return null;
        }
        if (name.startsWith("`"))
            return name.substring(1, name.length() - 1);
        if (name.startsWith("\""))
            return name.substring(1, name.length() - 1);
        return name;
    }

    public void addDatabaseObject(String schema, String table)
    {
        moreDatabaseObjects.add(new SqlObject(schema, table));
    }

    public List<SqlObject> getMoreDatabaseObjects()
    {
        return moreDatabaseObjects;
    }

    public boolean hasMoreDatabaseObjects()
    {
        return !moreDatabaseObjects.isEmpty();
    }
}