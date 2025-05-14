package org.fox.ttrss;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.loader.content.AsyncTaskLoader;
import androidx.preference.PreferenceManager;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import org.fox.ttrss.ApiCommon.ApiError;
import org.fox.ttrss.types.Article;
import org.fox.ttrss.types.ArticleList;
import org.fox.ttrss.types.Feed;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;

public class HeadlinesLoader extends AsyncTaskLoader<ArticleList> implements ApiCommon.ApiCaller {
	private final String TAG = this.getClass().getSimpleName();

	private final int m_responseCode = 0;
	protected String m_responseMessage;
	private int m_apiStatusCode = 0;

	private Context m_context;
	private String m_lastErrorMessage;
	private ApiError m_lastError;
	private ArticleList m_articles;
	private Feed m_feed;
	private SharedPreferences m_prefs;
	private int m_firstId;
	private String m_searchQuery = "";
	private boolean m_firstIdChanged;
	private int m_offset;
	private int m_amountLoaded;
	private int m_resizeWidth;
	private boolean m_append;
	private boolean m_lazyLoadEnabled = true;
	private boolean m_loadingInProgress;

	HeadlinesLoader(Context context, Feed feed, int resizeWidth) {
		super(context);

		m_context = context;
		m_lastError = ApiError.NO_ERROR;
		m_feed = feed;
		m_articles = new ArticleList();
		m_resizeWidth = resizeWidth;
		m_prefs = PreferenceManager.getDefaultSharedPreferences(context);
	}

	protected void startLoading(boolean append) {
		// Log.d(TAG, this + " refresh, append=" + append + " inProgress=" + m_loadingInProgress + " lazyLoadEnabled=" + m_lazyLoadEnabled);

		if (!append) {
			m_append = false;
			m_lazyLoadEnabled = true;

			forceLoad();
		} else if (m_lazyLoadEnabled && !m_loadingInProgress) {
			m_append = true;
			forceLoad();
		} else {
			deliverResult(m_articles);
		}
	}

	@Override
	public void deliverResult(ArticleList data) {
		super.deliverResult(data);
	}

	public int getErrorMessage() {
		return ApiCommon.getErrorMessage(m_lastError);
	}

	ApiError getLastError() {
		return m_lastError;
	}

	String getLastErrorMessage() {
		return m_lastErrorMessage;
	}

	public boolean lazyLoadEnabled() {
		return m_lazyLoadEnabled;
	}

	@Override
	public ArticleList loadInBackground() {
		Log.d(TAG, "loadInBackground append=" + m_append + " offset=" + m_offset);

		m_loadingInProgress = true;

		final int skip = getSkip(m_append);
		final boolean allowForceUpdate = Application.getInstance().getApiLevel() >= 9 &&
				!m_feed.is_cat && m_feed.id > 0 && !m_append && skip == 0;

		HashMap<String,String> params = new HashMap<>();

		params.put("op", "getHeadlines");
		params.put("sid", Application.getInstance().getSessionId());
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
			if (m_prefs.getBoolean("always_downsample_images", false) || !Application.getInstance().isWifiConnected()) {
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

		if (Application.getInstance().getApiLevel() >= 12) {
			params.put("include_header", "true");
		}

		Log.d(TAG, "request more headlines, firstId=" + m_firstId + ", append=" + m_append + ", skip=" + skip);

		JsonElement result = ApiCommon.performRequest(m_context, params, this);

		Log.d(TAG, "got result=" + result);

		if (result != null) {
			try {
				JsonArray content = result.getAsJsonArray();
				if (content != null) {
					final List<Article> articlesJson;
					final JsonObject header;

					if (Application.getInstance().getApiLevel() >= 12) {
						header = content.get(0).getAsJsonObject();

						m_firstIdChanged = header.get("first_id_changed") != null;

						try {
							m_firstId = header.get("first_id").getAsInt();
						} catch (NumberFormatException e) {
							m_firstId = 0;
						}

						Log.d(TAG, "firstID=" + m_firstId + " firstIdChanged=" + m_firstIdChanged);

						Type listType = new TypeToken<List<Article>>() {}.getType();
						articlesJson = new Gson().fromJson(content.get(1), listType);
					} else {
						Type listType = new TypeToken<List<Article>>() {}.getType();
						articlesJson = new Gson().fromJson(content, listType);
					}

					if (skip == 0)
						m_articles.clear();

					m_amountLoaded = articlesJson.size();

					for (Article article : articlesJson)
						if (!m_articles.containsId(article.id)) {
							article.collectMediaInfo();
							article.cleanupExcerpt();
							article.fixNullFields();
							m_articles.add(article);
						}

					if (m_firstIdChanged) {
						Log.d(TAG, "first id changed, disabling lazy load");
						m_lazyLoadEnabled = false;
					}

					if (m_amountLoaded < Integer.parseInt(m_prefs.getString("headlines_request_size", "15"))) {
						Log.d(TAG, "amount loaded "+m_amountLoaded+" < request size, disabling lazy load");
						m_lazyLoadEnabled = false;
					}

					m_offset += m_amountLoaded;
					m_loadingInProgress = false;

					return m_articles;
				}

			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		m_loadingInProgress = false;

		return null;
	}

	private int getSkip(boolean append) {
		int skip = 0;

		if (append) {
			// adaptive, all_articles, marked, published, unread
			String viewMode = m_prefs.getString("view_mode", "adaptive");

			int numUnread = Math.toIntExact(m_articles.getUnreadCount());
			int numAll = Math.toIntExact(m_articles.getSizeWithoutFooters());

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
	public void setLastError(ApiError lastError) {
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
}
