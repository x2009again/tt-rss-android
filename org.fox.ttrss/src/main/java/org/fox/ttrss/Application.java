package org.fox.ttrss;

import android.os.Bundle;

import org.fox.ttrss.types.Article;
import org.fox.ttrss.types.ArticleList;

import java.util.HashMap;
import java.util.LinkedHashMap;

public class Application extends android.app.Application {
	private static Application m_singleton;

	// this is the only instance of a (large) object which contains all currently loaded articles and is
	// used by all fragments and activities concurrently
	private ArticleList m_articles = new ArticleList();

	// we use this to pass a large temporary object between activities
	public Article tmpActiveArticle;

	public int m_selectedArticleId;
	public String m_sessionId;
	public int m_apiLevel;
	public LinkedHashMap<String, String> m_customSortModes = new LinkedHashMap<String, String>();

	public static Application getInstance(){
		return m_singleton;
	}

	public static ArticleList getArticles() {
		return getInstance().m_articles;
	}

	@Override
	public final void onCreate() {
		super.onCreate();

		m_singleton = this;
	}
	
	public void save(Bundle out) {
		
		out.setClassLoader(getClass().getClassLoader());
		out.putString("gs:sessionId", m_sessionId);
		out.putInt("gs:apiLevel", m_apiLevel);
		out.putInt("gs:selectedArticleId", m_selectedArticleId);
		out.putSerializable("gs:customSortTypes", m_customSortModes);
	}
	
	public void load(Bundle in) {
		if (in != null) {
			m_sessionId = in.getString("gs:sessionId");
			m_apiLevel = in.getInt("gs:apiLevel");
			m_selectedArticleId = in.getInt("gs:selectedArticleId");

			HashMap<String, String> tmp = (HashMap<String, String>) in.getSerializable("gs:customSortTypes");

			m_customSortModes.clear();
			m_customSortModes.putAll(tmp);
		}
				
	}
}
