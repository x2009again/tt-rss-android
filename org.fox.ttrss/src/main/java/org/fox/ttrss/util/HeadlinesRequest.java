package org.fox.ttrss.util;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import org.fox.ttrss.ApiCommon;
import org.fox.ttrss.ApiRequest;
import org.fox.ttrss.HeadlinesFragment;
import org.fox.ttrss.OnlineActivity;
import org.fox.ttrss.types.Article;
import org.fox.ttrss.types.ArticleList;
import org.fox.ttrss.types.Feed;

import java.lang.reflect.Type;
import java.util.List;

public class HeadlinesRequest extends ApiRequest {
	private final String TAG = this.getClass().getSimpleName();
	
	private int m_offset = 0;
	private final OnlineActivity m_activity;
	private final ArticleList m_articles; // = new ArticleList(); //Application.getInstance().m_loadedArticles;
	private final Feed m_feed;

	protected boolean m_firstIdChanged = false;
	protected int m_firstId = 0;
	protected int m_amountLoaded = 0;

	public HeadlinesRequest(Context context, OnlineActivity activity, final Feed feed, ArticleList articles) {
		super(context);

        m_articles = articles;
		m_activity = activity;
		m_feed = feed;
	}
	
	protected void onPostExecute(JsonElement result) {
		if (result != null) {
			try {
				
				// check if we are returning results for correct feed
				/* if (Application.getInstance().m_activeFeed != null && !m_feed.equals(Application.getInstance().m_activeFeed)) {
					Log.d(TAG, "received results for wrong feed, bailing out.");
					return;
				} */
				
				JsonArray content = result.getAsJsonArray();
				if (content != null) {
					final List<Article> articles;
					final JsonObject header;

					if (m_activity.getApiLevel() >= 12) {
						header = content.get(0).getAsJsonObject();

						//Log.d(TAG, "headerID:" + header.get("top_id_changed"));

						m_firstIdChanged = header.get("first_id_changed") != null;
						try {
							m_firstId = header.get("first_id").getAsInt();
						} catch (NumberFormatException e) {
							m_firstId = 0;
						}

						Log.d(TAG, "firstID=" + m_firstId + " firstIdChanged=" + m_firstIdChanged);

						Type listType = new TypeToken<List<Article>>() {}.getType();
						articles = new Gson().fromJson(content.get(1), listType);
					} else {
						Type listType = new TypeToken<List<Article>>() {}.getType();
						articles = new Gson().fromJson(content, listType);
					}

					if (m_offset == 0) {
						m_articles.clear();
					} else {

						m_articles.stripFooters();

						while (m_articles.size() > HeadlinesFragment.HEADLINES_BUFFER_MAX) {
							m_articles.remove(0);
						}
					}

					m_amountLoaded = articles.size();

					for (Article f : articles)
						if (!m_articles.containsId(f.id)) {
							f.collectMediaInfo();
							f.cleanupExcerpt();
							m_articles.add(f);
						}

					return;
				}
						
			} catch (Exception e) {
				e.printStackTrace();						
			}
		}

		if (m_lastError == ApiCommon.ApiError.LOGIN_FAILED) {
			m_activity.login();
		} else {

			if (m_lastErrorMessage != null) {
				m_activity.toast(m_activity.getString(getErrorMessage()) + "\n" + m_lastErrorMessage);
			} else {
				m_activity.toast(getErrorMessage());
			}
			//m_activity.setLoadingStatus(getErrorMessage(), false);
		}
    }

	public void setOffset(int skip) {
		m_offset = skip;			
	}
}
