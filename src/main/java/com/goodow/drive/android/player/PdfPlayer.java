package com.goodow.drive.android.player;

import com.goodow.android.drive.R;
import com.goodow.drive.android.BusProvider;
import com.goodow.drive.android.GlobalConstant;
import com.goodow.drive.android.activity.BaseActivity;
import com.goodow.drive.android.toolutils.DeviceInformationTools;
import com.goodow.realtime.channel.Message;
import com.goodow.realtime.channel.MessageHandler;
import com.goodow.realtime.json.JsonObject;

import com.joanzapata.pdfview.PDFView;
import com.joanzapata.pdfview.listener.OnDrawListener;
import com.joanzapata.pdfview.listener.OnLoadCompleteListener;
import com.joanzapata.pdfview.listener.OnPageChangeListener;

import java.io.File;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.PointF;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Toast;

/**
 * @title: SamplePDF.java
 * @package drive-android
 * @description: PDF阅读器调用示例
 * @author www.dingpengwei@gmail.com
 * @createDate 2013 2013-12-4 上午10:48:34
 * @updateDate 2013 2013-12-4 上午10:48:34
 * @version V1.0
 */
public class PdfPlayer extends BaseActivity implements OnClickListener, OnLoadCompleteListener,
    OnPageChangeListener, OnDrawListener {
  private PDFView pdfView = null;
  private static final String CONTROL = PlayerRegistry.PREFIX + "pdf.control";
  private float currentScale = 2.4f;
  private int currentPage = 0;

  private final MessageHandler<JsonObject> eventHandler = new MessageHandler<JsonObject>() {
    @Override
    public void handle(Message<JsonObject> message) {
      currentScale = pdfView.getZoom();
      JsonObject body = message.body();
      if (body.has("move")) {
        /*
         * move 相对于当前页码的偏移量移动
         */
        if (pdfView != null) {
          pdfView.jumpTo(pdfView.getCurrentPage() + 1 + (int) body.getNumber("move"));
          pdfView.zoomCenteredTo(currentScale, new PointF(DeviceInformationTools
              .getScreenWidth(PdfPlayer.this) / 2, 0));
          pdfView.loadPages();
        }
      } else if (body.has("page")) {
        /*
         * page 指定页码的移动
         */
        if (pdfView != null) {
          pdfView.jumpTo((int) body.getNumber("page"));
          pdfView.zoomCenteredTo(currentScale, new PointF(DeviceInformationTools
              .getScreenWidth(PdfPlayer.this) / 2, 0));
          pdfView.loadPages();
        }
      } else if (body.has("scale")) {
        /*
         * scale 指定缩放数值,基数是1
         */
        if (pdfView != null) {
          currentScale = (float) body.getNumber("scale");
          pdfView.zoomCenteredTo(currentScale, new PointF(DeviceInformationTools
              .getScreenWidth(PdfPlayer.this) / 2, 0));
          pdfView.loadPages();
        }
      } else if (body.has("zoom")) {
        /*
         * zoom 指定缩放系数,基数是当前缩放值
         */
        if (pdfView != null && (float) body.getNumber("zoom") * currentScale < 10
            && (float) body.getNumber("zoom") * currentScale > 0.1) {
          currentScale = (float) body.getNumber("zoom") * currentScale;
          pdfView.zoomCenteredTo(currentScale, new PointF(DeviceInformationTools
              .getScreenWidth(PdfPlayer.this) / 2, 0));
          pdfView.loadPages();
        }
      }
    }
  };

  /*
   * override PDFVIEW LIB invoke when page load complete
   */
  @Override
  public void loadComplete(int nbPages) {
    pdfView.zoomCenteredTo(currentScale, new PointF(DeviceInformationTools
        .getScreenWidth(PdfPlayer.this) / 2, 0));
    pdfView.loadPages();
  }

  /*
   * (non-Javadoc)
   * 
   * @see android.view.View.OnClickListener#onClick(android.view.View) 处理屏幕按钮点击事件
   */
  @Override
  public void onClick(View v) {

  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    pdfView.zoomTo(this.currentScale);
    super.onConfigurationChanged(newConfig);
  }

  /*
   * override PDFVIEW LIB invoke when draw
   */
  @Override
  public void onLayerDrawn(Canvas canvas, float pageWidth, float pageHeight, int displayedPage) {
  }

  /*
   * override PDFVIEW LIB invoke when page change
   */
  @Override
  public void onPageChanged(int page, int pageCount) {
    this.currentPage = page;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    this.setContentView(R.layout.activity_pdf);
    this.pdfView = (PDFView) this.findViewById(R.id.pdfView);
    this.buildPdfView(this.getIntent());
  }

  @Override
  protected void onNewIntent(Intent intent) {
    this.buildPdfView(intent);
    super.onNewIntent(intent);
  }

  @Override
  protected void onPause() {
    super.onPause();
    BusProvider.get().unregisterHandler(CONTROL, eventHandler);
  }

  @Override
  protected void onRestoreInstanceState(final Bundle savedInstanceState) {
    new Handler().postDelayed(new Runnable() {
      @Override
      public void run() {
        currentPage = savedInstanceState.getInt("currentPage");
        currentScale = savedInstanceState.getInt("currentScale");
        pdfView.jumpTo(savedInstanceState.getInt("currentPage"));
        pdfView.loadPages();
      }
    }, 200);
    super.onRestoreInstanceState(savedInstanceState);
  }

  @Override
  protected void onResume() {
    super.onResume();
    BusProvider.get().registerHandler(CONTROL, eventHandler);
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    outState.putFloat("currentScale", this.currentScale);
    outState.putInt("currentPage", this.currentPage);
    super.onSaveInstanceState(outState);
  }

  /*
   * 加载文档
   */
  private void buildPdfView(Intent intent) {
    JsonObject jsonObject = (JsonObject) intent.getExtras().getSerializable("msg");
    File newFile = new File(GlobalConstant.STORAGEDIR + jsonObject.getString("path"));
    if (newFile.exists()) {
      pdfView.fromFile(newFile).defaultPage(1).onLoad(this).onDraw(this).onPageChange(this).onLoad(
          this).load();
    } else {
      Toast.makeText(this, this.getString(R.string.pdf_file_no_exist), Toast.LENGTH_SHORT).show();
    }
  }
}