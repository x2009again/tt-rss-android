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
import org.fox.ttrss.Application;
import org.fox.ttrss.OnlineActivity;
import org.fox.ttrss.types.Article;
import org.fox.ttrss.types.ArticleList;

import java.lang.reflect.Type;
import java.util.List;

public class HeadlinesRequest extends ApiRequest {
	private final String TAG = this.getClass().getSimpleName();
	
	private int m_offset = 0;
	private final OnlineActivity m_activity;
	protected boolean m_firstIdChanged = false;
	protected int m_firstId = 0;
	protected int m_amountLoaded = 0;

	public HeadlinesRequest(Context context, OnlineActivity activity, ArticleList articles) {
		super(context);

		m_activity = activity;
	}
	
	protected void onPostExecute(JsonElement result) {
		if (result != null) {
			try {
				JsonArray content = result.getAsJsonArray();
				if (content != null) {
					final List<Article> articlesJson;
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
						articlesJson = new Gson().fromJson(content.get(1), listType);
					} else {
						Type listType = new TypeToken<List<Article>>() {}.getType();
						articlesJson = new Gson().fromJson(content, listType);
					}

					ArticleList articles = Application.getArticles();

					if (m_offset == 0)
						articles.clear();
					else
						articles.stripFooters();

					m_amountLoaded = articlesJson.size();

					for (Article f : articlesJson)
						if (!articles.containsId(f.id)) {
							f.collectMediaInfo();
							f.cleanupExcerpt();
							articles.add(f);
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
