package org.fox.ttrss;

import android.annotation.SuppressLint;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import androidx.loader.content.Loader;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

import org.fox.ttrss.types.Feed;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class RootCategoriesFragment extends FeedsFragment {
	private final String TAG = this.getClass().getSimpleName();

	@SuppressLint("DefaultLocale")
	static class CatOrderComparator implements Comparator<Feed> {

		@Override
		public int compare(Feed a, Feed b) {
			if (a.id >= 0 && b.id >= 0)
				if (a.order_id != 0 && b.order_id != 0)
					return a.order_id - b.order_id;
				else
					return a.title.toUpperCase().compareTo(b.title.toUpperCase());
			else
				return a.id - b.id;
		}

	}

	@Override
	protected void sortFeeds(List<Feed> feeds, Feed feed) {
		try {
			feeds.sort(new CatOrderComparator());
		} catch (IllegalArgumentException e) {
			//
		}
	}

	@Override
	protected int getIconForFeed(Feed feed) {
		if (feed.id == Feed.TYPE_TOGGLE_UNREAD)
			return super.getIconForFeed(feed);
		else if (feed.id == Feed.CAT_LABELS)
			return R.drawable.outline_label_24;
		else if (feed.id == Feed.CAT_SPECIAL)
			return R.drawable.baseline_folder_special_24;
		else
			return R.drawable.baseline_folder_open_24;
	}

	@Override
	public void refresh() {
		if (!isAdded())
			return;

		FeedsModel model = new ViewModelProvider(this).get(FeedsModel.class);
		model.startLoading(m_rootFeed, true);
	}

	@Override
	protected void onFeedsLoaded(List<Feed> loadedFeeds) {
		List<Feed> feedsWork = new ArrayList<>();

		sortFeeds(loadedFeeds, m_rootFeed);

		// virtual cats implemented in getCategories since api level 1
		if (m_activity.getApiLevel() == 0) {
			feedsWork.add(0, new Feed(-2, getString(R.string.cat_labels), true));
			feedsWork.add(1, new Feed(-1, getString(R.string.cat_special), true));
			feedsWork.add(new Feed(0, getString(R.string.cat_uncategorized), true));
		}

		if (m_activity.getUnreadOnly())
			loadedFeeds = loadedFeeds.stream()
					.filter(f -> f.id == Feed.CAT_SPECIAL || f.unread > 0)
					.collect(Collectors.toList());

		loadedFeeds = loadedFeeds.stream()
				.peek(f -> f.is_cat = true)
				.collect(Collectors.toList());

		feedsWork.addAll(loadedFeeds);

		feedsWork.add(new Feed(Feed.TYPE_DIVIDER));
		feedsWork.add(new Feed(Feed.TYPE_TOGGLE_UNREAD, getString(R.string.unread_only), true));

		m_adapter.submitList(feedsWork);

	}
}

