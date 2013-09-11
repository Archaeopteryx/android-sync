/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.gecko.background.healthreport.prune;

import org.mozilla.gecko.background.common.log.Logger;
import org.mozilla.gecko.background.healthreport.Environment;
import org.mozilla.gecko.background.healthreport.EnvironmentBuilder;
import org.mozilla.gecko.background.healthreport.HealthReportConstants;
import org.mozilla.gecko.background.healthreport.HealthReportDatabaseStorage;
import org.mozilla.gecko.background.healthreport.ProfileInformationCache;

import android.content.ContentProviderClient;
import android.content.Context;

/**
 * Abstracts over the Storage instance behind the PrunePolicy. The underlying storage instance is
 * a {@link HealthReportDatabaseStorage} instance. Since our cleanup routine vacuums, auto_vacuum
 * can be disabled. It is enabled by default, however, turning it off requires an expensive vacuum
 * so we wait until our first {@link cleanup} call since we are vacuuming anyway. Note that this
 * cleanup must be time-based - see {@link shouldCleanupEarly}.
 */
public class AndroidPrunePolicyStorage implements PrunePolicyStorage {
  public static final String LOG_TAG = AndroidPrunePolicyStorage.class.getSimpleName();

  private final Context context;
  private final String profilePath;

  private ContentProviderClient client;
  private HealthReportDatabaseStorage storage;

  private int currentEnvironmentID; // So we don't prune the current environment.

  public AndroidPrunePolicyStorage(final Context context, final String profilePath) {
    this.context = context;
    this.profilePath = profilePath;

    this.currentEnvironmentID = -1;
  }

  public void pruneEvents(final int count) {
    getStorage().pruneEvents(count);
  }

  public void pruneEnvironments(final int count) {
    getStorage().pruneEnvironments(count);
  }

  public int deleteDataBefore(final long time) {
    return getStorage().deleteDataBefore(time, getCurrentEnvironmentID());
  }

  public boolean shouldCleanupEarly() {
    final HealthReportDatabaseStorage storage = getStorage();
    // If auto_vacuum is enabled, there are no free pages and we can't get the free page ratio in
    // order to know if we need to vacuum on fragmentation amount.
    if (storage.isAutoVacuumingDisabled()) {
      final float freePageRatio = storage.getFreePageRatio();
      final float freePageRatioLimit = getFreePageRatioLimit();
      if (freePageRatio > freePageRatioLimit) {
        Logger.debug(LOG_TAG, "Vacuuming based on fragmentation amount: " + freePageRatio + " / " +
            freePageRatioLimit);
        return true;
      }
    }
    return false;
  }

  public void cleanup() {
    final HealthReportDatabaseStorage storage = getStorage();
    // The change to auto_vacuum will only take affect after a vacuum.
    storage.disableAutoVacuuming();
    storage.vacuum();
  }

  public int getEventCount() {
    return getStorage().getEventCount();
  }

  public int getEnvironmentCount() {
    return getStorage().getEnvironmentCount();
  }

  public void close() {
    if (client != null) {
      client.release();
      client = null;
    }
  }

  /**
   * Retrieves the {@link HealthReportDatabaseStorage} associated with the profile of the policy.
   * For efficiency, the underlying {@link ContentProviderClient} and
   * {@link HealthReportDatabaseStorage} are cached for later invocations. However, this means a
   * call to this method MUST be accompanied by a call to {@link close}. Throws
   * {@link IllegalStateException} if the storage instance could not be retrieved - note that the
   * {@link ContentProviderClient} instance will not be closed in this case and
   * {@link releaseClient} should still be called.
   */
  protected HealthReportDatabaseStorage getStorage() {
    if (storage != null) {
      return storage;
    }

    client = EnvironmentBuilder.getContentProviderClient(context);
    if (client == null) {
      // TODO: Record prune failures and submit as part of FHR upload.
      Logger.warn(LOG_TAG, "Unable to get ContentProviderClient - throwing.");
      throw new IllegalStateException("Unable to get ContentProviderClient.");
    }

    try {
      storage = EnvironmentBuilder.getStorage(client, profilePath);
      if (storage == null) {
        // TODO: Record prune failures and submit as part of FHR upload.
        Logger.warn(LOG_TAG,"Unable to get HealthReportDatabaseStorage for " + profilePath +
            " - throwing.");
        throw new IllegalStateException("Unable to get HealthReportDatabaseStorage for " +
            profilePath + " (== null).");
      }
    } catch (ClassCastException ex) {
      // TODO: Record prune failures and submit as part of FHR upload.
      Logger.warn(LOG_TAG,"Unable to get HealthReportDatabaseStorage for " + profilePath +
          profilePath + " (ClassCastException).");
      throw new IllegalStateException("Unable to get HealthReportDatabaseStorage for " +
          profilePath + ".", ex);
    }

    return storage;
  }

  protected int getCurrentEnvironmentID() {
    if (currentEnvironmentID < 0) {
      final ProfileInformationCache cache = new ProfileInformationCache(profilePath);
      if (!cache.restoreUnlessInitialized()) {
        throw new IllegalStateException("Current environment unknown.");
      }
      final Environment env = EnvironmentBuilder.getCurrentEnvironment(cache);
      currentEnvironmentID = env.register();
    }
    return currentEnvironmentID;
  }

  private float getFreePageRatioLimit() {
    return HealthReportConstants.DB_FREE_PAGE_RATIO_LIMIT;
  }
}
