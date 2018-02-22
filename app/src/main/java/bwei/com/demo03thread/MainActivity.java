package bwei.com.demo03thread;

import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private String fileUrl = "";

    private Context mContext;
    // 设置数据库连接为全局变量。所有线程共用一个数据库连接。也不会close掉。
    // 所有线程用了就close的话，可能A线程在close的时候，B线程又想打开连接进行读写。
    // 不频繁开关连接，性能更好。等到生命周期结束才自动close
    private TaskDao taskDao;

    private ProgressBar progressbar;

    private Button btPause;
    private Button btCancel;

    private boolean isDownloading;
    private boolean isPaused;
    private boolean isCanceled;

    // 可以改线程数目，不要太多。原因你懂的
    public static final int THREAD_COUNT = 3;
    private EditText editText;
    private TextView textView;
    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            int len = (int) msg.obj;
            if (msg.what == 0) {

                textView.setText(len + "");
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mContext = this;
        // 一进应用就创建数据库
        taskDao = new TaskDao(mContext, "tasks.db", 1);
        progressbar = (ProgressBar) findViewById(R.id.progress);
        editText = (EditText) findViewById(R.id.editText);

        Button btn = (Button) findViewById(R.id.bt_download);
        btPause = (Button) findViewById(R.id.bt_pause);
        btCancel = (Button) findViewById(R.id.bt_cancel);
        textView = (TextView) findViewById(R.id.textView);

        btn.setOnClickListener(this);
        btPause.setOnClickListener(this);
        btCancel.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.bt_download:
                btPause.setVisibility(View.VISIBLE);
                btCancel.setVisibility(View.VISIBLE);
                progressbar.setVisibility(View.VISIBLE);
                fileDownload(editText.getText().toString().trim(), progressbar, btPause, btCancel);
                Toast.makeText(mContext, "正在下载...", Toast.LENGTH_SHORT).show();
                break;

            case R.id.bt_pause:
                pauseDownload();
                Toast.makeText(mContext, "下载暂停", Toast.LENGTH_SHORT).show();
                break;
            case R.id.bt_cancel:
                btPause.setVisibility(View.INVISIBLE);
                btCancel.setVisibility(View.INVISIBLE);
                progressbar.setVisibility(View.INVISIBLE);
                canceledDownload(editText.getText().toString().trim());
                Toast.makeText(mContext, "下载取消，删除文件...", Toast.LENGTH_SHORT).show();
                break;
            default:
        }
    }

    private class DownloadTask implements Runnable {
        private int thread;
        private long startIndex;
        private long endIndex;
        private long lastPosition;
        private String url;

        public DownloadTask(String url, int thread, long startIndex, long endIndex) {
            this.thread = thread;
            this.startIndex = startIndex;
            this.endIndex = endIndex;
            this.url = url;

        }


        @Override
        public void run() {
            // 先尝试读取断点，两种情况可导致不存在断点。
            // 1. 第一次进入应用，还没开始下载
            // 2. 下载完毕，断点被删除。重新下载
            // 能读取到，肯定下了一部分但是没下载完
            if (taskDao.getLastPoint(getFileName(url), thread) != -1) { // -1表示找不到键对应的值
                lastPosition = taskDao.getLastPoint(getFileName(url), thread);
                // 如果这部分下载完毕，直接返回，不再请求网络
                if (lastPosition == endIndex + 1) {
                    //Toast.makeText(MainActivity.this,"已下载完",Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            // 没找到就重新下载
            else {
                lastPosition = startIndex;
            }

            OkHttpClient client = new OkHttpClient();
            // 设置RANGE头，分段文件下载，从上次下载处继续
            Request request = new Request.Builder()
                    .addHeader("RANGE", "bytes=" + lastPosition + "-" + endIndex)
                    .url(url)
                    .build();

            File file = null;
            RandomAccessFile savedFile = null;
            try {
                Response response = client.newCall(request).execute();
                if (response != null && response.isSuccessful()) {

                    // 应用关联目录，无需申请读写存储的运行时权限
                    // 位于/sdcard/Android/data/包名/cache
                    file = new File(Environment.getExternalStorageDirectory() + "/" + getFileName(url));
                    savedFile = new RandomAccessFile(file, "rw");
                    savedFile.seek(lastPosition);
                    // 响应成功了准备断点
                    // new 一个task,初始化task和thread和position
                    Task threadTask = new Task();
                    threadTask.task = getFileName(url);
                    threadTask.thread = thread;
                    // 上面的两个是固定的，更新的时候只更新position
                    threadTask.position = -1;
                    // 必须先插入这条新的数据，才能在下面对其update
                    taskDao.addPoint(threadTask);

                    InputStream is = response.body().byteStream();
                    byte[] buffer = new byte[1024 * 1024];
                    int len;

                    int total = 0;

                    while ((!isPaused && !isCanceled && (len = is.read(buffer)) != -1)) {

                        savedFile.write(buffer, 0, len);

                        total += len;
                        //更新断点
                        threadTask.position = total + lastPosition;
                        // 保存断点
                        taskDao.savePoint(threadTask);
                    }
                    // 写完后可以把body关了
                    response.body().close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (savedFile != null) {
                        savedFile.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }
    }


    public void fileDownload(final String url, final ProgressBar progressBar, final Button pause, final Button cancel) {


        // 每次开始下载，自然要把这两个标志位置为false
        isPaused = false;
        isCanceled = false;

        // 注意boolean没有初始化默认为false，第一次进入点击下载肯定会执行，此后isDownloading为true。
        // 之后若没有点击暂停取消，标志位保持true。多次重复点击下载按钮，标志位没有改变故不会执行
        if (!isDownloading) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    isDownloading = true;
                    RandomAccessFile savedFile = null;
                    try {
                        final String fileName = getFileName(url);
                        long fileLength = getFileLength(url);
                        long partLength = fileLength / THREAD_COUNT;
                        // 应用关联目录，无需申请读写存储的运行时权限, 位于/sdcard/Android/data/包名/cache
                        File file = new File(Environment.getExternalStorageDirectory() + "/" + fileName);

                        // 随机访问，可通过seek方法定位到文件的任意位置，方便断点续传。
                        savedFile = new RandomAccessFile(file, "rw");
                        // 一开始就设置和待下载文件一样的长度，可以避免下载结束后才告知磁盘空间不足
                        // 如果不设置，seek函数不断移动到文件末尾，不断开辟空间。频繁的I/O操作降低了性能
                        savedFile.setLength(fileLength);
                        // 下面的算法适用于THREAD_COUNT等于任何数值
                        for (int thread = 0; thread < THREAD_COUNT; thread++) {
                            long startIndex = thread * partLength;
                            long endIndex = (thread + 1) * partLength - 1;
                            // 如果是最后一段，剩余的全部
                            if (thread == THREAD_COUNT - 1) {
                                endIndex = fileLength - 1;
                            }

                            // 开启线程下载
                            new Thread(new DownloadTask(url, thread, startIndex, endIndex)).start();
                        }

                        while (true) {

                            long totalProgress = 0;
                            for (int i = 0; i < THREAD_COUNT; i++) {
                                // 所有段加起来的下载字节数。推导一下，很简单
                                totalProgress += taskDao.getLastPoint(getFileName(url), i) - i * partLength;
                            }

                            // 这里有先乘100再除，否则先除是零点几，java除法抹去小数后就是0，再乘100也还是0
                            int progress = (int) (totalProgress * 100 / fileLength);
                            Log.i("进度条", progress + "");
                            Message msg = Message.obtain();


                            msg.what = 0;
                            msg.obj = progress;
                            handler.sendMessage(msg);
                            if (totalProgress == fileLength) {
                                progressBar.setProgress(100);
                                // 运行到此说明下载成功
                                taskDao.delete(getFileName(url));
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        pause.setVisibility(View.INVISIBLE);
                                        cancel.setVisibility(View.INVISIBLE);
                                        Toast.makeText(mContext, "下载成功", Toast.LENGTH_SHORT).show();
                                    }
                                });
                                break;
                            }

                        }

                    } catch (IOException e) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(mContext, "下载失败", Toast.LENGTH_SHORT).show();
                            }
                        });
                        e.printStackTrace();
                    } finally {
                        try {
                            if (savedFile != null) {
                                savedFile.close();
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                }
            }).start();
        }
    }

    // 获得文件长度
    private long getFileLength(String url) throws IOException {
        long contentLength = 0;
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(url)
                .build();
        Response response = client.newCall(request).execute();
        // 有响应且不为空
        if (response != null && response.isSuccessful()) {
            contentLength = response.body().contentLength();
            response.body().close();
        }
        return contentLength;
    }

    // 得到的是 xxx.xxx,注意不带斜杠
    private String getFileName(String url) {
        return url.substring(url.lastIndexOf("/") + 1);
    }

    // 暂停下载
    private void pauseDownload() {
        isPaused = true;
        isDownloading = false;

    }

    // 取消下载
    private void canceledDownload(String url) {
        isCanceled = true;
        isDownloading = false;
        File file = new File(getExternalCacheDir() + "/" + getFileName(url));
        if (file.exists()) {
            file.delete();
        }
        taskDao.delete(getFileName(url));
    }

}

