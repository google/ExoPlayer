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
package com.google.android.exoplayer2.ext.workmanager;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.google.android.exoplayer2.scheduler.Requirements;
import com.google.android.exoplayer2.scheduler.Scheduler;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/***
 * A {@link Scheduler} that uses {@link WorkManager}.
 */
public final class WorkManagerScheduler implements Scheduler {

    private static final String TAG = "WorkManagerScheduler";
    private static final String KEY_SERVICE_ACTION = "service_action";
    private static final String KEY_SERVICE_PACKAGE = "service_package";
    private static final String KEY_REQUIREMENTS = "requirements";

    private final String workName;

    /**
     * @param workName A name for work scheduled by this instance. If the same name was used by a previous
     *     instance, anything scheduled by the previous instance will be canceled by this instance if
     *     {@link #schedule(Requirements, String, String)} or {@link #cancel()} are called.
     */
    public WorkManagerScheduler(String workName) {
        this.workName = workName;
    }

    @Override
    public boolean schedule(Requirements requirements, String servicePackage, String serviceAction) {
        Constraints constraints = buildConstraints(requirements);
        Data inputData = buildInputData(requirements, servicePackage, serviceAction);
        OneTimeWorkRequest workRequest = buildWorkRequest(constraints, inputData);
        logd("Scheduling work: " + workName);
        WorkManager.getInstance().enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, workRequest);
        return true;
    }

    @Override
    public boolean cancel() {
        logd("Canceling work: " + workName);
        WorkManager.getInstance().cancelUniqueWork(workName);
        return true;
    }

    private static Constraints buildConstraints(Requirements requirements) {
        Constraints.Builder builder = new Constraints.Builder();

        switch (requirements.getRequiredNetworkType()) {
            case Requirements.NETWORK_TYPE_NONE:
                builder.setRequiredNetworkType(NetworkType.NOT_REQUIRED);
                break;
            case Requirements.NETWORK_TYPE_ANY:
                builder.setRequiredNetworkType(NetworkType.CONNECTED);
                break;
            case Requirements.NETWORK_TYPE_UNMETERED:
                builder.setRequiredNetworkType(NetworkType.UNMETERED);
                break;
            default:
                throw new UnsupportedOperationException();
        }

        if (requirements.isChargingRequired()) {
            builder.setRequiresCharging(true);
        }

        if (requirements.isIdleRequired() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            builder.setRequiresDeviceIdle(true);
        }

        return builder.build();
    }

    private static Data buildInputData(Requirements requirements, String servicePackage, String serviceAction) {
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

    private static void logd(String message) {
        if (DEBUG) {
            Log.d(TAG, message);
        }
    }

    /** A {@link Worker} that starts the target service if the requirements are met. */
    public static final class SchedulerWorker extends Worker {

        private final WorkerParameters workerParams;
        private final Context context;

        public SchedulerWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
            super(context, workerParams);
            this.workerParams = workerParams;
            this.context = context;
        }

        @NonNull
        @Override
        public Result doWork() {
            logd("SchedulerWorker is started");
            Data inputData = workerParams.getInputData();
            Assertions.checkNotNull(inputData, "Work started without input data.");
            Requirements requirements = new Requirements(inputData.getInt(KEY_REQUIREMENTS, 0));
            if (requirements.checkRequirements(context)) {
                logd("Requirements are met");
                String serviceAction = inputData.getString(KEY_SERVICE_ACTION);
                String servicePackage = inputData.getString(KEY_SERVICE_PACKAGE);
                Assertions.checkNotNull(serviceAction, "Service action missing.");
                Assertions.checkNotNull(servicePackage, "Service package missing.");
                Intent intent = new Intent(serviceAction).setPackage(servicePackage);
                logd("Starting service action: " + serviceAction + " package: " + servicePackage);
                Util.startForegroundService(context, intent);
                return Result.success();
            } else {
                logd("Requirements are not met");
                return Result.retry();
            }
        }
    }
}
