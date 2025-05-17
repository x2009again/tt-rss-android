package org.fox.ttrss;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

import org.fox.ttrss.types.Feed;

import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class FeedsFragment extends Fragment implements OnSharedPreferenceChangeListener {
	private final String TAG = this.getClass().getSimpleName();
	protected SharedPreferences m_prefs;
	protected MasterActivity m_activity;
	protected Feed m_rootFeed;
	private Feed m_selectedFeed;
	protected SwipeRefreshLayout m_swipeLayout;
    private boolean m_enableParentBtn = false;
	protected FeedsAdapter m_adapter;
	private RecyclerView m_list;
	private RecyclerView.LayoutManager m_layoutManager;

	public void initialize(@NonNull Feed rootFeed, boolean enableParentBtn) {
		Log.d(TAG, "initialize, feed=" + rootFeed);

        m_rootFeed = rootFeed;
		m_enableParentBtn = enableParentBtn;
	}

	@SuppressLint("DefaultLocale")
	static class FeedUnreadComparator implements Comparator<Feed> {

		@Override
		public int compare(Feed a, Feed b) {
			if (a.unread != b.unread)
					return b.unread - a.unread;
				else
					return a.title.toUpperCase().compareTo(b.title.toUpperCase());
			}

	}

	@SuppressLint("DefaultLocale")
	static class FeedTitleComparator implements Comparator<Feed> {

		@Override
		public int compare(Feed a, Feed b) {
			if (a.is_cat && b.is_cat)
				return a.title.toUpperCase().compareTo(b.title.toUpperCase());
			else if (a.is_cat && !b.is_cat)
				return -1;
			else if (!a.is_cat && b.is_cat)
				return 1;
			else if (a.id >= 0 && b.id >= 0)
				return a.title.toUpperCase().compareTo(b.title.toUpperCase());
			else
				return a.id - b.id;			
		}
		
	}

	@SuppressLint("DefaultLocale")
	static class SpecialOrderComparator implements Comparator<Feed> {
		static List<Integer> order = Arrays.asList(Feed.ALL_ARTICLES, Feed.FRESH, Feed.MARKED,
				Feed.PUBLISHED, Feed.ARCHIVED, Feed.RECENTLY_READ);

		@Override
		public int compare(Feed a, Feed b) {
			return Integer.valueOf(order.indexOf(a.id)).compareTo(order.indexOf(b.id));
		}
	}

	@SuppressLint("DefaultLocale")
	static class FeedOrderComparator implements Comparator<Feed> {

		@Override
		public int compare(Feed a, Feed b) {
			if (a.id >= 0 && b.id >= 0)
				if (a.is_cat && b.is_cat)
					if (a.order_id != 0 && b.order_id != 0)
						return a.order_id - b.order_id;
					else
						return a.title.toUpperCase().compareTo(b.title.toUpperCase());
				else if (a.is_cat)
					return -1;
				else if (b.is_cat)
					return 1;
				else if (a.order_id != 0 && b.order_id != 0)
					return a.order_id - b.order_id;
				else
					return a.title.toUpperCase().compareTo(b.title.toUpperCase());
			else
				if (a.id < CommonActivity.LABEL_BASE_INDEX && b.id < CommonActivity.LABEL_BASE_INDEX)
					return a.title.toUpperCase().compareTo(b.title.toUpperCase());
				else
					return a.id - b.id;
		}
		
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item
				.getMenuInfo();

		// all onContextItemSelected are invoked in sequence so we might get a context menu for headlines, etc
		// TODO context menu ids defined here should be unique to feedsfragment
		try {
			if (info != null) {
				final Feed feed = m_adapter.getCurrentList().get(info.position);

				Log.d(TAG, "context for feed=" + feed.id);

				int itemId = item.getItemId();
				if (itemId == R.id.browse_headlines) {
					Feed tmpFeed = new Feed(feed);

					if (!neverOpenHeadlines(feed))
						tmpFeed.always_open_headlines = true;

					m_activity.onFeedSelected(tmpFeed);
					return true;
				} else if (itemId == R.id.browse_feeds) {
					m_activity.onFeedSelected(feed);
					return true;
				} else if (itemId == R.id.unsubscribe_feed) {
					MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext())
							.setMessage(getString(R.string.unsubscribe_from_prompt, feed.title))
							.setPositiveButton(R.string.unsubscribe,
									(dialog, which) -> m_activity.unsubscribeFeed(feed))
							.setNegativeButton(R.string.dialog_cancel,
									(dialog, which) -> {

									});

					Dialog dlg = builder.create();
					dlg.show();

					return true;
				} else if (itemId == R.id.catchup_feed) {
					m_activity.catchupDialog(feed);
					return true;
				}
			}

		} catch (IndexOutOfBoundsException e) {
			e.printStackTrace();
		}

		Log.d(TAG, "onContextItemSelected, unhandled id=" + item.getItemId());
		return super.onContextItemSelected(item);
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
	    ContextMenuInfo menuInfo) {

		m_activity.getMenuInflater().inflate(R.menu.context_feed, menu);
		
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;

        Feed feed = m_adapter.getCurrentList().get(info.position);
		
		menu.setHeaderTitle(feed.title);

		if (!feed.is_cat)
			menu.findItem(R.id.browse_feeds).setVisible(false);

		if (neverOpenHeadlines(feed))
			menu.findItem(R.id.browse_headlines).setVisible(false);

		if (feed.id <= 0 || feed.is_cat)
			menu.findItem(R.id.unsubscribe_feed).setVisible(false);

		super.onCreateContextMenu(menu, v, menuInfo);
		
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (savedInstanceState != null) {
			m_rootFeed = savedInstanceState.getParcelable("m_feed");
			m_selectedFeed = savedInstanceState.getParcelable("m_selectedFeed");
			m_enableParentBtn = savedInstanceState.getBoolean("m_enableParentBtn");
		}
	}

	@Override
	public void onSaveInstanceState(Bundle out) {
		super.onSaveInstanceState(out);

		out.putParcelable("m_feed", m_rootFeed);
		out.putParcelable("m_selectedFeed", m_selectedFeed);
		out.putBoolean("m_enableParentBtn", m_enableParentBtn);
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {    	
		
		View view = inflater.inflate(R.layout.fragment_feeds, container, false);
		
		m_swipeLayout = view.findViewById(R.id.feeds_swipe_container);

	    m_swipeLayout.setOnRefreshListener(this::refresh);

		m_list = view.findViewById(R.id.feeds);
		registerForContextMenu(m_list);

		m_layoutManager = new LinearLayoutManager(m_activity.getApplicationContext());
		m_list.setLayoutManager(m_layoutManager);
		m_list.setItemAnimator(new DefaultItemAnimator());

		m_adapter = new FeedsAdapter();
		m_list.setAdapter(m_adapter);

		TextView login = view.findViewById(R.id.drawer_header_login);

		if (login != null) {
			login.setText(m_prefs.getString("login", ""));
		}

		TextView server = view.findViewById(R.id.drawer_header_server);

		if (server != null) {
			try {
				server.setText(new URL(m_prefs.getString("ttrss_url", "")).getHost());
			} catch (MalformedURLException e) {
				server.setText("");
			}
		}

		View settingsBtn = view.findViewById(R.id.drawer_settings_btn);

		if (settingsBtn != null) {
			settingsBtn.setOnClickListener(v -> {
				Intent intent = new Intent(getActivity(),
						PreferencesActivity.class);

				startActivityForResult(intent, 0);
			});
		}

		FeedsModel model = new ViewModelProvider(this).get(FeedsModel.class);

		model.getUpdatesData().observe(getActivity(), lastUpdate -> {
			Log.d(TAG, "observed update=" + lastUpdate);
		});

		model.getIsLoading().observe(getActivity(), isLoading -> {
			Log.d(TAG, "observed isLoading=" + isLoading);

			if (isAdded() && m_swipeLayout != null)
				m_swipeLayout.setRefreshing(isLoading);
		});

		model.getFeeds().observe(getActivity(), feeds -> {
			Log.d(TAG, "observed feeds=" + feeds);

			if (isAdded()) {
				onFeedsLoaded(feeds);

				if (model.getLastError() != null && model.getLastError() != ApiCommon.ApiError.SUCCESS) {
					if (model.getLastError() == ApiCommon.ApiError.LOGIN_FAILED) {
						m_activity.login(true);
					} else {
						if (model.getLastErrorMessage() != null) {
							m_activity.toast(getString(model.getErrorMessage()) + "\n" + model.getLastErrorMessage());
						} else {
							m_activity.toast(model.getErrorMessage());
						}
					}
				}
			}
		});

		return view;    	
	}

	protected void onFeedsLoaded(List<Feed> loadedFeeds) {
		List<Feed> feedsWork = new ArrayList<>();

		if (m_activity.getUnreadOnly() && m_rootFeed.id != Feed.CAT_SPECIAL)
			loadedFeeds = loadedFeeds.stream()
					.filter(f -> f.unread > 0)
					.collect(Collectors.toList());

		sortFeeds(loadedFeeds, m_rootFeed);

		if (m_enableParentBtn) {
			feedsWork.add(0, new Feed(Feed.TYPE_GOBACK));

			if (m_rootFeed.id >= 0 && !loadedFeeds.isEmpty()) {
				Feed feed = new Feed(m_rootFeed.id, m_rootFeed.title, true);

				feed.unread = loadedFeeds.stream().map(a -> a.unread).reduce(0, Integer::sum);
				feed.always_open_headlines = true;

				feedsWork.add(1, feed);
			}
		}

		feedsWork.addAll(loadedFeeds);

		feedsWork.add(new Feed(Feed.TYPE_DIVIDER));
		feedsWork.add(new Feed(Feed.TYPE_TOGGLE_UNREAD, getString(R.string.unread_only), true));

		m_adapter.submitList(feedsWork);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);		
		
		m_prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
		m_prefs.registerOnSharedPreferenceChangeListener(this);
		
		m_activity = (MasterActivity)activity;
	}

	@Override
	public void onResume() {
		super.onResume();

		Log.d(TAG, "onResume");

		setSelectedFeed(m_activity.getActiveFeed());

		refresh();

		m_activity.invalidateOptionsMenu();
	}

	public void refresh() {
		if (!isAdded())
			return;

		FeedsModel model = new ViewModelProvider(this).get(FeedsModel.class);
		model.startLoading(m_rootFeed, false);
	}

	private class FeedViewHolder extends RecyclerView.ViewHolder {

		private View view;
		private ImageView icon;
		private TextView title;
		private TextView unreadCounter;
		private MaterialSwitch rowSwitch;

		public FeedViewHolder(@NonNull View itemView) {
			super(itemView);

			view = itemView;
			icon = itemView.findViewById(R.id.icon);
			title = itemView.findViewById(R.id.title);
			unreadCounter = itemView.findViewById(R.id.unread_counter);
			rowSwitch = itemView.findViewById(R.id.row_switch);
		}
	}

	private class FeedDiffUtilItemCallback extends DiffUtil.ItemCallback<Feed> {

		@Override
		public boolean areItemsTheSame(@NonNull Feed oldItem, @NonNull Feed newItem) {
			return oldItem.id == newItem.id;
		}

		@Override
		public boolean areContentsTheSame(@NonNull Feed oldItem, @NonNull Feed newItem) {
			return oldItem.id == newItem.id &&
					oldItem.is_cat == newItem.is_cat &&
					oldItem.title.equals(newItem.title) &&
					oldItem.unread == newItem.unread;
		}
	}

	protected class FeedsAdapter extends ListAdapter<Feed, FeedViewHolder> {
		public static final int VIEW_NORMAL = 0;
		public static final int VIEW_SELECTED = 1;
		public static final int VIEW_GOBACK = 2;
		public static final int VIEW_TOGGLE_UNREAD = 4;
		public static final int VIEW_DIVIDER = 5;

		protected FeedsAdapter() {
			super(new FeedDiffUtilItemCallback());
		}

		@NonNull
		@Override
		public FeedViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			int layoutId = R.layout.feeds_row;

			switch (viewType) {
				case VIEW_SELECTED:
					layoutId = R.layout.feeds_row_selected;
					break;
				case VIEW_GOBACK:
					layoutId = R.layout.feeds_row_goback;
					break;
				case VIEW_TOGGLE_UNREAD:
					layoutId = R.layout.feeds_row_toggle;
					break;
				case VIEW_DIVIDER:
					layoutId = R.layout.feeds_row_divider;
					break;
			}

			return new FeedViewHolder(LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false));
		}

		@Override
		public void onBindViewHolder(@NonNull FeedViewHolder holder, int position) {
			Feed feed = getItem(position);

			if (holder.icon != null) {
				holder.icon.setImageResource(getIconForFeed(feed));
			}

			if (holder.title != null) {
				holder.title.setText(feed.title);

				if (feed.always_open_headlines || (!feed.is_cat && feed.id == -4)) {
					holder.title.setTypeface(null, Typeface.BOLD);
				} else {
					holder.title.setTypeface(null, Typeface.NORMAL);
				}

			}

			if (holder.unreadCounter != null) {
				holder.unreadCounter.setText(String.valueOf(feed.unread));
				holder.unreadCounter.setVisibility((feed.unread > 0) ? View.VISIBLE : View.INVISIBLE);
			}

			// there's only one kind of row with checkbox atm
			if (holder.rowSwitch != null) {
				holder.rowSwitch.setChecked(m_activity.getUnreadOnly());

				holder.rowSwitch.setOnCheckedChangeListener((button, isChecked) -> {
					m_activity.setUnreadOnly(isChecked);
				});
			}

			holder.view.setOnLongClickListener(view -> {
				if (feed.id != Feed.TYPE_TOGGLE_UNREAD && feed.id != Feed.TYPE_DIVIDER && feed.id != Feed.TYPE_GOBACK && feed.id != Feed.ALL_ARTICLES) {
					m_list.showContextMenuForChild(view);
				}
				return true;
			});

			// default open handler (i.e. tap)
			holder.view.setOnClickListener(view -> {
				if (feed.id == Feed.TYPE_GOBACK) {
					m_activity.getSupportFragmentManager().popBackStack();
				} else if (feed.id == Feed.TYPE_TOGGLE_UNREAD || feed.id == Feed.TYPE_DIVIDER) {
					//
				} else {
					Feed tmpFeed = new Feed(feed);

					if (!neverOpenHeadlines(feed) && !tmpFeed.always_open_headlines)
						tmpFeed.always_open_headlines = m_prefs.getBoolean("browse_cats_like_feeds", false);

					m_activity.onFeedSelected(tmpFeed);
				}
			});
		}

		@Override
		public int getItemViewType(int position) {
			Feed feed = getItem(position);

			if (feed.id == Feed.TYPE_GOBACK) {
				return VIEW_GOBACK;
			} else if (feed.id == Feed.TYPE_DIVIDER) {
				return VIEW_DIVIDER;
			} else if (feed.id == Feed.TYPE_TOGGLE_UNREAD) {
				return VIEW_TOGGLE_UNREAD;
			} else if (m_selectedFeed != null && feed.id == m_selectedFeed.id && feed.is_cat == m_selectedFeed.is_cat) {
				return VIEW_SELECTED;
			} else {
				return VIEW_NORMAL;
			}
		}

		public int getPositionOf(Feed feed) {
			List<Feed> feeds = getCurrentList();

			return IntStream.range(0, feeds.size())
					.sequential()
					.filter(i -> {
						Feed f = feeds.get(i);

						return f.id == feed.id && f.is_cat == feed.is_cat;
					})
					.findFirst()
					.orElse(-1);
		}
	}

	/**  we always show Labels and Special contents, regardless of the setting */
	private boolean neverOpenHeadlines(Feed feed) {
		return feed.id == Feed.CAT_SPECIAL || feed.id == Feed.CAT_LABELS;
	}

	protected void sortFeeds(List<Feed> feeds, Feed feed) {
		Comparator<Feed> cmp;

		if (feed.id == -1) {
			cmp = new SpecialOrderComparator();
		} else {
			if (m_prefs.getBoolean("sort_feeds_by_unread", false)) {
				cmp = new FeedUnreadComparator();
			} else {
				if (m_activity.getApiLevel() >= 3) {
					cmp = new FeedOrderComparator();
				} else {
					cmp = new FeedTitleComparator();
				}
			}
		}

		try {
			feeds.sort(cmp);
		} catch (IllegalArgumentException e) {
			//
		}
	}

	protected int getIconForFeed(Feed feed) {
		if (feed.id == Feed.TYPE_GOBACK) {
			return R.drawable.baseline_arrow_back_24;
		} else if (feed.id == Feed.TYPE_TOGGLE_UNREAD) {
			return R.drawable.baseline_filter_alt_24;
		} else if (feed.id == 0 && !feed.is_cat) {
			return R.drawable.baseline_archive_24;
		} else if (feed.id == -1 && !feed.is_cat) {
			return R.drawable.baseline_star_24;
		} else if (feed.id == -2 && !feed.is_cat) {
			return R.drawable.rss;
		} else if (feed.id == -3 && !feed.is_cat) {
			return R.drawable.baseline_local_fire_department_24;
		} else if (feed.id == -4 && !feed.is_cat) {
			return R.drawable.baseline_inbox_24;
		} else if (feed.id == -6 && !feed.is_cat) {
			return R.drawable.baseline_restore_24;
		} else if (feed.is_cat) {
			return R.drawable.baseline_folder_open_24;
		} else if (feed.id < -10 && !feed.is_cat) {
			return R.drawable.baseline_label_24;
		} else {
			return R.drawable.rss;
		}
	}
	
	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {

		// Can't access ViewModels from detached fragment (= backstack)
		if (isAdded()) {
			String[] filter = new String[] { "sort_feeds_by_unread", "show_unread_only" };

			if (Arrays.asList(filter).contains(key))
				refresh();
		}
	}

	public void setSelectedFeed(Feed feed) {
        if (m_adapter != null) {

			int oldPosition = -1;

			if (m_selectedFeed != null)
				oldPosition = m_adapter.getPositionOf(m_selectedFeed);

			int newPosition = m_adapter.getPositionOf(feed);

			m_selectedFeed = feed;

			if (oldPosition != -1)
				m_adapter.notifyItemChanged(oldPosition);

			if (newPosition != -1)
				m_adapter.notifyItemChanged(newPosition);

        }
    }
}
