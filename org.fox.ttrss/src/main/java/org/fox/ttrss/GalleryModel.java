package org.fox.ttrss;

import android.app.Application;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.fox.ttrss.types.GalleryEntry;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;

public class GalleryModel extends AndroidViewModel {
    private final String TAG = this.getClass().getSimpleName();

    private MutableLiveData<List<GalleryEntry>> m_items = new MutableLiveData<>(new ArrayList<>());

    public GalleryModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<List<GalleryEntry>> getItems() {
        return m_items;
    }

    public void collectItems(String articleText, String srcFirst) {
        Document doc = Jsoup.parse(articleText);

        /* look for srcFirst and post an update */

        Elements elems = doc.select("img,video");

        for (Element elem : elems) {
            GalleryEntry item = new GalleryEntry();

            if ("video".equalsIgnoreCase(elem.tagName())) {


            } else {
                String src = elem.attr("abs:src");


            }
        }
    }

    List<GalleryEntry> collectGalleryContents(String imgSrcFirst, String articleText, List<GalleryEntry> uncheckedItems ) {
        List<GalleryEntry> items = new ArrayList<>();

        Document doc = Jsoup.parse(articleText);

        Elements elems = doc.select("img,video");

        boolean firstFound = false;

        for (Element elem : elems) {

            GalleryEntry item = new GalleryEntry();

            if ("video".equalsIgnoreCase(elem.tagName())) {
                String cover = elem.attr("poster");

                Element source = elem.select("source").first();

                if (source != null) {
                    String src = source.attr("src");

                    if (!src.isEmpty()) {
                        //Log.d(TAG, "vid/src=" + src);

                        if (src.startsWith("//")) {
                            src = "https:" + src;
                        }

                        if (imgSrcFirst.equals(src))
                            firstFound = true;

                        try {
                            Uri checkUri = Uri.parse(src);

                            if (!"data".equalsIgnoreCase(checkUri.getScheme())) {
                                item.url = src;
                                item.coverUrl = cover;
                                item.type = GalleryEntry.GalleryEntryType.TYPE_VIDEO;
                            }

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }

            } else {
                String src = elem.attr("src");

                if (!src.isEmpty()) {
                    if (src.startsWith("//")) {
                        src = "https:" + src;
                    }

                    if (imgSrcFirst.equals(src))
                        firstFound = true;

                    Log.d(TAG, "img/fir=" + imgSrcFirst + ";");
                    Log.d(TAG, "img/src=" + src + "; ff=" + firstFound);

                    try {
                        Uri checkUri = Uri.parse(src);

                        if (!"data".equalsIgnoreCase(checkUri.getScheme())) {
                            item.url = src;
                            item.type = GalleryEntry.GalleryEntryType.TYPE_IMAGE;
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            if ((firstFound || imgSrcFirst.isEmpty()) && item.url != null) {
                if (m_items.isEmpty())
                    m_items.add(item);
                else
                    uncheckedItems.add(item);
            }
        }

        return firstFound;
    }
}
