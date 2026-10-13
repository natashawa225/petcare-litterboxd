package com.litterboxd.app.util;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Global executor helper for running database and hashing work on background threads
 * and delivering results back to the main UI thread.
 * Complies with course constraints: no coroutines, RxJava, or LiveData/ViewModel.
 */
public class AppExecutors {
    private static final int THREAD_COUNT = 3;
    private final Executor diskIO;
    private final Executor mainThread;

    private static final AppExecutors INSTANCE = new AppExecutors(
            Executors.newFixedThreadPool(THREAD_COUNT),
            new MainThreadExecutor()
    );

    public static AppExecutors getInstance() {
        return INSTANCE;
    }

    private AppExecutors(Executor diskIO, Executor mainThread) {
        this.diskIO = diskIO;
        this.mainThread = mainThread;
    }

    public Executor diskIO() {
        return diskIO;
    }

    public Executor mainThread() {
        return mainThread;
    }

    private static class MainThreadExecutor implements Executor {
        private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

        @Override
        public void execute(@NonNull Runnable command) {
            mainThreadHandler.post(command);
        }
    }
}
