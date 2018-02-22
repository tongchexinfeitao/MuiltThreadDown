package com.yaoxiaowen.download;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.Nullable;

import com.yaoxiaowen.download.config.InnerConstant;
import com.yaoxiaowen.download.bean.DownloadInfo;
import com.yaoxiaowen.download.bean.RequestInfo;
import com.yaoxiaowen.download.service.DownloadService;
import com.yaoxiaowen.download.utils.LogUtils;

import java.io.File;
import java.util.ArrayList;

/**
 * @author   www.yaoxiaowen.com
 * time:  2017/12/20 18:10
 * @since 1.0.0
 */
public class DownloadHelper {
    
    public static final String TAG = "DownloadHelper";

    private volatile static DownloadHelper SINGLETANCE;

    private static ArrayList<RequestInfo> requests = new ArrayList<>();

    private DownloadHelper(){
    }

    public static DownloadHelper getInstance(){
        if (SINGLETANCE == null){
            synchronized (DownloadHelper.class){
                if (SINGLETANCE == null){
                    SINGLETANCE = new DownloadHelper();
                }
            }
        }
        return SINGLETANCE;
    }

    /**
     * 提交  下载/暂停  等任务.(提交就意味着开始执行生效)
     * @param context
     */
    public synchronized void submit(Context context){
        if (requests.isEmpty()){
            LogUtils.w("没有下载任务可供执行");
            return;
        }
        Intent intent = new Intent(context, DownloadService.class);
        intent.putExtra(InnerConstant.Inner.SERVICE_INTENT_EXTRA, requests);
        context.startService(intent);
        requests.clear();
    }// end of "submit(..."


    /**
     *  添加 新的下载任务
     *
     * @param url  下载的url
     * @param file  存储在某个位置上的文件
     * @param action  下载过程会发出广播信息.该参数是广播的action
     * @return   DownloadHelper自身 (方便链式调用)
     */
    public DownloadHelper addTask(String url, File file, @Nullable String action){
        RequestInfo requestInfo = createRequest(url, file, action, InnerConstant.Request.loading);
        LogUtils.i(TAG, "addTask() requestInfo=" + requestInfo);

        requests.add(requestInfo);
        return this;
    }

    /**
     *  暂停某个下载任务
     *
     * @param url   下载的url
     * @param file  存储在某个位置上的文件
     * @param action  下载过程会发出广播信息.该参数是广播的action
     * @return DownloadHelper自身 (方便链式调用)
     */
    public DownloadHelper pauseTask(String url, File file, @Nullable String action){
        RequestInfo requestInfo = createRequest(url, file, action, InnerConstant.Request.pause);
        LogUtils.i(TAG, "pauseTask() -> requestInfo=" + requestInfo);
        requests.add(requestInfo);
        return this;
    }

    /**
     * 设定该模块是否输出 debug信息
     * Todo 要重构log模块, 对于我们的静态内部类，目前还不生效
     */
    private DownloadHelper setDebug(boolean isDebug){
        LogUtils.setDebug(isDebug);
        return this;
    }


    private RequestInfo createRequest(String url, File file, String action, int dictate){
        RequestInfo request = new RequestInfo();
        request.setDictate(dictate);
        request.setDownloadInfo(new DownloadInfo(url, file, action));
        return request;
    }
}
