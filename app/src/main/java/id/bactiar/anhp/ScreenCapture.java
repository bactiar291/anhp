package id.bactiar.anhp;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

final class ScreenCapture {
    private final Context context;
    private MediaProjection projection;
    private ImageReader reader;
    private VirtualDisplay display;
    private int width;
    private int height;
    private int density;

    ScreenCapture(Context context) {
        this.context = context.getApplicationContext();
    }

    synchronized void start(int resultCode, Intent data) {
        stop();
        MediaProjectionManager manager =
                (MediaProjectionManager) context.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (manager == null) throw new IllegalStateException("MediaProjectionManager unavailable");
        projection = manager.getMediaProjection(resultCode, data);
        if (projection == null) throw new IllegalStateException("MediaProjection denied");
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) throw new IllegalStateException("WindowManager unavailable");
        wm.getDefaultDisplay().getRealMetrics(metrics);
        width = Math.max(1, metrics.widthPixels);
        height = Math.max(1, metrics.heightPixels);
        density = metrics.densityDpi;
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        display = projection.createVirtualDisplay(
                "AnHP-capture",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.getSurface(),
                null,
                null);
    }

    synchronized boolean isReady() {
        return projection != null && reader != null && display != null;
    }

    synchronized void stop() {
        if (display != null) {
            display.release();
            display = null;
        }
        if (reader != null) {
            reader.close();
            reader = null;
        }
        if (projection != null) {
            projection.stop();
            projection = null;
        }
    }

    String captureJpegBase64(int maxWidth, int quality) throws Exception {
        Image image = null;
        ImageReader localReader;
        synchronized (this) {
            if (!isReady()) throw new IllegalStateException("Screen capture not active");
            localReader = reader;
        }
        long deadline = System.currentTimeMillis() + 1600;
        while (System.currentTimeMillis() < deadline && image == null) {
            image = localReader.acquireLatestImage();
            if (image == null) Thread.sleep(80);
        }
        if (image == null) throw new IllegalStateException("No screen frame available");
        try {
            Bitmap bitmap = imageToBitmap(image);
            try {
                Bitmap output = bitmap;
                Bitmap resized = null;
                if (bitmap.getWidth() > maxWidth) {
                    int newHeight = Math.max(1, (int) Math.round(bitmap.getHeight() * (maxWidth / (double) bitmap.getWidth())));
                    resized = Bitmap.createScaledBitmap(bitmap, maxWidth, newHeight, true);
                    output = resized;
                }
                try {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    output.compress(Bitmap.CompressFormat.JPEG, Math.max(35, Math.min(quality, 90)), out);
                    return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
                } finally {
                    if (resized != null) resized.recycle();
                }
            } finally {
                bitmap.recycle();
            }
        } finally {
            image.close();
        }
    }

    private static Bitmap imageToBitmap(Image image) {
        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        int pixelStride = plane.getPixelStride();
        int rowStride = plane.getRowStride();
        int width = image.getWidth();
        int height = image.getHeight();
        int rowPadding = rowStride - pixelStride * width;
        int bitmapWidth = width + rowPadding / pixelStride;
        Bitmap padded = Bitmap.createBitmap(bitmapWidth, height, Bitmap.Config.ARGB_8888);
        padded.copyPixelsFromBuffer(buffer);
        Bitmap cropped = Bitmap.createBitmap(padded, 0, 0, width, height);
        padded.recycle();
        return cropped;
    }
}
