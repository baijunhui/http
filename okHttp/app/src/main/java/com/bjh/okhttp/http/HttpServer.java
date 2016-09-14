package com.bjh.okhttp.http;

import com.bjh.okhttp.data.Data;
import com.bjh.okhttp.data.Result;
import com.google.gson.Gson;

import java.io.IOException;
import java.lang.reflect.Type;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Created by baijunhui on 16-9-14.
 */
public class HttpServer {

    private static HttpServer httpServer;
    private static OkHttpClient okHttpClient;
    private static Gson gson;

    public static HttpServer instance() {
        if (httpServer == null) {
            synchronized (HttpServer.class) {
                if (httpServer == null) {
                    httpServer = new HttpServer();
                    okHttpClient = new OkHttpClient();
                    gson = new Gson();
                }
            }
        }
        return httpServer;
    }


    public <T extends Data> void sendRequest(String url, final ResponseCallback<T> callback, final Type type) {
        if (url == null || url.length() == 0) {
            callback.onFail("url不对");
            return;
        }
        Request request = new Request.Builder().url(url).build();
        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onFail(e.toString());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String stringResult = new String(response.body().bytes(), "UTF-8");
                    Result<T> result = gson.fromJson(stringResult, type);
                    callback.onResponse(result);
                } else {
                    callback.onFail(response.toString());
                }
            }
        });

    }
}
