package org.fox.ttrss;

import org.fox.ttrss.types.Article;

public interface HeadlinesEventListener {
    void onArticleSelected(Article article);

    void onHeadlinesLoaded(boolean appended);

    void onHeadlinesLoadingProgress(int progress);
}
