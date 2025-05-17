package org.fox.ttrss;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

import org.fox.ttrss.types.ArticleList;
import org.fox.ttrss.types.Feed;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FeedsModel extends AndroidViewModel implements ApiCommon.ApiCaller {
    private final String TAG = this.getClass().getSimpleName();
    private MutableLiveData<List<Feed>> m_feeds = new MutableLiveData<>(new ArrayList<>());
    private MutableLiveData<Integer> m_loadingProgress = new MutableLiveData<>(Integer.valueOf(0));
    private MutableLiveData<Long> m_lastUpdate = new MutableLiveData<>(Long.valueOf(0));
    private MutableLiveData<Boolean> m_isLoading = new MutableLiveData<>(Boolean.valueOf(false));

    private Feed m_feed;

    private ExecutorService m_executor;
    private Handler m_mainHandler = new Handler(Looper.getMainLooper());

    private final int m_responseCode = 0;
    protected String m_responseMessage;
    private int m_apiStatusCode = 0;
    private String m_lastErrorMessage;
    private ApiCommon.ApiError m_lastError;
    private boolean m_rootMode;

    public FeedsModel(@NonNull Application application) {
        super(application);

        // do we need concurrency or not?
        m_executor = Executors.newSingleThreadExecutor();

        Log.d(TAG, this + " created");
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

    public void startLoading(Feed feed, boolean rootMode) {
        Log.d(TAG, "startLoading feed id=" + feed.id + " cat=" + feed.is_cat);

        m_feed = feed;
        m_rootMode = rootMode;

        loadInBackground();
    }

    @Override
    public void notifyProgress(int progress) {
        m_loadingProgress.postValue(progress);
    }

    protected HashMap<String,String> constructParams() {
        HashMap<String,String> params = new HashMap<>();

        if (m_rootMode) {
            params.put("op", "getCategories");

            // this confusingly named option means "return top level categories only"
            params.put("enable_nested", "true");
        } else {
            params.put("op", "getFeeds");
            params.put("cat_id", String.valueOf(m_feed.id));
            params.put("include_nested", "true");
        }

        params.put("sid", ((org.fox.ttrss.Application)getApplication()).getSessionId());

        return params;
    }

    private void loadInBackground() {
        Log.d(TAG, this + " loadInBackground");

        m_isLoading.postValue(true);

        HashMap<String,String> params = constructParams();

        m_executor.execute(() -> {
            JsonElement result = ApiCommon.performRequest(getApplication(), params, this);

            Log.d(TAG, "got result=" + result);

            try {
                JsonArray content = result.getAsJsonArray();
                if (content != null) {

                    Type listType = new TypeToken<List<Feed>>() {
                    }.getType();
                    List<Feed> feeds = new Gson().fromJson(content, listType);

                    m_feeds.postValue(feeds);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            m_isLoading.postValue(false);
        });
    }

    public LiveData<Integer> getLoadingProgress() {
        return m_loadingProgress;
    }

    public LiveData<Long> getUpdatesData() {
        return m_lastUpdate;
    }

    public LiveData<Boolean> getIsLoading() { return m_isLoading; }

    public LiveData<List<Feed>> getFeeds() {
        return m_feeds;
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

}