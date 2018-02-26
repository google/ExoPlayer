/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.ext.jobdispatcher;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import com.firebase.jobdispatcher.Constraint;
import com.firebase.jobdispatcher.FirebaseJobDispatcher;
import com.firebase.jobdispatcher.GooglePlayDriver;
import com.firebase.jobdispatcher.Job;
import com.firebase.jobdispatcher.Job.Builder;
import com.firebase.jobdispatcher.JobParameters;
import com.firebase.jobdispatcher.JobService;
import com.firebase.jobdispatcher.Lifetime;
import com.google.android.exoplayer2.scheduler.Requirements;
import com.google.android.exoplayer2.scheduler.Scheduler;
import com.google.android.exoplayer2.util.Util;

/**
 * A {@link Scheduler} which uses {@link com.firebase.jobdispatcher.FirebaseJobDispatcher} to
 * schedule a {@link Service} to be started when its requirements are met. The started service must
 * call {@link Service#startForeground(int, Notification)} to make itself a foreground service upon
 * being started, as documented by {@link Service#startForegroundService(Intent)}.
 *
 * <p>To use {@link JobDispatcherScheduler} application needs to have RECEIVE_BOOT_COMPLETED
 * permission and you need to define JobDispatcherSchedulerService in your manifest:
 *
 * <pre>{@literal
 * <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"/>
 *
 * <service
 *     android:name="com.google.android.exoplayer2.ext.jobdispatcher.JobDispatcherScheduler$JobDispatcherSchedulerService"
 *     android:exported="false">
 *   <intent-filter>
 *     <action android:name="com.firebase.jobdispatcher.ACTION_EXECUTE"/>
 *   </intent-filter>
 * </service>
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
 *
 * <p>This Scheduler uses Google Play services but does not do any availability checks. Any uses
 * should be guarded with a call to {@code
 * GoogleApiAvailability#isGooglePlayServicesAvailable(android.content.Context)}
 *
 * @see <a
 *     href="https://developers.google.com/android/reference/com/google/android/gms/common/GoogleApiAvailability#isGooglePlayServicesAvailable(android.content.Context)">GoogleApiAvailability</a>
 */
public final class JobDispatcherScheduler implements Scheduler {

  private static final String TAG = "JobDispatcherScheduler";
  private static final String SERVICE_ACTION = "SERVICE_ACTION";
  private static final String SERVICE_PACKAGE = "SERVICE_PACKAGE";
  private static final String REQUIREMENTS = "REQUIREMENTS";

  private final String jobTag;
  private final Job job;
  private final FirebaseJobDispatcher jobDispatcher;

  /**
   * @param context Used to create a {@link FirebaseJobDispatcher} service.
   * @param requirements The requirements to execute the job.
   * @param jobTag Unique tag for the job. Using the same tag as a previous job can cause that job
   *     to be replaced or canceled.
   * @param serviceAction The action which the service will be started with.
   * @param servicePackage The package of the service which contains the logic of the job.
   */
  public JobDispatcherScheduler(
      Context context,
      Requirements requirements,
      String jobTag,
      String serviceAction,
      String servicePackage) {
    this.jobDispatcher = new FirebaseJobDispatcher(new GooglePlayDriver(context));
    this.jobTag = jobTag;
    this.job = buildJob(jobDispatcher, requirements, jobTag, serviceAction, servicePackage);
  }

  @Override
  public boolean schedule() {
    int result = jobDispatcher.schedule(job);
    logd("Scheduling JobDispatcher job: " + jobTag + " result: " + result);
    return result == FirebaseJobDispatcher.SCHEDULE_RESULT_SUCCESS;
  }

  @Override
  public boolean cancel() {
    int result = jobDispatcher.cancel(jobTag);
    logd("Canceling JobDispatcher job: " + jobTag + " result: " + result);
    return result == FirebaseJobDispatcher.CANCEL_RESULT_SUCCESS;
  }

  private static Job buildJob(
      FirebaseJobDispatcher dispatcher,
      Requirements requirements,
      String tag,
      String serviceAction,
      String servicePackage) {
    Builder builder =
        dispatcher
            .newJobBuilder()
            .setService(JobDispatcherSchedulerService.class) // the JobService that will be called
            .setTag(tag);

    switch (requirements.getRequiredNetworkType()) {
      case Requirements.NETWORK_TYPE_NONE:
        // do nothing.
        break;
      case Requirements.NETWORK_TYPE_ANY:
        builder.addConstraint(Constraint.ON_ANY_NETWORK);
        break;
      case Requirements.NETWORK_TYPE_UNMETERED:
        builder.addConstraint(Constraint.ON_UNMETERED_NETWORK);
        break;
      default:
        throw new UnsupportedOperationException();
    }

    if (requirements.isIdleRequired()) {
      builder.addConstraint(Constraint.DEVICE_IDLE);
    }
    if (requirements.isChargingRequired()) {
      builder.addConstraint(Constraint.DEVICE_CHARGING);
    }
    builder.setLifetime(Lifetime.FOREVER).setReplaceCurrent(true);

    // Extras, work duration.
    Bundle extras = new Bundle();
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
  public static final class JobDispatcherSchedulerService extends JobService {
    @Override
    public boolean onStartJob(JobParameters params) {
      logd("JobDispatcherSchedulerService is started");
      Bundle extras = params.getExtras();
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
