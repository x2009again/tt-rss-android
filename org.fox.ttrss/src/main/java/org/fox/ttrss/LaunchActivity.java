package org.fox.ttrss;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

@SuppressLint("CustomSplashScreen")
public class LaunchActivity extends Activity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // https://code.google.com/p/android/issues/detail?id=26658
        if (!isTaskRoot()) {
            finish();
            return;
        }

        Intent main = new Intent(LaunchActivity.this, OnlineActivity.class);
        startActivity(main);

        finish();
    }

}
