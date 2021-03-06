/**
 * Tungsten Scale-Out Stack
 * Copyright (C) 2014 Continuent Inc.
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
 * Initial developer(s): Stephane Giron
 * Contributor(s):
 */

package com.continuent.tungsten.replicator.extractor.parallel;

import java.util.Arrays;
import java.util.List;

import com.continuent.tungsten.replicator.database.Table;

/**
 * Defines a chunk that contains the whole table (no chunk for this table).
 * 
 * @author <a href="mailto:stephane.giron@continuent.com">Stephane Giron</a>
 * @version 1.0
 */
public class NoChunk extends AbstractChunk implements Chunk
{

    private Table        table;
    private List<String> columns = null;

    public NoChunk(Table table, String[] columns)
    {
        this.table = table;
        if (columns != null)
            this.columns = Arrays.asList(columns);
    }

    /**
     * {@inheritDoc}
     * 
     * @see com.continuent.tungsten.replicator.extractor.parallel.AbstractChunk#getTable()
     */
    @Override
    public Table getTable()
    {
        return table;
    }

    /**
     * {@inheritDoc}
     * 
     * @see com.continuent.tungsten.replicator.extractor.parallel.AbstractChunk#getColumns()
     */
    @Override
    public List<String> getColumns()
    {
        return columns;
    }

    /**
     * {@inheritDoc}
     * 
     * @see com.continuent.tungsten.replicator.extractor.parallel.AbstractChunk#getFrom()
     */
    @Override
    public Object getFrom()
    {
        return null;
    }

    /**
     * {@inheritDoc}
     * 
     * @see com.continuent.tungsten.replicator.extractor.parallel.AbstractChunk#getTo()
     */
    @Override
    public Object getTo()
    {
        return null;
    }

    /**
     * {@inheritDoc}
     * 
     * @see com.continuent.tungsten.replicator.extractor.parallel.AbstractChunk#getNbBlocks()
     */
    @Override
    public long getNbBlocks()
    {
        return 0;
    }

    /**
     * {@inheritDoc}
     * 
     * @see com.continuent.tungsten.replicator.extractor.parallel.AbstractChunk#getWhereClause()
     */
    @Override
    protected String getWhereClause()
    {
        return null;
    }

}
