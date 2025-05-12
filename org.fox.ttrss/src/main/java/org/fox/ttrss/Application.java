package org.fox.ttrss;

import android.os.Bundle;

import org.fox.ttrss.types.ArticleList;

import java.util.HashMap;
import java.util.LinkedHashMap;

public class Application extends android.app.Application {
	private static Application m_singleton;

	// this is the only instance of a (large) object which contains all currently loaded articles and is
	// used by all fragments and activities concurrently
	private final ArticleList m_articles = new ArticleList();

	private String m_sessionId;
	private int m_apiLevel;
	public LinkedHashMap<String, String> m_customSortModes = new LinkedHashMap<>();

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

	public String getSessionId() {
		return m_sessionId;
	}

	public void setSessionId(String sessionId) {
		m_sessionId = sessionId;
	}

	public int getApiLevel() {
		return m_apiLevel;
	}

	public void setApiLevel(int apiLevel) {
		m_apiLevel = apiLevel;
	}
	
	public void save(Bundle out) {
		
		out.setClassLoader(getClass().getClassLoader());
		out.putString("gs:sessionId", m_sessionId);
		out.putInt("gs:apiLevel", m_apiLevel);
		out.putSerializable("gs:customSortTypes", m_customSortModes);
	}
	
	/** @noinspection unchecked*/
    public void load(Bundle in) {
		if (in != null) {
			m_sessionId = in.getString("gs:sessionId");
			m_apiLevel = in.getInt("gs:apiLevel");

			HashMap<String, String> tmp = (HashMap<String, String>) in.getSerializable("gs:customSortTypes");

			m_customSortModes.clear();
			m_customSortModes.putAll(tmp);
		}
				
	}
}
