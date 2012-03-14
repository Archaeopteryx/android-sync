/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is Android Sync Client.
 *
 * The Initial Developer of the Original Code is
 * the Mozilla Foundation.
 * Portions created by the Initial Developer are Copyright (C) 2011
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *   Jason Voll <jvoll@mozilla.com>
 *   Richard Newman <rnewman@mozilla.com>
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK ***** */

package org.mozilla.gecko.sync.repositories.android;

import java.util.ArrayList;

import org.mozilla.gecko.db.BrowserContract;
import org.mozilla.gecko.sync.Logger;
import org.mozilla.gecko.sync.repositories.NullCursorException;
import org.mozilla.gecko.sync.repositories.domain.Record;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

public abstract class AndroidBrowserRepositoryDataAccessor {

  protected static final String[] GUID_COLUMNS = new String[] { BrowserContract.SyncColumns.GUID };
  protected Context context;
  protected String LOG_TAG() {
    return "BrowserDataAccessor";
  }
  protected final RepoUtils.QueryHelper queryHelper;

  public AndroidBrowserRepositoryDataAccessor(Context context) {
    this.context = context;
    this.queryHelper = new RepoUtils.QueryHelper(context, getUri(), LOG_TAG());
  }

  protected abstract String[] getAllColumns();
  /**
   * Override this to add timestamps to an about-to-be-inserted record.
   * @param values The <code>ContentValues</code> to be inserted.
   * @param record The <code>Record</code> that <code>values</code> was generated from.
   */

  /**
   * Convert a <code>Record</code> into a <code>ContentValues</code>.
   *
   * @param record The <code>Record</code> to be converted.
   * @return The <code>ContentValues</code> corresponding to <code>record</code>.
   */
  protected abstract ContentValues getContentValues(Record record);

  /**
   * Override this to add timestamps to an about-to-be-inserted record.
   *
   * @param values The <code>ContentValues</code> to be inserted.
   * @param record The <code>Record</code> that <code>values</code> was generated from.
   */
  protected void addTimestampsForInsert(ContentValues values, Record record) {
    // By default, let the Content Provider update timestamps for about-to-be-inserted records.
  }

  /**
   * Override this to add timestamps to an about-to-be-updated record.
   *
   * @param values The <code>ContentValues</code> to be inserted.
   * @param record The <code>Record</code> that <code>values</code> was generated from.
   */
  protected void addTimestampsForUpdate(ContentValues values, Record record) {
    // By default, let the Content Provider update timestamps for about-to-be-updated records.
  }

  protected abstract Uri getUri();

  /**
   * Dump all the records in raw format.
   */
  public void dumpDB() {
    Cursor cur = null;
    try {
      cur = queryHelper.safeQuery(".dumpDB", null, null, null, null);
      RepoUtils.dumpCursor(cur);
    } catch (NullCursorException e) {
    } finally {
      if (cur != null) {
        cur.close();
      }
    }
  }

  public String dateModifiedWhere(long timestamp) {
    return BrowserContract.SyncColumns.DATE_MODIFIED + " >= " + Long.toString(timestamp);
  }

  public void wipe() {
    Uri uri = getUri();
    Logger.info(LOG_TAG(), "Wiping: " + uri + "...");
    context.getContentResolver().delete(uri, null, null);
    Logger.info(LOG_TAG(), "Wiping: " + uri + "... DONE");
  }

  public ArrayList<String> deletedGuids() throws NullCursorException {
    String where = BrowserContract.SyncColumns.IS_DELETED + "= 1";
    Cursor cur = queryHelper.safeQuery(".deletedGuids", GUID_COLUMNS, where, null, null);

    ArrayList<String> deletedGuids = new ArrayList<String>();
    try {
      if (!cur.moveToFirst()) {
        return deletedGuids;
      }
      while (!cur.isAfterLast()) {
        String deletedGuid = RepoUtils.getStringFromCursor(cur, BrowserContract.SyncColumns.GUID);
        deletedGuids.add(deletedGuid);
        cur.moveToNext();
      }
    } finally {
      cur.close();
    }
    return deletedGuids;
  }

  public void purgeDeleted() throws NullCursorException {
    Logger.info(LOG_TAG(), "purgeDeleted...");
    for (String deletedGuid : deletedGuids()) {
      delete(deletedGuid);
    }
    Logger.info(LOG_TAG(), "purgeDeleted... DONE");
  }

  protected void delete(String guid) {
    String where  = BrowserContract.SyncColumns.GUID + " = ?";
    String[] args = new String[] { guid };

    int deleted = context.getContentResolver().delete(getUri(), where, args);
    if (deleted == 1) {
      return;
    }
    Logger.warn(LOG_TAG(), "Unexpectedly deleted " + deleted + " rows (for guid " + guid);
  }

  public void delete(Record record) {
    delete(record.guid);
  }

  public void update(String guid, Record newRecord) {
    Logger.info(LOG_TAG(), "Updating record with guid " + guid + "...");
    ContentValues cv = getContentValues(newRecord);
    addTimestampsForUpdate(cv, newRecord);
    updateByGuid(guid, cv);
    Logger.info(LOG_TAG(), "Updating record with guid " + guid + "... DONE");
  }

  public void updateByGuid(String guid, ContentValues cv) {
    String where  = BrowserContract.SyncColumns.GUID + " = ?";
    String[] args = new String[] { guid };

    int updated = context.getContentResolver().update(getUri(), cv, where, args);
    if (updated == 1) {
      return;
    } else {
      Logger.warn(LOG_TAG(), "Unexpectedly updated " + updated + " rows (for guid " + guid);
      throw new RuntimeException();
    }
  }

  public Uri insert(Record record) {
    Logger.info(LOG_TAG(), "Inserting record with guid " + record.guid + "...");
    ContentValues cv = getContentValues(record);
    addTimestampsForInsert(cv, record);
    Uri ret = context.getContentResolver().insert(getUri(), cv);
    Logger.info(LOG_TAG(), "Inserting record with guid " + record.guid + "... DONE");
    return ret;
  }

  /**
   * Fetch all records.
   * The caller is responsible for closing the cursor.
   *
   * @return A cursor. You *must* close this when you're done with it.
   * @throws NullCursorException
   */
  public Cursor fetchAll() throws NullCursorException {
    return queryHelper.safeQuery(".fetchAll", getAllColumns(), null, null, null);
  }

  /**
   * Fetch GUIDs for records modified since the provided timestamp.
   * The caller is responsible for closing the cursor.
   *
   * @param timestamp
   * @return A cursor. You *must* close this when you're done with it.
   * @throws NullCursorException
   */
  public Cursor getGUIDsSince(long timestamp) throws NullCursorException {
    return queryHelper.safeQuery(".getGUIDsSince",
                                 GUID_COLUMNS,
                                 dateModifiedWhere(timestamp),
                                 null, null);
  }

  /**
   * Fetch records modified since the provided timestamp.
   * The caller is responsible for closing the cursor.
   *
   * @param timestamp
   * @return A cursor. You *must* close this when you're done with it.
   * @throws NullCursorException
   */
  public Cursor fetchSince(long timestamp) throws NullCursorException {
    return queryHelper.safeQuery(".fetchSince",
                                 getAllColumns(),
                                 dateModifiedWhere(timestamp),
                                 null, null);
  }

  /**
   * Fetch records for the provided GUIDs.
   * The caller is responsible for closing the cursor.
   *
   * @param guids
   * @return A cursor. You *must* close this when you're done with it.
   * @throws NullCursorException
   */
  public Cursor fetch(String guids[]) throws NullCursorException {
    String where = computeSQLInClause(guids.length, "guid");
    return queryHelper.safeQuery(".fetch", getAllColumns(), where, guids, null);
  }

  protected String computeSQLInClause(int items, String field) {
    StringBuilder builder = new StringBuilder(field);
    builder.append(" IN (");
    int i = 0;
    for (; i < items - 1; ++i) {
      builder.append("?, ");
    }
    if (i < items) {
      builder.append("?");
    }
    builder.append(")");
    return builder.toString();
  }
}
