package org.fox.ttrss;

import org.fox.ttrss.types.Article;
import org.fox.ttrss.types.ArticleList;

public interface HeadlinesEventListener {
	void onArticleListSelectionChange();
	void onArticleSelected(Article article);
	void onArticleSelected(Article article, boolean open);
	void onHeadlinesLoaded(boolean appended);	
}
