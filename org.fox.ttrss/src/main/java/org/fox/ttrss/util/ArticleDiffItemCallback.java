package org.fox.ttrss.util;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;

import org.fox.ttrss.types.Article;

public class ArticleDiffItemCallback extends DiffUtil.ItemCallback<Article> {
    private final String TAG = this.getClass().getSimpleName();
    @Override
    public boolean areItemsTheSame(@NonNull Article a1, @NonNull Article a2) {
        return a1.id == a2.id;
    }

    @Override
    public boolean areContentsTheSame(@NonNull Article a1, @NonNull Article a2) {
        return a1.id == a2.id && a1.unread == a2.unread && a1.marked == a2.marked
                && a1.selected == a2.selected && a1.published == a2.published
                && a1.note.equals(a2.note);
    }
}
