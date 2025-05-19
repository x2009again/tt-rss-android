package org.fox.ttrss;

import android.annotation.SuppressLint;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import androidx.loader.content.Loader;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

import org.fox.ttrss.types.Feed;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class RootCategoriesFragment extends FeedsFragment {
	private final String TAG = this.getClass().getSimpleName();

	@Override
	protected FeedsModel getModel() {
		return new ViewModelProvider(this).get(RootCategoriesModel.class);
	}
}
