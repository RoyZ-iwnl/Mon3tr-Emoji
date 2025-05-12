package gg.dmr.royz.m3.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class ImageConverter {
    private static final String TAG = "ImageConverter";
    private static final int TARGET_SIZE = 240; // 目标尺寸

    public static byte[] convertImage(Context context, Uri uri) throws Exception {
        // 读取图片
        InputStream inputStream = context.getContentResolver().openInputStream(uri);
        if (inputStream == null) {
            throw new Exception("无法打开图片");
        }

        // 获取图片原始尺寸
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(inputStream, null, options);
        inputStream.close();

        // 计算缩放比例
        int scale = Math.max(1, Math.min(
                options.outWidth / TARGET_SIZE,
                options.outHeight / TARGET_SIZE
        ));

        // 重新打开输入流
        inputStream = context.getContentResolver().openInputStream(uri);
        if (inputStream == null) {
            throw new Exception("无法打开图片");
        }

        // 加载缩放后的图片
        options = new BitmapFactory.Options();
        options.inSampleSize = scale;
        Bitmap originalBitmap = BitmapFactory.decodeStream(inputStream, null, options);
        inputStream.close();

        if (originalBitmap == null) {
            throw new Exception("图片解码失败");
        }

        try {
            // 创建目标大小的位图
            float ratioX = TARGET_SIZE / (float) originalBitmap.getWidth();
            float ratioY = TARGET_SIZE / (float) originalBitmap.getHeight();
            float ratio = Math.min(ratioX, ratioY);

            Matrix matrix = new Matrix();
            matrix.postScale(ratio, ratio);

            Bitmap resizedBitmap = Bitmap.createBitmap(
                    originalBitmap,
                    0, 0,
                    originalBitmap.getWidth(),
                    originalBitmap.getHeight(),
                    matrix,
                    true
            );

            // 如果生成了新的位图，回收原始位图
            if (resizedBitmap != originalBitmap) {
                originalBitmap.recycle();
            }

            // 转换为RGB565格式
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            int width = resizedBitmap.getWidth();
            int height = resizedBitmap.getHeight();

            // 确保位图未被回收
            if (!resizedBitmap.isRecycled()) {
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int pixel = resizedBitmap.getPixel(x, y);

                        // 提取RGB分量
                        int r = (pixel >> 16) & 0xFF;
                        int g = (pixel >> 8) & 0xFF;
                        int b = pixel & 0xFF;

                        // 转换为RGB565格式
                        int rgb565 = ((r & 0xF8) << 8) | ((g & 0xFC) << 3) | (b >> 3);

                        // 写入两个字节
                        outputStream.write((rgb565 >> 8) & 0xFF);
                        outputStream.write(rgb565 & 0xFF);
                    }
                }

                // 最后才回收resizedBitmap
                resizedBitmap.recycle();

                return outputStream.toByteArray();
            } else {
                throw new Exception("位图已被回收");
            }
        } catch (Exception e) {
            Log.e(TAG, "图片处理失败", e);
            throw new Exception("图片处理失败: " + e.getMessage());
        }
    }
}