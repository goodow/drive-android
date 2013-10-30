package com.goodow.drive.android.fragment;

import com.goodow.android.drive.R;
import com.goodow.drive.android.Interface.ILocalFragment;
import com.goodow.drive.android.Interface.INotifyData;
import com.goodow.drive.android.Interface.IOnItemClickListener;
import com.goodow.drive.android.Interface.IRemoteControl;
import com.goodow.drive.android.activity.MainActivity;
import com.goodow.drive.android.adapter.CollaborativeAdapter;
import com.goodow.drive.android.adapter.DialogListViewAdapter;
import com.goodow.drive.android.global_data_cache.GlobalConstant;
import com.goodow.drive.android.global_data_cache.GlobalDataCacheForMemorySingleton;
import com.goodow.drive.android.toolutils.Tools;
import com.goodow.realtime.BaseModelEvent;
import com.goodow.realtime.CollaborativeList;
import com.goodow.realtime.CollaborativeMap;
import com.goodow.realtime.Document;
import com.goodow.realtime.DocumentLoadedHandler;
import com.goodow.realtime.EventHandler;
import com.goodow.realtime.Model;
import com.goodow.realtime.ModelInitializerHandler;
import com.goodow.realtime.ObjectChangedEvent;
import com.goodow.realtime.Realtime;

import android.app.AlertDialog;
import android.app.ListFragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import elemental.json.JsonArray;
import elemental.json.JsonObject;

public class LessonListFragment extends ListFragment implements ILocalFragment {
  private final String TAG = this.getClass().getSimpleName();

  private IRemoteControl path;
  private JsonArray currentPathList;
  private CollaborativeMap currentFolder;
  // 对话框中当前文件夹
  private CollaborativeMap dialogCurrentFolder;
  // 上一个文件夹
  private CollaborativeMap dialogLastFolder;

  private final IntentFilter intentFilter = new IntentFilter("DATA_CONTROL");

  // 监听当有文件被删除时,刷新适配器
  private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      adapter.notifyDataSetInvalidated();
    }
  };

  private CollaborativeAdapter adapter;
  private Document doc;

  private Model model;

  private CollaborativeMap root;
  // documentId
  private String DOCID;
  // folders
  private static final String FOLDER_KEY = GlobalConstant.DocumentIdAndDataKey.FOLDERSKEY
      .getValue();

  // files
  private static final String FILE_KEY = GlobalConstant.DocumentIdAndDataKey.FILESKEY.getValue();
  private EventHandler<?> listEventHandler;
  private EventHandler<ObjectChangedEvent> valuesChangeEventHandler;

  private INotifyData iNotifyData;

  private PopupWindow popupWindow;

  public LessonListFragment() {
    super();
  }

  @Override
  public void backFragment() {
    if (null != currentPathList && 1 < currentPathList.length()) {
      remoteHandle();
      path.changePath(null, DOCID);
    } else {
      if (null != getActivity()) {
        Toast.makeText(getActivity(), R.string.backFolderErro, Toast.LENGTH_SHORT).show();
      }
    }
  }

  @Override
  public void connectUi() {
    Log.i(TAG, "connectUi()");

    MainActivity activity = (MainActivity) getActivity();
    if (null != activity) {
      path = activity.getRemoteControlObserver();
    }

    if (null != path) {
      path.setNotifyData(iNotifyData);

      currentPathList = path.getCurrentPath();
      if (0 == currentPathList.length()) {
        path.changePath(root.getId(), DOCID);
        currentPathList = path.getCurrentPath();
      }

      showData();
    }
  }

  public void deleteFileorFolder(final CollaborativeMap clickItem) {
    CollaborativeList list;
    // 为空是文件夹
    if (clickItem.get("type") == null) {
      list = currentFolder.get(FOLDER_KEY);

    } else {
      list = currentFolder.get(FILE_KEY);
    }
    for (int i = 0; i < list.length(); i++) {
      CollaborativeMap map = list.get(i);
      // 文件blobkey
      if (clickItem.getId().equals(map.getId())) {
        list.remove(i);
      }
    }
  }

  public void findlastFolder(CollaborativeMap tmp, CollaborativeMap lastMap) {
    CollaborativeList list = lastMap.get(FOLDER_KEY);
    for (int i = 0; i < list.length(); i++) {
      CollaborativeMap map = list.get(i);
      CollaborativeList list1 = map.get(FOLDER_KEY);
      // 如果相等，那么返回值为值为上一层
      if ((tmp.getId()).equals(map.getId())) {
        dialogLastFolder = lastMap;
        break;
      } else if (list1.length() > 0) {
        // 遍历子文件夹
        findlastFolder(tmp, map);
      }
    }
  }

  public CollaborativeMap getDialogCurrentFolder() {
    return dialogCurrentFolder;
  }

  // 查找上一层文件夹
  public CollaborativeMap lastFolder(CollaborativeMap tmp) {
    CollaborativeMap lastMap = root;
    findlastFolder(tmp, lastMap);
    return dialogLastFolder;
  }

  @Override
  public void loadDocument() {
    DOCID =
        "@tmp/" + GlobalDataCacheForMemorySingleton.getInstance().getUserId() + "/"
            + GlobalConstant.DocumentIdAndDataKey.LESSONDOCID.getValue();
    Log.i(TAG, "loadDocument() DOCID: " + DOCID);

    // 文件Document
    DocumentLoadedHandler onLoaded = new DocumentLoadedHandler() {
      @Override
      public void onLoaded(Document document) {
        Log.i(TAG, "onLoaded()");

        doc = document;
        model = doc.getModel();
        root = model.getRoot();

        connectUi();
      }
    };

    // 初始化
    ModelInitializerHandler initializer = new ModelInitializerHandler() {
      @Override
      public void onInitializer(Model model_) {
        model = model_;
        root = model.getRoot();

        String[] mapKey = {"label", FILE_KEY, FOLDER_KEY};
        CollaborativeMap[] values = new CollaborativeMap[3];

        for (int k = 0; k < values.length; k++) {
          CollaborativeMap map = model.createMap(null);
          for (int i = 0; i < mapKey.length; i++) {
            if ("label".equals(mapKey[i])) {

              map.set(mapKey[i], "Lesson " + k);
            } else {
              CollaborativeList subList = model.createList();

              if (FOLDER_KEY.equals(mapKey[i])) {
                CollaborativeMap subMap = model.createMap(null);
                subMap.set("label", "SubFolder");
                subMap.set(FILE_KEY, model.createList());
                subMap.set(FOLDER_KEY, model.createList());
                subList.push(subMap);
              }

              map.set(mapKey[i], subList);
            }
          }

          values[k] = map;
        }

        CollaborativeList list = model_.createList();
        list.pushAll((Object[]) values);

        root.set(GlobalConstant.DocumentIdAndDataKey.FOLDERSKEY.getValue(), list);
        root.set(GlobalConstant.DocumentIdAndDataKey.FILESKEY.getValue(), model_.createList());
      }
    };

    Realtime.load(DOCID, onLoaded, initializer, null);
  }

  public void moveFileorFolder(final CollaborativeMap clickItem) {

    View popupWindow_view =
        View.inflate(getActivity().getWindow().getContext(), R.layout.dialog_move, null);
    // 创建PopupWindow实例
    popupWindow =
        new PopupWindow(popupWindow_view, getActivity().getWindowManager().getDefaultDisplay()
            .getWidth() * 5 / 12,
            getActivity().getWindowManager().getDefaultDisplay().getHeight() - 12, true);
    popupWindow_view.setFocusable(true);
    // 设置允许在外点击消失
    popupWindow.setOutsideTouchable(true);
    // 点击返回键是，不改变背景
    popupWindow.setBackgroundDrawable(new BitmapDrawable());

    // 获得布局中的控件
    // 上一层
    final LinearLayout lastLayout = (LinearLayout) popupWindow_view.findViewById(R.id.last_layout);
    // 创建文件夹
    final ImageView createFile = (ImageView) popupWindow_view.findViewById(R.id.img_createfolder);
    // 显示文件夹名称
    final TextView tv_test = (TextView) popupWindow_view.findViewById(R.id.tv_test);
    // listView
    final ListView listView = (ListView) popupWindow_view.findViewById(R.id.dialog_listView);
    // 取消
    final Button bt_cancle = (Button) popupWindow_view.findViewById(R.id.bt_cancle);
    // 确定
    final Button bt_ok = (Button) popupWindow_view.findViewById(R.id.bt_ok);
    // 对话框的适配器
    final DialogListViewAdapter dialogAdapter =
        new DialogListViewAdapter(getActivity(), null, null);
    // 设置当前的文件夹
    setDialogCurrentFolder(currentFolder);
    dialogAdapter.setFileList((CollaborativeList) getDialogCurrentFolder().get(FILE_KEY));
    dialogAdapter.setFolderList((CollaborativeList) getDialogCurrentFolder().get(FOLDER_KEY));
    tv_test.setText(getDialogCurrentFolder().get("label").toString());
    listView.setAdapter(dialogAdapter);
    listView.setOnItemClickListener(new OnItemClickListener() {
      @Override
      public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        // 点击时，可见
        if (lastLayout.getVisibility() == View.GONE) {
          lastLayout.setVisibility(View.VISIBLE);
        }
        CollaborativeMap dialogClickItem = (CollaborativeMap) view.getTag();

        // 只有当要点击的文件夹和当前的文件路径不同时
        if (dialogClickItem != clickItem) {
          setDialogCurrentFolder(dialogClickItem);
          dialogAdapter.setFileList((CollaborativeList) getDialogCurrentFolder().get(FILE_KEY));
          dialogAdapter.setFolderList((CollaborativeList) getDialogCurrentFolder().get(FOLDER_KEY));
          tv_test.setText(getDialogCurrentFolder().get("label").toString());
          dialogAdapter.notifyDataSetChanged();
        }
      }
    });
    createFile.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        View view =
            View.inflate(getActivity().getWindow().getContext(), R.layout.fragment_rename, null);
        final EditText editText = (EditText) view.findViewById(R.id.editText_rename);
        editText.setText("新建文件夹");
        editText.selectAll();
        new AlertDialog.Builder(getActivity().getWindow().getContext()).setTitle("文件夹名称").setView(
            view).setPositiveButton("确定", new OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            // 当目录下文件夹
            CollaborativeList currentList = getDialogCurrentFolder().get(FOLDER_KEY);
            // 创建新的文件夹
            CollaborativeMap newMap = model.createMap(null);
            newMap.set("label", editText.getText().toString());
            newMap.set(FILE_KEY, model.createList());
            newMap.set(FOLDER_KEY, model.createList());
            currentList.push(newMap);
            tv_test.setText(getDialogCurrentFolder().get("label").toString());
            dialogAdapter.notifyDataSetChanged();
          }
        }).setNegativeButton("取消", null).show();
      }
    });
    if (getDialogCurrentFolder() == root) {
      lastLayout.setVisibility(View.GONE);
    }

    // 回到上一层目录
    lastLayout.setOnClickListener(new View.OnClickListener() {

      @Override
      public void onClick(View v) {
        CollaborativeMap lastFolder = lastFolder(getDialogCurrentFolder());
        setDialogCurrentFolder(lastFolder);
        dialogAdapter.setFileList((CollaborativeList) getDialogCurrentFolder().get(FILE_KEY));
        dialogAdapter.setFolderList((CollaborativeList) getDialogCurrentFolder().get(FOLDER_KEY));
        dialogAdapter.notifyDataSetChanged();
        // 到最顶层是，不可见
        if (lastFolder == root) {
          lastLayout.setVisibility(View.GONE);
        }
        tv_test.setText(getDialogCurrentFolder().get("label").toString());
      }
    });
    bt_ok.setOnClickListener(new View.OnClickListener() {

      @Override
      public void onClick(View v) {
        // 移除
        deleteFileorFolder(clickItem);
        CollaborativeList currentList = null;
        // 添加
        if (clickItem.get("type") == null) {
          currentList = getDialogCurrentFolder().get(FOLDER_KEY);
          currentList.push(clickItem);
          Toast.makeText(
              getActivity().getWindow().getContext(),
              "文件夹  \"" + clickItem.get("label") + "\" 已从 \"" + currentFolder.get("label")
                  + "\" 移到 \"" + getDialogCurrentFolder().get("label") + "\"", 1).show();
        } else {
          currentList = getDialogCurrentFolder().get(FILE_KEY);
          currentList.push(clickItem);
          Toast.makeText(
              getActivity().getWindow().getContext(),
              "文件  \"" + clickItem.get("label") + "\" 已从 \"" + currentFolder.get("label")
                  + "\" 移到 \"" + getDialogCurrentFolder().get("label") + "\"", 1).show();
        }
        popupWindow.dismiss();
      }
    });
    bt_cancle.setOnClickListener(new View.OnClickListener() {

      @Override
      public void onClick(View v) {
        popupWindow.dismiss();
      }
    });
    popupWindow.showAtLocation(getView(), Gravity.CENTER, 0, 0);
  }

  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    Log.i(TAG, "onActivityCreated()");

    MainActivity activity = (MainActivity) getActivity();

    activity.setActionBarTitle("我的课程");
    TextView textView = (TextView) activity.findViewById(R.id.openfailure_text);
    // ImageView imageView = (ImageView) activity.findViewById(R.id.openfailure_img);
    // activity.setOpenStateView(textView, imageView);
    activity.setOpenStateView(textView, null);
  }

  // 创建fragment
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Log.i(TAG, "onCreate()");
    final MainActivity activity = (MainActivity) LessonListFragment.this.getActivity();
    RelativeLayout relativeLayout = (RelativeLayout) getActivity().findViewById(R.id.mainConnect);
    relativeLayout.setVisibility(View.VISIBLE);
    // 指示这个Fragment应该作为可选菜单的添加项
    setHasOptionsMenu(true);
    adapter = new CollaborativeAdapter(this.getActivity(), null, null, new IOnItemClickListener() {
      @Override
      public void onItemClick(CollaborativeMap file) {
        DataDetailFragment dataDetailFragment = activity.getDataDetailFragment();
        dataDetailFragment.setFile(file);
        dataDetailFragment.initView();

        activity.setDataDetailLayoutState(View.VISIBLE);
        activity.setLocalFragmentForDetail(dataDetailFragment);
      }
    });
    setListAdapter(adapter);

    initEventHandler();
  }

  @Override
  public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
    super.onCreateOptionsMenu(menu, inflater);
    MenuItem addFolder = menu.add(0, 1, 0, R.string.add_folder);
    addFolder.setIcon(R.drawable.ds_plussign_holo_light);
    addFolder.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
  }

  // 当fragment被初始化 要显示他的内容的时候调用。
  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_folderlist, container, false);
  }

  // ListView中的item点击事件
  @Override
  public void onListItemClick(ListView l, View v, int position, long id) {
    CollaborativeMap clickItem = (CollaborativeMap) v.getTag();
    String type = clickItem.get("type");
    // 类型不为空,且不是手偶图片
    if (null != type && !type.equals(Tools.MIME_TYPE_Table.RES_PRINT.getMimeType())) {
      path.playFile(clickItem);
    } else {
      String blobKey = clickItem.get("blobKey");
      if (blobKey == null) {
        remoteHandle();
        // 进入文件夹
        path.changePath(clickItem.getId(), DOCID);
      }
    }
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == 1) {
      View view =
          View.inflate(getActivity().getWindow().getContext(), R.layout.fragment_rename, null);
      final EditText editText = (EditText) view.findViewById(R.id.editText_rename);
      editText.setText("新建文件夹");
      editText.selectAll();
      new AlertDialog.Builder(getActivity().getWindow().getContext()).setTitle("文件夹名称").setView(
          view).setPositiveButton("确定", new OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
          // 当目录下文件夹
          CollaborativeList currentList = currentFolder.get(FOLDER_KEY);
          // 创建新的文件夹
          CollaborativeMap newMap = model.createMap(null);
          newMap.set("label", editText.getText().toString());
          newMap.set(FILE_KEY, model.createList());
          newMap.set(FOLDER_KEY, model.createList());
          currentList.push(newMap);
          adapter.notifyDataSetChanged();
        }
      }).setNegativeButton("取消", null).show();
    }
    return super.onOptionsItemSelected(item);
  }

  // 失去焦点
  @Override
  public void onPause() {
    super.onPause();

    MainActivity activity = (MainActivity) getActivity();
    activity.restActionBarTitle();

    if (null != broadcastReceiver) {
      activity.unregisterReceiver(broadcastReceiver);
    }
    remoteHandle();
  }

  // 获得焦点
  @Override
  public void onResume() {
    super.onResume();
    MainActivity activity = (MainActivity) getActivity();

    activity.setLocalFragment(this);
    activity.setLastiRemoteDataFragment(this);
    activity.registerReceiver(broadcastReceiver, intentFilter);

    loadDocument();
  }

  @Override
  public void onStart() {
    super.onStart();
    // 长按一个条目时，弹出移动，重命名，删除按钮
    getListView().setOnItemLongClickListener(new OnItemLongClickListener() {
      // final String[] itemStrings = new String[] {"移至...", "重命名", "删除"};

      @Override
      public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        final CollaborativeMap clickItem = (CollaborativeMap) view.getTag();
        // String type = clickItem.get("type");
        // String blobKey = clickItem.get("blobKey");
        // 长按后，弹出对话框
        new AlertDialog.Builder(getActivity().getWindow().getContext()).setTitle(
            (String) clickItem.get("label")).setItems(new String[] {"移至...", "重命名", "删除"},
            new OnClickListener() {
              private String name;

              @Override
              public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                // 移动
                  case 0:
                    moveFileorFolder(clickItem);

                    break;
                  // 重命名
                  case 1:
                    View view =
                        View.inflate(getActivity().getWindow().getContext(),
                            R.layout.fragment_rename, null);
                    final EditText editText = (EditText) view.findViewById(R.id.editText_rename);
                    editText.setText((String) clickItem.get("label"));
                    editText.selectAll();
                    if (clickItem.get("type") == null) {
                      name = "文件夹重命名";
                    } else {
                      name = "文件重命名";
                    }
                    new AlertDialog.Builder(getActivity().getWindow().getContext()).setTitle(name)
                        .setView(view).setPositiveButton("确定", new OnClickListener() {
                          @Override
                          public void onClick(DialogInterface dialog, int which) {
                            // 重命名
                            clickItem.set("label", editText.getText().toString());
                          }
                        }).setNegativeButton("取消", null).show();
                    break;
                  // 删除
                  case 2:
                    deleteFileorFolder(clickItem);
                    break;
                }

              }

            }).show();
        return true;
      }
    });
  }

  public void setDialogCurrentFolder(CollaborativeMap dialogCurrentFolder) {
    this.dialogCurrentFolder = dialogCurrentFolder;
  }

  public void showData() {
    if (null != getActivity()) {
      RelativeLayout relativeLayout = (RelativeLayout) getActivity().findViewById(R.id.mainConnect);
      relativeLayout.setVisibility(View.GONE);
    }

    currentFolder = model.getObject(currentPathList.get(currentPathList.length() - 1).asString());

    if (null != currentFolder) {
      currentFolder.addObjectChangedListener(valuesChangeEventHandler);
      CollaborativeList folderList = (CollaborativeList) currentFolder.get(FOLDER_KEY);
      CollaborativeList fileList = (CollaborativeList) currentFolder.get(FILE_KEY);

      adapter.setFolderList(folderList);
      adapter.setFileList(fileList);
      adapter.notifyDataSetChanged();

      // 设置action bar的显示
      MainActivity activity = (MainActivity) getActivity();
      if (null != activity) {
        activity.setActionBarContent(currentPathList, model, DOCID);
      }
    }

    openState();
  }

  private void initEventHandler() {
    if (listEventHandler == null) {
      listEventHandler = new EventHandler<BaseModelEvent>() {
        @Override
        public void handleEvent(BaseModelEvent event) {
          adapter.notifyDataSetChanged();
          openState();
        }
      };
    }

    if (valuesChangeEventHandler == null) {
      valuesChangeEventHandler = new EventHandler<ObjectChangedEvent>() {
        @Override
        public void handleEvent(ObjectChangedEvent event) {
          adapter.notifyDataSetChanged();

          openState();
        }
      };
    }

    if (iNotifyData == null) {
      iNotifyData = new INotifyData() {
        @Override
        public void notifyData(JsonObject newJson) {
          Log.i(TAG, "notifyData()");

          currentPathList =
              newJson.get(GlobalConstant.DocumentIdAndDataKey.CURRENTPATHKEY.getValue());

          if (null != currentPathList && 0 != currentPathList.length()) {
            CollaborativeMap map =
                model.getObject(currentPathList.get(currentPathList.length() - 1).asString());
            if (null != map) {
              showData();
            } else {
              if (null != getActivity()) {
                // Toast.makeText(getActivity(), R.string.remoteControlError,
                // Toast.LENGTH_SHORT).show();
              }
            }
          }
        }
      };
    }
  }

  private void openState() {
    if (null != currentFolder) {
      // 文件夹的list
      CollaborativeList folderList = currentFolder.get(FOLDER_KEY);
      // 文件的list
      CollaborativeList fileList = currentFolder.get(FILE_KEY);

      MainActivity activity = (MainActivity) getActivity();
      if (null != activity) {
        if (null != folderList && 0 == folderList.length() && null != fileList
            && 0 == fileList.length()) {
          activity.openState(LinearLayout.VISIBLE);
        } else {
          activity.openState(LinearLayout.INVISIBLE);
        }
      }
    }
  }

  // 删除监听
  private void remoteHandle() {
    if (null != currentPathList) {
      String mapId = currentPathList.get(currentPathList.length() - 1).asString();
      CollaborativeMap currentmap = model.getObject(mapId);
      if (null != currentmap) {
        currentmap.removeObjectChangedListener(valuesChangeEventHandler);
      }
    }
  }
}
