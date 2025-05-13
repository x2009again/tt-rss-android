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
	private String m_searchQuery = "";
	private Feed m_feed;
	private SharedPreferences m_prefs;
	private int m_firstId = 0;
	private boolean m_refreshInProgress;
	private boolean m_lazyLoadDisabled;
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

	public void setSearchQuery(String searchQuery) {
		m_searchQuery = searchQuery;
	}

	@Override
	public void onSaveInstanceState(Bundle out) {
		super.onSaveInstanceState(out);

		out.putInt("m_articleId", m_articleId);
		out.putParcelable("m_feed", m_feed);
		out.putInt("m_firstId", m_firstId);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (savedInstanceState != null) {
			m_articleId = savedInstanceState.getInt("m_articleId");
			m_feed = savedInstanceState.getParcelable("m_feed");
			m_firstId = savedInstanceState.getInt("m_firstId");
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

					new Handler().postDelayed(() -> m_listener.onArticleSelected(article, false), 250);

					//Log.d(TAG, "Page #" + position + "/" + m_adapter.getCount());

					if (!m_refreshInProgress && !m_lazyLoadDisabled && (m_activity.isSmallScreen() || m_activity.isPortrait()) && position >= m_adapter.getItemCount() - 5) {
						Log.d(TAG, "loading more articles...");

						new Handler().postDelayed(() -> refresh(true), 100);
					}
				}
			}
		});

		return view;
	}

	protected void refresh(final boolean append) {
		//
	}

	/* protected void refresh(final boolean append) {

		if (!append) {
			m_lazyLoadDisabled = false;
		}

		m_refreshInProgress = true;

		@SuppressLint("StaticFieldLeak") HeadlinesRequest req = new HeadlinesRequest(getActivity().getApplicationContext(), m_activity, Application.getArticles()) {
			@Override
			protected void onPostExecute(JsonElement result) {
				if (isDetached() || !isAdded()) return;

				if (!append) {
					m_pager.setCurrentItem(0, false);
					Application.getArticles().clear();
				}

				super.onPostExecute(result);

				m_refreshInProgress = false;

				if (result != null) {

					if (m_firstIdChanged) {
						m_lazyLoadDisabled = true;
					}

					if (m_firstIdChanged && !(m_activity instanceof DetailActivity && !m_activity.isPortrait())) {
						//m_activity.toast(R.string.headlines_row_top_changed);

						Snackbar.make(getView(), R.string.headlines_row_top_changed, Snackbar.LENGTH_LONG)
								.setAction(R.string.reload, v -> refresh(false)).show();
					}

					if (m_amountLoaded < Integer.parseInt(m_prefs.getString("headlines_request_size", "15"))) {
						m_lazyLoadDisabled = true;
					}

					ArticlePager.this.m_firstId = m_firstId;

					try {
						m_adapter.notifyDataSetChanged();
					} catch (BadParcelableException e) {
						if (getActivity() != null) {							
							getActivity().finish();
							return;
						}
					}

					if (!Application.getArticles().isEmpty()) {
						if (Application.getArticles().getById(m_articleId) == null) {
							Article article = Application.getArticles().get(0);

							m_articleId = article.id;
							m_listener.onArticleSelected(article, false);
						}
					}

				} else {
					m_lazyLoadDisabled = true;

					if (m_lastError == ApiCommon.ApiError.LOGIN_FAILED) {
						m_activity.login(true);
					} else {
						m_activity.toast(getErrorMessage());
					}
				}
			}
		};
		
		final Feed feed = m_feed;
		
		final String sessionId = m_activity.getSessionId();
		int skip = 0;
		
		if (append) {
			// adaptive, all_articles, marked, published, unread
			String viewMode = m_activity.getViewMode();
			int numUnread = 0;
			int numAll = Application.getArticles().size();
			
			for (Article a : Application.getArticles()) {
				if (a.unread) ++numUnread;
			}
			
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
		
		final int fskip = skip;
		
		req.setOffset(skip);

		HashMap<String,String> map = new HashMap<>();
		map.put("op", "getHeadlines");
		map.put("sid", sessionId);
		map.put("feed_id", String.valueOf(feed.id));
		map.put("show_excerpt", "true");
		map.put("excerpt_length", String.valueOf(CommonActivity.EXCERPT_MAX_LENGTH));
		map.put("show_content", "true");
		map.put("include_attachments", "true");
		map.put("limit", m_prefs.getString("headlines_request_size", "15"));
		map.put("offset", String.valueOf(0));
		map.put("view_mode", m_activity.getViewMode());
		map.put("skip", String.valueOf(fskip));
		map.put("include_nested", "true");
		map.put("has_sandbox", "true");
		map.put("order_by", m_activity.getSortMode());

		if (feed.is_cat) map.put("is_cat", "true");

		if (m_searchQuery != null && !m_searchQuery.isEmpty()) {
			map.put("search", m_searchQuery);
			map.put("search_mode", "");
			map.put("match_on", "both");
		}

		if (m_firstId > 0) map.put("check_first_id", String.valueOf(m_firstId));

		if (m_activity.getApiLevel() >= 12) {
			map.put("include_header", "true");
		}

		if (m_prefs.getBoolean("enable_image_downsampling", false)) {
			if (m_prefs.getBoolean("always_downsample_images", false) || !m_activity.isWifiConnected()) {
				map.put("resize_width", String.valueOf(m_activity.getResizeWidth()));
			}
		}

		Log.d(TAG, "[AP] request more headlines, firstId=" + m_firstId);

		req.execute(map);
	} */
	
	@Override
	public void onAttach(@NonNull Activity activity) {
		super.onAttach(activity);		
		
		m_listener = (HeadlinesEventListener)activity;
		m_activity = (OnlineActivity)activity;
		
		m_prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
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
