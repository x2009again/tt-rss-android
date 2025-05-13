package org.fox.ttrss;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.Html;
import android.transition.Fade;
import android.transition.Transition;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.view.ViewCompat;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.amulyakhare.textdrawable.TextDrawable;
import com.amulyakhare.textdrawable.util.ColorGenerator;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.GlideDrawable;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.GlideDrawableImageViewTarget;
import com.bumptech.glide.request.target.Target;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import org.fox.ttrss.glide.ProgressTarget;
import org.fox.ttrss.types.Article;
import org.fox.ttrss.types.ArticleList;
import org.fox.ttrss.types.Attachment;
import org.fox.ttrss.types.Feed;
import org.fox.ttrss.util.HeadlinesDiffutilCallback;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import jp.wasabeef.glide.transformations.CropCircleTransformation;

public class HeadlinesFragment extends androidx.fragment.app.Fragment implements LoaderManager.LoaderCallbacks<ArticleList> {

	@NonNull
	@Override
	public Loader<ArticleList> onCreateLoader(int id, @Nullable Bundle args) {
		return new HeadlinesLoader(getContext(), m_feed, m_activity.getResizeWidth());
	}

	@Override
	public void onLoadFinished(@NonNull Loader<ArticleList> loader, ArticleList data) {
		Log.d(TAG, "onLoadFinished loader=" + loader + " size=" + (data != null ? data.size() : "N/A (null)"));

		HeadlinesLoader headlinesLoader = (HeadlinesLoader) loader;
		ArticleList sharedArticles = Application.getArticles();

		// successful update
		if (data != null) {

			// detail activity does not use footers
			if (!(m_activity instanceof DetailActivity)) {

				if (headlinesLoader.lazyLoadEnabled())
					data.add(new Article(Article.TYPE_LOADMORE));

				data.add(new Article(Article.TYPE_AMR_FOOTER));
			}

			DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new HeadlinesDiffutilCallback(sharedArticles, data));

			sharedArticles.clear();
			sharedArticles.addAll(data);

			diffResult.dispatchUpdatesTo(m_adapter);

			if (headlinesLoader.getFirstIdChanged())
				Snackbar.make(getView(), R.string.headlines_row_top_changed, Snackbar.LENGTH_LONG)
						.setAction(R.string.reload, v -> refresh(false)).show();

			if (!headlinesLoader.getAppend())
				m_list.scrollToPosition(0);

			m_listener.onHeadlinesLoaded(headlinesLoader.getAppend());

		} else {
			if (headlinesLoader.getLastError() == ApiCommon.ApiError.LOGIN_FAILED) {
				m_activity.login();
			} else {
				if (headlinesLoader.getLastErrorMessage() != null) {
					m_activity.toast(m_activity.getString(headlinesLoader.getErrorMessage()) + "\n" + headlinesLoader.getLastErrorMessage());
				} else {
					m_activity.toast(headlinesLoader.getErrorMessage());
				}
			}
		}

		if (m_swipeLayout != null)
			m_swipeLayout.setRefreshing(false);
	}

	@Override
	public void onLoaderReset(@NonNull Loader<ArticleList> loader) {

	}

	public enum ArticlesSelection { ALL, NONE, UNREAD }

    public static final int FLAVOR_IMG_MIN_SIZE = 128;
	public static final int THUMB_IMG_MIN_SIZE = 32;

	private final String TAG = this.getClass().getSimpleName();

	private Feed m_feed;
	private int m_activeArticleId;
	private String m_searchQuery = "";
	private HeadlinesLoader m_loader;

	private SharedPreferences m_prefs;

	private ArticleListAdapter m_adapter;
	private final ArticleList m_readArticles = new ArticleList();
	private HeadlinesEventListener m_listener;
	private OnlineActivity m_activity;
	private SwipeRefreshLayout m_swipeLayout;
    private boolean m_compactLayoutMode = false;
    private RecyclerView m_list;
	private LinearLayoutManager m_layoutManager;

	private MediaPlayer m_mediaPlayer;
	private TextureView m_activeTexture;

	public ArticleList getSelectedArticles() {
        ArticleList tmp = new ArticleList();

        for (Article a : Application.getArticles()) {
            if (a.selected) tmp.add(a);
        }

		return tmp;
	}

	public void initialize(Feed feed) {
		m_feed = feed;
	}

	public void initialize(Feed feed, int activeArticleId, boolean compactMode) {
		m_feed = feed;
        m_compactLayoutMode = compactMode;
		m_activeArticleId = activeArticleId;
	}

	public boolean onArticleMenuItemSelected(MenuItem item, Article article, int position) {

		if (article == null) return false;

        int itemId = item.getItemId();
        if (itemId == R.id.article_set_labels) {
            m_activity.editArticleLabels(article);
            return true;
        } else if (itemId == R.id.article_edit_note) {
            m_activity.editArticleNote(article);
            return true;
        } else if (itemId == R.id.headlines_article_unread) {
            article.unread = !article.unread;
            m_activity.saveArticleUnread(article);
            m_adapter.notifyItemChanged(position);
            return true;
        } else if (itemId == R.id.headlines_article_link_copy) {
            m_activity.copyToClipboard(article.link);
            return true;
        } else if (itemId == R.id.headlines_article_link_open) {
            m_activity.openUri(Uri.parse(article.link));

            if (article.unread) {
                article.unread = false;
                m_activity.saveArticleUnread(article);

                m_adapter.notifyItemChanged(position);
            }
            return true;
        } else if (itemId == R.id.headlines_share_article) {
            m_activity.shareArticle(article);
            return true;
        } else if (itemId == R.id.catchup_above) {
            final Article fa = article;

            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext())
				.setMessage(R.string.confirm_catchup_above)
				.setPositiveButton(R.string.dialog_ok,
					(dialog, which) -> catchupAbove(fa))
				.setNegativeButton(R.string.dialog_cancel,
					(dialog, which) -> { });

            Dialog dialog = builder.create();
            dialog.show();
            return true;
        }
        Log.d(TAG, "onArticleMenuItemSelected, unhandled id=" + item.getItemId());
        return false;
    }

	private void catchupAbove(Article article) {
		ArticleList tmp = new ArticleList();
		for (Article a : Application.getArticles()) {
            if (article.equalsById(a))
                break;

            if (a.unread) {
                a.unread = false;
                tmp.add(a);
            }
        }

		if (!tmp.isEmpty()) {
			m_activity.setArticlesUnread(tmp, Article.UPDATE_SET_FALSE);
			m_adapter.notifyDataSetChanged();
        }
	}

	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item
				.getMenuInfo();

		if (info != null) {

			try {
				Article article = Application.getArticles().get(info.position);

				if (!onArticleMenuItemSelected(item, article, info.position))
					return super.onContextItemSelected(item);
			} catch (IndexOutOfBoundsException e) {
				e.printStackTrace();
			}
		}

		return super.onContextItemSelected(item);
	}

    public HeadlinesFragment() {
        super();

        Transition fade = new Fade();

        setEnterTransition(fade);
        setReenterTransition(fade);
    }

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
	    ContextMenuInfo menuInfo) {

		getActivity().getMenuInflater().inflate(R.menu.context_headlines, menu);

		menu.findItem(R.id.article_set_labels).setEnabled(m_activity.getApiLevel() >= 1);
		menu.findItem(R.id.article_edit_note).setEnabled(m_activity.getApiLevel() >= 1);

		super.onCreateContextMenu(menu, v, menuInfo);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (savedInstanceState != null) {
			m_feed = savedInstanceState.getParcelable("m_feed");
			m_activeArticleId = savedInstanceState.getInt("m_activeArticleId");
			m_searchQuery = savedInstanceState.getString("m_searchQuery");
			m_compactLayoutMode = savedInstanceState.getBoolean("m_compactLayoutMode");
		}

		setRetainInstance(true);

		Glide.get(getContext()).clearMemory();
	}

	@Override
	public void onSaveInstanceState(Bundle out) {
		super.onSaveInstanceState(out);

		out.putParcelable("m_feed", m_feed);
		out.putInt("m_activeArticleId", m_activeArticleId);
		out.putString("m_searchQuery", m_searchQuery);
		out.putBoolean("m_compactLayoutMode", m_compactLayoutMode);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

		String headlineMode = m_prefs.getString("headline_mode", "HL_DEFAULT");

        if ("HL_COMPACT".equals(headlineMode) || "HL_COMPACT_NOIMAGES".equals(headlineMode))
            m_compactLayoutMode = true;

		DisplayMetrics metrics = new DisplayMetrics();
		getActivity().getWindowManager().getDefaultDisplay().getMetrics(metrics);

		View view = inflater.inflate(R.layout.fragment_headlines, container, false);

		m_swipeLayout = view.findViewById(R.id.headlines_swipe_container);

	    m_swipeLayout.setOnRefreshListener(() -> refresh(false));

		m_list = view.findViewById(R.id.headlines_list);
		registerForContextMenu(m_list);

		m_layoutManager = new LinearLayoutManager(m_activity.getApplicationContext());
		m_list.setLayoutManager(m_layoutManager);
		m_list.setItemAnimator(new DefaultItemAnimator());

		m_adapter = new ArticleListAdapter(Application.getArticles());

		m_list.setAdapter(m_adapter);

		if (m_prefs.getBoolean("headlines_swipe_to_dismiss", true) && !m_prefs.getBoolean("headlines_mark_read_scroll", false) ) {

			ItemTouchHelper swipeHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {

				@Override
				public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
					return false;
				}

				@Override
				public int getSwipeDirs(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {

					int position = viewHolder.getBindingAdapterPosition();

					try {
						Article article = Application.getArticles().get(position);

						if (article == null || article.id < 0)
							return 0;
					} catch (IndexOutOfBoundsException e) {
						return 0;
					}

					return super.getSwipeDirs(recyclerView, viewHolder);
				}

				@Override
				public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {

					final int adapterPosition = viewHolder.getBindingAdapterPosition();

                    try {
						final Article article = Application.getArticles().get(adapterPosition);
						final boolean wasUnread;

						if (article != null && article.id > 0) {
							if (article.unread) {
								wasUnread = true;

								article.unread = false;
								m_activity.saveArticleUnread(article);
							} else {
								wasUnread = false;
							}

							Application.getArticles().remove(adapterPosition);
							m_adapter.notifyItemRemoved(adapterPosition);

							Snackbar.make(m_list, R.string.headline_undo_row_prompt, Snackbar.LENGTH_LONG)
									.setAction(getString(R.string.headline_undo_row_button), v -> {

                                        if (wasUnread) {
                                            article.unread = true;
                                            m_activity.saveArticleUnread(article);
                                        }

                                        Application.getArticles().add(adapterPosition, article);
                                        m_adapter.notifyItemInserted(adapterPosition);
                                        m_adapter.notifyItemRangeChanged(adapterPosition, 1);
                                    }).show();

						}

					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			});

			swipeHelper.attachToRecyclerView(m_list);

		}

		m_list.setOnScrollListener(new RecyclerView.OnScrollListener() {
			@Override
			public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
				super.onScrollStateChanged(recyclerView, newState);

				if (newState == RecyclerView.SCROLL_STATE_IDLE) {
					if (m_prefs.getBoolean("headlines_mark_read_scroll", false)) {
						if (!m_readArticles.isEmpty()) {
							m_activity.toggleArticlesUnread(m_readArticles);

							for (Article a : m_readArticles) {
								a.unread = false;

								m_adapter.notifyItemChanged(Application.getArticles().getPositionById(a.id));
							}

							if (m_feed != null)
								m_feed.unread -= m_readArticles.size();

							m_readArticles.clear();

							new Handler().postDelayed(() -> m_activity.refresh(false), 100);
						}
					}

					int lastVisibleItem = m_layoutManager.findLastVisibleItemPosition();

					if (lastVisibleItem >= Application.getArticles().size() - 5) {
						refresh(true);
					}
				}
			}

			@Override
			public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
				super.onScrolled(recyclerView, dx, dy);

				int firstVisibleItem = m_layoutManager.findFirstVisibleItemPosition();
				int lastVisibleItem = m_layoutManager.findLastVisibleItemPosition();

				// Log.d(TAG, "onScrolled: FVI=" + firstVisibleItem + " LVI=" + lastVisibleItem);

				if (m_prefs.getBoolean("headlines_mark_read_scroll", false)) {

					for (int i = 0; i < firstVisibleItem; i++) {
						try {
							Article article = Application.getArticles().get(i);

							if (article.unread && !m_readArticles.contains(article)) {
								Log.d(TAG, "adding to mark read=" + article.title);

								m_readArticles.add(article);
							}
						} catch (IndexOutOfBoundsException e) {
							e.printStackTrace();
						}
					}
				}

				/* if (lastVisibleItem >= Application.getArticles().size() - 5)
					new Handler().postDelayed(() -> refresh(true), 1000); */
			}
		});

        if (m_activity.isSmallScreen() && m_feed != null) {
            m_activity.setTitle(m_feed.title);
        }

		Log.d(TAG, "onCreateView, feed=" + m_feed);

		return view;
	}

	@Override
	public void onResume() {
		super.onResume();

        if (Application.getArticles().getSizeWithoutFooters() == 0) {
            refresh(false);
        } else {
			Article activeArticle = Application.getArticles().getById(m_activeArticleId);

			if (activeArticle != null)
				scrollToArticle(activeArticle);
		}

		m_activity.invalidateOptionsMenu();
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		m_prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
		m_activity = (OnlineActivity) activity;
		m_listener = (HeadlinesEventListener) activity;
	}

	public void refresh(final boolean append) {
		// if we try to initLoader() all the time, onLoadFinished() might be sent twice
		// https://stackoverflow.com/questions/11293441/android-loadercallbacks-onloadfinished-called-twice
		if (m_loader == null) {
			m_loader = (HeadlinesLoader) LoaderManager.getInstance(this).
					initLoader(Application.LOADER_HEADLINES, null, this);
		}

		m_loader.setSearchQuery(getSearchQuery());
		m_loader.startLoading(append);
	}

	static class ArticleViewHolder extends RecyclerView.ViewHolder {
		public View view;
		public Article article;

		public TextView titleView;
		public TextView feedTitleView;
		public MaterialButton markedView;
		public MaterialButton scoreView;
		public MaterialButton publishedView;
		public TextView excerptView;
		public ImageView flavorImageView;
		public ImageView flavorVideoKindView;
		public TextView authorView;
		public TextView dateView;
		public CheckBox selectionBoxView;
		public MaterialButton menuButtonView;
		public ViewGroup flavorImageHolder;
		public ProgressBar flavorImageLoadingBar;
        public View headlineFooter;
        public ImageView textImage;
        public ImageView textChecked;
		public View headlineHeader;
		public View flavorImageOverflow;
		public TextureView flavorVideoView;
		public MaterialButton attachmentsView;
		public ProgressTarget<String, GlideDrawable> flavorProgressTarget;

		public ArticleViewHolder(View v) {
			super(v);

			view = v;

			view.getViewTreeObserver().addOnPreDrawListener(() -> {
                View flavorImage = view.findViewById(R.id.flavor_image);

                if (flavorImage != null) {
                    article.flavorViewHeight = flavorImage.getMeasuredHeight();
                }

                return true;
            });

			titleView = v.findViewById(R.id.title);

			feedTitleView = v.findViewById(R.id.feed_title);
			markedView = v.findViewById(R.id.marked);
			scoreView = v.findViewById(R.id.score);
			publishedView = v.findViewById(R.id.published);
			excerptView = v.findViewById(R.id.excerpt);
			flavorImageView = v.findViewById(R.id.flavor_image);
			flavorVideoKindView = v.findViewById(R.id.flavor_video_kind);
			authorView = v.findViewById(R.id.author);
			dateView = v.findViewById(R.id.date);
			selectionBoxView = v.findViewById(R.id.selected);
			menuButtonView = v.findViewById(R.id.article_menu_button);
			flavorImageHolder = v.findViewById(R.id.flavorImageHolder);
			flavorImageLoadingBar = v.findViewById(R.id.flavorImageLoadingBar);
			textImage = v.findViewById(R.id.text_image);
			textChecked = v.findViewById(R.id.text_checked);
			headlineHeader = v.findViewById(R.id.headline_header);
			flavorImageOverflow = v.findViewById(R.id.gallery_overflow);
			flavorVideoView = v.findViewById(R.id.flavor_video);
			attachmentsView = v.findViewById(R.id.attachments);

			if (flavorImageView != null && flavorImageLoadingBar != null) {
				flavorProgressTarget = new FlavorProgressTarget<>(new GlideDrawableImageViewTarget(flavorImageView), flavorImageLoadingBar);
			}
		}

		public void clearAnimation() {
			view.clearAnimation();
		}
	}

	private static class FlavorProgressTarget<Z> extends ProgressTarget<String, Z> {
		private final ProgressBar progress;
		public FlavorProgressTarget(Target<Z> target, ProgressBar progress) {
			super(target);
			this.progress = progress;
		}

		@Override public float getGranualityPercentage() {
			return 0.1f; // this matches the format string for #text below
		}

		@Override protected void onConnecting() {
			progress.setIndeterminate(true);
			progress.setVisibility(View.VISIBLE);
		}
		@Override protected void onDownloading(long bytesRead, long expectedLength) {
			progress.setIndeterminate(false);
			progress.setProgress((int)(100 * bytesRead / expectedLength));
		}
		@Override protected void onDownloaded() {
			progress.setIndeterminate(true);
		}
		@Override protected void onDelivered() {
			progress.setVisibility(View.INVISIBLE);
		}
	}

	private class ArticleListAdapter extends RecyclerView.Adapter<ArticleViewHolder>  {
		private final ArticleList items;

		public static final int VIEW_NORMAL = 0;
		public static final int VIEW_UNREAD = 1;
		public static final int VIEW_ACTIVE = 2;
		public static final int VIEW_ACTIVE_UNREAD = 3;
		public static final int VIEW_LOADMORE = 4;
		public static final int VIEW_AMR_FOOTER = 5;

		public static final int VIEW_COUNT = VIEW_AMR_FOOTER + 1;

		private final Integer[] origTitleColors = new Integer[VIEW_COUNT];

        private final ColorGenerator m_colorGenerator = ColorGenerator.DEFAULT;
        private final TextDrawable.IBuilder m_drawableBuilder = TextDrawable.builder().round();

		boolean flavorImageEnabled;
		private final int m_screenHeight;
		private int m_lastAddedPosition;

		private final ConnectivityManager m_cmgr;

		private boolean canShowFlavorImage() {
			if (flavorImageEnabled) {
				if (m_prefs.getBoolean("headline_images_wifi_only", false)) {
					// why do i have to get this service every time instead of using a member variable :(
					NetworkInfo wifi = m_cmgr.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

					if (wifi != null)
						return wifi.isConnected();

				} else {
					return true;
				}
			}

			return false;
		}

		public ArticleListAdapter(ArticleList items) {
			super();
			this.items = items;

			Display display = m_activity.getWindowManager().getDefaultDisplay();
			Point size = new Point();
			display.getSize(size);
			m_screenHeight = size.y;

			String headlineMode = m_prefs.getString("headline_mode", "HL_DEFAULT");
			flavorImageEnabled = "HL_DEFAULT".equals(headlineMode) || "HL_COMPACT".equals(headlineMode);

			m_cmgr = (ConnectivityManager) m_activity.getSystemService(Context.CONNECTIVITY_SERVICE);
		}

		@Override
		public ArticleViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

			int layoutId = m_compactLayoutMode ? R.layout.headlines_row_compact : R.layout.headlines_row;

			switch (viewType) {
				case VIEW_AMR_FOOTER:
					layoutId = R.layout.headlines_footer;
					break;
				case VIEW_LOADMORE:
					layoutId = R.layout.headlines_row_loadmore;
					break;
				case VIEW_UNREAD:
					layoutId = m_compactLayoutMode ? R.layout.headlines_row_compact_unread : R.layout.headlines_row_unread;
					break;
				case VIEW_ACTIVE:
					layoutId = m_compactLayoutMode ? R.layout.headlines_row_compact_active : R.layout.headlines_row;
					break;
				case VIEW_ACTIVE_UNREAD:
					layoutId = m_compactLayoutMode ? R.layout.headlines_row_compact_active_unread : R.layout.headlines_row_unread;
					break;
			}

			View v = LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false);

			//registerForContextMenu(v);

			return new ArticleViewHolder(v);
		}

		@Override
		public void onBindViewHolder(final ArticleViewHolder holder, int position) {
			holder.article = items.get(position);

			int headlineFontSize = m_prefs.getInt("headlines_font_size_sp_int", 13);
			int headlineSmallFontSize = Math.max(10, Math.min(18, headlineFontSize - 2));

			final Article article = holder.article;

			if (article.id == Article.TYPE_AMR_FOOTER && m_prefs.getBoolean("headlines_mark_read_scroll", false)) {
				WindowManager wm = (WindowManager) m_activity.getSystemService(Context.WINDOW_SERVICE);
				Display display = wm.getDefaultDisplay();
				int screenHeight = (int)(display.getHeight() * 1.5);

				holder.view.setLayoutParams(new ListView.LayoutParams(ListView.LayoutParams.MATCH_PARENT, screenHeight));
			}

			// nothing else of interest for those below anyway
			if (article.id < 0) return;

			holder.view.setOnLongClickListener(v -> {
                m_list.showContextMenuForChild(v);
                return true;
            });

			holder.view.setOnClickListener(v -> {
                m_listener.onArticleSelected(article);
				setActiveArticleId(article.id);

                // only set active article when it makes sense (in DetailActivity)
                //if (getActivity() instanceof DetailActivity) {
                    //m_activeArticleId = article.id;
                    //m_adapter.notifyDataSetChanged();
                //}
            });

			// block footer clicks to make button/selection clicking easier
			if (holder.headlineFooter != null) {
				holder.headlineFooter.setOnClickListener(view -> {
                    //
                });
			}

			if (holder.textImage != null) {
				updateTextCheckedState(holder, article);

				holder.textImage.setOnClickListener(view -> {
                    Log.d(TAG, "textImage : onclicked");

                    article.selected = !article.selected;

                    updateTextCheckedState(holder, article);

                    m_listener.onArticleListSelectionChange(getSelectedArticles());

                    Log.d(TAG, "num selected: " + getSelectedArticles().size());
                });
				ViewCompat.setTransitionName(holder.textImage, "gallery:" + article.flavorImageUri);

				if (article.flavorImage != null) {

					holder.textImage.setOnLongClickListener(v -> {

                        openGalleryForType(article, holder, holder.textImage);

                        return true;
                    });

				}
			}

			if (holder.titleView != null) {
				holder.titleView.setText(Html.fromHtml(article.title));
				holder.titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.min(21, headlineFontSize + 3));

				adjustTitleTextView(article.score, holder.titleView, position);
			}

			if (holder.feedTitleView != null) {
				if (article.feed_title != null && m_feed != null && (m_feed.is_cat || m_feed.id < 0)) {
					holder.feedTitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, headlineSmallFontSize);
					holder.feedTitleView.setText(article.feed_title);

				} else {
					holder.feedTitleView.setVisibility(View.GONE);
				}

			}

			TypedValue tvTertiary = new TypedValue();
			m_activity.getTheme().resolveAttribute(R.attr.colorTertiary, tvTertiary, true);

			TypedValue tvPrimary = new TypedValue();
			m_activity.getTheme().resolveAttribute(R.attr.colorPrimary, tvPrimary, true);

			if (holder.markedView != null) {
				TypedValue tv = new TypedValue();
				m_activity.getTheme().resolveAttribute(article.marked ? R.attr.ic_star : R.attr.ic_star_outline, tv, true);

				holder.markedView.setIconResource(tv.resourceId);


				if (article.marked)
					holder.markedView.setIconTint(ColorStateList.valueOf(tvTertiary.data));
				else
					holder.markedView.setIconTint(ColorStateList.valueOf(tvPrimary.data));

				holder.markedView.setOnClickListener(v -> {
                    article.marked = !article.marked;

                    m_adapter.notifyItemChanged(m_list.getChildAdapterPosition(holder.view));

                    m_activity.saveArticleMarked(article);
                });
			}

			if (holder.scoreView != null) {
				TypedValue tv = new TypedValue();
				int scoreAttr = R.attr.ic_action_trending_flat;

				if (article.score > 0)
					scoreAttr = R.attr.ic_action_trending_up;
				else if (article.score < 0)
					scoreAttr = R.attr.ic_action_trending_down;

				m_activity.getTheme().resolveAttribute(scoreAttr, tv, true);

				holder.scoreView.setIconResource(tv.resourceId);

				if (article.score > Article.SCORE_HIGH)
					holder.scoreView.setIconTint(ColorStateList.valueOf(tvTertiary.data));
				else
					holder.scoreView.setIconTint(ColorStateList.valueOf(tvPrimary.data));

				if (m_activity.getApiLevel() >= 16) {
					holder.scoreView.setOnClickListener(v -> {
                        final EditText edit = new EditText(getActivity());
                        edit.setText(String.valueOf(article.score));

                        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext())
                                .setTitle(R.string.score_for_this_article)
                                .setPositiveButton(R.string.set_score,
									(dialog, which) -> {
										try {
                                            article.score = Integer.parseInt(edit.getText().toString());
											m_activity.saveArticleScore(article);
											m_adapter.notifyItemChanged(m_list.getChildAdapterPosition(holder.view));
										} catch (NumberFormatException e) {
											m_activity.toast(R.string.score_invalid);
											e.printStackTrace();
										}
									})
								.setNegativeButton(getString(R.string.cancel),
									(dialog, which) -> { }).setView(edit);

                        Dialog dialog = builder.create();
                        dialog.show();
                    });
				}
			}

			if (holder.publishedView != null) {
				TypedValue tv = new TypedValue();
				m_activity.getTheme().resolveAttribute(R.attr.ic_rss_box, tv, true);

				holder.publishedView.setIconResource(tv.resourceId);

				if (article.published)
					holder.publishedView.setIconTint(ColorStateList.valueOf(tvTertiary.data));
				else
					holder.publishedView.setIconTint(ColorStateList.valueOf(tvPrimary.data));

				holder.publishedView.setOnClickListener(v -> {
                    article.published = !article.published;
                    m_adapter.notifyItemChanged(m_list.getChildAdapterPosition(holder.view));

                    m_activity.saveArticlePublished(article);
                });
			}

			if (holder.attachmentsView != null) {
				if (article.attachments != null && !article.attachments.isEmpty()) {
					holder.attachmentsView.setVisibility(View.VISIBLE);

					holder.attachmentsView.setOnClickListener(v -> m_activity.displayAttachments(article));

				} else {
					holder.attachmentsView.setVisibility(View.GONE);
				}
			}

			if (holder.excerptView != null) {
				if (!m_prefs.getBoolean("headlines_show_content", true)) {
					holder.excerptView.setVisibility(View.GONE);
				} else {
					String excerpt = "";

					try {
						if (article.excerpt != null) {
							excerpt = article.excerpt;
						} else if (article.articleDoc != null) {
							excerpt = article.articleDoc.text();

							if (excerpt.length() > CommonActivity.EXCERPT_MAX_LENGTH)
								excerpt = excerpt.substring(0, CommonActivity.EXCERPT_MAX_LENGTH) + "…";
						}
					} catch (Exception e) {
						e.printStackTrace();
						excerpt = "";
					}

					holder.excerptView.setTextSize(TypedValue.COMPLEX_UNIT_SP, headlineFontSize);
					holder.excerptView.setText(excerpt);

					if (!excerpt.isEmpty()) {
						holder.excerptView.setVisibility(View.VISIBLE);
					} else {
						holder.excerptView.setVisibility(View.GONE);
					}

					if (!canShowFlavorImage()) {
						holder.excerptView.setPadding(holder.excerptView.getPaddingLeft(),
								0,
								holder.excerptView.getPaddingRight(),
								holder.excerptView.getPaddingBottom());
					}
				}
			}

			if (!m_compactLayoutMode && holder.flavorImageHolder != null) {

				/* reset to default in case of convertview */
				holder.flavorImageLoadingBar.setVisibility(View.GONE);
				holder.flavorImageLoadingBar.setIndeterminate(false);
				holder.flavorImageView.setVisibility(View.GONE);
				holder.flavorVideoKindView.setVisibility(View.GONE);
				holder.flavorImageOverflow.setVisibility(View.GONE);
				holder.flavorVideoView.setVisibility(View.GONE);

				Glide.clear(holder.flavorImageView);

				// this is needed if our flavor image goes behind base listview element
				holder.headlineHeader.setOnClickListener(v -> {
                    m_listener.onArticleSelected(article);

                    // only set active article when it makes sense (in DetailActivity)
                    if (getActivity() instanceof DetailActivity) {
                        m_activeArticleId = article.id;
                        m_adapter.notifyDataSetChanged();
                    }
                });

				holder.headlineHeader.setOnLongClickListener(v -> {
                    m_list.showContextMenuForChild(holder.view);

                    return true;
                });

				if (canShowFlavorImage() && article.flavorImageUri != null && holder.flavorImageView != null) {
					if (holder.flavorImageOverflow != null) {
						holder.flavorImageOverflow.setOnClickListener(v -> {
                            PopupMenu popup = new PopupMenu(getActivity(), holder.flavorImageOverflow);
                            MenuInflater inflater = popup.getMenuInflater();
                            inflater.inflate(R.menu.content_gallery_entry, popup.getMenu());

                            popup.setOnMenuItemClickListener(item -> {

                                Uri mediaUri = Uri.parse(article.flavorStreamUri != null ? article.flavorStreamUri : article.flavorImageUri);

                                int itemId = item.getItemId();
                                if (itemId == R.id.article_img_open) {
                                    m_activity.openUri(mediaUri);
                                    return true;
                                } else if (itemId == R.id.article_img_copy) {
                                    m_activity.copyToClipboard(mediaUri.toString());
                                    return true;
                                } else if (itemId == R.id.article_img_share) {
                                    m_activity.shareImageFromUri(mediaUri.toString());
                                    return true;
                                } else if (itemId == R.id.article_img_share_url) {
                                    m_activity.shareText(mediaUri.toString());
                                    return true;
                                } else if (itemId == R.id.article_img_view_caption) {
                                    m_activity.displayImageCaption(article.flavorImageUri, article.content);
                                    return true;
                                }
                                return false;
                            });

                            popup.show();
                        });

						holder.flavorImageView.setOnLongClickListener(v -> {
                            m_list.showContextMenuForChild(holder.view);
                            return true;
                        });
					}

					holder.flavorImageView.setVisibility(View.VISIBLE);
					holder.flavorImageView.setMaxHeight((int)(m_screenHeight * 0.6f));

					// prevent lower listiew entries from jumping around if this row is modified
					if (article.flavorViewHeight > 0) {
						FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) holder.flavorImageView.getLayoutParams();
						lp.height = article.flavorViewHeight;
					}

					holder.flavorProgressTarget.setModel(article.flavorImageUri);

					try {

						Glide.with(getContext())
								.load(article.flavorImageUri)
								//.dontTransform()
								.diskCacheStrategy(DiskCacheStrategy.ALL)
								.skipMemoryCache(false)
								.listener(new RequestListener<String, GlideDrawable>() {
									@Override
									public boolean onException(Exception e, String model, Target<GlideDrawable> target, boolean isFirstResource) {

										holder.flavorImageLoadingBar.setVisibility(View.GONE);
										holder.flavorImageView.setVisibility(View.GONE);

										return false;
									}

									@Override
									public boolean onResourceReady(GlideDrawable resource, String model, Target<GlideDrawable> target, boolean isFromMemoryCache, boolean isFirstResource) {

										holder.flavorImageLoadingBar.setVisibility(View.GONE);

										if (resource.getIntrinsicWidth() > FLAVOR_IMG_MIN_SIZE && resource.getIntrinsicHeight() > FLAVOR_IMG_MIN_SIZE) {

											holder.flavorImageView.setVisibility(View.VISIBLE);
											holder.flavorImageOverflow.setVisibility(View.VISIBLE);

											adjustVideoKindView(holder, article);

											return false;
										} else {

											holder.flavorImageOverflow.setVisibility(View.GONE);
											holder.flavorImageView.setVisibility(View.GONE);

											return true;
										}
									}
								})
								.into(holder.flavorProgressTarget);
					} catch (OutOfMemoryError e) {
						e.printStackTrace();
					}
				}

				if (m_prefs.getBoolean("inline_video_player", false) && article.flavorImage != null &&
						"video".equalsIgnoreCase(article.flavorImage.tagName()) && article.flavorStreamUri != null) {

					holder.flavorImageView.setOnLongClickListener(v -> {
                        releaseSurface();
                        openGalleryForType(article, holder, holder.flavorImageView);
                        return true;
                    });

					holder.flavorVideoView.setOnLongClickListener(v -> {
                        releaseSurface();
                        openGalleryForType(article, holder, holder.flavorImageView);
                        return true;
                    });

					holder.flavorImageView.setOnClickListener(view -> {
                        releaseSurface();
                        m_mediaPlayer = new MediaPlayer();

                        holder.flavorVideoView.setVisibility(View.VISIBLE);
                        final ProgressBar bar = holder.flavorImageLoadingBar;

                        bar.setIndeterminate(true);
                        bar.setVisibility(View.VISIBLE);

                        holder.flavorVideoView.setOnClickListener(v -> {
							try {
								if (m_mediaPlayer.isPlaying())
									m_mediaPlayer.pause();
								else
									m_mediaPlayer.start();
								} catch (IllegalStateException e) {
									releaseSurface();
								}
							});

                        m_activeTexture = holder.flavorVideoView;

                        ViewGroup.LayoutParams lp = m_activeTexture.getLayoutParams();

                        Drawable drawable = holder.flavorImageView.getDrawable();

                        if (drawable != null) {

                            float aspect = drawable.getIntrinsicWidth() / (float) drawable.getIntrinsicHeight();

                            lp.height = holder.flavorImageView.getMeasuredHeight();
                            lp.width = (int) (lp.height * aspect);

                            m_activeTexture.setLayoutParams(lp);
                        }

                        holder.flavorVideoView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                                 @Override
                                 public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                                     try {
                                         m_mediaPlayer.setSurface(new Surface(surface));

                                         m_mediaPlayer.setDataSource(article.flavorStreamUri);

                                         m_mediaPlayer.setOnPreparedListener(mp -> {
                                             try {
												 bar.setVisibility(View.GONE);
                                                 mp.setLooping(true);
                                                 mp.start();
                                             } catch (IllegalStateException e) {
                                                 e.printStackTrace();
                                             }
                                         });

                                         m_mediaPlayer.prepareAsync();
                                     } catch (Exception e) {
                                         e.printStackTrace();
                                     }

                                 }

                                 @Override
                                 public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

                                 }

                                 @Override
                                 public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                                     try {
                                         m_mediaPlayer.release();
                                     } catch (Exception e) {
                                         e.printStackTrace();
                                     }
                                     return false;
                                 }

                                 @Override
                                 public void onSurfaceTextureUpdated(SurfaceTexture surface) {

                                 }
                             }
                        );

                    });

				} else {
					holder.flavorImageView.setOnClickListener(view -> openGalleryForType(article, holder, holder.flavorImageView));
				}
			}

			String articleAuthor = article.author != null ? article.author : "";

			if (holder.authorView != null) {
				holder.authorView.setTextSize(TypedValue.COMPLEX_UNIT_SP, headlineSmallFontSize);

				if (!articleAuthor.isEmpty()) {
					holder.authorView.setText(getString(R.string.author_formatted, articleAuthor));
				} else {
					holder.authorView.setText("");
				}
			}

			if (holder.dateView != null) {
				holder.dateView.setTextSize(TypedValue.COMPLEX_UNIT_SP, headlineSmallFontSize);

				Date d = new Date((long)article.updated * 1000);
				Date now = new Date();
				long half_a_year_ago = now.getTime()/1000L - 182*24*60*60;

				DateFormat df;

				if (now.getYear() == d.getYear() && now.getMonth() == d.getMonth() && now.getDay() == d.getDay()) {
					df = new SimpleDateFormat("HH:mm");
				} else if (article.updated > half_a_year_ago) {
					df = new SimpleDateFormat("MMM dd");
				} else {
					df = new SimpleDateFormat("MMM yyyy");
				}

				df.setTimeZone(TimeZone.getDefault());
				holder.dateView.setText(df.format(d));
			}


			if (holder.selectionBoxView != null) {
				holder.selectionBoxView.setChecked(article.selected);
				holder.selectionBoxView.setOnClickListener(view -> {
                    CheckBox cb = (CheckBox)view;

                    article.selected = cb.isChecked();

                    m_listener.onArticleListSelectionChange(getSelectedArticles());

                    Log.d(TAG, "num selected: " + getSelectedArticles().size());
                });
			}

			if (holder.menuButtonView != null) {

				holder.menuButtonView.setOnClickListener(v -> {

                    PopupMenu popup = new PopupMenu(getActivity(), v);
                    MenuInflater inflater = popup.getMenuInflater();
                    inflater.inflate(R.menu.context_headlines, popup.getMenu());

                    popup.getMenu().findItem(R.id.article_set_labels).setEnabled(m_activity.getApiLevel() >= 1);
                    popup.getMenu().findItem(R.id.article_edit_note).setEnabled(m_activity.getApiLevel() >= 1);

                    popup.setOnMenuItemClickListener(item -> onArticleMenuItemSelected(item, article,
							m_list.getChildAdapterPosition(holder.view)));

                    popup.show();
                });
			}
		}

		@Override
		public int getItemViewType(int position) {
			Article a = items.get(position);

			if (a.id == Article.TYPE_AMR_FOOTER) {
				return VIEW_AMR_FOOTER;
			} else if (a.id == Article.TYPE_LOADMORE) {
				return VIEW_LOADMORE;
			} else if (a.id == m_activeArticleId && a.unread) {
				return VIEW_ACTIVE_UNREAD;
			} else if (a.id == m_activeArticleId) {
				return VIEW_ACTIVE;
			} else if (a.unread) {
				return VIEW_UNREAD;
			} else {
				return VIEW_NORMAL;
			}
		}

		@Override
		public int getItemCount() {
			return items.size();
		}

		private void updateTextCheckedState(final ArticleViewHolder holder, final Article article) {
            String tmp = !article.title.isEmpty() ? article.title.substring(0, 1).toUpperCase() : "?";

            if (article.selected) {
				holder.textImage.setImageDrawable(m_drawableBuilder.build(" ", 0xff616161));
                holder.textChecked.setVisibility(View.VISIBLE);
            } else {
				final Drawable textDrawable = m_drawableBuilder.build(tmp, m_colorGenerator.getColor(article.title));

				holder.textImage.setImageDrawable(textDrawable);

				if (!canShowFlavorImage() || article.flavorImage == null) {
					holder.textImage.setImageDrawable(textDrawable);

				} else {
					Glide.with(getContext())
							.load(article.flavorImageUri)
							.placeholder(textDrawable)
							.thumbnail(0.5f)
							.bitmapTransform(new CropCircleTransformation(getActivity()))
							.diskCacheStrategy(DiskCacheStrategy.ALL)
							.skipMemoryCache(false)
							.listener(new RequestListener<String, GlideDrawable>() {
								@Override
								public boolean onException(Exception e, String model, Target<GlideDrawable> target, boolean isFirstResource) {
									return false;
								}

								@Override
								public boolean onResourceReady(GlideDrawable resource, String model, Target<GlideDrawable> target, boolean isFromMemoryCache, boolean isFirstResource) {

									return resource.getIntrinsicWidth() < THUMB_IMG_MIN_SIZE || resource.getIntrinsicHeight() < THUMB_IMG_MIN_SIZE;
								}
							})
							.into(holder.textImage);
				}

                holder.textChecked.setVisibility(View.GONE);
            }
        }

		private void openGalleryForType(Article article, ArticleViewHolder holder, View transitionView) {
			//Log.d(TAG, "openGalleryForType: " + article + " " + holder + " " + transitionView);

			if ("iframe".equalsIgnoreCase(article.flavorImage.tagName())) {
				m_activity.openUri(Uri.parse(article.flavorStreamUri));
			} else {

				Intent intent = new Intent(m_activity, GalleryActivity.class);

				intent.putExtra("firstSrc", article.flavorStreamUri != null ? article.flavorStreamUri : article.flavorImageUri);
				intent.putExtra("title", article.title);

				// FIXME maybe: gallery view works with document as html, it's easier to add this hack rather than
				// rework it to additionally operate on separate attachment array (?)
				// also, maybe consider video attachments? kinda hard to do without a poster tho (for flavor view)

				String tempContent = article.content;

				if (article.attachments != null) {
					Document doc = new Document("");

					for (Attachment a : article.attachments) {
						if (a.content_type != null) {
							if (a.content_type.contains("image/")) {
								Element img = new Element("img").attr("src", a.content_url);
								doc.appendChild(img);
							}
						}
					}

					tempContent = doc.outerHtml() + tempContent;
				}

				intent.putExtra("content", tempContent);

				ActivityOptionsCompat options =
						ActivityOptionsCompat.makeSceneTransitionAnimation(m_activity,
								transitionView != null ? transitionView : holder.flavorImageView,
								"gallery:" + (article.flavorStreamUri != null ? article.flavorStreamUri : article.flavorImageUri));

				ActivityCompat.startActivity(m_activity, intent, options.toBundle());
			}

		}

		private void adjustVideoKindView(ArticleViewHolder holder, Article article) {
			if (article.flavorImage != null) {
				if (article.flavor_kind == Article.FLAVOR_KIND_YOUTUBE || "iframe".equalsIgnoreCase(article.flavorImage.tagName())) {
					holder.flavorVideoKindView.setImageResource(R.drawable.baseline_play_circle_outline_24);
					holder.flavorVideoKindView.setVisibility(View.VISIBLE);
				} else if (article.flavor_kind == Article.FLAVOR_KIND_VIDEO || "video".equalsIgnoreCase(article.flavorImage.tagName())) {
					holder.flavorVideoKindView.setImageResource(R.drawable.baseline_play_circle_24);
					holder.flavorVideoKindView.setVisibility(View.VISIBLE);
				} else {
					holder.flavorVideoKindView.setVisibility(View.INVISIBLE);
				}
			} else {
				holder.flavorVideoKindView.setVisibility(View.INVISIBLE);
			}
		}

		private void adjustTitleTextView(int score, TextView tv, int position) {
			int viewType = getItemViewType(position);
			if (origTitleColors[viewType] == null)
				// store original color
				origTitleColors[viewType] = tv.getCurrentTextColor();

			if (score < Article.SCORE_LOW) {
				tv.setPaintFlags(tv.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
			} else {
				tv.setTextColor(origTitleColors[viewType]);
				tv.setPaintFlags(tv.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
			}
		}
	}

	private void releaseSurface() {
		try {
			if (m_mediaPlayer != null) {
				m_mediaPlayer.release();
			}
		} catch (IllegalStateException e) {
			e.printStackTrace();
		}

		try {
			if (m_activeTexture != null) {
				m_activeTexture.setVisibility(View.GONE);
			}
		} catch (IllegalStateException e) {
			e.printStackTrace();
		}
	}

	public void notifyUpdated() {
		m_adapter.notifyDataSetChanged();
	}

	public void scrollToArticle(Article article) {
		scrollToArticleId(article.id);
	}

	public void scrollToArticleId(int id) {
		int position = Application.getArticles().getPositionById(id);

		if (position != -1)
			m_list.scrollToPosition(position);
	}

	public void setActiveArticleId(int articleId) {
		if (m_list != null && articleId != m_activeArticleId) {

			ArticleList articles = Application.getArticles();

			int oldPosition = articles.getPositionById(m_activeArticleId);
			int newPosition = articles.getPositionById(articleId);

			m_activeArticleId = articleId;

			if (oldPosition != -1)
				m_adapter.notifyItemChanged(oldPosition);

			m_adapter.notifyItemChanged(newPosition);

			scrollToArticleId(articleId);

			if (newPosition >= articles.size() - 5)
				refresh(true);
		}
	}

	public void setSelection(ArticlesSelection select) {
		ArticleList articlesWithoutFooters = Application.getArticles().getWithoutFooters();

		for (Article a : articlesWithoutFooters)
            a.selected = false;

		if (select != ArticlesSelection.NONE) {
			for (Article a : articlesWithoutFooters) {
				if (select == ArticlesSelection.ALL || select == ArticlesSelection.UNREAD && a.unread) {
					a.selected = true;
				}
			}
		}

        if (m_adapter != null) {
            m_adapter.notifyDataSetChanged();
        }
	}

	public int getActiveArticleId() {
		return m_activeArticleId;
	}

	public String getSearchQuery() {
		return m_searchQuery;
	}

	public void setSearchQuery(String query) {
		if (!m_searchQuery.equals(query)) {
			m_searchQuery = query;

			refresh(false);
		}
	}

	public Feed getFeed() {
		return m_feed;
	}

	@Override
	public void onPause() {
		super.onPause();

		releaseSurface();
	}

}
