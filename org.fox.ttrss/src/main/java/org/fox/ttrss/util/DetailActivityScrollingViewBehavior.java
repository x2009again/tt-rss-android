package org.fox.ttrss.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.ViewCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.bottomappbar.BottomAppBar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;

public class DetailActivityScrollingViewBehavior extends AppBarLayout.ScrollingViewBehavior {

    private final SharedPreferences m_prefs;

    public DetailActivityScrollingViewBehavior(Context context, AttributeSet attrs) {
        super(context, attrs);

        m_prefs = PreferenceManager
                .getDefaultSharedPreferences(context);
    }

    @Override
    public boolean layoutDependsOn(CoordinatorLayout parent, View child, View dependency) {
        return super.layoutDependsOn(parent, child, dependency) ||
                dependency instanceof FloatingActionButton || dependency instanceof BottomAppBar;
    }

    @Override
    public boolean onStartNestedScroll(@NonNull CoordinatorLayout coordinatorLayout,
                                       @NonNull View child, @NonNull View directTargetChild,
                                       @NonNull View target, int axes, int type) {
        // Ensure we react to vertical scrolling
        return axes == ViewCompat.SCROLL_AXIS_VERTICAL ||
                super.onStartNestedScroll(coordinatorLayout, child, directTargetChild, target, axes, type);
    }

    @Override
    public void onNestedPreScroll(@NonNull CoordinatorLayout coordinatorLayout,
                                  @NonNull View child, @NonNull View target, int dx, int dy,
                                  @NonNull int[] consumed, int type) {
        super.onNestedPreScroll(coordinatorLayout, child, target, dx, dy, consumed, type);

        boolean enableFab = m_prefs.getBoolean("enable_article_fab", true);

        if (dy > 0) {
            // User scrolled down -> hide the FAB
            List<View> dependencies = coordinatorLayout.getDependencies(child);
            for (View view : dependencies) {
                if (view instanceof FloatingActionButton) {
                    ((FloatingActionButton) view).hide();
                } else if (view instanceof BottomAppBar) {
                    ((BottomAppBar) view).performHide();
                }
            }
        } else if (dy < 0) {
            // User scrolled up -> show the FAB
            List<View> dependencies = coordinatorLayout.getDependencies(child);
            for (View view : dependencies) {
                if (enableFab && view instanceof FloatingActionButton) {
                    ((FloatingActionButton) view).show();
                } else if (view instanceof BottomAppBar) {
                    ((BottomAppBar) view).performShow();
                }
            }
        }

    }
}