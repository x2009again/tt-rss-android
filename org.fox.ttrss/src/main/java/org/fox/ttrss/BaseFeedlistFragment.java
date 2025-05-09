package org.fox.ttrss;

import android.content.Intent;
import android.content.SharedPreferences;
import android.util.TypedValue;
import android.view.InflateException;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;

import org.fox.ttrss.offline.OfflineActivity;

import java.net.MalformedURLException;
import java.net.URL;

public abstract class BaseFeedlistFragment extends androidx.fragment.app.Fragment {
    abstract public void refresh();

    public void initDrawerHeader(LayoutInflater inflater, View view, ListView list, final CommonActivity activity, final SharedPreferences prefs, boolean isRoot) {

        boolean isOffline = activity instanceof OfflineActivity;

        try {
            View layout = inflater.inflate(R.layout.drawer_header, list, false);
            list.addHeaderView(layout, null, false);

            TextView login = view.findViewById(R.id.drawer_header_login);
            TextView server = view.findViewById(R.id.drawer_header_server);

            login.setText(prefs.getString("login", ""));
            try {
                server.setText(new URL(prefs.getString("ttrss_url", "")).getHost());
            } catch (MalformedURLException e) {
                server.setText("");
            }

            View settings = view.findViewById(R.id.drawer_settings_btn);

            settings.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        Intent intent = new Intent(getActivity(),
                                PreferencesActivity.class);

                        startActivityForResult(intent, 0);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });

            /* deal with ~material~ footers */

            // divider
            final View footer = inflater.inflate(R.layout.drawer_divider, list, false);
            footer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    //
                }
            });
            list.addFooterView(footer);

            // unread only checkbox
            final View rowToggle = inflater.inflate(R.layout.feeds_row_toggle, list, false);
            list.addFooterView(rowToggle);
            TextView text = rowToggle.findViewById(R.id.title);
            text.setText(R.string.unread_only);

            ImageView icon = rowToggle.findViewById(R.id.icon);
            TypedValue tv = new TypedValue();
            getActivity().getTheme().resolveAttribute(R.attr.ic_filter_variant, tv, true);
            icon.setImageResource(tv.resourceId);

            final SwitchCompat rowSwitch = rowToggle.findViewById(R.id.row_switch);
            rowSwitch.setChecked(activity.getUnreadOnly());

            rowSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton button, boolean isChecked) {
                    activity.setUnreadOnly(isChecked);
                    refresh();
                }
            });

            footer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    rowSwitch.setChecked(!rowSwitch.isChecked());
                }
            });

            // root or subdirectory (i.e. feed category)
            if (isRoot) {
                // offline
                final View offlineFooter = inflater.inflate(R.layout.feeds_row, list, false);
                offlineFooter.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (activity instanceof OnlineActivity) {
                            ((OnlineActivity)activity).switchOffline();

                        } else if (activity instanceof OfflineActivity) {
                            ((OfflineActivity)activity).switchOnline();
                        }
                    }
                });

                list.addFooterView(offlineFooter);
                text = offlineFooter.findViewById(R.id.title);
                text.setText(isOffline ? R.string.go_online : R.string.go_offline);

                icon = offlineFooter.findViewById(R.id.icon);
                tv = new TypedValue();
                getActivity().getTheme().resolveAttribute(isOffline ? R.attr.ic_cloud_upload : R.attr.ic_cloud_download, tv, true);
                icon.setImageResource(tv.resourceId);

                TextView counter = offlineFooter.findViewById(R.id.unread_counter);
                counter.setText(R.string.blank);
            }

        } catch (InflateException e) {
            // welp couldn't inflate header i guess
            e.printStackTrace();
        } catch (java.lang.UnsupportedOperationException e) {
            e.printStackTrace();
        }

    }

}
