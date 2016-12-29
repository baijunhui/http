package com.bjh.okhttp;

/**
 * Created by baijunhui on 16-12-29.
 */

public interface ProgressCallback {

    interface ResProgressCallback {
        void onFail(String msg);
        void onProgress(int progress);
    }

    interface DownProgressCallback {
        void onFail(String msg);
        void onDownloadProgress(float progress);
    }
}
