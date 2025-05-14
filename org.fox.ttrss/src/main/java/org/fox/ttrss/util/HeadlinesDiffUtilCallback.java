package org.fox.ttrss.util;

import android.util.Log;

import androidx.recyclerview.widget.DiffUtil;

import org.fox.ttrss.types.Article;
import org.fox.ttrss.types.ArticleList;

public class HeadlinesDiffUtilCallback extends DiffUtil.Callback {
		private final String TAG = this.getClass().getSimpleName();
		private ArticleList m_oldList;
		private ArticleList m_newList;

		public HeadlinesDiffUtilCallback(ArticleList oldList, ArticleList newList) {
			m_oldList = oldList;
			m_newList = newList;
		}

		@Override
		public int getOldListSize() {
			return m_oldList != null ? m_oldList.size() : 0;
		}

		@Override
		public int getNewListSize() {
			return m_newList != null ? m_newList.size() : 0;
		}

		@Override
		public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
			Article a1 = m_oldList.get(oldItemPosition);
			Article a2 = m_newList.get(newItemPosition);

			// Log.d(TAG, "[DIFF] areItemsTheSame a1=" + a1.title + " a2=" + a2.title);

			return a1.id == a2.id;
		}

		@Override
		public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
			Article a1 = m_oldList.get(oldItemPosition);
			Article a2 = m_newList.get(newItemPosition);

			// Log.d(TAG, "[DIFF] areContentsTheSame a1=" + a1.title + " a2=" + a2.title);

			return a1.id == a2.id && a1.unread == a2.unread && a1.marked == a2.marked
				&& a1.published == a2.published && a1.note.equals(a2.note);
		}
	}
