package id.bactiar.anhp;

import android.util.Log;

final class Logx {
    private static final String TAG = "AnHP";

    private Logx() {
    }

    static void d(String message) {
        Log.d(TAG, message);
    }

    static void e(String message, Throwable throwable) {
        Log.e(TAG, message, throwable);
    }
}
