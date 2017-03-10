package me.nereo.multi_image_selector;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.io.File;
import java.util.ArrayList;

import me.nereo.multi_image_selector.bean.Image;
import me.nereo.multi_image_selector.utils.FileUtils;
import me.nereo.multi_image_selector.utils.ScreenUtils;
import uk.co.senab.photoview.PhotoView;
import uk.co.senab.photoview.PhotoViewAttacher;

/**
 * Created by alvinzeng on 15/02/2017.
 */

public class PreviewActivity extends AppCompatActivity {

    public static ArrayList<Image> images;
    public static ArrayList<Image> selectImages;
    public static int position = 0;
    public static int maxCount = 9;
    public static int maxDuration = 0;
    public static int pickerMode;

    ViewPager viewpager;
    ViewGroup barLayout, footer;

    CheckBox checkBox;
    private Button mSubmitButton;
    TextView titleTextView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            //window.setStatusBarColor(Color.TRANSPARENT);
            //window.setNavigationBarColor(Color.TRANSPARENT);
        }

        setContentView(R.layout.ms_activity_preview);

        if (images == null || selectImages == null) {
            finish();
        }

        if (images == selectImages) {
            images = (ArrayList<Image>) selectImages.clone();
        }

        // 完成按钮
        mSubmitButton = (Button) findViewById(R.id.commit);

        mSubmitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setResult(RESULT_OK);
                finish();
            }
        });

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setResult(RESULT_CANCELED);
                finish();
            }
        });
        titleTextView = (TextView) findViewById(R.id.titleTextView);
        barLayout = (ViewGroup) findViewById(R.id.barLayout);
        footer = (ViewGroup) findViewById(R.id.footer);
        checkBox = (CheckBox) findViewById(R.id.checkbox);
        viewpager = (ViewPager) findViewById(R.id.viewpager);
        viewpager.setAdapter(new SamplePagerAdapter(images));
        if (position > 0) {
            viewpager.setCurrentItem(position);
        }

        checkBox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Image image = images.get(viewpager.getCurrentItem());
                if (checkBox.isChecked()) {
                    if (selectImages.size() >= maxCount) {

                        int resId = R.string.msg_amount_limit;
                        if (pickerMode == MultiImageSelectorFragment.MODE_IMAGE) {
                            resId = R.string.msg_image_amount_limit;
                        } else if (pickerMode == MultiImageSelectorFragment.MODE_VIDEO) {
                            resId = R.string.msg_video_amount_limit;
                        }
                        Toast.makeText(PreviewActivity.this, getString(resId, maxCount), Toast.LENGTH_SHORT).show();
                        checkBox.setChecked(false);
                        return;
                    }

                    if (image.isVideo && maxDuration > 0 && (int) (image.duration / 1000) > maxDuration) {
                        Toast.makeText(PreviewActivity.this, getString(R.string.msg_duration_limit, maxDuration), Toast.LENGTH_SHORT).show();
                        checkBox.setChecked(false);
                        return;
                    }

                    if (!selectImages.contains(image)) {
                        selectImages.add(image);
                    }
                } else {
                    selectImages.remove(image);
                }

                updateDoneText();
            }
        });

        viewpager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                Image image = images.get(position);
                checkBox.setChecked(selectImages.contains(image));
                titleTextView.setText((viewpager.getCurrentItem() + 1) + "/" + images.size());
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });
        titleTextView.setText((viewpager.getCurrentItem() + 1) + "/" + images.size());
        Image image = images.get(viewpager.getCurrentItem());
        checkBox.setChecked(selectImages.contains(image));
        updateDoneText();
    }

    private void updateDoneText() {
        if (selectImages.size() > 0) {
            mSubmitButton.setEnabled(true);
            mSubmitButton.setText(String.format("%s(%d/%d)",
                    getString(R.string.action_done), selectImages.size(), maxCount));
        } else {
            mSubmitButton.setEnabled(false);
            mSubmitButton.setText(R.string.action_done);
        }
    }


    private void onItemTap() {
        Window window = getWindow();
        if (barLayout.getVisibility() == View.VISIBLE) {
            barLayout.setVisibility(View.GONE);
            footer.setVisibility(View.GONE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                window.setStatusBarColor(Color.TRANSPARENT);
                //window.setNavigationBarColor(Color.TRANSPARENT);
            }
        } else {
            barLayout.setVisibility(View.VISIBLE);
            footer.setVisibility(View.VISIBLE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

                TypedValue typedValue = new TypedValue();
                Resources.Theme theme = getTheme();
                theme.resolveAttribute(R.attr.colorPrimaryDark, typedValue, true);
                int color = typedValue.data;

                window.setStatusBarColor(color);
                //window.setNavigationBarColor(color);
            }
        }
    }

    class SamplePagerAdapter extends PagerAdapter {

        ArrayList<Image> photos = new ArrayList<>();

        public SamplePagerAdapter(ArrayList<Image> photos) {
            super();
            this.photos = photos;
        }

        @Override
        public int getCount() {
            return photos.size();
        }

        @Override
        public View instantiateItem(ViewGroup container, final int position) {

            View view = LayoutInflater.from(container.getContext()).inflate(R.layout.ms_image_preview, null);
            ImageView imageView = (ImageView) view.findViewById(R.id.imageView);
            PhotoView photoView = (PhotoView) view.findViewById(R.id.photoView);
            ImageView playImageView = (ImageView) view.findViewById(R.id.playImageView);

            if (photos.get(position).path.endsWith(".gif")) {
                photoView.setVisibility(View.GONE);
                Glide.with(PreviewActivity.this).load(photos.get(position).path).asGif().diskCacheStrategy(DiskCacheStrategy.SOURCE).into(imageView);
            } else {
                imageView.setVisibility(View.GONE);

                ViewGroup.LayoutParams lp = photoView.getLayoutParams();
                lp.height = ScreenUtils.getScreenRealHeight(PreviewActivity.this);
                photoView.setLayoutParams(lp);

                Glide.with(PreviewActivity.this).load(photos.get(position).path).into(photoView);

                photoView.setOnPhotoTapListener(new PhotoViewAttacher.OnPhotoTapListener() {
                    @Override
                    public void onPhotoTap(View view, float x, float y) {
                        onItemTap();
                    }

                    @Override
                    public void onOutsidePhotoTap() {
                        onItemTap();
                    }
                });
            }

            if (photos.get(position).isVideo) {
                playImageView.setVisibility(View.VISIBLE);
                playImageView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(PreviewActivity.this, VideoPreviewActivity.class);
                        intent.putExtra(VideoPreviewActivity.VIDEO_PATH, photos.get(position).path);
                        startActivity(intent);
                        /*
                        try {
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            String type = "video/*";
                            File mTmpFile = new File(photos.get(position).path);

                            Uri uri = null;
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, photos.get(position).id);
                            } else {
                                uri = Uri.fromFile(mTmpFile);
                            }
                            intent.setDataAndType(uri, type);
                            startActivity(intent);
                        } catch (Exception e) {
                            Toast.makeText(PreviewActivity.this, "未找到播放器", Toast.LENGTH_SHORT).show();
                        }*/
                    }
                });
            } else {
                playImageView.setVisibility(View.GONE);
            }

            container.addView(view);

            imageView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onItemTap();
                }
            });

            return view;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView((View) object);
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }

        @Override
        public int getItemPosition(Object object) {
            return POSITION_NONE;
        }
    }
}
