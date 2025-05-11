package org.fox.ttrss;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import java.text.SimpleDateFormat;
import java.util.Date;

public class PreferencesFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());

        findPreference("ttrss_url").setSummary(prefs.getString("ttrss_url", getString(R.string.ttrss_url_summary)));
        findPreference("login").setSummary(prefs.getString("login", getString(R.string.login_summary)));

        findPreference("show_logcat").setOnPreferenceClickListener(new androidx.preference.Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(@NonNull androidx.preference.Preference preference) {
                Intent intent = new Intent(getActivity(), LogcatActivity.class);
                startActivity(intent);
                return false;
            }
        });

        findPreference("network_settings").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                getActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.preferences_container, new NetworkPreferencesFragment() )
                        .addToBackStack( NetworkPreferencesFragment.class.getSimpleName() )
                        .commit();

                return false;
            }
        });

        CommonActivity activity = (CommonActivity) getActivity();

        findPreference("force_phone_layout").setEnabled(activity.isTablet());

        try {
            String version;
            int versionCode;
            String buildTimestamp;

            PackageInfo packageInfo = activity.getPackageManager().
                    getPackageInfo(activity.getPackageName(), 0);

            version = packageInfo.versionName;
            versionCode = packageInfo.versionCode;

            findPreference("version").setSummary(getString(R.string.prefs_version, version, versionCode));

            buildTimestamp = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss").format(new Date(BuildConfig.TIMESTAMP));

            findPreference("build_timestamp").setSummary(getString(R.string.prefs_build_timestamp, buildTimestamp));

        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences,rootKey);
    }
}