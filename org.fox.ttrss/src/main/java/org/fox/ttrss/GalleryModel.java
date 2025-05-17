package org.fox.ttrss;

import android.app.Application;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import org.fox.ttrss.types.GalleryEntry;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GalleryModel extends AndroidViewModel {
    private final String TAG = this.getClass().getSimpleName();

    private MutableLiveData<List<GalleryEntry>> m_items = new MutableLiveData<>(new ArrayList<>());

    public GalleryModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<List<GalleryEntry>> getItems() {
        return m_items;
    }

    private ExecutorService m_executor = Executors.newSingleThreadExecutor();
    private Handler m_mainHandler = new Handler(Looper.getMainLooper());

    public void collectItems(String articleText, String srcFirst) {
        Document doc = Jsoup.parse(articleText);

        List<GalleryEntry> checkList = new ArrayList<>();

        /* look for srcFirst quickly and post an update */

        Log.d(TAG, "looking for srcFirst=" + srcFirst);

        Elements elems = doc.select("img,video");

        for (Element elem : elems) {
            if ("video".equalsIgnoreCase(elem.tagName())) {
                Element source = elem.select("source").first();
                String poster = elem.attr("abs:poster");

                if (source != null) {
                    String src = source.attr("abs:src");

                    Log.d(TAG, "checking vid src=" + src + " poster=" + poster);

                    if (poster != null && poster.equals(srcFirst) || src != null && src.equals(srcFirst)) {
                        Log.d(TAG, "first item found, vid=" + src);

                        GalleryEntry item = new GalleryEntry(src, GalleryEntry.GalleryEntryType.TYPE_VIDEO, poster);

                        checkList.add(item);

                        m_items.postValue(checkList);
                    } else {
                        try {
                            Uri checkUri = Uri.parse(src);

                            if (!"data".equalsIgnoreCase(checkUri.getScheme())) {
                                checkList.add(new GalleryEntry(src, GalleryEntry.GalleryEntryType.TYPE_VIDEO, poster));

                                m_items.postValue(checkList);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }

            } else {
                String src = elem.attr("abs:src");

                Log.d(TAG, "checking img src=" + src);

                if (src != null && src.equals(srcFirst)) {
                    Log.d(TAG, "first item found, img=" + src);

                    GalleryEntry item = new GalleryEntry(src, GalleryEntry.GalleryEntryType.TYPE_IMAGE, null);

                    checkList.add(item);

                    m_items.postValue(checkList);
                } else {
                    try {
                        Uri checkUri = Uri.parse(src);

                        if (!"data".equalsIgnoreCase(checkUri.getScheme())) {

                            m_executor.execute(() -> {
                                Log.d(TAG, "checking image with glide: " + src);

                                try {
                                    Bitmap bmp = Glide.with(getApplication().getApplicationContext())
                                            .load(src)
                                            .asBitmap()
                                            .skipMemoryCache(false)
                                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                                            .into(HeadlinesFragment.FLAVOR_IMG_MIN_SIZE, HeadlinesFragment.FLAVOR_IMG_MIN_SIZE)
                                            .get();

                                    if (bmp != null && bmp.getWidth() >= HeadlinesFragment.FLAVOR_IMG_MIN_SIZE && bmp.getHeight() >= HeadlinesFragment.FLAVOR_IMG_MIN_SIZE) {
                                        Log.d(TAG, "image matches gallery criteria, adding...");

                                        checkList.add(new GalleryEntry(src, GalleryEntry.GalleryEntryType.TYPE_IMAGE, null));
                                        m_items.postValue(checkList);
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            });
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}
