/**
 * Tungsten Scale-Out Stack
 * Copyright (C) 2007-2009 Continuent Inc.
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
 * Initial developer(s): Edward Archibald
 * Contributor(s): Robert Hodges
 */

package com.continuent.tungsten.common.patterns.notification;

import java.util.Map;

import com.continuent.tungsten.common.cluster.resource.notification.ClusterResourceNotification;

public interface ResourceNotifier extends Runnable
{
    public abstract void prepare() throws Exception;

    public abstract void addListener(ResourceNotificationListener listener);

    public abstract void notifyListeners(
            ClusterResourceNotification notification)
            throws ResourceNotificationException;

    public abstract Map<String, NotificationGroupMember> getNotificationGroupMembers();
}
