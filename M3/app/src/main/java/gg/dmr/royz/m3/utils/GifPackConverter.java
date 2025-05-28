/*
 * Mon3tr Emoji - ESP32-C3 BLE Project and Android APP for custom display
 * Copyright (C) 2025  RoyZ-iwnl
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * 本程序是自由软件，在自由软件联盟发布的GNU通用公共许可证条款下，
 * 你可以对其进行再发布及修改。协议版本为第三版或（随你）更新的版本。
 * 
 * 本程序的发布是希望它能够有用，但不负任何担保责任；
 * 具体详情请参见GNU通用公共许可证。
 * 
 * 你理当已收到一份GNU通用公共许可证的副本。
 * 如果没有，请查阅<https://www.gnu.org/licenses/>
 * 
 * Contact/联系方式: Roy@DMR.gg
 */
package gg.dmr.royz.m3.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

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

    // 目标尺寸
    private static final int TARGET_WIDTH = 240;
    private static final int TARGET_HEIGHT = 240;

    // 最大帧数量限制
    private static final int MAX_FRAMES = 500;

    // 最大FPS限制
    private static final int MAX_FPS = 25;

    // 最小FPS限制
    private static final int MIN_FPS = 15;

    /**
     * 将GIF文件转换为GifPack格式
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
            int originalFrameCount = gifDrawable.getNumberOfFrames();
            if (originalFrameCount <= 0) {
                LogUtil.logError("GIF没有有效帧");
                return null;
            }

            LogUtil.log("原始GIF信息：" + originalFrameCount + "帧，" +
                    gifDrawable.getIntrinsicWidth() + "x" +
                    gifDrawable.getIntrinsicHeight());

            // 计算帧采样率和FPS
            int frameStep = Math.max(1, originalFrameCount / MAX_FRAMES);
            int actualFrameCount = Math.min(MAX_FRAMES, (originalFrameCount + frameStep - 1) / frameStep);

            // 计算总时长（毫秒）
            int totalDuration = 0;
            for (int i = 0; i < originalFrameCount; i += frameStep) {
                totalDuration += gifDrawable.getFrameDuration(i);
            }

            // 计算目标FPS
            int fps;
            if (totalDuration > 0) {
                fps = Math.round(actualFrameCount * 1000f / totalDuration);
                fps = Math.min(MAX_FPS, Math.max(MIN_FPS, fps));
            } else {
                fps = 10; // 默认FPS
            }

            LogUtil.log("处理后参数：" + actualFrameCount + "帧，FPS=" + fps + "，采样步长=" + frameStep);

            // 提取并处理帧
            List<byte[]> jpegFrames = new ArrayList<>();
            for (int i = 0; i < originalFrameCount && jpegFrames.size() < MAX_FRAMES; i += frameStep) {
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

                // 将帧转换为JPEG（提高压缩率）
                ByteArrayOutputStream jpegOutput = new ByteArrayOutputStream();
                resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 75, jpegOutput);

                // 添加到帧列表
                jpegFrames.add(jpegOutput.toByteArray());

                // 释放资源
                resizedBitmap.recycle();

                LogUtil.log("处理帧 " + (jpegFrames.size()) + "/" + actualFrameCount +
                        "，大小：" + jpegOutput.size() + " 字节");
            }

            // 检查是否成功获取帧
            if (jpegFrames.isEmpty()) {
                LogUtil.logError("未能转换任何GIF帧");
                return null;
            }

            LogUtil.log("成功转换 " + jpegFrames.size() + " 帧");

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

        // 使用小端序ByteBuffer确保字节序一致
        ByteBuffer buffer = ByteBuffer.allocate(fileSize);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        // 创建文件头 (16字节)
        // 魔术字节 "GFPK"
        buffer.put(MAGIC_BYTES);

        // 版本
        buffer.put(VERSION);

        // 帧数 (2字节，小端序)
        buffer.putShort((short)frameCount);

        // FPS (1字节)
        buffer.put((byte)fps);

        // 宽度 (2字节，小端序)
        buffer.putShort((short)width);

        // 高度 (2字节，小端序)
        buffer.putShort((short)height);

        // 预留 (4字节)
        buffer.putInt(0);

        // 计算每帧在文件中的偏移量
        int currentOffset = 16 + indexTableSize; // 头部16字节 + 偏移量表大小

        // 写入偏移量表
        for (byte[] jpegFrame : jpegFrames) {
            buffer.putInt(currentOffset);
            currentOffset += jpegFrame.length;
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
