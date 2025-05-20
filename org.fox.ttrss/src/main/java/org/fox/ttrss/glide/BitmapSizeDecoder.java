package org.fox.ttrss.glide;

import android.graphics.BitmapFactory;

import androidx.annotation.NonNull;

import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.ResourceDecoder;
import com.bumptech.glide.load.engine.Resource;
import com.bumptech.glide.load.resource.SimpleResource;

import java.io.File;
import java.io.IOException;

class BitmapSizeDecoder implements ResourceDecoder<File, BitmapFactory.Options> {
    @Override
    public Resource<BitmapFactory.Options> decode(File file, int width, int height, Options options) throws IOException {
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        bmOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bmOptions);
        return new SimpleResource<>(bmOptions);
    }

    @Override
    public boolean handles(@NonNull File source, @NonNull Options options) throws IOException {
        return true;
    }
}

