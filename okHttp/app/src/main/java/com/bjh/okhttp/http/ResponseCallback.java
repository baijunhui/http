package com.bjh.okhttp.http;


import com.bjh.okhttp.data.Data;
import com.bjh.okhttp.data.Result;

/**
 * Created by baijunhui on 16-9-14.
 */
public interface ResponseCallback<T extends Data> {
    void onResponse(Result<T> result);
    void onFail(String msg);
}
