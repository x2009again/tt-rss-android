package org.fox.ttrss.types;

import android.os.Parcel;
import android.os.Parcelable;

import org.fox.ttrss.Application;

import java.util.ListIterator;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class ArticleList extends CopyOnWriteArrayList<Article> implements Parcelable {
	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel out, int flags) {
		out.writeList(this);
	}

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
	
	public void readFromParcel(Parcel in) {
		in.readList(this, getClass().getClassLoader());
	}
	
	public ArticleList() { }
	
	public ArticleList(Parcel in) {		
		readFromParcel(in);
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

	public int getById(int id) {
		for (int i = 0; i < size(); i++) {
			if (get(i).id == id) {
				return i;
			}
		}

		return -1;
	}

	public String getAsCommaSeparatedIds() {
		return this.stream().map(a -> String.valueOf(a.id))
				.collect(Collectors.joining(","));
	}

	@SuppressWarnings("rawtypes")
	public static final Parcelable.Creator CREATOR =
    	new Parcelable.Creator() {
            public ArticleList createFromParcel(Parcel in) {
                return new ArticleList(in);
            }
 
            public ArticleList[] newArray(int size) {
                return new ArticleList[size];
            }
        };

}