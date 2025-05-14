package org.fox.ttrss.util;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.AsyncListDiffer;
import androidx.recyclerview.widget.DiffUtil;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import java.util.List;

// https://gist.github.com/Gnzlt/7e8a23ba0c3b046ed33c824b284d7270
public abstract class DiffFragmentStateAdapter<T> extends FragmentStateAdapter {

    private final AsyncListDiffer<T> differ;

    protected DiffFragmentStateAdapter(FragmentActivity fragmentActivity, DiffUtil.ItemCallback<T> diffCallback) {
        super(fragmentActivity);
        differ = new AsyncListDiffer<>(this, diffCallback);
    }

    protected DiffFragmentStateAdapter(Fragment fragment, DiffUtil.ItemCallback<T> diffCallback) {
        super(fragment);
        differ = new AsyncListDiffer<>(this, diffCallback);
    }

    protected DiffFragmentStateAdapter(FragmentManager fragmentManager, Lifecycle lifecycle, DiffUtil.ItemCallback<T> diffCallback) {
        super(fragmentManager, lifecycle);
        differ = new AsyncListDiffer<>(this, diffCallback);
    }

    public void submitList(List<T> list, Runnable commitCallback) {
        differ.submitList(list, commitCallback);
    }

    public void submitList(List<T> list) {
        differ.submitList(list, null);
    }

    public List<T> getCurrentList() {
        return differ.getCurrentList();
    }

    protected T getItem(int position) {
        return differ.getCurrentList().get(position);
    }

    @Override
    public int getItemCount() {
        return differ.getCurrentList().size();
    }
}

