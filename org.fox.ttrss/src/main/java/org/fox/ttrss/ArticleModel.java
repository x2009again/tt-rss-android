package org.fox.ttrss;

import android.app.Application;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.preference.PreferenceManager;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import org.fox.ttrss.types.Article;
import org.fox.ttrss.types.ArticleList;
import org.fox.ttrss.types.Feed;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ArticleModel extends AndroidViewModel implements ApiCommon.ApiCaller {
    private final String TAG = this.getClass().getSimpleName();
    private final MutableLiveData<ArticleList> m_articles = new MutableLiveData<>(new ArticleList());
    private SharedPreferences m_prefs;
    private final int m_responseCode = 0;
    protected String m_responseMessage;
    private int m_apiStatusCode = 0;

    private String m_lastErrorMessage;
    private ApiCommon.ApiError m_lastError;
    private Feed m_feed;
    private int m_firstId;
    private String m_searchQuery = "";
    private boolean m_firstIdChanged;
    private int m_offset;
    private int m_amountLoaded;
    private int m_resizeWidth;
    private boolean m_append;
    private boolean m_lazyLoadEnabled = true;
    private boolean m_loadingInProgress;
    private ExecutorService m_executor;
    private Handler m_mainHandler = new Handler(Looper.getMainLooper());
    private MutableLiveData<Long> m_lastUpdate = new MutableLiveData<>(Long.valueOf(0));

    public ArticleModel(@NonNull Application application) {
        super(application);

        m_prefs = PreferenceManager.getDefaultSharedPreferences(application);

        // do we need concurrency or not?
        m_executor = Executors.newSingleThreadExecutor();
    }

    public LiveData<Long> getUpdatesData() {
        return m_lastUpdate;
    }

    public LiveData<ArticleList> getArticles() {
        return m_articles;
    }


    public void update(int position, Article article) {
        m_articles.getValue().set(position, article);
        m_articles.postValue(m_articles.getValue());
    }

    public void update(ArticleList articles) {
        m_articles.postValue(articles);
    }

    public void startLoading(boolean append, @NonNull Feed feed, int resizeWidth) {
        Log.d(TAG, "startLoading append=" + append);

        m_resizeWidth = resizeWidth;

        if (!append) {
            m_append = false;
            m_lazyLoadEnabled = true;
            m_feed = feed;

            loadInBackground();
        } else if (feed != m_feed || m_lazyLoadEnabled && !m_loadingInProgress) {
            m_append = true;
            m_feed = feed;

            loadInBackground();
        } else {
            m_articles.postValue(m_articles.getValue());
        }
    }

    private void loadInBackground() {
        Log.d(TAG, this + " loadInBackground append=" + m_append + " offset=" + m_offset);

        ArticleList articlesWork = new ArticleList(m_articles.getValue());

        m_loadingInProgress = true;

        final int skip = getSkip(m_append, articlesWork);
        final boolean allowForceUpdate = org.fox.ttrss.Application.getInstance().getApiLevel() >= 9 &&
                !m_feed.is_cat && m_feed.id > 0 && !m_append && skip == 0;

        HashMap<String,String> params = new HashMap<>();

        params.put("op", "getHeadlines");
        params.put("sid", org.fox.ttrss.Application.getInstance().getSessionId());
        params.put("feed_id", String.valueOf(m_feed.id));
        params.put("show_excerpt", "true");
        params.put("excerpt_length", String.valueOf(CommonActivity.EXCERPT_MAX_LENGTH));
        params.put("show_content", "true");
        params.put("include_attachments", "true");
        params.put("view_mode", m_prefs.getString("view_mode", "adaptive"));
        params.put("limit", m_prefs.getString("headlines_request_size", "15"));
        params.put("skip", String.valueOf(skip));
        params.put("include_nested", "true");
        params.put("has_sandbox", "true");
        params.put("order_by", m_prefs.getString("headlines_sort_mode", "default"));

        if (m_prefs.getBoolean("enable_image_downsampling", false)) {
            if (m_prefs.getBoolean("always_downsample_images", false) || !org.fox.ttrss.Application.getInstance().isWifiConnected()) {
                params.put("resize_width", String.valueOf(m_resizeWidth));
            }
        }

        if (m_feed.is_cat)
            params.put("is_cat", "true");

        if (allowForceUpdate) {
            params.put("force_update", "true");
        }

        if (m_searchQuery != null && !m_searchQuery.isEmpty()) {
            params.put("search", m_searchQuery);
            params.put("search_mode", "");
            params.put("match_on", "both");
        }

        if (m_firstId > 0)
            params.put("check_first_id", String.valueOf(m_firstId));

        if (org.fox.ttrss.Application.getInstance().getApiLevel() >= 12) {
            params.put("include_header", "true");
        }

        Log.d(TAG, "firstId=" + m_firstId + " append=" + m_append + " skip=" + skip + " localSize=" + articlesWork.size());

        m_executor.execute(() -> {
            JsonElement result = ApiCommon.performRequest(getApplication(), params, this);

            Log.d(TAG, "got result=" + result);

            if (result != null) {
                try {
                    JsonArray content = result.getAsJsonArray();
                    if (content != null) {
                        final List<Article> articlesJson;
                        final JsonObject header;

                        if (org.fox.ttrss.Application.getInstance().getApiLevel() >= 12) {
                            header = content.get(0).getAsJsonObject();

                            m_firstIdChanged = header.get("first_id_changed") != null;

                            try {
                                m_firstId = header.get("first_id").getAsInt();
                            } catch (NumberFormatException e) {
                                m_firstId = 0;
                            }

                            Log.d(TAG, this + " firstID=" + m_firstId + " firstIdChanged=" + m_firstIdChanged);

                            Type listType = new TypeToken<List<Article>>() {}.getType();
                            articlesJson = new Gson().fromJson(content.get(1), listType);
                        } else {
                            Type listType = new TypeToken<List<Article>>() {}.getType();
                            articlesJson = new Gson().fromJson(content, listType);
                        }

                        if (!m_append)
                            articlesWork.clear();

                        m_amountLoaded = articlesJson.size();

                        for (Article article : articlesJson)
                            if (!articlesWork.containsId(article.id)) {
                                article.collectMediaInfo();
                                article.cleanupExcerpt();
                                article.fixNullFields();
                                articlesWork.add(article);
                            }

                        if (m_firstIdChanged) {
                            Log.d(TAG, "first id changed, disabling lazy load");
                            m_lazyLoadEnabled = false;
                        }

                        if (m_amountLoaded < Integer.parseInt(m_prefs.getString("headlines_request_size", "15"))) {
                            Log.d(TAG, this + " amount loaded "+m_amountLoaded+" < request size, disabling lazy load");
                            m_lazyLoadEnabled = false;
                        }

                        m_offset += m_amountLoaded;
                        m_loadingInProgress = false;

                        Log.d(TAG, this + " loaded headlines=" + m_amountLoaded + " resultingLocalSize=" + articlesWork.size());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            m_mainHandler.post(() -> {
                m_articles.setValue(articlesWork);
                m_lastUpdate.setValue(System.currentTimeMillis());
            });
        });

        m_loadingInProgress = false;

    }

    private int getSkip(boolean append, ArticleList articles) {
        int skip = 0;

        if (append) {
            // adaptive, all_articles, marked, published, unread
            String viewMode = m_prefs.getString("view_mode", "adaptive");

            int numUnread = Math.toIntExact(articles.getUnreadCount());
            int numAll = Math.toIntExact(articles.size());

            if ("marked".equals(viewMode)) {
                skip = numAll;
            } else if ("published".equals(viewMode)) {
                skip = numAll;
            } else if ("unread".equals(viewMode)) {
                skip = numUnread;
            } else if (m_searchQuery != null && !m_searchQuery.isEmpty()) {
                skip = numAll;
            } else if ("adaptive".equals(viewMode)) {
                skip = numUnread > 0 ? numUnread : numAll;
            } else {
                skip = numAll;
            }
        }

        return skip;
    }

    @Override
    public void setStatusCode(int statusCode) {
        m_apiStatusCode = statusCode;
    }

    @Override
    public void setLastError(ApiCommon.ApiError lastError) {
        m_lastError = lastError;
    }

    @Override
    public void setLastErrorMessage(String message) {
        m_lastErrorMessage = message;
    }

    public boolean getFirstIdChanged() {
        return m_firstIdChanged;
    }

    public boolean getAppend() {
        return m_append;
    }

    public void setSearchQuery(String searchQuery) {
        m_searchQuery = searchQuery;
    }

    public String getSearchQuery() {
        return m_searchQuery;
    }

    public int getOffset() {
        return m_offset;
    }

    public boolean lazyLoadEnabled() {
        return m_lazyLoadEnabled;
    }

    public int getErrorMessage() {
        return ApiCommon.getErrorMessage(m_lastError);
    }

    ApiCommon.ApiError getLastError() {
        return m_lastError;
    }

    String getLastErrorMessage() {
        return m_lastErrorMessage;
    }

    public boolean isLoading() {
        return m_loadingInProgress;
    }
}
