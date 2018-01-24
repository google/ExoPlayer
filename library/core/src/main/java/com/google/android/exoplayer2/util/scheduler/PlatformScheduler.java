/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.util.scheduler;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.Service;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.PersistableBundle;
import android.util.Log;
import com.google.android.exoplayer2.util.Util;

/**
 * A {@link Scheduler} which uses {@link android.app.job.JobScheduler} to schedule a {@link Service}
 * to be started when its requirements are met. The started service must call {@link
 * Service#startForeground(int, Notification)} to make itself a foreground service upon being
 * started, as documented by {@link Service#startForegroundService(Intent)}.
 *
 * <p>To use {@link PlatformScheduler} application needs to have RECEIVE_BOOT_COMPLETED permission
 * and you need to define PlatformSchedulerService in your manifest:
 *
 * <pre>{@literal
 * <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"/>
 *
 * <service android:name="com.google.android.exoplayer2.util.scheduler.PlatformScheduler$PlatformSchedulerService"
 *          android:permission="android.permission.BIND_JOB_SERVICE"
 *          android:exported="true"/>
 * }</pre>
 *
 * The service to be scheduled must be defined in the manifest with an intent-filter:
 *
 * <pre>{@literal
 * <service android:name="MyJobService"
 *          android:exported="false">
 *  <intent-filter>
 *    <action android:name="MyJobService.action"/>
 *    <category android:name="android.intent.category.DEFAULT"/>
 *  </intent-filter>
 * </service>
 * }</pre>
 */
@TargetApi(21)
public final class PlatformScheduler implements Scheduler {

  private static final String TAG = "PlatformScheduler";
  private static final String SERVICE_ACTION = "SERVICE_ACTION";
  private static final String SERVICE_PACKAGE = "SERVICE_PACKAGE";
  private static final String REQUIREMENTS = "REQUIREMENTS";

  private final int jobId;
  private final JobInfo jobInfo;
  private final JobScheduler jobScheduler;

  /**
   * @param context Used to access to {@link JobScheduler} service.
   * @param requirements The requirements to execute the job.
   * @param jobId Unique identifier for the job. Using the same id as a previous job can cause that
   *     job to be replaced or canceled.
   * @param serviceAction The action which the service will be started with.
   * @param servicePackage The package of the service which contains the logic of the job.
   */
  public PlatformScheduler(
      Context context,
      Requirements requirements,
      int jobId,
      String serviceAction,
      String servicePackage) {
    this.jobId = jobId;
    this.jobInfo = buildJobInfo(context, requirements, jobId, serviceAction, servicePackage);
    this.jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
  }

  @Override
  public boolean schedule() {
    int result = jobScheduler.schedule(jobInfo);
    logd("Scheduling JobScheduler job: " + jobId + " result: " + result);
    return result == JobScheduler.RESULT_SUCCESS;
  }

  @Override
  public boolean cancel() {
    logd("Canceling JobScheduler job: " + jobId);
    jobScheduler.cancel(jobId);
    return true;
  }

  private static JobInfo buildJobInfo(
      Context context,
      Requirements requirements,
      int jobId,
      String serviceAction,
      String servicePackage) {
    JobInfo.Builder builder =
        new JobInfo.Builder(jobId, new ComponentName(context, PlatformSchedulerService.class));

    int networkType;
    switch (requirements.getRequiredNetworkType()) {
      case Requirements.NETWORK_TYPE_NONE:
        networkType = JobInfo.NETWORK_TYPE_NONE;
        break;
      case Requirements.NETWORK_TYPE_ANY:
        networkType = JobInfo.NETWORK_TYPE_ANY;
        break;
      case Requirements.NETWORK_TYPE_UNMETERED:
        networkType = JobInfo.NETWORK_TYPE_UNMETERED;
        break;
      case Requirements.NETWORK_TYPE_NOT_ROAMING:
        if (Util.SDK_INT >= 24) {
          networkType = JobInfo.NETWORK_TYPE_NOT_ROAMING;
        } else {
          throw new UnsupportedOperationException();
        }
        break;
      case Requirements.NETWORK_TYPE_METERED:
        if (Util.SDK_INT >= 26) {
          networkType = JobInfo.NETWORK_TYPE_METERED;
        } else {
          throw new UnsupportedOperationException();
        }
        break;
      default:
        throw new UnsupportedOperationException();
    }

    builder.setRequiredNetworkType(networkType);
    builder.setRequiresDeviceIdle(requirements.isIdleRequired());
    builder.setRequiresCharging(requirements.isChargingRequired());
    builder.setPersisted(true);

    // Extras, work duration.
    PersistableBundle extras = new PersistableBundle();
    extras.putString(SERVICE_ACTION, serviceAction);
    extras.putString(SERVICE_PACKAGE, servicePackage);
    extras.putInt(REQUIREMENTS, requirements.getRequirementsData());

    builder.setExtras(extras);
    return builder.build();
  }

  private static void logd(String message) {
    if (DEBUG) {
      Log.d(TAG, message);
    }
  }

  /** A {@link JobService} to start a service if the requirements are met. */
  public static final class PlatformSchedulerService extends JobService {
    @Override
    public boolean onStartJob(JobParameters params) {
      logd("PlatformSchedulerService is started");
      PersistableBundle extras = params.getExtras();
      Requirements requirements = new Requirements(extras.getInt(REQUIREMENTS));
      if (requirements.checkRequirements(this)) {
        logd("requirements are met");
        String serviceAction = extras.getString(SERVICE_ACTION);
        String servicePackage = extras.getString(SERVICE_PACKAGE);
        Intent intent = new Intent(serviceAction).setPackage(servicePackage);
        logd("starting service action: " + serviceAction + " package: " + servicePackage);
        if (Util.SDK_INT >= 26) {
          startForegroundService(intent);
        } else {
          startService(intent);
        }
      } else {
        logd("requirements are not met");
        jobFinished(params, /* needsReschedule */ true);
      }
      return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
      return false;
    }
  }
}
