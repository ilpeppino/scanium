package android.graphics;

import java.io.OutputStream;

public class Bitmap {
    private int mWidth;
    private int mHeight;

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    // Fake fields for field access if Kotlin properties use them? 
    // Kotlin properties 'width' and 'height' usually map to getWidth()/getHeight().
    // But direct field access might be used if they were fields. 
    // In Android Bitmap, they are methods.

    public enum Config {
        ARGB_8888
    }

    public enum CompressFormat {
        JPEG, PNG, WEBP
    }

    public static Bitmap createBitmap(int width, int height, Config config) {
        Bitmap b = new Bitmap();
        b.mWidth = width;
        b.mHeight = height;
        return b;
    }

    public boolean compress(CompressFormat format, int quality, OutputStream stream) {
        try {
            stream.write(new byte[]{1, 2, 3});
        } catch (Exception e) {
            return false;
        }
        return true;
    }
}
