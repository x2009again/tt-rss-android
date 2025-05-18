package org.fox.ttrss;

import android.app.Application;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import java.util.HashMap;

// this is used to store fragment data which is temporary but should survive orientation changes
public class HeadlinesFragmentModel extends AndroidViewModel {
    private HashMap<String, Size> m_flavorImageSizes = new HashMap<>();

    public HashMap<String, Size> getFlavorImageSizes() {
        return m_flavorImageSizes;
    }

    public HeadlinesFragmentModel(@NonNull Application application) {
        super(application);
    }
}
