package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.UserNotification;

/** Atomic keyed insertion on the workflow's database transaction. */
public interface UserNotificationInsert {
    /** Returns the new ID, or null when this key already exists. */
    Long insertOnce(UserNotification notification);
}
