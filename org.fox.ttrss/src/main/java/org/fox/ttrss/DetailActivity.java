package org.fox.ttrss;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.PreferenceManager;

import com.google.android.material.bottomappbar.BottomAppBar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.fox.ttrss.types.Article;
import org.fox.ttrss.types.ArticleList;
import org.fox.ttrss.types.Feed;

public class DetailActivity extends OnlineActivity implements HeadlinesEventListener {
	private final String TAG = this.getClass().getSimpleName();
	protected BottomAppBar m_bottomAppBar;

	protected SharedPreferences m_prefs;
    private Article m_activeArticle;

    @SuppressLint("NewApi")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		m_prefs = PreferenceManager
				.getDefaultSharedPreferences(getApplicationContext());

		setAppTheme(m_prefs);

		super.onCreate(savedInstanceState);

		if (m_prefs.getBoolean("force_phone_layout", false)) {
			setContentView(R.layout.activity_detail_phone);
		} else {
			setContentView(R.layout.activity_detail);
		}

		Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);

        m_forceDisableActionMode = isPortrait() || isSmallScreen();

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

		setSmallScreen(findViewById(R.id.sw600dp_anchor) == null);
		
		Application.getInstance().load(savedInstanceState);

        if (isPortrait()) {
            View headlines = findViewById(R.id.headlines_fragment);

            if (headlines != null)
        		headlines.setVisibility(View.GONE);
        }

		m_bottomAppBar = findViewById(R.id.detail_bottom_appbar);

		if (m_bottomAppBar != null) {
			m_bottomAppBar.setOnMenuItemClickListener(item -> {

                final ArticlePager ap = (ArticlePager) getSupportFragmentManager().findFragmentByTag(FRAG_ARTICLE);
                final HeadlinesFragment hf = (HeadlinesFragment) getSupportFragmentManager().findFragmentByTag(FRAG_HEADLINES);

                Article article = ap.getSelectedArticle();

                if (article == null) return false;

                int itemId = item.getItemId();

                if (itemId == R.id.article_set_labels) {
                    editArticleLabels(article);

                    return true;
                } else if (itemId == R.id.toggle_attachments) {
                    displayAttachments(article);

                    return true;
                } else if (itemId == R.id.article_edit_note) {
                    editArticleNote(article);

                    return true;
                } else if (itemId == R.id.article_set_score) {
                    setArticleScore(article);

                    return true;
                } else if (itemId == R.id.toggle_unread) {
                    article.unread = !article.unread;
                    saveArticleUnread(article);

                    if (hf != null) {
                        hf.notifyUpdated();
                    }
                }

                return false;
            });
		}

		FloatingActionButton fab = findViewById(R.id.detail_fab);

		if (fab != null) {
			if (m_prefs.getBoolean("enable_article_fab", true)) {
				fab.show();

				fab.setOnClickListener(view -> {
                    if (m_activeArticle != null) {
                        openUri(Uri.parse(m_activeArticle.link));
                    }
                });
			} else {
				fab.hide();
			}
		}

        if (savedInstanceState == null) {
			Intent i = getIntent();
			
			if (i.getExtras() != null) {
				boolean shortcutMode = i.getBooleanExtra("shortcut_mode", false);
				
				Log.d(TAG, "is_shortcut_mode: " + shortcutMode);
				
				Feed tmpFeed;
				
				if (shortcutMode) {
					int feedId = i.getIntExtra("feed_id", 0);
					boolean isCat = i.getBooleanExtra("feed_is_cat", false);
					String feedTitle = i.getStringExtra("feed_title");
					
					tmpFeed = new Feed(feedId, feedTitle, isCat);
				} else {
					tmpFeed = i.getParcelableExtra("feed");
				}
				
				final Feed feed = tmpFeed;
				
				final Article article = Application.getInstance().tmpActiveArticle;
				final String searchQuery = i.getStringExtra("searchQuery");

                FragmentTransaction ft = getSupportFragmentManager().beginTransaction();

                final HeadlinesFragment hf = new HeadlinesFragment();
                hf.initialize(feed, article, true);
                hf.setSearchQuery(searchQuery);

                ft.replace(R.id.headlines_fragment, hf, FRAG_HEADLINES);

				ArticlePager af = new ArticlePager();
				af.initialize(article != null ? article : new Article(), feed);
				af.setSearchQuery(searchQuery);

				ft.replace(R.id.article_fragment, af, FRAG_ARTICLE);

				ft.commit();

				if (feed != null)
					setTitle(feed.title);

				initBottomBarMenu();
			}
		}
	}

	@Override
	public void invalidateOptionsMenu() {
		super.invalidateOptionsMenu();

		initBottomBarMenu();
	}

	protected void initBottomBarMenu() {
		if (m_bottomAppBar != null) {
			Menu menu = m_bottomAppBar.getMenu();

			menu.findItem(R.id.article_set_labels).setEnabled(getApiLevel() >= 1);
			menu.findItem(R.id.article_edit_note).setEnabled(getApiLevel() >= 1);

			final ArticlePager ap = (ArticlePager) getSupportFragmentManager().findFragmentByTag(FRAG_ARTICLE);

			if (ap != null) {
				Article article = ap.getSelectedArticle();

				if (article != null) {
					if (article.score > 0) {
						menu.findItem(R.id.article_set_score).setIcon(R.drawable.baseline_trending_up_24);
					} else if (article.score < 0) {
						menu.findItem(R.id.article_set_score).setIcon(R.drawable.baseline_trending_down_24);
					} else {
						menu.findItem(R.id.article_set_score).setIcon(R.drawable.baseline_trending_flat_24);
					}

					menu.findItem(R.id.toggle_unread).setIcon(article.unread ? R.drawable.baseline_mark_email_unread_24 :
							R.drawable.baseline_email_24);

					menu.findItem(R.id.toggle_attachments).setVisible(article.attachments != null && !article.attachments.isEmpty());
				}
			}
		}
	}

	@Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (!isSmallScreen()) {
            findViewById(R.id.headlines_fragment).setVisibility(isPortrait() ? View.GONE : View.VISIBLE);
        }

        m_forceDisableActionMode = isPortrait() || isSmallScreen();
        invalidateOptionsMenu();
    }

	@Override
	protected void refresh() {
		super.refresh();
	}
	
	@Override
	protected void loginSuccess(boolean refresh) {
		Log.d(TAG, "loginSuccess");

		invalidateOptionsMenu();
		
		if (refresh) refresh();
	}
	
	@Override
	public void onSaveInstanceState(Bundle out) {
		super.onSaveInstanceState(out);

		Application.getInstance().save(out);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        Log.d(TAG, "onOptionsItemSelected, unhandled id=" + item.getItemId());
        return super.onOptionsItemSelected(item);
    }
	
	@Override
	public void onResume() {
		super.onResume();
	}

	@Override
	protected void initMenu() {
		super.initMenu();

		if (m_menu != null && getSessionId() != null) {
			m_menu.setGroupVisible(R.id.menu_group_feeds, false);

			m_menu.setGroupVisible(R.id.menu_group_headlines, !isPortrait() && !isSmallScreen());
			m_menu.findItem(R.id.headlines_toggle_sort_order).setVisible(false);

			ArticlePager af = (ArticlePager) getSupportFragmentManager().findFragmentByTag(FRAG_ARTICLE);
			
			m_menu.setGroupVisible(R.id.menu_group_article, af != null);

			m_menu.findItem(R.id.search).setVisible(false);
		}		
	}
	
	@Override
	public void onArticleListSelectionChange(ArticleList m_selectedArticles) {
		invalidateOptionsMenu();
	}

	@Override
	public void onArticleSelected(Article article) {
		onArticleSelected(article, true);
	}

	@Override
	public void onArticleSelected(final Article article, boolean open) {
		
		if (article == null) return;
		
		if (article.unread) {
			article.unread = false;
			saveArticleUnread(article);
		}

		try {
			preloadUriIfAllowed(Uri.parse(article.link));
		} catch (Exception e) {
			e.printStackTrace();
		}

		if (!getSupportActionBar().isShowing()) getSupportActionBar().show();

		if (open) {

			new Handler().postDelayed(() -> {
ArticlePager af = (ArticlePager) getSupportFragmentManager().findFragmentByTag(FRAG_ARTICLE);

                if (af != null) {
                    af.setActiveArticle(article);
                }
            }, 250);

		} else {
			HeadlinesFragment hf = (HeadlinesFragment) getSupportFragmentManager().findFragmentByTag(FRAG_HEADLINES);
			if (hf != null) {
				hf.setActiveArticle(article);
			}
		}

        m_activeArticle = article;

		//Application.getInstance().m_activeArticle = article;
		
		invalidateOptionsMenu();
	}

	@Override
	public void onHeadlinesLoaded(boolean appended) {
		HeadlinesFragment hf = (HeadlinesFragment) getSupportFragmentManager().findFragmentByTag(FRAG_HEADLINES);
		ArticlePager ap = (ArticlePager) getSupportFragmentManager().findFragmentByTag(FRAG_ARTICLE);

        if (ap != null) {
            ap.notifyUpdated();
        }

		if (hf != null) {
			Article article = hf.getActiveArticle();
						
			if (article == null && !hf.getAllArticles().isEmpty()) {

				article = hf.getAllArticles().get(0);

				hf.setActiveArticle(article);

				FragmentTransaction ft = getSupportFragmentManager()
						.beginTransaction();

				ArticlePager af = new ArticlePager();
				af.initialize(article, hf.getFeed());

				ft.replace(R.id.article_fragment, af, FRAG_ARTICLE);
				ft.commitAllowingStateLoss();
			}
		}
	}
	
	@Override
	public void onBackPressed() {
        Intent resultIntent = new Intent();

        Application.getInstance().tmpActiveArticle = m_activeArticle;

        setResult(Activity.RESULT_OK, resultIntent);

		try {
			super.onBackPressed();
		} catch (IllegalStateException e) {
			// java.lang.IllegalStateException: Can not perform this action after onSaveInstanceState
			e.printStackTrace();
		}
    }

	@Override
	public void onPause() {
		super.onPause();

		if (isFinishing()) {
			overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
		}

	}
}
