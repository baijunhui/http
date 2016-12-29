package com.bjh.okhttp;

/**
 * Created by baijunhui on 16-12-29.
 */

public interface DownloadCallback {

    void onDownloadFail();

    void onDownloadSuccess();
}
