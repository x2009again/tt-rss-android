package org.fox.ttrss.types;

import java.util.ListIterator;
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

	public ArticleList getWithoutFooters() {
		return this.stream().filter(a -> { return a.id > 0; }).collect(Collectors.toCollection(ArticleList::new));
	}

	public long getUnreadCount() {
		return this.stream().filter(a -> { return a.unread; }).count();
	}

	public long getSizeWithoutFooters() {
		return this.stream().filter(a -> { return a.id > 0; }).count();
	}

	/** strips all trailing items with negative IDs (Article.TYPE_LOADMORE, Article.TYPE_AMR_FOOTER) */
	public void stripFooters() {
		for (ListIterator<Article> iterator = this.listIterator(size()); iterator.hasPrevious();) {
			final Article article = iterator.previous();

			if (article.id < 0)
				this.remove(article);
			else
				break;
		}
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