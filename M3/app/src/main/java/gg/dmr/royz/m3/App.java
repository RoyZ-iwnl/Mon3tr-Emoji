package gg.dmr.royz.m3;

import android.app.Application;

import gg.dmr.royz.m3.utils.LogUtil;

/**
 * 应用入口类
 */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        LogUtil.log("应用启动");
    }
}