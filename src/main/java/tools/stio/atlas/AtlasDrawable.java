/*
 * Copyright (c) 2017 Oleg Orlov. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tools.stio.atlas;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;

import java.io.File;
import java.util.TreeMap;

import tools.stio.atlas.Atlas.DownloadQueue;
import tools.stio.atlas.Atlas.FileStreamProvider;
import tools.stio.atlas.Atlas.ImageLoader;
import tools.stio.atlas.Atlas.ImageLoader.InputStreamProvider;
import tools.stio.atlas.Atlas.Tools;
import tools.stio.atlas.Dt.Log;

/**
 * ImageLoader/DownloadQueue backed drawable. Use {@link Atlas#imageFromUrl(String)} or {@link Atlas#imageFromUrlOrFile(String, File)}
 *
 * @author Oleg Orlov
 * @since  06 Nov 2017
 */
public class AtlasDrawable extends android.graphics.drawable.Drawable implements DownloadQueue.CompleteListener, ImageLoader.ImageLoadListener {
    private static final String TAG = AtlasDrawable.class.getSimpleName();
    private static final boolean debug = false;
    private static final boolean debugDraw = false;
    private static final Paint debugPaintDwnld = new Paint();
    private static final Paint debugPaintInflt = new Paint();
    private static final Paint debugPaintCmplt = new Paint();
    private static final Paint debugPaintStroke = new Paint();

    //
    // --- debug purposes drawable tracker ---
    //
    private static volatile int nextAutoId = 0;
    public static final TreeMap<Integer, AtlasDrawable> all = new TreeMap<Integer, AtlasDrawable>();
    /** drawables tracker for debug purposes */
    private final int autoId = nextAutoId++;
    {
        all.put(autoId, this);
    }
    //
    // ---     end of drawable tracker     ---
    //

    static {
        debugPaintDwnld.setStyle(Style.FILL);
        debugPaintDwnld.setColor(Color.rgb(176, 190, 197));

        debugPaintInflt.setStyle(Style.FILL);
        debugPaintInflt.setColor(Color.rgb(255, 150, 0));

        debugPaintCmplt.setStyle(Style.FILL);
        debugPaintCmplt.setColor(Color.rgb(36, 155, 34));

        debugPaintStroke.setStyle(Style.STROKE);
        debugPaintStroke.setColor(Color.BLACK);
    }

    /** Way to force ImageView to re-request image dimensions */
    private static final Handler mainHandler = new Handler(Looper.getMainLooper()) {
        public void handleMessage(android.os.Message msg) {
            switch (msg.what) {
                case 0: {
                    AtlasDrawable drawable = (AtlasDrawable) msg.obj;
                    //if (debug) Log.w(TAG, "handleMessage() callback: " + drawable.getCallback() + ", msg: " + msg);
                    if (drawable.getCallback() instanceof ImageView) {
                        ((ImageView)drawable.getCallback()).setImageState(null, true);
                    }
                    drawable.invalidateSelf();
                    break;
                }
            }
        }
    };

    /** Is used to address bitmaps in cache */
    private String id;

    /** is used while image original width  is not available (during download and inflating)*/
    private int defaultWidth    = 1;
    /** is used while image original height is not available (during download and inflating)*/
    private int defaultHeight   = 1;

    File from;
    InputStreamProvider inputStreamProvider;
    ImageLoader.ImageSpec spec;
    Paint workPaint = new Paint();
    long inflatedAt = 0;

    /**
     * if true - inflates bitmap as soon as bitmap's data get available. Otherwise only at first draw.
     * Saves heap from images that might be never rendered
     */
    final boolean inflateImmediately = false;

    /** provides boundaries */
    private BoundsProvider boundsProvider;

    private static final int FADING_MILLIS = 120;

    private int fadeInDuration = FADING_MILLIS;

    /**
     * Creates {@link AtlasDrawable} ready to download and display.
     * {@link #schedule(DownloadQueue)} needs to be called to download file in order to display it.
     * <p>If file exists, use {@link AtlasDrawable#AtlasDrawable(String, File)} to avoid download
     *
     * @param {@link #id} - must be specified for correct work
     */
    public AtlasDrawable(String imageId){
        if (imageId == null) throw new IllegalArgumentException("AtlasDrawable .id cannot be null");
        this.id = imageId;
        if (debug) Log.w(TAG, "AtlasDrawable() " + autoId + ": " + imageId);
    }

    public AtlasDrawable(String imageId, InputStreamProvider inputStreamProvider) {
        if (imageId == null) throw new IllegalArgumentException("AtlasDrawable .id cannot be null");
        this.id = imageId;
        this.inputStreamProvider = inputStreamProvider;
        if (debug) Log.w(TAG, "AtlasDrawable() " + autoId + ": " + imageId + ", provider: " + inputStreamProvider);
    }

    /**
     * Creates {@link AtlasDrawable} from local file
     *
     * @param imageId - is used to address objects in cache
     */
    public AtlasDrawable(String imageId, File from) {
        this(imageId);
        if (from == null) throw new IllegalArgumentException("file must be specified");
        if (from.exists() && from.isDirectory()) throw new IllegalArgumentException("Specified file is a directory and cannot be used [" + from.getAbsolutePath()+ "]");

        this.from = from;
        this.inputStreamProvider = new FileStreamProvider(from);
    }

    public AtlasDrawable defaultSize(int width, int height) {
        this.defaultWidth  = width;
        this.defaultHeight = height;
        return this;
    }

    /** schedule download file to default location */
    public AtlasDrawable schedule(DownloadQueue queue) {
        queue.schedule(id, this);
        return this;
    }

    /** schedule download to specific location */
    public AtlasDrawable schedule(DownloadQueue queue, File toFile) {
        queue.schedule(id, toFile, this);
        return this;
    }

    @Override
    public void onDownloadComplete(String url, File file) {
        if (debug) Log.w(TAG, "onDownloadComplete() id: " + id + ", file: " + file);
        this.from = file;
        this.inputStreamProvider = new FileStreamProvider(from);
        if (inflateImmediately) {
            requestInflate();
        }

        invalidate();
    }

    @Override
    public void onImageLoaded(ImageLoader.ImageSpec spec) {
        this.spec = spec;
        long now = System.currentTimeMillis();
        if (debug) Log.w(TAG, "onImageLoaded()      "
                              + (inflatedAt != 0 ? "inlatedAt: [" + (now - inflatedAt) + " -> 0]" : "")
                              + " spec: " + spec + ", callback: " + getCallback());
        //if (debug) Log.w(TAG, "onImageLoaded()      spec: " + spec + ", callback: " + getCallback());
        if (inflatedAt == 0) this.inflatedAt = now;
        invalidate();
    }

    @Override
    public void setBounds(int left, int top, int right, int bottom) {
        int width = right - left;
        int height = bottom - top;
         if (debug) Log.w(TAG, "setBounds()          " + left+ "," + top + " -> " + right+ "," + bottom + (spec == null ? (", id: " + id) : ", spec: " + spec) + ", from: " + Dt.printStackTrace(7));
        super.setBounds(left, top, right, bottom);
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        if (debug) Log.w(TAG, "onBoundsChange()     " + bounds + (spec == null ? (", id: " + id) : ", spec: " + spec) + ", from: " + Dt.printStackTrace(7));
        super.onBoundsChange(bounds);
    }

    public interface BoundsProvider {
        public int getIntrinsicWidth (AtlasDrawable of, int intrinsicWidth, int intrinsicHeight);
        public int getIntrinsicHeight(AtlasDrawable of, int intrinsicWidth, int intrinsicHeight);
    }

    public static class ImageViewBounds implements BoundsProvider {
        private static final String TAG = ImageViewBounds.class.getSimpleName();
        private static final boolean debug = false;

        ImageView imageView;

        public ImageViewBounds(ImageView imageView) {
            this.imageView = imageView;
        }

        public int getIntrinsicWidth(AtlasDrawable of, int intrinsicWidth, int intrinsicHeight) {
            if (debug) Log.w(TAG, "getIntrinsicWidth() .ImageViewBounds \tintrinsic: " + intrinsicWidth + "x" + intrinsicHeight + ", iv: " + imageView.getWidth() + "x" + imageView.getHeight());

            if (imageView.getScaleType() == ScaleType.CENTER_CROP) {
                if (imageView.getWidth() == 0) return intrinsicWidth;   // no view dimensions - no calculations
                int width = imageView.getWidth();
                if (debug) Log.w(TAG, "getIntrinsicWidth() .ImageViewBounds width: " + width + ",\tintrinsic: " + intrinsicWidth + "x" + intrinsicHeight + ", iv: " + imageView.getWidth() + "x" + imageView.getHeight());
                return width;
            }
            return intrinsicWidth;
        }

        public int getIntrinsicHeight(AtlasDrawable of, int intrinsicWidth, int intrinsicHeight) {
            if (debug) Log.w(TAG, "getIntrinsicHeight() .ImageViewBounds \tintrinsic: " + intrinsicWidth + "x" + intrinsicHeight + ", iv: " + imageView.getWidth() + "x" + imageView.getHeight());

            if (imageView.getScaleType() == ScaleType.CENTER_CROP) {
                if (imageView.getHeight() == 0) return intrinsicHeight;
                double ratio = 1.0 * imageView.getWidth() / intrinsicWidth;
                int height = (int) (intrinsicHeight * ratio);
                if (debug) Log.w(TAG, "getIntrinsicHeight() .ImageViewBounds height: " + height + ",\tintrinsic: " + intrinsicWidth + "x" + intrinsicHeight + ", iv: " + imageView.getWidth() + "x" + imageView.getHeight());
                return height;
            }

            return intrinsicHeight;
        }
    }


    public AtlasDrawable setBounds(BoundsProvider boundsProvider)  {
        this.boundsProvider = boundsProvider;
        return this;
    }

    private int getInnerIntrinsicWidth() {
        int width = 0;
        if (spec != null) width = spec.originalWidth;
        if (width == 0) width = Atlas.imageLoader.getOriginalImageWidth(id);
        if (width == 0) width = defaultWidth;   // fallback to default
        if (debug) Log.w(TAG, "getInnerIntrinsicWidth()  " + width + (spec == null ? (", id: " + id) : ", spec: " + spec) + " from: " + Dt.printStackTrace(7));
        return width;
    }

    private int getInnerIntrinsicHeight() {
        int height = 0;
        if (spec != null) height = spec.originalHeight;
        if (height == 0) height = Atlas.imageLoader.getOriginalImageHeight(id);
        if (height == 0) height = defaultHeight;
        if (debug) Log.w(TAG, "getInnerIntrinsicHeight() " + height + (spec == null ? (", id: " + id) : ", spec: " + spec)  + " from: " + Dt.printStackTrace(7));
        return height;
    }

    /**
     * Return original size of image or 1 if image is not loaded
     * <p><b>NOTE:</b> if intrinsic dimensions are 0x0 - ImageView doesn't pass control to {@link AtlasDrawable#draw(Canvas)}
     */
    @Override
    public int getIntrinsicWidth() {

        int width = getInnerIntrinsicWidth();
        int height = getInnerIntrinsicHeight();

        if (boundsProvider != null) {
            width = boundsProvider.getIntrinsicWidth(this, width, height);
            if (width == 0) Log.e(TAG, "getIntrinsicWidth() provider must not return 0 width");
            if (debug) Log.w(TAG, "getIntrinsicWidth()  provider: " + width + (spec == null ? (", id: " + id) : ", spec: " + spec) + " from: " + Dt.printStackTrace(7));
            return width;
        }

        return width;
    }

    /**
     * Return original size of image or 1 if image is not loaded
     * <p><b>Note:</b> if intrinsic dimensions are 0x0 - ImageView doesn't pass control to {@link AtlasDrawable#draw(Canvas)}
     */
    @Override
    public int getIntrinsicHeight() {
        int width = getInnerIntrinsicWidth();
        int height = getInnerIntrinsicHeight();

        if ( boundsProvider != null) {
            height = boundsProvider.getIntrinsicHeight(this, width, height);
            if (height == 0) Log.e(TAG, "getIntrinsicWidth() provider must not return 0 height");
            if (debug) Log.w(TAG, "getIntrinsicHeight() provider: " + height + (spec == null ? (", id: " + id) : ", spec: " + spec)  + " from: " + Dt.printStackTrace(7));
        }

        return height;
    }

    @Override
    public void draw(Canvas canvas) {
        if (debug) Log.d(TAG, "draw() " + autoId +  ": id: " + id + ", callback: " + getCallback());
        Bitmap bmp = (Bitmap) Atlas.imageLoader.getImageFromCache(id);
        if (bmp != null) {
            long now = System.currentTimeMillis();

            // bmp may be available when drawable is new (inflatedAt = 0), so set "inflated" somewhen in the past
            if (inflatedAt == 0) inflatedAt = now - (fadeInDuration * 2);

            long age = now - inflatedAt;
            int alpha = (int) (255 * 1.0f * Math.min(age, fadeInDuration) / fadeInDuration);
            workPaint.setAlpha(alpha);
            if (debug) Log.d(TAG, "draw() " + autoId +  ": age: " + age + ", alpha: " + alpha);

            if (bmp.getWidth() > 1400 || bmp.getHeight() > 1400) {
                //boolean debug = true;
                if (debug) Log.w(TAG, "draw() " + autoId +  ": huge bmp: " + bmp.getWidth() + "x" + bmp.getHeight() + ", required " + getBounds().width() + "x" + getBounds().height() + ": " + spec);
            }

            canvas.drawBitmap(bmp, null, getBounds(), workPaint );
            if (        (getBounds().width()  > bmp.getWidth()  && spec != null && bmp.getWidth()  < spec.originalWidth)
                    ||  (getBounds().height() > bmp.getHeight() && spec != null && bmp.getHeight() < spec.originalHeight)) {
                requestInflate();
            }
            if (age < fadeInDuration) {
                invalidateSelf();
            }
        } else {
            if (debug) Log.d(TAG, "draw() " + autoId +  ": no bitmap, request id: " + id);

            // if we draw empty bitmap, need to animate appeareance after it is inflated
            inflatedAt = 0;

            requestInflate();
        }
        if (debugDraw) {
            if (bmp != null) {
                Tools.drawRect(getBounds().left, getBounds().top, 10, 10, debugPaintCmplt, debugPaintStroke, canvas);
            } else if (inputStreamProvider == null) {
                Tools.drawRect(getBounds().left, getBounds().top, 10, 10, debugPaintDwnld, debugPaintStroke, canvas);
            } else {
                Tools.drawRect(getBounds().left, getBounds().top, 10, 10, debugPaintInflt, debugPaintStroke, canvas);
            }
        }
    }

    /** only when file is fetched and not scheduled yet. supports following scenarios:
     * - drawable is new, bmp is not available non-scheduled
     * - drawable is new, bmp is not available,    scheduled
     * - drawable is new, bmp is already inflated
     * - drawable had bmp before, but now bmp is not available, non-scheduled
     * - drawable had bmp before, but now bmp is not available,     scheduled
     */
    void requestInflate() {
        // if already scheduled - don't schedule again.
        // XXX: if two drawables schedule same image, one of them wouldn't be notified

        if (inputStreamProvider != null) {
            int requiredWidth = getBounds().width();
            int requiredHeight = getBounds().height();

            if (requiredWidth == defaultWidth || requiredHeight == defaultHeight)  {
                if (debug) Log.w(TAG, "requestInflate() small boundaries: " + requiredWidth + "x" + requiredHeight + ", \t id: " + id + ", spec: " + spec);
                Atlas.imageLoader.requestImage(id, inputStreamProvider, this, true);
            } else {
                if (debug) Log.w(TAG, "requestInflate()       boundaries: " + requiredWidth + "x" + requiredHeight + ", \t id: " + id);
                boolean decodeOnly = requiredWidth == 0 && requiredHeight == 0;
                Atlas.imageLoader.requestImage(id, inputStreamProvider, requiredWidth, requiredHeight, false, this, decodeOnly);
            }
        }
    }

    protected void invalidate() {
        if  (Looper.getMainLooper() == Looper.myLooper()) invalidateSelf();
        else mainHandler.obtainMessage(0, this).sendToTarget();
    }

    public void invalidateSelf() {
        if (getCallback() == null) {
            if (debug) Log.w(TAG, "invalidateSelf() callback is null!");
        }
        super.invalidateSelf();
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder
            .append(autoId).append(": ")
            .append("id: ").append(id)
            .append(", from: ").append(from)
            .append(", spec: ").append(spec)
            .append(", inflatedAt: ").append(inflatedAt);
        return builder.toString();
    }

    public String getId() {
        return id;
    }

    public ImageLoader.ImageSpec getSpec() {
        return spec;
    }

    public File getFile() {
        return from;
    }

}
