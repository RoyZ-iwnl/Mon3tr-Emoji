package gg.dmr.royz.m3.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

// 注意：需要在应用的build.gradle中添加依赖：
// implementation 'pl.droidsonroids.gif:android-gif-drawable:1.2.25'
import pl.droidsonroids.gif.GifDrawable;

/**
 * GifPack转换工具类
 * 用于将GIF图片转换为自定义的GifPack格式
 */
public class GifPackConverter {
    private static final String TAG = "GifPackConverter";

    // GifPack魔术字节
    private static final byte[] MAGIC_BYTES = {'G', 'F', 'P', 'K'};

    // GifPack版本
    private static final byte VERSION = 0x01;

    // 默认目标宽高 (与设备屏幕尺寸一致)
    private static final int TARGET_WIDTH = 240;
    private static final int TARGET_HEIGHT = 240;

    /**
     * 将GIF文件转换为GifPack格式
     *
     * @param context 上下文
     * @param gifUri GIF文件的Uri
     * @return 转换后的GifPack格式的字节数组，如果转换失败则返回null
     */
    public static byte[] convertGifToGifPack(Context context, Uri gifUri) {
        InputStream inputStream = null;
        GifDrawable gifDrawable = null;

        try {
            // 打开GIF文件
            inputStream = context.getContentResolver().openInputStream(gifUri);
            if (inputStream == null) {
                LogUtil.logError("无法打开GIF文件");
                return null;
            }

            // 读取GIF数据
            byte[] gifData = readStreamToByteArray(inputStream);

            // 创建GIF可绘制对象
            gifDrawable = new GifDrawable(gifData);

            // 获取GIF信息
            int frameCount = gifDrawable.getNumberOfFrames();
            if (frameCount <= 0) {
                LogUtil.logError("GIF没有有效帧");
                return null;
            }

            LogUtil.log("GIF信息：" + frameCount + "帧，" +
                    gifDrawable.getIntrinsicWidth() + "x" +
                    gifDrawable.getIntrinsicHeight());

            // 每帧的延迟时间，计算平均帧率
            int totalDuration = 0;
            for (int i = 0; i < frameCount; i++) {
                totalDuration += gifDrawable.getFrameDuration(i);
            }
            int fps = Math.round(frameCount * 1000f / totalDuration);
            // 确保fps范围在1-60之间
            fps = Math.min(60, Math.max(1, fps));

            LogUtil.log("计算得到FPS: " + fps);

            // 提取并处理每一帧
            List<byte[]> jpegFrames = new ArrayList<>();
            for (int i = 0; i < frameCount; i++) {
                // 获取该帧的图像
                Bitmap frameBitmap = gifDrawable.seekToFrameAndGet(i);
                if (frameBitmap == null) {
                    LogUtil.logError("无法获取第" + i + "帧");
                    continue;
                }

                // 调整尺寸为目标大小
                Bitmap resizedBitmap = ImageConverter.resizeBitmap(
                        frameBitmap.copy(Bitmap.Config.ARGB_8888, true),
                        TARGET_WIDTH, TARGET_HEIGHT);

                // 将帧转换为JPEG
                ByteArrayOutputStream jpegOutput = new ByteArrayOutputStream();
                resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, jpegOutput);

                // 添加到帧列表
                jpegFrames.add(jpegOutput.toByteArray());

                // 释放资源
                resizedBitmap.recycle();
            }

            // 检查是否成功获取帧
            if (jpegFrames.isEmpty()) {
                LogUtil.logError("未能转换任何GIF帧");
                return null;
            }

            // 构建GifPack文件
            return createGifPackFile(jpegFrames, fps, TARGET_WIDTH, TARGET_HEIGHT);

        } catch (Exception e) {
            LogUtil.logError("GIF转换失败: " + e.getMessage());
            e.printStackTrace();
            return null;
        } finally {
            try {
                if (inputStream != null) inputStream.close();
                if (gifDrawable != null) gifDrawable.recycle();
            } catch (IOException e) {
                // 忽略关闭错误
            }
        }
    }

    /**
     * 创建GifPack文件
     *
     * @param jpegFrames JPEG格式的帧列表
     * @param fps 每秒帧数
     * @param width 图像宽度
     * @param height 图像高度
     * @return GifPack格式的字节数组
     */
    private static byte[] createGifPackFile(List<byte[]> jpegFrames, int fps, int width, int height) {
        // 计算帧索引表大小
        int frameCount = jpegFrames.size();
        int indexTableSize = frameCount * 4;

        // 计算所有JPEG帧的总大小
        int totalJpegSize = 0;
        for (byte[] jpegFrame : jpegFrames) {
            totalJpegSize += jpegFrame.length;
        }

        // 计算文件总大小
        int fileSize = 16 + indexTableSize + totalJpegSize; // 头部 + 索引表 + 帧数据
        ByteBuffer buffer = ByteBuffer.allocate(fileSize);

        // 创建文件头 (16字节)
        // 魔术字节 "GFPK"
        buffer.put(MAGIC_BYTES);
        // 版本
        buffer.put(VERSION);
        // 帧数 (2字节)
        buffer.put((byte)(frameCount & 0xFF));
        buffer.put((byte)((frameCount >> 8) & 0xFF));
        // FPS
        buffer.put((byte)fps);
        // 宽度 (2字节)
        buffer.put((byte)(width & 0xFF));
        buffer.put((byte)((width >> 8) & 0xFF));
        // 高度 (2字节)
        buffer.put((byte)(height & 0xFF));
        buffer.put((byte)((height >> 8) & 0xFF));
        // 预留 (4字节)
        buffer.put(new byte[4]);

        // 计算每帧在文件中的偏移量
        int currentOffset = 16 + indexTableSize; // 头部16字节 + 偏移量表大小
        int[] offsets = new int[frameCount];

        for (int i = 0; i < frameCount; i++) {
            offsets[i] = currentOffset;
            currentOffset += jpegFrames.get(i).length;
        }

        // 写入偏移量表
        for (int offset : offsets) {
            buffer.put((byte)(offset & 0xFF));
            buffer.put((byte)((offset >> 8) & 0xFF));
            buffer.put((byte)((offset >> 16) & 0xFF));
            buffer.put((byte)((offset >> 24) & 0xFF));
        }

        // 写入JPEG帧数据
        for (byte[] jpegFrame : jpegFrames) {
            buffer.put(jpegFrame);
        }

        LogUtil.log("创建GifPack文件成功：" +
                fileSize + "字节，" +
                frameCount + "帧，" +
                width + "x" + height +
                " FPS:" + fps);

        return buffer.array();
    }

    /**
     * 读取输入流到字节数组
     */
    private static byte[] readStreamToByteArray(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int bytesRead;

        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }

        return outputStream.toByteArray();
    }
}