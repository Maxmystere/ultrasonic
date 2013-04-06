/*
 This file is part of Subsonic.

 Subsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Subsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Subsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2009 (C) Sindre Mehus
 */
package net.sourceforge.subsonic.androidapp.util;

import android.app.ActionBar;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Handler;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import net.sourceforge.subsonic.androidapp.R;
import net.sourceforge.subsonic.androidapp.domain.MusicDirectory;
import net.sourceforge.subsonic.androidapp.service.MusicService;
import net.sourceforge.subsonic.androidapp.service.MusicServiceFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Asynchronous loading of images, with caching.
 * <p/>
 * There should normally be only one instance of this class.
 *
 * @author Sindre Mehus
 */
public class ImageLoader implements Runnable {

    private static final String TAG = ImageLoader.class.getSimpleName();
    private static final int CONCURRENCY = 5;

    private final LRUCache<String, Drawable> cache = new LRUCache<String, Drawable>(100);
    private final BlockingQueue<Task> queue;
    private final int imageSizeDefault;
    private final int imageSizeLarge;
    private Drawable largeUnknownImage;
    private Drawable drawable;

    public ImageLoader(Context context) {
        queue = new LinkedBlockingQueue<Task>(500);

        // Determine the density-dependent image sizes.
        imageSizeDefault = context.getResources().getDrawable(R.drawable.unknown_album).getIntrinsicHeight();
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        imageSizeLarge = (int) Math.round(Math.min(metrics.widthPixels, metrics.heightPixels));

        for (int i = 0; i < CONCURRENCY; i++) {
            new Thread(this, "ImageLoader").start();
        }

        createLargeUnknownImage(context);
    }

    private void createLargeUnknownImage(Context context) {
        BitmapDrawable drawable = (BitmapDrawable) context.getResources().getDrawable(R.drawable.unknown_album_large);
        Log.i(TAG, "createLargeUnknownImage");
        Bitmap bitmap = Util.scaleBitmap(drawable.getBitmap(), imageSizeLarge);

        //bitmap = createReflection(bitmap);
        largeUnknownImage = Util.createDrawableFromBitmap(context, bitmap);
    }

    public void loadImage(View view, MusicDirectory.Entry entry, boolean large, boolean crossfade) {
    	if (entry == null) {
            setUnknownImage(view, large);
            return;
    	}
    	
    	String coverArt = entry.getCoverArt();
    	
        if (coverArt == null) {
            setUnknownImage(view, large);
            return;
        }
        
        int size = large ? imageSizeLarge : imageSizeDefault;
        Drawable drawable = cache.get(getKey(coverArt, size));
        
        if (drawable != null) {
            setImage(view, drawable, large);
            return;
        }

        if (!large) {
            setUnknownImage(view, large);
        }
        
        queue.offer(new Task(view, entry, size, large, large, crossfade));
    }
    
    public void setActionBarArtwork(final View view, final MusicDirectory.Entry entry, final ActionBar ab) {
        if (entry == null || entry.getCoverArt() == null) {
        	ab.setLogo(largeUnknownImage);
        }

        final int size = imageSizeLarge;
        drawable = cache.get(getKey(entry.getCoverArt(), size));
        
        if (drawable != null) {
        	ab.setLogo(drawable);
        }
        
        final Handler handler = new Handler(){
            @Override
            public void handleMessage(Message msg) {
            	drawable = (Drawable) msg.obj;
                ab.setLogo(drawable);
            }
        };
        
    	new Thread(new Runnable() {
    	    public void run() {
            	MusicService musicService = MusicServiceFactory.getMusicService(view.getContext());
            	
                try
                {
                	Bitmap bitmap = musicService.getCoverArt(view.getContext(), entry, size, true, null);
                	drawable = Util.createDrawableFromBitmap(view.getContext(), bitmap);
                	Message msg = Message.obtain();
                	msg.obj = drawable;
                	handler.sendMessage(msg);
                	cache.put(getKey(entry.getCoverArt(), size), drawable);
                } catch (Throwable x) {
                	Log.e(TAG, "Failed to download album art.", x);
                }
    	    }
    	  }).start();
    }
    
    private String getKey(String coverArtId, int size) {
        return coverArtId + size;
    }

    private void setImage(View view, Drawable drawable, boolean crossfade) {
        if (view instanceof TextView) {
            // Cross-fading is not implemented for TextView since it's not in use.  It would be easy to add it, though.
            TextView textView = (TextView) view;
            textView.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null);
        } else if (view instanceof ImageView) {
            ImageView imageView = (ImageView) view;
            if (crossfade) {

                Drawable existingDrawable = imageView.getDrawable();
                if (existingDrawable == null) {
                    Bitmap emptyImage = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
                    existingDrawable = new BitmapDrawable(emptyImage);
                }

                Drawable[] layers = new Drawable[]{existingDrawable, drawable};

                TransitionDrawable transitionDrawable = new TransitionDrawable(layers);
                imageView.setImageDrawable(transitionDrawable);
                transitionDrawable.startTransition(250);
            } else {
                imageView.setImageDrawable(drawable);
            }
        }
    }

    private void setUnknownImage(View view, boolean large) {
        if (large) {
            setImage(view, largeUnknownImage, false);
        } else {
            if (view instanceof TextView) {
                ((TextView) view).setCompoundDrawablesWithIntrinsicBounds(R.drawable.unknown_album, 0, 0, 0);
            } else if (view instanceof ImageView) {
                ((ImageView) view).setImageResource(R.drawable.unknown_album);
            }
        }
    }

    public void clear() {
        queue.clear();
    }

    @Override
    public void run() {
        while (true) {
            try {
                Task task = queue.take();
                task.execute();
            } catch (Throwable x) {
                Log.e(TAG, "Unexpected exception in ImageLoader.", x);
            }
        }
    }

    private class Task {
        private final View view;
        private final MusicDirectory.Entry entry;
        private final Handler handler;
        private final int size;
        private final boolean reflection;
        private final boolean saveToFile;
        private final boolean crossfade;

        public Task(View view, MusicDirectory.Entry entry, int size, boolean reflection, boolean saveToFile, boolean crossfade) {
            this.view = view;
            this.entry = entry;
            this.size = size;
            this.reflection = reflection;
            this.saveToFile = saveToFile;
            this.crossfade = crossfade;
            handler = new Handler();
        }
        
        public void execute() {
            try {
                MusicService musicService = MusicServiceFactory.getMusicService(view.getContext());
                Bitmap bitmap = musicService.getCoverArt(view.getContext(), entry, size, saveToFile, null);

                if (reflection) {
                    //bitmap = createReflection(bitmap);
                }

                final Drawable drawable = Util.createDrawableFromBitmap(view.getContext(), bitmap);
                cache.put(getKey(entry.getCoverArt(), size), drawable);

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        setImage(view, drawable, crossfade);
                    }
                });
            } catch (Throwable x) {
                Log.e(TAG, "Failed to download album art.", x);
            }
        }
    }
}