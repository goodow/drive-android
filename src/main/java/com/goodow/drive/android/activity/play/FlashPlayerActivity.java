package com.goodow.drive.android.activity.play;

import com.goodow.android.drive.R;
import com.goodow.drive.android.global_data_cache.GlobalDataCacheForMemorySingleton;
import com.goodow.drive.android.toolutils.JSInvokeClass;
import java.util.List;

import android.util.DisplayMetrics;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebSettings.PluginState;
import android.webkit.WebView;
import android.widget.TextView;

@SuppressLint("SetJavaScriptEnabled")
public class FlashPlayerActivity extends Activity {

  public class AndroidBridge {
    public void goMarket() {
      mHandler.post(new Runnable() {
        @Override
        public void run() {
          Intent installIntent = new Intent("android.intent.action.VIEW");
          installIntent.setData(Uri.parse("market://details?id=com.adobe.flashplayer"));
          startActivity(installIntent);
        }
      });
    }
  };

  public static enum IntentExtraTagEnum {
    // flash 资源名称
    FLASH_NAME,
    // flash 资源完整path(已经下载到了本地)
    FLASH_PATH_OF_LOCAL_FILE,
    // flash 资源的网络url
    FLASH_PATH_OF_SERVER_URL
  }

  private String localFlashFilePath;
  private String filePath;
  private WebView flashWebView;

  // private ProgressDialog mProgressDialog;

  private final Handler mHandler = new Handler();

  @Override
  public void onPause() {
    super.onPause();
    if (null != flashWebView) {
      flashWebView.onPause();
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    if (null != flashWebView) {
      flashWebView.onResume();
    }
  }

  @SuppressLint("JavascriptInterface")
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    GlobalDataCacheForMemorySingleton.getInstance.addActivity(this);

    setContentView(R.layout.activity_flash_player);

    // 获取从外部传进来的 flash资源完整路径
    if (getIntent().hasExtra(IntentExtraTagEnum.FLASH_PATH_OF_LOCAL_FILE.name())) {
      // url
      // filePath = getIntent().getStringExtra(IntentExtraTagEnum.FLASH_PATH_OF_LOCAL_FILE.name());

      flashWebView = (WebView) findViewById(R.id.flash_webView_offline);

      filePath = "file:///android_asset/flash_loading.html";
      localFlashFilePath = getIntent().getStringExtra(IntentExtraTagEnum.FLASH_PATH_OF_LOCAL_FILE.name());

      DisplayMetrics dMetrics = new DisplayMetrics();
      getWindowManager().getDefaultDisplay().getMetrics(dMetrics);
      int width = dMetrics.widthPixels;
      int height = dMetrics.heightPixels;
      JSInvokeClass jsInvokeClass = new JSInvokeClass(width, height, localFlashFilePath);

      flashWebView.addJavascriptInterface(jsInvokeClass, "CallJava");
    } else {
      // url
      filePath = getIntent().getStringExtra(IntentExtraTagEnum.FLASH_PATH_OF_SERVER_URL.name());

      flashWebView = (WebView) findViewById(R.id.flash_webView_online);
    }

    // 获取从外部传进来的 flash资源完整路径
    String flashfileName = getIntent().getStringExtra(IntentExtraTagEnum.FLASH_NAME.name());

    final TextView flashFileNameTextView = (TextView) this.findViewById(R.id.flash_file_name_textView);
    if (!TextUtils.isEmpty(flashfileName)) {
      flashFileNameTextView.setText(flashfileName);
    }

    flashWebView.setVisibility(View.VISIBLE);
    setTitle("Flash播放器");

    WebSettings webSettings = flashWebView.getSettings();
    webSettings.setPluginState(PluginState.ON);
    webSettings.setSupportZoom(true);
    // WebView启用javascript脚本执行
    webSettings.setJavaScriptEnabled(true);
    webSettings.setJavaScriptCanOpenWindowsAutomatically(true);

    try {
      Thread.sleep(500);// 主线程暂停下，否则容易白屏，原因未知
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    // mProgressDialog = ProgressDialog.show(this, "请稍等...", "加载flash中...",
    // true);
    flashWebView.setWebChromeClient(new WebChromeClient() {
      @Override
      public void onProgressChanged(WebView view, int newProgress) {
        super.onProgressChanged(view, newProgress);
        System.out.println("newProgress:" + String.valueOf(newProgress));
        if (newProgress == 100) {
          new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
              FlashPlayerActivity.this.findViewById(R.id.pb_indeterminate).setVisibility(View.GONE);
            }
          }, 500);
        }
      }
    });

    if (checkinstallornotadobeflashapk()) {
      flashWebView.loadUrl(filePath);
    } else {
      installadobeapk();
    }
  }

  // 退出时关闭flash播放
  @Override
  protected void onDestroy() {
    super.onDestroy();
    flashWebView.destroy();

    GlobalDataCacheForMemorySingleton.getInstance.removeActivity(this);

    System.gc();
  }

  // 后台运行
  @Override
  protected void onUserLeaveHint() {
    flashWebView.destroy();
    this.finish();
    System.gc();
    super.onUserLeaveHint();
  }

  // 检查机子是否安装的有Adobe Flash相关APK
  private boolean checkinstallornotadobeflashapk() {
    PackageManager pm = getPackageManager();
    List<PackageInfo> infoList = pm.getInstalledPackages(PackageManager.GET_SERVICES);
    for (PackageInfo info : infoList) {
      if ("com.adobe.flashplayer".equals(info.packageName)) {
        return true;
      }
    }
    return false;
  }

  // 安装Adobe Flash APK
  @SuppressLint("JavascriptInterface")
  private void installadobeapk() {
    flashWebView.loadUrl("file:///android_asset/go_market.html");
    flashWebView.addJavascriptInterface(new AndroidBridge(), "android");
  }
}
