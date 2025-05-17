package org.fox.ttrss;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.PopupMenu;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.DiffUtil;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import org.fox.ttrss.types.GalleryEntry;
import org.fox.ttrss.util.DiffFragmentStateAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class GalleryActivity extends CommonActivity {
    private final String TAG = this.getClass().getSimpleName();

    protected String m_title;
    private ArticleImagesPagerAdapter m_adapter;
    public String m_content;
    private ViewPager2 m_pager; // TODO replace with viewpager2
    private ProgressBar m_checkProgress;
    private boolean m_firstWasSelected;

    private static class GalleryEntryDiffItemCallback extends DiffUtil.ItemCallback<GalleryEntry> {

        @Override
        public boolean areItemsTheSame(@NonNull GalleryEntry oldItem, @NonNull GalleryEntry newItem) {
            return oldItem.url.equals(newItem.url);
        }

        @Override
        public boolean areContentsTheSame(@NonNull GalleryEntry oldItem, @NonNull GalleryEntry newItem) {
            return oldItem.url.equals(newItem.url) && oldItem.type.equals(newItem.type);
        }
    }

    private static class ArticleImagesPagerAdapter extends DiffFragmentStateAdapter<GalleryEntry> {
        protected ArticleImagesPagerAdapter(FragmentActivity fragmentActivity, DiffUtil.ItemCallback<GalleryEntry> diffCallback) {
            super(fragmentActivity, diffCallback);
        }

        @Override
        public Fragment createFragment(int position) {

            GalleryEntry item = getItem(position);

            switch (item.type) {
                case TYPE_IMAGE: {
                    GalleryImageFragment frag = new GalleryImageFragment();
                    frag.initialize(item.url);

                    return frag;
                }
                case TYPE_VIDEO:
                    GalleryVideoFragment frag = new GalleryVideoFragment();
                    frag.initialize(item.url, item.coverUrl);

                    return frag;
            }

            return null;
        }
    }

    public void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);

        out.putString("m_title", m_title);
        out.putString("m_content", m_content);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // ActivityCompat.postponeEnterTransition(this);

        // we use that before parent onCreate so let's init locally
        m_prefs = PreferenceManager
                .getDefaultSharedPreferences(getApplicationContext());

        setAppTheme(m_prefs);

        super.onCreate(savedInstanceState);

        Window window = getWindow();
        WindowInsetsControllerCompat windowInsetsController = WindowCompat.getInsetsController(window, window.getDecorView());
        windowInsetsController.hide(WindowInsetsCompat.Type.statusBars());

        setContentView(R.layout.activity_gallery);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().hide();

        if (savedInstanceState == null) {
            m_title = getIntent().getStringExtra("title");
            m_content = getIntent().getStringExtra("content");

            // this should be dealt with first so that transition completes properly
            String firstSrc = getIntent().getStringExtra("firstSrc");

            GalleryModel model = new ViewModelProvider(this).get(GalleryModel.class);
            model.collectItems(m_content, firstSrc);

            model.getItemsToCheck().observe(this, itemsToCheck -> {
                Log.d(TAG, "observed items to check=" + itemsToCheck);

                m_checkProgress.setMax(itemsToCheck);
                m_checkProgress.setVisibility(itemsToCheck > 0 ? View.VISIBLE : View.GONE);
                m_checkProgress.setProgress(0);
            });

            model.getCheckProgress().observe(this, progress -> {
                Log.d(TAG, "observed item check progress=" + progress);

                m_checkProgress.setProgress(progress);
                m_checkProgress.setVisibility(progress < m_checkProgress.getMax() ? View.VISIBLE : View.GONE);
            });

            model.getItems().observe(this, galleryEntries -> {
                Log.d(TAG, "observed gallery entries=" + galleryEntries + " firstSrc=" + firstSrc);

                m_adapter.submitList(galleryEntries, () -> {
                    if (!m_firstWasSelected) {
                        for (GalleryEntry entry : galleryEntries) {
                            if (entry.url.equals(firstSrc)) {
                                int position = galleryEntries.indexOf(entry);

                                Log.d(TAG, "selecting first src=" + firstSrc + " pos=" + position);
                                m_pager.setCurrentItem(position);

                                m_firstWasSelected = true;
                                break;
                            }
                        }
                    }
                });
            });

        } else {
            // ArrayList<GalleryEntry> list = savedInstanceState.getParcelableArrayList("m_items");
            m_title = savedInstanceState.getString("m_title");
            m_content = savedInstanceState.getString("m_content");
        }

        findViewById(R.id.gallery_overflow).setOnClickListener(v -> {
            try {
                GalleryEntry entry = m_adapter.getCurrentList().get(m_pager.getCurrentItem());

                PopupMenu popup = new PopupMenu(GalleryActivity.this, v);
                MenuInflater inflater = popup.getMenuInflater();
                inflater.inflate(R.menu.content_gallery_entry, popup.getMenu());

                popup.getMenu().findItem(R.id.article_img_share)
                        .setVisible(entry.type == GalleryEntry.GalleryEntryType.TYPE_IMAGE);

                popup.setOnMenuItemClickListener(item -> onImageMenuItemSelected(item, entry));

                popup.show();

            } catch (IndexOutOfBoundsException e) {
                e.printStackTrace();
            }
        });

        setTitle(m_title);

        m_adapter = new ArticleImagesPagerAdapter(this, new GalleryEntryDiffItemCallback());

        m_pager = findViewById(R.id.gallery_pager);
        m_pager.setAdapter(m_adapter);

        m_checkProgress = findViewById(R.id.gallery_check_progress);

        /* Log.d(TAG, "items to check:" + uncheckedItems.size());

        MediaCheckTask mct = new MediaCheckTask() {
            @Override
            protected void onProgressUpdate(MediaProgressResult... result) {
                //m_items.add(result[0].item);
                m_adapter.notifyDataSetChanged();

                if (result[0].position < result[0].count) {
                    m_checkProgress.setVisibility(View.VISIBLE);
                    m_checkProgress.setMax(result[0].count);
                    m_checkProgress.setProgress(result[0].position);
                } else {
                    m_checkProgress.setVisibility(View.GONE);
                }

            }

            @Override
            protected void onPostExecute(List<GalleryEntry> result) {
                m_items.addAll(result);
            }
        };

        mct.execute(uncheckedItems); */
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        int position = m_pager.getCurrentItem();

        try {
            GalleryEntry entry = m_adapter.getCurrentList().get(position);

            if (onImageMenuItemSelected(item, entry))
                return true;

        } catch (IndexOutOfBoundsException e) {
            e.printStackTrace();
        }

        return super.onContextItemSelected(item);
    }

    public boolean onImageMenuItemSelected(MenuItem item, GalleryEntry entry) {
        String url = entry.url;

        int itemId = item.getItemId();
        if (itemId == R.id.article_img_open) {
            if (url != null) {
                try {
                    openUri(Uri.parse(url));
                } catch (Exception e) {
                    e.printStackTrace();
                    toast(R.string.error_other_error);
                }
            }
            return true;
        } else if (itemId == R.id.article_img_copy) {
            if (url != null) {
                copyToClipboard(url);
            }
            return true;
        } else if (itemId == R.id.article_img_share) {
            if (url != null) {
                if (entry.type == GalleryEntry.GalleryEntryType.TYPE_IMAGE) {
                    Log.d(TAG, "image sharing image from URL=" + url);

                    shareImageFromUri(url);
                }
            }
            return true;
        } else if (itemId == R.id.article_img_share_url) {
            if (url != null) {
                shareText(url);
            }
            return true;
        } else if (itemId == R.id.article_img_view_caption) {
            if (url != null) {
                displayImageCaption(url, m_content);
            }
            return true;
        }
        Log.d(TAG, "onImageMenuItemSelected, unhandled id=" + item.getItemId());
        return false;
    }
}
