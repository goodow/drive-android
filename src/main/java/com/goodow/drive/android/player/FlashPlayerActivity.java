package com.goodow.drive.android.player;

import com.goodow.android.drive.R;
import com.goodow.drive.android.activity.BaseActivity;
import com.goodow.realtime.json.JsonObject;

import java.io.File;
import java.util.List;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;
import roboguice.inject.ContentView;
import roboguice.inject.InjectView;

@ContentView(R.layout.activity_flash)
public class FlashPlayerActivity extends BaseActivity {
  @InjectView(R.id.flash)
  private FlashView flash;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    // 检测flash插件是否存在
    if (checkinstallornotadobeflashapk()) {
      flashPlay(getIntent());
    } else {
      Toast.makeText(this, R.string.prompt_flash_Plugins, Toast.LENGTH_LONG).show();
    }

  }

  @Override
  protected void onDestroy() {
    this.finish();
    flash.onDestory();
    System.gc();
    super.onDestroy();
  }

  @Override
  protected void onNewIntent(Intent intent) {
    if (checkinstallornotadobeflashapk()) {
      flashPlay(getIntent());
    } else {
      Toast.makeText(this, R.string.prompt_flash_Plugins, Toast.LENGTH_LONG).show();
    }
    super.onNewIntent(intent);
  }

  @Override
  protected void onPause() {
    super.onPause();
    flash.onPause();
  }

  @Override
  protected void onResume() {
    super.onResume();
    flash.onResume();
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

  private void flashPlay(Intent intent) {
    // 得到路径
    JsonObject msg = (JsonObject) intent.getExtras().get("msg");
    String path = msg.get("path");
    File mFile = new File(path);
    if (mFile.exists()) {
      flash.load();
      flash.setFlashPath(path);
      flash.start();
    } else {
      Toast.makeText(this, R.string.pdf_file_no_exist, Toast.LENGTH_LONG).show();
    }

  }
}
