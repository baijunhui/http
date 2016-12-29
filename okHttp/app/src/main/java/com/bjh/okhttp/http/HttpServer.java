package com.bjh.okhttp.http;

import android.util.Log;

import com.bjh.okhttp.DownloadCallback;
import com.bjh.okhttp.ProgressCallback;
import com.bjh.okhttp.data.Data;
import com.bjh.okhttp.data.Result;
import com.google.gson.Gson;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.Buffer;
import okio.BufferedSink;
import okio.Okio;
import okio.Source;

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


    /**
     * 查询服务器返回json字符
     * @param url
     * @param callback
     * @param type
     * @param <T>
     */
    public <T extends Data> void sendRequest(String url, final ResponseCallback<T> callback, final Type type) {
        if (url == null || url.length() == 0) {
            callback.onFail("url不对");
            return;
        }
        Request request = new Request.Builder().url(url).build();
        okHttpClient.newBuilder().writeTimeout(10, TimeUnit.SECONDS).build().newCall(request).enqueue(new Callback() {
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


    /**
     * 上传不带其他参数的文件
     * @param fileUrl
     * @param localUrl
     * @param callback
     */
    public void uploadFile(String fileUrl, String localUrl, final ResponseCallback callback) {
        File localFile = new File(localUrl);
        if (!localFile.exists()) {
            callback.onFail("文件不存在");
            return;
        }
        RequestBody body = RequestBody.create(MediaType.parse("application/octet-stream"), localFile);
        Request request = new Request.Builder().url(fileUrl).post(body).build();
        okHttpClient.newBuilder().writeTimeout(10, TimeUnit.SECONDS).build().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onFail("上传失败");
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {

            }
        });
    }


    /**
     * 传带参数的文件上传
     * @param fileUrl
     * @param params
     */
    public void uploadFile(String fileUrl, Map<String, Object> params) {
        MultipartBody.Builder builder = new MultipartBody.Builder();
        builder.setType(MultipartBody.FORM);

        for (String key : params.keySet()) {
            Object object = params.get(key);
            if (object instanceof File) {
                File file = (File) object;
                builder.addFormDataPart(key, file.getName(), RequestBody.create(null, file));
            } else {
                builder.addFormDataPart(key, object.toString());
            }
        }

        RequestBody body = builder.build();
        Request request = new Request.Builder().url(fileUrl).post(body).build();
        okHttpClient.newBuilder().writeTimeout(10, TimeUnit.SECONDS).build().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {

                }
            }
        });
    }

    /**
     * 带进度的上传文件
     * @param fileUrl
     * @param params
     * @param callback
     */
    public void uploadFile(String fileUrl, Map<String, Object> params, final ProgressCallback.ResProgressCallback callback) {
        MultipartBody.Builder builder = new MultipartBody.Builder();

        builder.setType(MultipartBody.FORM);

        for (String key : params.keySet()) {
            Object object = params.get(key);
            if (object instanceof File) {
                File file = (File) object;
                builder.addFormDataPart(key, file.getName(), createRequestBody(MediaType.parse("application/octet-stream"), file, callback));
            } else {
                builder.addFormDataPart(key, object.toString());
            }
        }

        RequestBody body = builder.build();
        Request request = new Request.Builder().url(fileUrl).post(body).build();

        okHttpClient.newBuilder().writeTimeout(10, TimeUnit.SECONDS).build().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onFail("上传失败");
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {

                } else {
                    callback.onFail("上传失败");
                }
            }
        });
    }

    private RequestBody createRequestBody(final MediaType parse, final File file, final ProgressCallback.ResProgressCallback callback) {
        return new RequestBody() {
            @Override
            public MediaType contentType() {
                return parse;
            }

            @Override
            public long contentLength() throws IOException {
                return file.length();
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                Source source = null;
                try {
                    source = Okio.source(file);

                    Buffer buffer = new Buffer();
                    long totalLength = contentLength();

                    long current = 0;
                    for (long readCount; (readCount = source.read(buffer, 2048)) != -1; ) {
                        sink.write(buffer, readCount);
                        current += readCount;

                        callback.onProgress((int) (current / totalLength));
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (source != null) {
                        source.close();
                    }
                }
            }
        };
    }

    /**
     * 下载
     * 没有添加回调,可以自定义,并且添加
     * @param fileUrl
     * @param descDir
     */
    public void downloadFile(String fileUrl, String descDir, final DownloadCallback callback) {
        String fileName = new File(fileUrl).getName();

        final File descFile = new File(descDir, fileName);
        if (descFile.exists()) {
            return;
        }

        Request request = new Request.Builder().url(fileUrl).build();

        okHttpClient.newBuilder().writeTimeout(50, TimeUnit.SECONDS).build().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onDownloadFail();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                InputStream in = null;
                in = response.body().byteStream();
                byte[] bytes = new byte[1024];
                int length = 0;
                FileOutputStream out = new FileOutputStream(descFile);
                while ((length = in.read(bytes)) != -1) {
                    out.write(bytes, 0, length);
                }

                out.flush();

                in.close();
                out.close();

                //// TODO: 16-12-29 add callback
                callback.onDownloadSuccess();
            }
        });
    }

    /**
     * 下载  有进度
     * @param fileUrl
     * @param descDir
     * @param callback
     */
    public void downloadFile(String fileUrl, String descDir, final ProgressCallback.DownProgressCallback callback) {
        String fileName = new File(fileUrl).getName();
        File descDirFile = new File(descDir);
        if (!descDirFile.exists()) {
            descDirFile.mkdirs();
        }
        final File descFile = new File(descDir, fileName);
        if (descFile.exists()) {
            return;
        }

        Request request = new Request.Builder().url(fileUrl).build();
        okHttpClient.newBuilder().writeTimeout(50, TimeUnit.SECONDS).build().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onFail("下载失败");
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                long totalLength = response.body().contentLength();
                InputStream inputStream = response.body().byteStream();
                FileOutputStream outputStream = new FileOutputStream(descFile);
                int length = 0;
                int currentLength = 0;
                byte[] bytes = new byte[1024];
                while ((length = inputStream.read(bytes)) != -1) {
                    currentLength += length;
                    outputStream.write(bytes, 0, length);
                    callback.onDownloadProgress(currentLength * 1.0f / totalLength);
                }

                outputStream.flush();
                inputStream.close();
                outputStream.close();
            }
        });
    }


}
