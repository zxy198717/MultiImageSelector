package me.nereo.multi_image_selector;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.FileProvider;
import android.support.v4.content.Loader;
import android.support.v7.widget.ListPopupWindow;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import me.nereo.multi_image_selector.adapter.FolderAdapter;
import me.nereo.multi_image_selector.adapter.ImageGridAdapter;
import me.nereo.multi_image_selector.bean.Folder;
import me.nereo.multi_image_selector.bean.Image;
import me.nereo.multi_image_selector.utils.FileUtils;
import me.nereo.multi_image_selector.utils.ScreenUtils;

import static android.app.Activity.RESULT_OK;
import static me.nereo.multi_image_selector.MultiImageSelectorActivity.EXTRA_RESULT;

/**
 * 图片选择Fragment
 * Created by Nereo on 2015/4/7.
 */
public class MultiImageSelectorFragment extends Fragment {

    public static final String TAG = "me.nereo.multi_image_selector.MultiImageSelectorFragment";

    private static final String KEY_TEMP_FILE = "key_temp_file";

    /**
     * 最大图片选择次数，int类型
     */
    public static final String EXTRA_SELECT_COUNT = "max_select_count";
    /**
     * 图片选择模式，int类型
     */
    public static final String EXTRA_SELECT_MODE = "select_count_mode";
    /**
     * 是否显示相机，boolean类型
     */
    public static final String EXTRA_SHOW_CAMERA = "show_camera";

    /**
     * 可选择视频时长
     */
    public static final String EXTRA_VIDEO_DURATION = "video_duration";

    /**
     * 选择模式，int类型
     */
    public static final String EXTRA_PICKER_MODE = "select_picker_mode";

    /**
     * 全部
     */
    public static final int MODE_ALL = 0;
    /**
     * 图片
     */
    public static final int MODE_IMAGE = 1;
    /**
     * 视频
     */
    public static final int MODE_VIDEO = 2;
    /**
     * 默认选择的数据集
     */
    public static final String EXTRA_DEFAULT_SELECTED_LIST = "default_result";
    /**
     * 单选
     */
    public static final int MODE_SINGLE = 0;
    /**
     * 多选
     */
    public static final int MODE_MULTI = 1;
    // 不同loader定义
    private static final int LOADER_ALL = 0;
    private static final int LOADER_CATEGORY = 1;
    // 请求加载系统照相机
    private static final int REQUEST_CAMERA = 100;
    // 请求加载系统照相机录制视频
    private static final int REQUEST_RECORDING = 101;

    private static final int REQUEST_PREVIEW = 104;

    private static final int WRITE_EXTERNAL_STORAGE_REQUEST_CODE = 1001;

    // 结果数据
    private ArrayList<String> resultList = new ArrayList<>();
    // 文件夹数据
    private ArrayList<Folder> mResultFolder = new ArrayList<>();

    // 图片Grid
    private GridView mGridView;
    private Callback mCallback;

    private ImageGridAdapter mImageAdapter;
    private FolderAdapter mFolderAdapter;

    private ListPopupWindow mFolderPopupWindow;

    // 类别
    private TextView mCategoryText;
    // 预览按钮
    private Button mPreviewBtn;
    // 底部View
    private View mPopupAnchorView;

    private int mDesireImageCount;

    private boolean hasFolderGened = false;
    private boolean mIsShowCamera = false;
    private int pickerMode = MODE_IMAGE;

    private File mTmpFile;

    private int maxDuration = 0;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        try {
            mCallback = (Callback) context;
        } catch (ClassCastException e) {
            throw new ClassCastException("The Activity must implement MultiImageSelectorFragment.Callback interface...");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_multi_image, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 选择图片数量
        mDesireImageCount = getArguments().getInt(EXTRA_SELECT_COUNT);
        maxDuration = getArguments().getInt(EXTRA_VIDEO_DURATION);
        // 图片选择模式
        final int mode = getArguments().getInt(EXTRA_SELECT_MODE);

        // 默认选择
        if (mode == MODE_MULTI) {
            ArrayList<String> tmp = getArguments().getStringArrayList(EXTRA_DEFAULT_SELECTED_LIST);
            if (tmp != null && tmp.size() > 0) {
                resultList = tmp;
            }
        }

        // 是否显示照相机
        mIsShowCamera = getArguments().getBoolean(EXTRA_SHOW_CAMERA, true);
        // 是否显示视频
        pickerMode = getArguments().getInt(EXTRA_PICKER_MODE, 0);
        mImageAdapter = new ImageGridAdapter(getActivity(), mIsShowCamera, 3);
        // 是否显示选择指示器
        mImageAdapter.showSelectIndicator(mode == MODE_MULTI);
        mImageAdapter.setRecordVideo(pickerMode == MODE_VIDEO, maxDuration);
        mPopupAnchorView = view.findViewById(R.id.footer);

        mCategoryText = (TextView) view.findViewById(R.id.category_btn);
        // 初始化，加载所有图片
        mCategoryText.setText(pickerMode == MODE_ALL ? R.string.pictures_and_videos : (pickerMode == MODE_IMAGE ? R.string.folder_all : R.string.folder_all_videos));
        mCategoryText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (mFolderPopupWindow == null) {
                    createPopupFolderList();
                }

                if (mFolderPopupWindow.isShowing()) {
                    mFolderPopupWindow.dismiss();
                } else {
                    mFolderPopupWindow.show();
                    int index = mFolderAdapter.getSelectIndex();
                    index = index == 0 ? index : index - 1;
                    mFolderPopupWindow.getListView().setSelection(index);
                }
            }
        });

        mPreviewBtn = (Button) view.findViewById(R.id.preview);
        // 初始化，按钮状态初始化
        if (resultList == null || resultList.size() <= 0) {
            mPreviewBtn.setText(R.string.preview);
            mPreviewBtn.setEnabled(false);
        } else {
            mPreviewBtn.setText(getResources().getString(R.string.preview) + "(" + resultList.size() + ")");
            mPreviewBtn.setEnabled(true);
        }
        mPreviewBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getContext(), PreviewActivity.class);
                PreviewActivity.images = (ArrayList<Image>) mImageAdapter.getSelectedImages();
                PreviewActivity.selectImages = (ArrayList<Image>) mImageAdapter.getSelectedImages();
                PreviewActivity.position = 0;
                PreviewActivity.maxCount = mDesireImageCount;
                PreviewActivity.maxDuration = maxDuration;
                PreviewActivity.pickerMode = pickerMode;
                startActivityForResult(intent, REQUEST_PREVIEW);
            }
        });

        mGridView = (GridView) view.findViewById(R.id.grid);
        mGridView.setAdapter(mImageAdapter);
        mGridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                if (mImageAdapter.isShowCamera()) {
                    // 如果显示照相机，则第一个Grid显示为照相机，处理特殊逻辑
                    if (i == 0) {
                        showCameraAction();
                    } else {
                        if (mode == MODE_SINGLE) {
                            Image image = (Image) mImageAdapter.getItem(i);
                            if (image.isVideo && maxDuration > 0 && (int) (image.duration / 1000) > maxDuration) {
                                Toast.makeText(getActivity(), getString(R.string.msg_duration_limit, maxDuration), Toast.LENGTH_SHORT).show();
                                return;
                            }
                            // 单选模式
                            if (mCallback != null) {
                                mCallback.onSingleImageSelected(image.path);
                            }
                            return;
                        }
                        Intent intent = new Intent(getContext(), PreviewActivity.class);
                        PreviewActivity.images = (ArrayList<Image>) mImageAdapter.getImages();
                        PreviewActivity.selectImages = (ArrayList<Image>) mImageAdapter.getSelectedImages();
                        PreviewActivity.position = i - 1;
                        PreviewActivity.maxCount = mDesireImageCount;
                        PreviewActivity.maxDuration = maxDuration;
                        PreviewActivity.pickerMode = pickerMode;
                        startActivityForResult(intent, REQUEST_PREVIEW);
                    }
                } else {
                    if (mode == MODE_SINGLE) {
                        Image image = (Image) mImageAdapter.getItem(i);
                        if (image.isVideo && maxDuration > 0 && (int) (image.duration / 1000) > maxDuration) {
                            Toast.makeText(getActivity(), getString(R.string.msg_duration_limit, maxDuration), Toast.LENGTH_SHORT).show();
                            return;
                        }
                        // 单选模式
                        if (mCallback != null) {
                            mCallback.onSingleImageSelected(image.path);
                        }
                        return;
                    }
                    Intent intent = new Intent(getContext(), PreviewActivity.class);
                    PreviewActivity.images = (ArrayList<Image>) mImageAdapter.getImages();
                    PreviewActivity.selectImages = (ArrayList<Image>) mImageAdapter.getSelectedImages();
                    PreviewActivity.position = i;
                    PreviewActivity.maxCount = mDesireImageCount;
                    PreviewActivity.maxDuration = maxDuration;
                    PreviewActivity.pickerMode = pickerMode;
                    startActivityForResult(intent, REQUEST_PREVIEW);
                }
            }
        });

        mImageAdapter.setOnItemCheckboxClickListener(new ImageGridAdapter.OnItemCheckboxClickListener() {
            @Override
            public void onClick(int position) {
                Image image = (Image) mImageAdapter.getItem(position);
                selectImageFromGrid(image, mode);
            }
        });

        mFolderAdapter = new FolderAdapter(getActivity());
        mFolderAdapter.setPickerMode(pickerMode);

        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(getContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, WRITE_EXTERNAL_STORAGE_REQUEST_CODE);
        } else {
            getActivity().getSupportLoaderManager().initLoader(LOADER_ALL, null, mLoaderCallback);
        }
    }

    /**
     * 创建弹出的ListView
     */
    private void createPopupFolderList() {
        Point point = ScreenUtils.getScreenSize(getActivity());
        int width = point.x;
        int height = (int) (point.y * (4.5f / 8.0f));
        mFolderPopupWindow = new ListPopupWindow(getActivity());
        mFolderPopupWindow.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
        mFolderPopupWindow.setAdapter(mFolderAdapter);
        mFolderPopupWindow.setContentWidth(width);
        mFolderPopupWindow.setWidth(width);
        mFolderPopupWindow.setHeight(height);
        mFolderPopupWindow.setAnchorView(mPopupAnchorView);
        mFolderPopupWindow.setModal(true);
        mFolderPopupWindow.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {

                mFolderAdapter.setSelectIndex(i);

                final int index = i;
                final AdapterView v = adapterView;

                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mFolderPopupWindow.dismiss();

                        if (index == 0) {
                            getActivity().getSupportLoaderManager().restartLoader(LOADER_ALL, null, mLoaderCallback);
                            mCategoryText.setText(pickerMode == MODE_ALL ? R.string.pictures_and_videos : (pickerMode == MODE_IMAGE ? R.string.folder_all : R.string.folder_all_videos));
                            if (mIsShowCamera) {
                                mImageAdapter.setShowCamera(true);
                            } else {
                                mImageAdapter.setShowCamera(false);
                            }
                        } else {
                            Folder folder = (Folder) v.getAdapter().getItem(index);
                            if (null != folder) {
                                mImageAdapter.setData(folder.images);
                                mCategoryText.setText(folder.name);
                                // 设定默认选择
                                if (resultList != null && resultList.size() > 0) {
                                    mImageAdapter.setDefaultSelected(resultList);
                                }
                            }
                            mImageAdapter.setShowCamera(false);
                        }

                        // 滑动到最初始位置
                        mGridView.smoothScrollToPosition(0);
                    }
                }, 100);

            }
        });
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(KEY_TEMP_FILE, mTmpFile);
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        if (savedInstanceState != null) {
            mTmpFile = (File) savedInstanceState.getSerializable(KEY_TEMP_FILE);
        }
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // 相机拍照完成后，返回图片路径
        if (requestCode == REQUEST_CAMERA) {
            if (resultCode == RESULT_OK) {
                if (mTmpFile != null) {
                    if (mCallback != null) {
                        mCallback.onCameraShot(mTmpFile);
                    }
                }
            } else {
                while (mTmpFile != null && mTmpFile.exists()) {
                    boolean success = mTmpFile.delete();
                    if (success) {
                        mTmpFile = null;
                    }
                }
            }
        } else if (REQUEST_RECORDING == requestCode) {
            if (resultCode == RESULT_OK) {
                if (mTmpFile != null) {
                    if (maxDuration > 0) {
                        MediaPlayer mediaPlayer = new MediaPlayer();
                        try {
                            mediaPlayer.setDataSource(mTmpFile.getPath());
                            mediaPlayer.prepare();
                            int duration = mediaPlayer.getDuration();
                            if ((int) duration / 1000 > maxDuration) {
                                Toast.makeText(getActivity(), getString(R.string.msg_duration_limit, maxDuration), Toast.LENGTH_SHORT).show();
                                return;
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        } finally {
                            mediaPlayer.release();
                        }

                    }

                    if (mCallback != null) {
                        mCallback.onCameraShot(mTmpFile);
                    }
                }
            } else {
                while (mTmpFile != null && mTmpFile.exists()) {
                    boolean success = mTmpFile.delete();
                    if (success) {
                        mTmpFile = null;
                    }
                }
            }
        } else if (REQUEST_PREVIEW == requestCode) {

            mImageAdapter.notifyDataSetChanged();
            if (mCallback != null) {
                resultList.clear();
                ArrayList<String> paths = new ArrayList<>();
                for (Image image : mImageAdapter.getSelectedImages()) {
                    paths.add(image.path);
                    resultList.add(image.path);
                }
                mCallback.onMultiImageSelected(paths);
            }

            if (resultCode == RESULT_OK) {
                if (mCallback != null) {
                    mCallback.onDoneClick();
                }
            } else {
                if (mImageAdapter.getSelectedImages().size() != 0) {
                    mPreviewBtn.setEnabled(true);
                    mPreviewBtn.setText(getResources().getString(R.string.preview) + "(" + mImageAdapter.getSelectedImages().size() + ")");
                } else {
                    mPreviewBtn.setEnabled(false);
                    mPreviewBtn.setText(R.string.preview);
                }
            }
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        if (mFolderPopupWindow != null) {
            if (mFolderPopupWindow.isShowing()) {
                mFolderPopupWindow.dismiss();
            }
        }
        super.onConfigurationChanged(newConfig);
    }

    /**
     * 选择相机
     */
    private void showCameraAction() {
        if (pickerMode == MODE_VIDEO) {
            Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
            if (maxDuration > 0) {
                intent.putExtra(android.provider.MediaStore.EXTRA_DURATION_LIMIT, maxDuration);
            }

            if (intent.resolveActivity(getActivity().getPackageManager()) != null) {
                try {
                    mTmpFile = FileUtils.createVideoTmpFile(getActivity());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (mTmpFile != null && mTmpFile.exists()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        ContentValues contentValues = new ContentValues(1);
                        contentValues.put(MediaStore.Images.Media.DATA, mTmpFile.getAbsolutePath());
                        Uri uri = getContext().getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
                        intent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
                    } else {
                        intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(mTmpFile));
                    }

                    startActivityForResult(intent, REQUEST_RECORDING);
                } else {
                    Toast.makeText(getActivity(), "新建文件错误", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(getActivity(), R.string.msg_no_camera, Toast.LENGTH_SHORT).show();
            }
            return;
        }

        // 跳转到系统照相机
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(getActivity().getPackageManager()) != null) {
            // 设置系统相机拍照后的输出路径
            // 创建临时文件
            try {
                mTmpFile = FileUtils.createTmpFile(getActivity());
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (mTmpFile != null && mTmpFile.exists()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    ContentValues contentValues = new ContentValues(1);
                    contentValues.put(MediaStore.Images.Media.DATA, mTmpFile.getAbsolutePath());
                    Uri uri = getContext().getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
                    cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
                } else {
                    cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(mTmpFile));
                }
                startActivityForResult(cameraIntent, REQUEST_CAMERA);
            } else {
                Toast.makeText(getActivity(), "图片错误", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(getActivity(), R.string.msg_no_camera, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 选择图片操作
     *
     * @param image
     */
    private void selectImageFromGrid(Image image, int mode) {
        if (image.isVideo && maxDuration > 0 && (int) (image.duration / 1000) > maxDuration) {
            Toast.makeText(getActivity(), getString(R.string.msg_duration_limit, maxDuration), Toast.LENGTH_SHORT).show();
            return;
        }
        if (image != null) {
            // 多选模式
            if (mode == MODE_MULTI) {
                if (resultList.contains(image.path)) {
                    resultList.remove(image.path);
                    if (resultList.size() != 0) {
                        mPreviewBtn.setEnabled(true);
                        mPreviewBtn.setText(getResources().getString(R.string.preview) + "(" + resultList.size() + ")");
                    } else {
                        mPreviewBtn.setEnabled(false);
                        mPreviewBtn.setText(R.string.preview);
                    }
                    if (mCallback != null) {
                        mCallback.onImageUnselected(image.path);
                    }
                } else {
                    // 判断选择数量问题
                    if (mDesireImageCount == resultList.size()) {
                        int resId = R.string.msg_amount_limit;
                        if (pickerMode == MODE_IMAGE) {
                            resId = R.string.msg_image_amount_limit;
                        } else if (pickerMode == MODE_VIDEO) {
                            resId = R.string.msg_video_amount_limit;
                        }
                        Toast.makeText(getActivity(), getString(resId, mDesireImageCount), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    resultList.add(image.path);
                    mPreviewBtn.setEnabled(true);
                    mPreviewBtn.setText(getResources().getString(R.string.preview) + "(" + resultList.size() + ")");
                    if (mCallback != null) {
                        mCallback.onImageSelected(image.path);
                    }
                }
                mImageAdapter.select(image);
            } else if (mode == MODE_SINGLE) {
                // 单选模式
                if (mCallback != null) {
                    mCallback.onSingleImageSelected(image.path);
                }
            }
        }
    }

    private LoaderManager.LoaderCallbacks<Cursor> mLoaderCallback = new LoaderManager.LoaderCallbacks<Cursor>() {
        /*
        private final String[] IMAGE_PROJECTION = {
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.MIME_TYPE,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media._ID };
        */
        private final String[] IMAGE_PROJECTION = {
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.DATE_ADDED,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Images.Media._ID,
                MediaStore.Files.FileColumns.MEDIA_TYPE,
                MediaStore.Video.VideoColumns.DURATION,
        };

        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle args) {
            if (id == LOADER_ALL) {
                // Return only video and image metadata.
                String selection = "";
                if (pickerMode == MODE_ALL || pickerMode == MODE_IMAGE) {
                    selection = MediaStore.Files.FileColumns.MEDIA_TYPE + "="
                            + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE;
                    if (pickerMode == MODE_ALL) {
                        selection += " OR "
                                + MediaStore.Files.FileColumns.MEDIA_TYPE + "="
                                + MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;
                    }
                } else {
                    selection = MediaStore.Files.FileColumns.MEDIA_TYPE + "="
                            + MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;
                }

                Uri queryUri = MediaStore.Files.getContentUri("external");

                CursorLoader cursorLoader = new CursorLoader(
                        getActivity(),
                        queryUri,
                        IMAGE_PROJECTION,
                        selection,
                        null, // Selection args (none).
                        MediaStore.Files.FileColumns.DATE_ADDED + " DESC" // Sort order.
                );
                return cursorLoader;
            } else if (id == LOADER_CATEGORY) {
                CursorLoader cursorLoader = new CursorLoader(getActivity(),
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, IMAGE_PROJECTION,
                        IMAGE_PROJECTION[4] + ">0 AND " + IMAGE_PROJECTION[0] + " like '%" + args.getString("path") + "%'",
                        null, IMAGE_PROJECTION[2] + " DESC");
                return cursorLoader;
            }

            return null;
        }

        private boolean fileExist(String path) {
            if (!TextUtils.isEmpty(path)) {
                return new File(path).exists();
            }
            return false;
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            if (data != null) {
                if (data.getCount() > 0) {
                    List<Image> images = new ArrayList<>();
                    data.moveToFirst();
                    do {
                        String path = data.getString(data.getColumnIndexOrThrow(IMAGE_PROJECTION[0]));
                        String name = data.getString(data.getColumnIndexOrThrow(IMAGE_PROJECTION[1]));
                        long dateTime = data.getLong(data.getColumnIndexOrThrow(IMAGE_PROJECTION[2]));
                        int mediaType = data.getInt(data.getColumnIndexOrThrow(IMAGE_PROJECTION[6]));
                        int duration = data.getInt(data.getColumnIndexOrThrow(IMAGE_PROJECTION[7]));
                        Image image = null;
                        if (fileExist(path)) {
                            image = new Image(path, name, dateTime);
                            image.setVideo(mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO, duration);
                            images.add(image);
                        } else {
                            continue;
                        }
                        if (!hasFolderGened) {
                            // 获取文件夹名称
                            File folderFile = new File(path).getParentFile();
                            if (folderFile != null && folderFile.exists()) {
                                String fp = folderFile.getAbsolutePath();
                                Folder f = getFolderByPath(fp);
                                if (f == null) {
                                    Folder folder = new Folder();
                                    folder.name = folderFile.getName();
                                    folder.path = fp;
                                    folder.cover = image;
                                    List<Image> imageList = new ArrayList<>();
                                    imageList.add(image);
                                    folder.images = imageList;
                                    mResultFolder.add(folder);
                                } else {
                                    f.images.add(image);
                                }
                            }
                        }

                    } while (data.moveToNext());

                    mImageAdapter.setData(images);
                    // 设定默认选择
                    if (resultList != null && resultList.size() > 0) {
                        mImageAdapter.setDefaultSelected(resultList);
                    }

                    if (!hasFolderGened) {
                        mFolderAdapter.setData(mResultFolder);
                        hasFolderGened = true;
                    }

                }
            }
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {

        }
    };

    private Folder getFolderByPath(String path) {
        if (mResultFolder != null) {
            for (Folder folder : mResultFolder) {
                if (TextUtils.equals(folder.path, path)) {
                    return folder;
                }
            }
        }
        return null;
    }

    /**
     * 回调接口
     */
    public interface Callback {
        void onSingleImageSelected(String path);

        void onImageSelected(String path);

        void onImageUnselected(String path);

        void onCameraShot(File imageFile);

        void onMultiImageSelected(List<String> paths);

        void onDoneClick();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == WRITE_EXTERNAL_STORAGE_REQUEST_CODE) {
            if (grantResults.length == 2 && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                // Permission Granted
                getActivity().getSupportLoaderManager().initLoader(LOADER_ALL, null, mLoaderCallback);
            } else {
                // Permission Denied
                Toast.makeText(getActivity(), "在设置-应用-" + getApplicationName() + "-权限中开启相机与储存空间权限，以正常使用拍照", Toast.LENGTH_SHORT).show();
                getActivity().finish();
            }
        }
    }

    private String getApplicationName() {
        PackageManager packageManager = null;
        ApplicationInfo applicationInfo = null;
        try {
            packageManager = getActivity().getApplicationContext().getPackageManager();
            applicationInfo = packageManager.getApplicationInfo(getActivity().getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            applicationInfo = null;
        }
        String applicationName =
                (String) packageManager.getApplicationLabel(applicationInfo);
        return applicationName;
    }
}
