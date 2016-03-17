package com.example.janiszhang.multithreadingdownloaddemo;

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private Button mDownloadButton;
    private EditText mFileUrl;
    private ProgressBar mProgressBar;
    private URL mUrl;
    private boolean isDownloading = false;
    private List<HashMap<String, Integer>> threadList = new ArrayList<>();
    private File mFile;
    private DownloadHandler mDownloadHandler = new DownloadHandler(this);
    private int total = 0;
    private int mContentLength;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mFileUrl = (EditText) findViewById(R.id.et_file_url);
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mDownloadButton = (Button) findViewById(R.id.download_button);
        mFileUrl.setText("http://download.sj.qq.com/upload/connAssitantDownload/upload/MobileAssistant_1.apk");//测试用数据

        mDownloadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isDownloading) {
                    if (TextUtils.isEmpty(mFileUrl.getText().toString())) {
                        Toast.makeText(MainActivity.this,
                                "Url不能为空", Toast.LENGTH_SHORT).show();
                    } else {
                        mDownloadButton.setText("暂停");
                        isDownloading = true;
                        new DownloadTask().execute(mFileUrl.getText().toString());
                    }
                } else {
                    isDownloading = false;
                    mDownloadButton.setText("下载");
                }
            }
        });
    }

    private ProgressBar getProgressBar() {
        return mProgressBar;
    }

    private Button getDownloadButton() {
        return mDownloadButton;
    }

    private String getFileName(String url) {
        int index = url.lastIndexOf("/") + 1;
        return url.substring(index);
    }

    synchronized private void updateProgress(int len) {
        total += len;
        int temp = total * 100 / mContentLength;
        mDownloadHandler.obtainMessage(0, temp).sendToTarget();
    }

    public static class DownloadHandler extends android.os.Handler {
        public final WeakReference<MainActivity> mActivity;

        public DownloadHandler(MainActivity activity) {
            mActivity = new WeakReference<MainActivity>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            MainActivity activity = mActivity.get();

            switch (msg.what) {
                case 0:
                    int progress = (int) msg.obj;
                    activity.getProgressBar().setProgress(progress);
                    if (progress == 100) {
//                        Toast.makeText(activity, "下载成功",Toast.LENGTH_SHORT).show();
                        activity.getDownloadButton().setText("下载成功");
                    }
            }
        }
    }

    class DownloadTask extends AsyncTask<String, Integer, Void> {

        @Override
        protected Void doInBackground(String... params) {
            if (threadList.size() == 0) {
                try {
                    mUrl = new URL(params[0]);
                    HttpURLConnection urlConnection = (HttpURLConnection) mUrl.openConnection();//注意不是URLConnection, URLConnection是抽象类,HttpURLConnection是它的子类
                    urlConnection.setRequestMethod("GET");
                    urlConnection.setConnectTimeout(5000);
                    mContentLength = urlConnection.getContentLength();

                    //mProgressBar.setProgress(0);

                    if (mContentLength < 0) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "文件不存在!", Toast.LENGTH_SHORT).show();//使用getApplicationContext()是不合适的
                            }
                        });
                    }

                    mFile = new File(Environment.getExternalStorageDirectory(), getFileName(params[0]));
                    if (mFile.exists()) {
                        mFile.delete();
                    }
                    RandomAccessFile randomFile = new RandomAccessFile(mFile, "rw");
                    randomFile.setLength(mContentLength);

                    int blockSize = mContentLength / 3;
                    for (int i = 0; i < 3; i++) {
                        int begin = i * blockSize;
                        int end = (i + 1) * blockSize - 1;
                        if (i == 2) {
                            end = mContentLength;
                        }
                        HashMap<String, Integer> map = new HashMap<>();
                        map.put("begin", begin);
                        map.put("end", end);
                        map.put("finished", 0);
                        threadList.add(map);
                        //new Thread
                        new Thread(new DownloadThread(i, begin, end, mFile, mUrl)).start();
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                for (int i = 0; i < threadList.size(); i++) {
                    HashMap<String, Integer> map = threadList.get(i);
                    new Thread(new DownloadThread(i, map.get("begin") + map.get("finished"), map.get("end"), mFile, mUrl)).start();
                }
            }

            return null;
        }
    }

    class DownloadThread implements Runnable {
        private int begin;
        private int end;
        private File file;
        private URL url;
        private int id;

        public DownloadThread(int id, int begin, int end, File file, URL url) {
            this.begin = begin;
            this.end = end;
            this.file = file;
            this.url = url;
            this.id = id;
        }

        @Override
        public void run() {
            try {
                if (begin >= end) {
                    return;
                }
                RandomAccessFile randomFile = new RandomAccessFile(file, "rw");
                randomFile.seek(begin);

                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.setRequestProperty("Range", "bytes=" + begin + "-" + end);//起始位置

                InputStream is = urlConnection.getInputStream();
                byte[] buf = new byte[1024 * 1024];
                int len = 0;
                HashMap<String, Integer> map = threadList.get(id);
                while ((len = is.read(buf)) != -1 && isDownloading) {
                    randomFile.write(buf, 0, len);
                    updateProgress(len);
                    map.put("finished", map.get("finished") + len);
                    Log.i("zhangbz", "running");
                }
                is.close();
                randomFile.close();
                Log.i("zhangbz", "thread stop");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
