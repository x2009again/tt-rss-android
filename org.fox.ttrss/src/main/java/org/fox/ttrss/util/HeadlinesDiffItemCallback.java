package org.fox.ttrss.util;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;

import org.fox.ttrss.types.Article;

public class HeadlinesDiffItemCallback extends DiffUtil.ItemCallback<Article> {
    @Override
    public boolean areItemsTheSame(@NonNull Article a1, @NonNull Article a2) {
        // Log.d(TAG, "[DIFF] areItemsTheSame a1=" + a1.title + " a2=" + a2.title);

        return a1.id == a2.id;
    }

    @Override
    public boolean areContentsTheSame(@NonNull Article a1, @NonNull Article a2) {
        // Log.d(TAG, "[DIFF] areContentsTheSame a1=" + a1.title + " a2=" + a2.title);

        return a1.id == a2.id && a1.unread == a2.unread && a1.marked == a2.marked
                && a1.published == a2.published && a1.note.equals(a2.note);
    }
}
