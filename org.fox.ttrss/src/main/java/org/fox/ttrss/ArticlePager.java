package org.fox.ttrss;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.PreferenceManager;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import org.fox.ttrss.types.Article;
import org.fox.ttrss.types.Feed;

public class ArticlePager extends androidx.fragment.app.Fragment {

	private final String TAG = "ArticlePager";
	private PagerAdapter m_adapter;
	private HeadlinesEventListener m_listener;
	private int m_articleId;
	private OnlineActivity m_activity;
	private Feed m_feed;
	private ViewPager2 m_pager;

	private static class PagerAdapter extends FragmentStateAdapter {
		
		public PagerAdapter(FragmentActivity fa) {
			super(fa);
		}

		@Override
		@NonNull
		public Fragment createFragment(int position) {
			try {
				Article article = Application.getArticles().get(position);

				if (article != null) {
					ArticleFragment af = new ArticleFragment();
					af.initialize(article);

					return af;
				}
			} catch (IndexOutOfBoundsException e) {
				e.printStackTrace();
			}

			return null;
		}

		@Override
		public int getItemCount() {
			return Application.getArticles().size();
		}

	}
		
	public void initialize(int articleId, Feed feed) {
		m_articleId = articleId;
		m_feed = feed;
	}

	@Override
	public void onSaveInstanceState(Bundle out) {
		super.onSaveInstanceState(out);

		out.putInt("m_articleId", m_articleId);
		out.putParcelable("m_feed", m_feed);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (savedInstanceState != null) {
			m_articleId = savedInstanceState.getInt("m_articleId");
			m_feed = savedInstanceState.getParcelable("m_feed");
		}

		setRetainInstance(true);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {    	
		View view = inflater.inflate(R.layout.fragment_article_pager, container, false);

		m_adapter = new PagerAdapter(getActivity());
		
		m_pager = view.findViewById(R.id.article_pager);

		int position = Application.getArticles().getPositionById(m_articleId);

		m_listener.onArticleSelected(Application.getArticles().getById(m_articleId), false);

		m_pager.setAdapter(m_adapter);
		m_pager.setOffscreenPageLimit(3);

		m_pager.setCurrentItem(position, false);
		m_pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
			@Override
			public void onPageSelected(int position) {
				Log.d(TAG, "onPageSelected: " + position);

				final Article article = Application.getArticles().get(position);

				if (article != null) {
					m_articleId = article.id;

					m_listener.onArticleSelected(article, false);
				}
			}
		});

		return view;
	}

	@Override
	public void onAttach(@NonNull Activity activity) {
		super.onAttach(activity);		
		
		m_listener = (HeadlinesEventListener)activity;
		m_activity = (OnlineActivity)activity;
	}

	@SuppressLint("NewApi")
	@Override
	public void onResume() {
		super.onResume();

		//if (m_adapter != null) m_adapter.notifyDataSetChanged();

		m_activity.invalidateOptionsMenu();
	}

	public void setActiveArticleId(int articleId) {
		if (m_pager != null && articleId != m_articleId) {
			int position = Application.getArticles().getPositionById(articleId);

			m_pager.setCurrentItem(position, false);
		}
	}

	public void switchToArticle(boolean next) {
		int position = Application.getArticles().getPositionById(m_articleId);

		if (position != -1) {

			if (next)
				position++;
			else
				position--;

			try {
				Article targetArticle = Application.getArticles().get(position);

				setActiveArticleId(targetArticle.id);
			} catch (IndexOutOfBoundsException e) {
				e.printStackTrace();
			}
		}
	}

	public int getSelectedArticleId() {
		return m_articleId;
	}

	public void notifyUpdated() {
		m_adapter.notifyDataSetChanged();
	}
}
