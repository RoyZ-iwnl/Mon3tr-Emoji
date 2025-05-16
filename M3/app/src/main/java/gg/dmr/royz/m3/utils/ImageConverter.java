package gg.dmr.royz.m3.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.net.Uri;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 图片转换工具类
 * 负责将Bitmap调整为设备支持的格式和尺寸
 */
public class ImageConverter {
    private static final String TAG = "ImageConverter";

    // 图片格式常量，与ESP32的格式定义对应
    public static final byte FORMAT_JPEG = 0x10;   // JPEG格式
    public static final byte FORMAT_PNG = 0x20;    // PNG格式
    public static final byte FORMAT_GIF = 0x30;    // GIF格式

    // 目标尺寸
    public static final int TARGET_WIDTH = 240;
    public static final int TARGET_HEIGHT = 240;

    /**
     * 从Uri直接加载GIF文件的字节数据
     * 保持原始GIF格式，不做转换
     *
     * @param context 上下文
     * @param uri     图片Uri
     * @return GIF文件的原始字节数据，如果不是GIF文件则返回null
     */
    public static byte[] loadGifFromUri(Context context, Uri uri) {
        InputStream inputStream = null;
        ByteArrayOutputStream outputStream = null;
        try {
            // 检查文件扩展名是否为GIF
            // 使用 MIME 类型判断是否为 GIF
            byte formatType = getFormatType(context, uri);
            if (formatType != FORMAT_GIF) {
                LogUtil.logError("选择的文件不是GIF格式");
                return null;
            }

            // 打开输入流
            inputStream = context.getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                LogUtil.logError("无法打开GIF文件流");
                return null;
            }

            // 检查GIF文件头
            byte[] header = new byte[6];
            if (inputStream.read(header) != 6) {
                LogUtil.logError("读取GIF头失败");
                return null;
            }

            // 验证GIF文件头
            if (header[0] != 'G' || header[1] != 'I' || header[2] != 'F' ||
                    header[3] != '8' || (header[4] != '7' && header[4] != '9') || header[5] != 'a') {
                LogUtil.logError("无效的GIF文件头");
                return null;
            }

            // 重新打开流以获取全部数据
            inputStream.close();
            inputStream = context.getContentResolver().openInputStream(uri);

            // 读取所有字节
            outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            byte[] gifData = outputStream.toByteArray();

            // 检查文件大小是否合理
            if (gifData.length < 50) {
                LogUtil.logError("GIF文件太小，可能不是有效的GIF");
                return null;
            }

            // 记录GIF文件信息
            LogUtil.log("读取GIF文件成功: " + gifData.length + " 字节");
            return gifData;

        } catch (Exception e) {
            LogUtil.logError("处理GIF文件失败: " + e.getMessage());
            return null;
        } finally {
            try {
                if (inputStream != null) inputStream.close();
                if (outputStream != null) outputStream.close();
            } catch (IOException e) {
                // 忽略关闭错误
            }
        }
    }


    /**
     * 将Bitmap转换为JPEG格式
     *
     * @param bitmap  源Bitmap
     * @param quality JPEG压缩质量(0-100)
     * @return JPEG格式的字节数组
     */
    public static byte[] convertBitmapToJpeg(Bitmap bitmap, int quality) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream);
        return stream.toByteArray();
    }

    /**
     * 将Bitmap转换为PNG格式
     *
     * @param bitmap 源Bitmap
     * @return PNG格式的字节数组
     */
    public static byte[] convertBitmapToPng(Bitmap bitmap) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        return stream.toByteArray();
    }

    /**
     * 从Uri加载图片并调整大小为240x240
     *
     * @param context 上下文
     * @param uri     图片Uri
     * @return 调整大小后的Bitmap
     */
    public static Bitmap loadImageFromUri(Context context, Uri uri) {
        try {
            InputStream input = context.getContentResolver().openInputStream(uri);

            // 先解码图片尺寸
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(input, null, options);
            input.close();

            // 重新打开流
            input = context.getContentResolver().openInputStream(uri);

            // 解码完整图片
            options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap originalBitmap = BitmapFactory.decodeStream(input, null, options);
            input.close();

            if (originalBitmap == null) {
                LogUtil.logError("无法解码图片");
                return null;
            }

            // 调整大小并居中裁剪
            return resizeBitmap(originalBitmap, TARGET_WIDTH, TARGET_HEIGHT);

        } catch (Exception e) {
            LogUtil.logError("加载图片失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 调整图片尺寸并居中裁剪，保持目标宽高比
     *
     * @param bitmap       原始图片
     * @param targetWidth  目标宽度
     * @param targetHeight 目标高度
     * @return 调整后的图片
     */
    public static Bitmap resizeBitmap(Bitmap bitmap, int targetWidth, int targetHeight) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        // 创建画布居中绘制
        Bitmap result = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        canvas.drawColor(Color.BLACK); // 填充黑色背景

        // 计算缩放比例，保持原始宽高比
        float scaleWidth = (float) targetWidth / width;
        float scaleHeight = (float) targetHeight / height;
        float scale = Math.min(scaleWidth, scaleHeight);

        // 计算居中位置
        int scaledWidth = (int) (width * scale);
        int scaledHeight = (int) (height * scale);
        int offsetX = (targetWidth - scaledWidth) / 2;
        int offsetY = (targetHeight - scaledHeight) / 2;

        // 创建缩放矩阵
        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);

        // 缩放原图
        Bitmap scaledBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);

        // 将缩放后的图片绘制到目标画布上
        canvas.drawBitmap(scaledBitmap, offsetX, offsetY, null);

        // 回收不需要的Bitmap
        if (scaledBitmap != bitmap) {
            scaledBitmap.recycle();
        }
        bitmap.recycle();

        return result;
    }

    /**
     * 获取图片格式类型
     *
     * @param context 上下文
     * @param uri     图片的 URI
     * @return 图片格式类型
     */
    public static byte getFormatType(Context context, Uri uri) {
        try {
            // 获取文件的 MIME 类型
            String mimeType = context.getContentResolver().getType(uri);

            if (mimeType == null) {
                return FORMAT_JPEG; // 如果无法获取类型，默认使用 JPEG
            }

            // 根据 MIME 类型判断文件格式
            switch (mimeType.toLowerCase()) {
                case "image/png":
                    return FORMAT_PNG;
                case "image/gif":
                    return FORMAT_GIF;
                default:
                    return FORMAT_JPEG;
            }
        } catch (Exception e) {
            return FORMAT_JPEG; // 发生错误时默认使用 JPEG
        }
    }
}