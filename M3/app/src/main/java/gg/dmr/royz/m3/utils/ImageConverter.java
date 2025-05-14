package gg.dmr.royz.m3.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;

import java.nio.ByteBuffer;

import gg.dmr.royz.m3.bluetooth.Constants;

/**
 * 图片转换工具类
 * 将普通图片转换为设备所需的RGB565格式
 */
public class ImageConverter {
    private static final String TAG = "ImageConverter";

    /**
     * 将位图转换为RGB565格式的字节数组
     * 实现类似Python示例中的convert_image_to_rgb565函数
     */
    public static byte[] convertBitmapToRgb565(Bitmap originalBitmap) {
        // 调整大小并保持宽高比
        Bitmap resizedBitmap = resizeToFit(originalBitmap, Constants.IMAGE_WIDTH, Constants.IMAGE_HEIGHT);

        // 创建目标大小的空白画布(240x240)
        Bitmap targetBitmap = Bitmap.createBitmap(Constants.IMAGE_WIDTH, Constants.IMAGE_HEIGHT, Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(targetBitmap);

        // 背景填充黑色
        canvas.drawColor(Color.BLACK);

        // 计算居中位置
        int x = (Constants.IMAGE_WIDTH - resizedBitmap.getWidth()) / 2;
        int y = (Constants.IMAGE_HEIGHT - resizedBitmap.getHeight()) / 2;

        // 将调整后的位图绘制到画布上
        Paint paint = new Paint();
        paint.setFilterBitmap(true);
        canvas.drawBitmap(resizedBitmap, x, y, paint);

        // 释放中间位图
        if (resizedBitmap != originalBitmap) {
            resizedBitmap.recycle();
        }

        // 将位图转换为RGB565字节数组
        ByteBuffer buffer = ByteBuffer.allocate(Constants.IMAGE_WIDTH * Constants.IMAGE_HEIGHT * 2);

        // 遍历像素，按RGB565格式处理
        for (int row = 0; row < Constants.IMAGE_HEIGHT; row++) {
            for (int col = 0; col < Constants.IMAGE_WIDTH; col++) {
                int pixel = targetBitmap.getPixel(col, row);

                // 提取RGB值
                int r = Color.red(pixel);
                int g = Color.green(pixel);
                int b = Color.blue(pixel);

                // 转换为RGB565格式(16位)
                // R: 5位 (高位), G: 6位 (中间), B: 5位 (低位)
                int rgb565 = ((r & 0xF8) << 8) | ((g & 0xFC) << 3) | (b >> 3);

                // 写入缓冲区(小端序：低字节在前)
                buffer.put((byte) (rgb565 & 0xFF));
                buffer.put((byte) ((rgb565 >> 8) & 0xFF));
            }
        }

        // 释放目标位图
        targetBitmap.recycle();

        // 返回字节数组
        return buffer.array();
    }

    /**
     * 调整位图大小，保持宽高比
     */
    private static Bitmap resizeToFit(Bitmap bitmap, int maxWidth, int maxHeight) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        // 已经是目标大小
        if (width == maxWidth && height == maxHeight) {
            return bitmap;
        }

        // 计算缩放比例
        float scaleWidth = (float) maxWidth / width;
        float scaleHeight = (float) maxHeight / height;
        float scale = Math.min(scaleWidth, scaleHeight);

        // 创建变换矩阵
        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);

        // 创建调整大小后的位图
        return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
    }

    /**
     * 优化版本：直接从RGB_565格式位图获取字节数组
     * 这个方法比上面的方法更高效，但可能在某些设备上实现不同
     */
    public static byte[] getOptimizedRgb565Bytes(Bitmap originalBitmap) {
        // 调整大小并保持宽高比
        Bitmap resizedBitmap = resizeToFit(originalBitmap, Constants.IMAGE_WIDTH, Constants.IMAGE_HEIGHT);

        // 创建目标大小的空白画布(RGB_565格式)
        Bitmap targetBitmap = Bitmap.createBitmap(Constants.IMAGE_WIDTH, Constants.IMAGE_HEIGHT, Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(targetBitmap);

        // 背景填充黑色
        canvas.drawColor(Color.BLACK);

        // 计算居中位置
        int x = (Constants.IMAGE_WIDTH - resizedBitmap.getWidth()) / 2;
        int y = (Constants.IMAGE_HEIGHT - resizedBitmap.getHeight()) / 2;

        // 将调整后的位图绘制到画布上
        Paint paint = new Paint();
        paint.setFilterBitmap(true);
        canvas.drawBitmap(resizedBitmap, x, y, paint);

        // 释放中间位图
        if (resizedBitmap != originalBitmap) {
            resizedBitmap.recycle();
        }

        // 从RGB_565位图中提取原始字节数组
        ByteBuffer buffer = ByteBuffer.allocate(Constants.IMAGE_WIDTH * Constants.IMAGE_HEIGHT * 2);
        targetBitmap.copyPixelsToBuffer(buffer);
        byte[] result = buffer.array();

        // 释放目标位图
        targetBitmap.recycle();

        return result;
    }
}