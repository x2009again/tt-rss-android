package org.fox.ttrss;

import android.annotation.SuppressLint;
import android.app.Activity;
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
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

import org.fox.ttrss.types.Feed;
import org.fox.ttrss.types.FeedCategory;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

public class FeedsFragment extends Fragment implements OnSharedPreferenceChangeListener,
		LoaderManager.LoaderCallbacks<JsonElement> {
	private final String TAG = this.getClass().getSimpleName();
	private SharedPreferences m_prefs;
	private MasterActivity m_activity;
	private int m_catId;
	private int m_selectedFeedId;
	private SwipeRefreshLayout m_swipeLayout;
    private boolean m_enableParentBtn = false;
	private FeedsAdapter m_adapter;
	private RecyclerView m_list;
	private RecyclerView.LayoutManager m_layoutManager;

	public void initialize(int catId) {
        m_catId = catId;
	}

	@Override
	public Loader<JsonElement> onCreateLoader(int id, Bundle args) {
		if (m_swipeLayout != null) m_swipeLayout.setRefreshing(true);

		final String sessionId = m_activity.getSessionId();
		final boolean unreadOnly = false;
		// final boolean unreadOnly = m_activity.getUnreadOnly() && (m_activeCategory == null || m_activeCategory.id != -1);

		HashMap<String,String> params = new HashMap<>();
		params.put("op", "getFeeds");
		params.put("sid", sessionId);
		params.put("include_nested", "true");
		params.put("cat_id", String.valueOf(m_catId));

		if (unreadOnly)
			params.put("unread_only", "true");

		return new ApiLoader(getContext(), params);
	}

	@Override
	public void onLoadFinished(Loader<JsonElement> loader, JsonElement result) {
		if (m_swipeLayout != null) m_swipeLayout.setRefreshing(false);

		if (result != null) {
			try {
				JsonArray content = result.getAsJsonArray();
				if (content != null) {

					Type listType = new TypeToken<List<Feed>>() {}.getType();
					final List<Feed> feedsJson = new Gson().fromJson(content, listType);

					List<Feed> feeds = new ArrayList<>();

					// int catUnread = 0;

					/* for (Feed f : feeds)
						if (f.id > -10 || m_catId != -4) { // skip labels for flat feedlist for now
							if (m_activeCategory != null || f.id >= 0) {
								m_feeds.add(f);
								catUnread += f.unread;
							}

							if (m_activeCategory != null && m_activeCategory.id == -1)
								f.title = Feed.getSpecialFeedTitleById(m_activity, f.id);
						}

					sortFeeds();

					if (m_activeCategory == null) {
						Feed feed = new Feed(-1, "Special", true);
						feed.unread = catUnread;

						m_feeds.add(0, feed);

					}

					if (m_enableParentBtn && m_activeCategory != null && m_activeCategory.id >= 0 && !m_feeds.isEmpty()) {
						Feed feed = new Feed(m_activeCategory.id, m_activeCategory.title, true);
						feed.unread = catUnread;
						feed.always_display_as_feed = true;
						feed.display_title = getString(R.string.feed_all_articles);

						m_feeds.add(0, feed);
					}

					m_adapter.notifyDataSetChanged(); */

					// m_adapter.sortFeeds(feedsJson);

					feeds.add(new Feed(Feed.TYPE_HEADER));
					feeds.add(new Feed(Feed.TYPE_GOBACK));

					feeds.addAll(feedsJson);

					feeds.add(new Feed(Feed.TYPE_DIVIDER));
					feeds.add(new Feed(Feed.TYPE_TOGGLE_UNREAD));

					m_adapter.submitList(feeds);

					return;
				}

			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		ApiLoader apiLoader = (ApiLoader) loader;

		if (apiLoader.getLastError() != null && apiLoader.getLastError() != ApiCommon.ApiError.SUCCESS) {
			if (apiLoader.getLastError() == ApiCommon.ApiError.LOGIN_FAILED) {
				m_activity.login(true);
			} else {
				if (apiLoader.getLastErrorMessage() != null) {
					m_activity.toast(getString(apiLoader.getErrorMessage()) + "\n" + apiLoader.getLastErrorMessage());
				} else {
					m_activity.toast(apiLoader.getErrorMessage());
				}
			}
		}
	}

	@Override
	public void onLoaderReset(Loader<JsonElement> loader) { }

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
		/* AdapterContextMenuInfo info = (AdapterContextMenuInfo) item
				.getMenuInfo();
		int itemId = item.getItemId();
		if (itemId == R.id.browse_headlines) {
			Feed feed = getFeedAtPosition(info.position);
			if (feed != null) {
				m_activity.onFeedSelected(feed);
			}
			return true;
		} else if (itemId == R.id.browse_feeds) {
			Feed feed = getFeedAtPosition(info.position);
			if (feed != null) {
				m_activity.onCatSelected(new FeedCategory(feed.id, feed.title, feed.unread), false);
			}
			return true;
		} else if (itemId == R.id.unsubscribe_feed) {
			final Feed feed = getFeedAtPosition(info.position);
			if (feed != null) {
				MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext())
						.setMessage(getString(R.string.unsubscribe_from_prompt, feed.title))
						.setPositiveButton(R.string.unsubscribe,
								(dialog, which) -> m_activity.unsubscribeFeed(feed))
						.setNegativeButton(R.string.dialog_cancel,
								(dialog, which) -> {

								});

				Dialog dlg = builder.create();
				dlg.show();
			}

			return true;
		} else if (itemId == R.id.catchup_feed) {
			Feed feed = getFeedAtPosition(info.position);

			if (feed != null) {
				m_activity.catchupDialog(feed);
			}
			return true;
		} */

		Log.d(TAG, "onContextItemSelected, unhandled id=" + item.getItemId());
		return super.onContextItemSelected(item);
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
	    ContextMenuInfo menuInfo) {
		
		/* getActivity().getMenuInflater().inflate(R.menu.context_feed, menu);
		
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;

        Feed feed = (Feed) m_list.getItemAtPosition(info.position);
		
		menu.setHeaderTitle(feed.display_title != null ? feed.display_title : feed.title);

		if (!feed.is_cat) {
			menu.findItem(R.id.browse_feeds).setVisible(false);
		}

		if (feed.id <= 0) {
			menu.findItem(R.id.unsubscribe_feed).setVisible(false);
		} */

		super.onCreateContextMenu(menu, v, menuInfo);
		
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (savedInstanceState != null) {
			m_catId = savedInstanceState.getInt("m_catId");
			m_selectedFeedId = savedInstanceState.getInt("m_selectedId");
			m_enableParentBtn = savedInstanceState.getBoolean("m_enableParentBtn");
		}
	}

	@Override
	public void onSaveInstanceState(Bundle out) {
		super.onSaveInstanceState(out);

		out.putInt("m_catId", m_catId);
		out.putInt("m_selectedId", m_selectedFeedId);
		out.putBoolean("m_enableParentBtn", m_enableParentBtn);
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {    	
		
		View view = inflater.inflate(R.layout.fragment_feeds_recycler, container, false);
		
		m_swipeLayout = view.findViewById(R.id.feeds_swipe_container);

	    m_swipeLayout.setOnRefreshListener(this::refresh);

		m_list = view.findViewById(R.id.feeds);
		registerForContextMenu(m_list);

		m_layoutManager = new LinearLayoutManager(m_activity.getApplicationContext());
		m_list.setLayoutManager(m_layoutManager);
		m_list.setItemAnimator(new DefaultItemAnimator());

		m_adapter = new FeedsAdapter();
		m_list.setAdapter(m_adapter);

		/* if (m_enableParentBtn) {
			View layout = inflater.inflate(R.layout.feeds_goback, m_list, false);

			layout.setOnClickListener(view1 -> m_activity.getSupportFragmentManager().popBackStack());

			m_list.addHeaderView(layout, null, false);
		}

		m_adapter = new FeedListAdapter(getActivity(), R.layout.feeds_row, m_feeds);
		m_list.setAdapter(m_adapter);
		m_list.setOnItemClickListener(this);

		registerForContextMenu(m_list); */

		return view;    	
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

		LoaderManager.getInstance(this).initLoader(Application.LOADER_FEEDS, null, this).forceLoad();

		m_activity.invalidateOptionsMenu();
	}
	
	/* @Override
	public void onItemClick(AdapterView<?> av, View view, int position, long id) {
		ListView list = (ListView)av;

		if (list != null) {
            Feed feed = (Feed)list.getItemAtPosition(position);

			if (feed != null) {
				if (feed.is_cat) {
					if (feed.always_display_as_feed) {
						m_activity.onCatSelected(new FeedCategory(feed.id, feed.title, feed.unread), true);
					} else if (feed.id < 0) {
						m_activity.onCatSelected(new FeedCategory(feed.id, feed.title, feed.unread), false);
					} else {
						m_activity.onCatSelected(new FeedCategory(feed.id, feed.title, feed.unread));
					}
				} else {
					m_activity.onFeedSelected(feed);
				}
			}
			
    		//m_selectedFeed = feed;
			//m_adapter.notifyDataSetChanged();
		}
	} */

	public void refresh() {
		if (m_swipeLayout != null) {
            m_swipeLayout.setRefreshing(true);
        }

		LoaderManager.getInstance(this).restartLoader(Application.LOADER_FEEDS, null, this).forceLoad();
	}

	private class FeedViewHolder extends RecyclerView.ViewHolder {

		private View view;
		private ImageView icon;
		private TextView title;
		private TextView unreadCounter;
		private TextView rowSwitch;
		private View settingsBtn;
		private TextView login;
		private TextView server;

		public FeedViewHolder(@NonNull View itemView) {
			super(itemView);

			view = itemView;
			icon = itemView.findViewById(R.id.icon);
			title = itemView.findViewById(R.id.title);
			unreadCounter = itemView.findViewById(R.id.unread_counter);
			rowSwitch = itemView.findViewById(R.id.row_switch);
			settingsBtn = itemView.findViewById(R.id.drawer_settings_btn);
			login = itemView.findViewById(R.id.drawer_header_login);
			server = itemView.findViewById(R.id.drawer_header_server);
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

	private class FeedsAdapter extends ListAdapter<Feed, FeedViewHolder> {
		public static final int VIEW_NORMAL = 0;
		public static final int VIEW_SELECTED = 1;
		public static final int VIEW_GOBACK = 2;
		public static final int VIEW_HEADER = 3;
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
				case VIEW_HEADER:
					layoutId = R.layout.feeds_row_header;
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
				if (feed.id == Feed.TYPE_GOBACK) {
					holder.icon.setImageResource(R.drawable.baseline_arrow_back_24);
				} else if (feed.id == 0 && !feed.is_cat) {
					holder.icon.setImageResource(R.drawable.baseline_archive_24);
				} else if (feed.id == -1 && !feed.is_cat) {
					holder.icon.setImageResource(R.drawable.baseline_star_24);
				} else if (feed.id == -2 && !feed.is_cat) {
					holder.icon.setImageResource(R.drawable.rss);
				} else if (feed.id == -3 && !feed.is_cat) {
					holder.icon.setImageResource(R.drawable.baseline_local_fire_department_24);
				} else if (feed.id == -4 && !feed.is_cat) {
					holder.icon.setImageResource(R.drawable.baseline_inbox_24);
				} else if (feed.id == -6 && !feed.is_cat) {
					holder.icon.setImageResource(R.drawable.baseline_restore_24);
				} else if (feed.is_cat) {
					holder.icon.setImageResource(R.drawable.baseline_folder_open_24);
				} else {
					holder.icon.setImageResource(R.drawable.rss);
				}
			}

			if (holder.title != null) {
				holder.title.setText(feed.display_title != null ? feed.display_title : feed.title);

				if (feed.always_display_as_feed || (!feed.is_cat && feed.id == -4)) {
					holder.title.setTypeface(null, Typeface.BOLD);
				} else {
					holder.title.setTypeface(null, Typeface.NORMAL);
				}

			}

			if (holder.unreadCounter != null) {
				holder.unreadCounter.setText(String.valueOf(feed.unread));
				holder.unreadCounter.setVisibility((feed.unread > 0) ? View.VISIBLE : View.INVISIBLE);
			}

			if (holder.settingsBtn != null) {
				holder.settingsBtn.setOnClickListener(view -> {
					Intent intent = new Intent(getActivity(),
							PreferencesActivity.class);

					startActivityForResult(intent, 0);
				});
			}

			holder.view.setOnClickListener(view -> {
				if (feed.is_cat) {
					if (feed.always_display_as_feed) {
						m_activity.onCatSelected(new FeedCategory(feed.id, feed.title, feed.unread), true);
					} else if (feed.id < 0) {
						m_activity.onCatSelected(new FeedCategory(feed.id, feed.title, feed.unread), false);
					} else {
						m_activity.onCatSelected(new FeedCategory(feed.id, feed.title, feed.unread));
					}
				} else {
					m_activity.onFeedSelected(feed);
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
			} else if (feed.id == Feed.TYPE_HEADER) {
				return VIEW_HEADER;
			} else if (feed.id == Feed.TYPE_TOGGLE_UNREAD) {
				return VIEW_TOGGLE_UNREAD;
			} else if (feed.id == m_selectedFeedId) {
				return VIEW_SELECTED;
			} else {
				return VIEW_NORMAL;
			}
		}

		public void sortFeeds(List<Feed> feeds) {
			Comparator<Feed> cmp;

			if (m_prefs.getBoolean("sort_feeds_by_unread", false)) {
				cmp = new FeedUnreadComparator();
			} else {
				if (m_activity.getApiLevel() >= 3) {
					cmp = new FeedOrderComparator();
				} else {
					cmp = new FeedTitleComparator();
				}
			}

			try {
				feeds.sort(cmp);
			} catch (IllegalArgumentException e) {
				// sort order got changed in prefs or something
				e.printStackTrace();
			}
		}
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {

		refresh();
	}

	/* public Feed getFeedAtPosition(int position) {
		try {
			return (Feed) m_list.getItemAtPosition(position);
		} catch (ArrayIndexOutOfBoundsException e) {
			e.printStackTrace();
		}

		return null;
	} */

	public void setSelectedFeedId(int feedId) {
        if (m_adapter != null) {
			m_selectedFeedId = feedId;

			// TODO handle properly
			m_adapter.notifyDataSetChanged();
        }
    }
}
