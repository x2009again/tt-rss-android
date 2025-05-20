package org.fox.ttrss;

import androidx.lifecycle.ViewModelProvider;

public class RootCategoriesFragment extends FeedsFragment {
	private final String TAG = this.getClass().getSimpleName();

	@Override
	protected FeedsModel getModel() {
		return new ViewModelProvider(this).get(RootCategoriesModel.class);
	}
}
