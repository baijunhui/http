package com.bjh.okhttp.data;

/**
 * Created by baijunhui on 16-9-14.
 */
public class Result<T extends Data> {
    public T data;

    @Override
    public String toString() {
        return data.toString();
    }
}
