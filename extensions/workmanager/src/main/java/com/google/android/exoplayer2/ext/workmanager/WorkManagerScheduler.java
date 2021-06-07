/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.ext.workmanager;

import android.content.Context;
import android.content.Intent;
import androidx.annotation.RequiresApi;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.google.android.exoplayer2.scheduler.Requirements;
import com.google.android.exoplayer2.scheduler.Scheduler;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;

/** A {@link Scheduler} that uses {@link WorkManager}. */
public final class WorkManagerScheduler implements Scheduler {

  private static final String TAG = "WorkManagerScheduler";
  private static final String KEY_SERVICE_ACTION = "service_action";
  private static final String KEY_SERVICE_PACKAGE = "service_package";
  private static final String KEY_REQUIREMENTS = "requirements";
  private static final int SUPPORTED_REQUIREMENTS =
      Requirements.NETWORK
          | Requirements.NETWORK_UNMETERED
          | (Util.SDK_INT >= 23 ? Requirements.DEVICE_IDLE : 0)
          | Requirements.DEVICE_CHARGING
          | Requirements.DEVICE_STORAGE_NOT_LOW;

  private final WorkManager workManager;
  private final String workName;

  /** @deprecated Call {@link #WorkManagerScheduler(Context, String)} instead. */
  @Deprecated
  @SuppressWarnings("deprecation")
  public WorkManagerScheduler(String workName) {
    this.workName = workName;
    workManager = WorkManager.getInstance();
  }

  /**
   * @param context A context.
   * @param workName A name for work scheduled by this instance. If the same name was used by a
   *     previous instance, anything scheduled by the previous instance will be canceled by this
   *     instance if {@link #schedule(Requirements, String, String)} or {@link #cancel()} are
   *     called.
   */
  public WorkManagerScheduler(Context context, String workName) {
    this.workName = workName;
    workManager = WorkManager.getInstance(context.getApplicationContext());
  }

  @Override
  public boolean schedule(Requirements requirements, String servicePackage, String serviceAction) {
    Constraints constraints = buildConstraints(requirements);
    Data inputData = buildInputData(requirements, servicePackage, serviceAction);
    OneTimeWorkRequest workRequest = buildWorkRequest(constraints, inputData);
    workManager.enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, workRequest);
    return true;
  }

  @Override
  public boolean cancel() {
    workManager.cancelUniqueWork(workName);
    return true;
  }

  @Override
  public Requirements getSupportedRequirements(Requirements requirements) {
    return requirements.filterRequirements(SUPPORTED_REQUIREMENTS);
  }

  private static Constraints buildConstraints(Requirements requirements) {
    Requirements filteredRequirements = requirements.filterRequirements(SUPPORTED_REQUIREMENTS);
    if (!filteredRequirements.equals(requirements)) {
      Log.w(
          TAG,
          "Ignoring unsupported requirements: "
              + (filteredRequirements.getRequirements() ^ requirements.getRequirements()));
    }

    Constraints.Builder builder = new Constraints.Builder();
    if (requirements.isUnmeteredNetworkRequired()) {
      builder.setRequiredNetworkType(NetworkType.UNMETERED);
    } else if (requirements.isNetworkRequired()) {
      builder.setRequiredNetworkType(NetworkType.CONNECTED);
    } else {
      builder.setRequiredNetworkType(NetworkType.NOT_REQUIRED);
    }
    if (Util.SDK_INT >= 23 && requirements.isIdleRequired()) {
      setRequiresDeviceIdle(builder);
    }
    if (requirements.isChargingRequired()) {
      builder.setRequiresCharging(true);
    }
    if (requirements.isStorageNotLowRequired()) {
      builder.setRequiresStorageNotLow(true);
    }

    return builder.build();
  }

  @RequiresApi(23)
  private static void setRequiresDeviceIdle(Constraints.Builder builder) {
    builder.setRequiresDeviceIdle(true);
  }

  private static Data buildInputData(
      Requirements requirements, String servicePackage, String serviceAction) {
    Data.Builder builder = new Data.Builder();

    builder.putInt(KEY_REQUIREMENTS, requirements.getRequirements());
    builder.putString(KEY_SERVICE_PACKAGE, servicePackage);
    builder.putString(KEY_SERVICE_ACTION, serviceAction);

    return builder.build();
  }

  private static OneTimeWorkRequest buildWorkRequest(Constraints constraints, Data inputData) {
    OneTimeWorkRequest.Builder builder = new OneTimeWorkRequest.Builder(SchedulerWorker.class);

    builder.setConstraints(constraints);
    builder.setInputData(inputData);

    return builder.build();
  }

  /** A {@link Worker} that starts the target service if the requirements are met. */
  // This class needs to be public so that WorkManager can instantiate it.
  public static final class SchedulerWorker extends Worker {

    private final WorkerParameters workerParams;
    private final Context context;

    public SchedulerWorker(Context context, WorkerParameters workerParams) {
      super(context, workerParams);
      this.workerParams = workerParams;
      this.context = context;
    }

    @Override
    public Result doWork() {
      Data inputData = Assertions.checkNotNull(workerParams.getInputData());
      Requirements requirements = new Requirements(inputData.getInt(KEY_REQUIREMENTS, 0));
      int notMetRequirements = requirements.getNotMetRequirements(context);
      if (notMetRequirements == 0) {
        String serviceAction = Assertions.checkNotNull(inputData.getString(KEY_SERVICE_ACTION));
        String servicePackage = Assertions.checkNotNull(inputData.getString(KEY_SERVICE_PACKAGE));
        Intent intent = new Intent(serviceAction).setPackage(servicePackage);
        Util.startForegroundService(context, intent);
        return Result.success();
      } else {
        Log.w(TAG, "Requirements not met: " + notMetRequirements);
        return Result.retry();
      }
    }
  }
}
