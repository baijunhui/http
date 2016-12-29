package com.bjh.okhttp;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bjh.okhttp.data.Result;
import com.bjh.okhttp.data.TopicList;
import com.bjh.okhttp.http.HttpServer;
import com.bjh.okhttp.http.ResponseCallback;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {

    String textUrl = "http://xhweb.yy.com/3.6.123/android/feed/getTopicList?appId=1001&sign=sign&data=%7B%22start%22%3A0%2C%22size%22%3A100%7D";
    String imageUrl = "http://hiphotos.baidu.com/%B3%F5%BC%B6%BE%D1%BB%F7%CA%D6/pic/item/929b56443840bfc6b3b7dc64.jpg";
    String apkUrl = "https://qd.myapp.com/myapp/qqteam/qq_hd/apad/home/qqhd_release_forhome.apk";


    ImageView imageView;
    TextView progressView;
    Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        progressView = (TextView) findViewById(R.id.tv_download_progress);
        imageView = (ImageView) findViewById(R.id.iv_async_task);

        findViewById(R.id.tv_ok_http).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                queryData();
            }
        });

        findViewById(R.id.tv_async_task).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                queryImage();
            }
        });

        findViewById(R.id.tv_download).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                download();
            }
        });

        findViewById(R.id.tv_download_with_progress).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                downloadWithProgress();
            }
        });
    }

    private void downloadWithProgress() {
        String localDir = Environment.getExternalStorageDirectory() + File.separator + "bjh" + File.separator;
        HttpServer.instance().downloadFile(apkUrl, localDir, new ProgressCallback.DownProgressCallback() {
            @Override
            public void onFail(final String msg) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onDownloadProgress(final float progress) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        progressView.setText(String.valueOf((int) (progress * 100)) + "%");
                    }
                });
            }
        });
    }

    private void download() {

    }

    private void queryData() {
        HttpServer.instance().sendRequest(textUrl, new ResponseCallback<TopicList>() {

            @Override
            public void onResponse(final Result<TopicList> result) {
                //这是在异步线程里的，所以不能直接调用Ui相关的，可以使用EventBus进行传递，Handler等
                //并且这里应该写在model里不应该写在activity里，我们可以注意
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, result.toString(), Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onFail(final String msg) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }, new TypeToken<Result<TopicList>>() {
        }.getType());
    }

    private void queryImage() {
        new DownloadTask().execute(imageUrl);
    }

    public class DownloadTask extends AsyncTask<String , Integer, Bitmap> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            Log.e("bjh", "当前任务进度:" + values[0] + "%");
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            super.onPostExecute(bitmap);
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap);
            }
        }

        @Override
        protected Bitmap doInBackground(String... strings) {
            URL url;
            Bitmap bitmap = null;
            try {
                url = new URL(strings[0]);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.connect();
                InputStream stream = connection.getInputStream();
                bitmap = BitmapFactory.decodeStream(stream);
                stream.close();
                connection.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }

            return bitmap;
        }
    }
}
