package com.goodow.drive.android.player;

import com.goodow.android.drive.R;
import com.goodow.drive.android.BusProvider;
import com.goodow.drive.android.Constant;
import com.goodow.drive.android.activity.BaseActivity;
import com.goodow.realtime.channel.Bus;
import com.goodow.realtime.channel.Message;
import com.goodow.realtime.channel.MessageHandler;
import com.goodow.realtime.json.Json;
import com.goodow.realtime.json.JsonObject;

import java.io.File;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnHoverListener;
import android.view.View.OnTouchListener;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import android.widget.Toast;

public class PicturePlayAcivity extends BaseActivity implements OnTouchListener {
  private final static String TAG = PicturePlayAcivity.class.getSimpleName();
  private ImageView mImageView;
  private Bitmap mBitmap;
  private Matrix translateMatrix;
  private Matrix baseMatrix;
  private float scale;
  private float viewWidth;
  private float viewHeight;
  private int drawableWidth;
  private int drawableHeight;
  float mLastTouchX;
  float mLastTouchY;
  private String path;

  // 工具箱
  private LinearLayout ll_act_picture_tools = null;
  private boolean isDrawing = false;
  private static final int MESSAGE_CODE_DISSMISS_BAR = 0;
  // 延迟时间
  private final static int DELAYTIME = 15 * 1000;

  private final MessageHandler<JsonObject> postHandler = new MessageHandler<JsonObject>() {

    @Override
    public void handle(Message<JsonObject> message) {
      JsonObject msg = message.body();
      if (msg.has("path")) {
        // 包含path的时候返回,onNewIntent处理
        return;
      }
      handleMessage(msg);
    }
  };

  private float currentScale;

  private final Handler toolBarHandler = new Handler() {
    @Override
    public void handleMessage(android.os.Message msg) {
      switch (msg.what) {
        case MESSAGE_CODE_DISSMISS_BAR:
          if (!isDrawing) {
            ll_act_picture_tools.setVisibility(View.INVISIBLE);
          }
          break;

        default:
          break;
      }
    };
  };

  // 画笔功能的点击事件
  public void onClick(View v) {
    switch (v.getId()) {
    // 放大
      case R.id.iv_act_picture_zoom_out:
        bus.send(Bus.LOCAL + BusProvider.SID + "player", Json.createObject().set("zoomBy", 1.1),
            null);
        break;
      // 缩小
      case R.id.iv_act_picture_zoom_in:
        bus.send(Bus.LOCAL + BusProvider.SID + "player", Json.createObject().set("zoomBy", 0.9),
            null);
        break;
      // 1:1
      case R.id.iv_act_picture_full_screen:
        bus.send(Bus.LOCAL + BusProvider.SID + "player", Json.createObject().set("zoomTo", 1), null);
        break;
      // 画笔的获取与丢弃
      case R.id.iv_act_picture_pen:
        if (isDrawing) {
          isDrawing = false;
          bus.send(Bus.LOCAL + BusProvider.SID + "view.scrawl", Json.createObject().set(
              "annotation", false), null);
          delayDissmissToolsbar();
        } else {
          isDrawing = true;
          bus.send(Bus.LOCAL + BusProvider.SID + "view.scrawl", Json.createObject().set(
              "annotation", true), null);
          delayDissmissToolsbarCancle();
        }
        break;
      // 橡皮擦
      case R.id.iv_act_picture_eraser:
        bus.send(Bus.LOCAL + BusProvider.SID + "view.scrawl", Json.createObject()
            .set("clear", true), null);
        break;
      default:
        break;
    }
  }

  @Override
  public boolean onTouch(View v, MotionEvent ev) {
    switch (ev.getAction()) {
      case MotionEvent.ACTION_DOWN:
        // 记录起始点坐标
        mLastTouchX = ev.getX();
        mLastTouchY = ev.getY();
        break;
      case MotionEvent.ACTION_MOVE:
        // 当前触摸点坐标
        float x = ev.getX();
        float y = ev.getY();
        // 移动距离
        float dx = x - mLastTouchX;
        float dy = y - mLastTouchY;
        Matrix currentMatrix = mImageView.getImageMatrix();
        translateMatrix = new Matrix(currentMatrix);
        // 更新起始点坐标
        mLastTouchX = x;
        mLastTouchY = y;
        // 移动并检查边界
        translateMatrix.postTranslate(dx, dy);
        if (checkMatrixBounds(translateMatrix)) {
          mImageView.setImageMatrix(translateMatrix);
        }

        break;
      case MotionEvent.ACTION_UP:
        mLastTouchX = ev.getX();
        mLastTouchY = ev.getY();
        break;
    }
    return true;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_picture);

    JsonObject jsonObject = (JsonObject) getIntent().getExtras().getSerializable("msg");
    path = jsonObject.getString("path");

    initView();
    if (new File(path).isFile()) {
      mBitmap = setImage(path);
    } else {
      Toast.makeText(this, "图片不存在", Toast.LENGTH_SHORT).show();
      return;
    }
    mImageView.setImageBitmap(mBitmap);
    setInit(0);
    handleMessage(jsonObject);
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    // 回收bitmap,imageview监听置空,引用置空,加速垃圾回收
    if (mImageView != null) {
      mImageView.setOnTouchListener(null);
      mImageView = null;
    }
    if (mBitmap != null && !mBitmap.isRecycled()) {
      mBitmap.recycle();
      mBitmap = null;
    }
    System.gc();
  }

  @Override
  protected void onNewIntent(Intent intent) {
    setIntent(intent);
    JsonObject jsonObject = (JsonObject) intent.getExtras().getSerializable("msg");
    String path = jsonObject.getString("path");
    if (!path.equals(this.path)) {
      // path相同则不重新加载,只进行设置
      if (new File(path).isFile()) {
        this.path = path;
        mBitmap = setImage(path);
      } else {
        Toast.makeText(this, "图片不存在", Toast.LENGTH_SHORT).show();
        return;
      }
      mImageView.setImageBitmap(mBitmap);
      setInit(0);
    }
    handleMessage(jsonObject);
  }

  @Override
  protected void onPause() {
    super.onPause();
    // Always unregister when an handler no longer should be on the bus.
    bus.unregisterHandler(Constant.ADDR_PLAYER, postHandler);
  }

  @Override
  protected void onResume() {
    super.onResume();
    // Register handlers so that we can receive event messages.
    bus.registerHandler(Constant.ADDR_PLAYER, postHandler);
  }

  /**
   * 边界检查校正,超出边界进行反向移动
   * 
   * @param matrix imageview当前使用的矩阵
   * @return 校正成功true,失败false
   */
  private boolean checkMatrixBounds(Matrix matrix) {
    final ImageView imageView = mImageView;
    if (null == imageView) {
      return false;
    }
    // 获取当前图片的边界信息
    Drawable d = mImageView.getDrawable();
    RectF rect = null;
    if (null != d) {
      rect = new RectF();
      rect.set(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
      matrix.mapRect(rect);
    }
    if (null == rect) {
      return false;
    }

    final float height = rect.height(), width = rect.width();
    float deltaX = 0, deltaY = 0;
    // 超出边界后反向移动距离计算
    if (height <= viewHeight) {
      deltaY = (viewHeight - height) / 2 - rect.top;
    } else if (rect.top > 0) {
      deltaY = -rect.top;
    } else if (rect.bottom < viewHeight) {
      deltaY = viewHeight - rect.bottom;
    }

    if (width <= viewWidth) {
      deltaX = (viewWidth - width) / 2 - rect.left;
    } else if (rect.left > 0) {
      deltaX = -rect.left;
    } else if (rect.right < viewWidth) {
      deltaX = viewWidth - rect.right;
    }
    // 矩阵平移
    matrix.postTranslate(deltaX, deltaY);
    return true;
  }

  /**
   * DPW 延迟取消工具框
   */
  private void delayDissmissToolsbar() {
    android.os.Message msg = new android.os.Message();
    msg.what = MESSAGE_CODE_DISSMISS_BAR;
    toolBarHandler.sendMessageDelayed(msg, DELAYTIME);
  }

  /**
   * DPW 取消延迟取消工具框
   */
  private void delayDissmissToolsbarCancle() {
    toolBarHandler.removeMessages(MESSAGE_CODE_DISSMISS_BAR);
  }

  /**
   * 获取显示图片的rect
   * 
   * @return rect
   */
  private RectF getImageRect() {
    Drawable d = mImageView.getDrawable();
    RectF currentRect = new RectF();
    currentRect.set(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
    Matrix currentMatrix = mImageView.getImageMatrix();
    currentMatrix.mapRect(currentRect);
    return currentRect;
  }

  private void handleMessage(JsonObject msg) {
    if (msg.has("fit")) {
      // 适应屏幕
      int mFit = (int) msg.getNumber("fit");
      setFit(mFit);
    } else if (msg.has("zoomTo")) {
      // 以原图为基础放大缩小
      float mZoom = (float) msg.getNumber("zoomTo");
      setZoomTo(mZoom, false);
      currentScale = mZoom * drawableWidth / getImageRect().width();
    }
    if (msg.has("zoomBy")) {
      // 按幅度放大缩小
      float mScale = (float) msg.getNumber("zoomBy");
      setZoomTo(currentScale * mScale, false);
      currentScale = currentScale * mScale;
    }
    if (msg.has("image")) {
      double x = -1;
      double y = -1;
      JsonObject image = msg.getObject("image");
      x = image.getNumber("x");
      y = image.getNumber("y");
      if (x >= 0 || y >= 0) {
        setCenter(x, y);
      }
    }
  }

  private void initView() {
    mImageView = (ImageView) this.findViewById(R.id.actvity_picture);
    ImageView iv_common_back = (ImageView) findViewById(R.id.iv_common_back);
    mImageView.setOnTouchListener(this);
    // 返回键
    iv_common_back.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        JsonObject msg = Json.createObject();
        msg.set("return", true);
        bus.send(Bus.LOCAL + Constant.ADDR_CONTROL, msg, null);
      }
    });
    this.ll_act_picture_tools = (LinearLayout) this.findViewById(R.id.ll_act_picture_tools);
    mImageView.setOnHoverListener(new OnHoverListener() {
      @Override
      public boolean onHover(View v, MotionEvent event) {
        switch (event.getAction()) {
          case MotionEvent.ACTION_HOVER_MOVE:
            if (!toolBarHandler.hasMessages(0)) {
              ll_act_picture_tools.setVisibility(View.VISIBLE);
              delayDissmissToolsbar();
            }
            break;
        }
        return true;
      }
    });

  }

  /**
   * 将x,y表示的点移动到屏幕中心
   * 
   * @param x 点的横坐标值和图片宽度的比例
   * @param y 点的纵坐标值和图片高度的比例
   */
  private void setCenter(double x, double y) {
    RectF currentRect = getImageRect();
    float top = currentRect.top;
    float left = currentRect.left;
    float locX = (float) (x * currentRect.width());
    float locY = (float) (y * currentRect.height());
    float centerX = viewWidth / 2 - left;
    float centerY = viewHeight / 2 - top;
    setTranslate(centerX - locX, centerY - locY);
  }

  /**
   * 图片自适应缩放
   * 
   * @param fit 0:适配屏幕(图片在屏幕上完全显示),1:表示宽度完全显示,2:表示高度完全显示
   */
  private void setFit(int fit) {
    Log.d(TAG, "fit");
    float widthScale = viewWidth / drawableWidth;
    float heightScale = viewHeight / drawableHeight;
    if (fit == 0) {
      scale = Math.min(1.0f, Math.min(widthScale, heightScale));
    }
    if (fit == 1) {
      scale = widthScale;
    }
    if (fit == 2) {
      scale = heightScale;
    }
    baseMatrix.reset();
    baseMatrix.postScale(scale, scale);
    currentScale = scale;
    baseMatrix.postTranslate((viewWidth - drawableWidth * scale) / 2F, (viewHeight - drawableHeight
        * scale) / 2F);
    mImageView.setImageMatrix(baseMatrix);
    mImageView.invalidate();
  }

  /**
   * 初始化加载图片,图片太大时压缩
   * 
   * @param path 图片路径
   * @return 返回生成的bitmap
   */
  private Bitmap setImage(String path) {
    final BitmapFactory.Options options = new BitmapFactory.Options();
    // 只加载边界信息
    options.inJustDecodeBounds = true;
    BitmapFactory.decodeFile(path, options);
    // 计算压缩比率
    final int height = options.outHeight;
    final int width = options.outWidth;
    DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
    viewWidth = displayMetrics.widthPixels;
    viewHeight = displayMetrics.heightPixels;
    float scaleY = height / viewHeight;
    float scaleX = width / viewWidth;
    float scale = Math.max(scaleY, scaleX);
    options.inSampleSize = scale > 2 ? 2 : 1;
    // 真正加载图片
    options.inJustDecodeBounds = false;
    return BitmapFactory.decodeFile(path, options);
  }

  /**
   * 图片初始化,获得宽高,矩阵等参数
   * 
   * @param fit true进行适应屏幕初始化,false不适应屏幕
   */
  private void setInit(int fit) {
    mImageView.setScaleType(ScaleType.MATRIX);
    baseMatrix = mImageView.getImageMatrix();
    drawableWidth = mImageView.getDrawable().getIntrinsicWidth();
    drawableHeight = mImageView.getDrawable().getIntrinsicHeight();
    if (fit >= 0) {
      setFit(fit);
    }
  }

  /**
   * 图片缩放,以指定的中心点进行缩放
   * 
   * @param scale 缩放系数
   * @param focalX 缩放中心点x轴坐标
   * @param focalY 缩放中心点y轴坐标
   * @param animate 是否使用缩放动画
   */
  private void setScale(float scale, float focalX, float focalY, boolean animate) {
    baseMatrix.postScale(scale, scale, focalX, focalY);
    checkMatrixBounds(baseMatrix);
    mImageView.setImageMatrix(baseMatrix);
    mImageView.invalidate();
  }

  /**
   * 图片平移
   * 
   * @param dX x轴移动距离
   * @param dY y轴移动距离
   */
  private void setTranslate(float dX, float dY) {
    baseMatrix.postTranslate(dX, dY);
    checkMatrixBounds(baseMatrix);
    mImageView.setImageMatrix(baseMatrix);
    mImageView.invalidate();
  }

  /**
   * 以图片原始大小为基准缩放
   * 
   * @param zoom 缩放系数
   * @param animate 是否使用缩放动画
   */
  private void setZoomTo(float zoom, boolean animate) {
    RectF currentRect = getImageRect();
    // 计算当前图片和原图的比例,在此基础上乘以zoom系数缩放
    float scale = drawableWidth / currentRect.width();
    setScale(zoom * scale, mImageView.getWidth() / 2, mImageView.getHeight() / 2, false);
  }

}
