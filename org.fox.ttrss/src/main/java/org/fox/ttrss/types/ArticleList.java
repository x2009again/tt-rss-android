package org.fox.ttrss.types;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class ArticleList extends CopyOnWriteArrayList<Article> {
    public boolean containsId(int id) {
        return findById(id) != null;
    }

    public boolean contains(Article article) {
        return containsId(article.id);
    }

	public Article findById(int id) {
		for (Article a : this) {
			if (a.id == id)
				return a;			
		}			
		return null;
	}

	public ArticleList() { }

	public ArticleList(ArticleList clone) {
		this.addAll(clone);
	}

	public int getUnreadCount() {
		return getUnread().size();
	}

	public ArticleList getUnread() {
		return this.stream().filter(a -> { return a.unread; }).collect(Collectors.toCollection(ArticleList::new));
	}

	public ArticleList getSelected() {
		return this.stream().filter(a -> { return a.selected; }).collect(Collectors.toCollection(ArticleList::new));
	}

	public int getSelectedCount() {
		return getSelected().size();
	}

	public int getPositionById(int id) {
		for (int i = 0; i < size(); i++) {
			if (get(i).id == id) {
				return i;
			}
		}

		return -1;
	}

	public Article getById(int id) {
		for (Article a : this) {
			if (a.id == id)
				return a;
		}
		return null;
	}
	public String getAsCommaSeparatedIds() {
		return this.stream().map(a -> String.valueOf(a.id))
				.collect(Collectors.joining(","));
	}
}