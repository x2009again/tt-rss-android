package org.fox.ttrss.util;

import androidx.recyclerview.widget.DiffUtil;

import org.fox.ttrss.types.ArticleList;

public class HeadlinesDiffUtilCallback extends DiffUtil.Callback {
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
			return m_newList.get(newItemPosition).id == m_oldList.get(oldItemPosition).id;
		}

		@Override
		public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
			return false;
		}
	}
