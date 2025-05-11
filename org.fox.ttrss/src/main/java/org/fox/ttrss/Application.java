package org.fox.ttrss;

import android.os.Bundle;

import org.fox.ttrss.types.Article;
import org.fox.ttrss.types.ArticleList;

import java.util.HashMap;
import java.util.LinkedHashMap;

public class Application extends android.app.Application {
	private static Application m_singleton;
	
	public ArticleList tmpArticleList;
	public Article tmpArticle;

	public int m_selectedArticleId;
	public String m_sessionId;
	public int m_apiLevel;
	public LinkedHashMap<String, String> m_customSortModes = new LinkedHashMap<String, String>();

	public static Application getInstance(){
		return m_singleton;
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
