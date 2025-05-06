package util;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 全局线程池管理器，统一应用中的后台线程和主线程调度。
 */
public final class AppExecutors {

    private static final AppExecutors INSTANCE = new AppExecutors();
    private final ExecutorService io;
    private final Executor main;

    private AppExecutors() {
        io = Executors.newCachedThreadPool();
        main = new MainThreadExecutor();
    }

    public static AppExecutors getInstance() {
        return INSTANCE;
    }

    public ExecutorService io() {
        return io;
    }

    public Executor main() {
        return main;
    }

    public void shutdown() {
        io.shutdown();
    }

    private static class MainThreadExecutor implements Executor {
        private final Handler handler = new Handler(Looper.getMainLooper());

        @Override
        public void execute(Runnable command) {
            handler.post(command);
        }
    }
}
