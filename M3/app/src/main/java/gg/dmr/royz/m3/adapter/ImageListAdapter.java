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
package gg.dmr.royz.m3.adapter;

import android.annotation.SuppressLint;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import gg.dmr.royz.m3.R;
import gg.dmr.royz.m3.model.DeviceImage;

/**
 * 图片列表适配器
 * 用于显示设备上存储的图片列表
 */
public class ImageListAdapter extends RecyclerView.Adapter<ImageListAdapter.ViewHolder>
        implements View.OnClickListener, View.OnLongClickListener {

    private List<DeviceImage> imageList = new ArrayList<>();
    private byte currentDisplayIndex = -1; // 当前显示的图片索引
    private boolean dragEnabled = false;
    private int selectedPosition = -1; // 记录当前选中位置

    // 点击事件监听器
    public interface OnItemClickListener {
        void onItemClick(int position, DeviceImage image);
        void onItemLongClick(int position, DeviceImage image);
    }

    private OnItemClickListener listener;

    // 设置监听器
    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    // 视图持有者
    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView nameTextView;
        public TextView sizeTextView;
        public TextView indexTextView;
        public TextView formatTextView;
        //public View selectedIndicator;
        public View displayingIndicator;
        public View container;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            nameTextView = itemView.findViewById(R.id.image_name);
            sizeTextView = itemView.findViewById(R.id.image_size);
            indexTextView = itemView.findViewById(R.id.image_index);
            formatTextView = itemView.findViewById(R.id.image_format);
            //selectedIndicator = itemView.findViewById(R.id.selected_indicator);
            displayingIndicator = itemView.findViewById(R.id.displaying_indicator);
            container = itemView.findViewById(R.id.item_container);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_image, parent, false);

        // 设置点击监听器
        view.setOnClickListener(this);
        view.setOnLongClickListener(this);

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DeviceImage image = imageList.get(position);

        // 设置数据
        holder.nameTextView.setText(image.getName().isEmpty() ?
                "图片 " + image.getIndex() : image.getName());

        // 获取MaterialCardView引用
        MaterialCardView cardView = (MaterialCardView) holder.itemView;

        // 设置选中状态 - 使用主色调高亮
        if (image.isSelected()) {
            cardView.setStrokeColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.blue_500));
            cardView.setStrokeWidth(4);
            /*holder.selectedIndicator.setVisibility(View.VISIBLE);
            holder.selectedIndicator.setBackgroundTintList(
                    ColorStateList.valueOf(ContextCompat.getColor(holder.itemView.getContext(), R.color.blue_500))
            );*/
        } else {
            cardView.setStrokeWidth(0);
            //holder.selectedIndicator.setVisibility(View.INVISIBLE);
        }

        // 设置当前显示状态 - 使用绿色高亮
        if (image.getIndex() == currentDisplayIndex) {
            if (!image.isSelected()) {
                cardView.setStrokeColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.green_500));
                cardView.setStrokeWidth(3);
            }
            holder.displayingIndicator.setVisibility(View.VISIBLE);
            holder.displayingIndicator.setBackgroundTintList(
                    ColorStateList.valueOf(ContextCompat.getColor(holder.itemView.getContext(), R.color.green_500))
            );
        } else {
            holder.displayingIndicator.setVisibility(View.INVISIBLE);
            if (!image.isSelected()) {
                cardView.setStrokeWidth(0);
            }
        }

        // 保存位置
        holder.itemView.setTag(position);

        // 格式化大小
        String size;
        if (image.getSize() < 1024) {
            size = image.getSize() + " B";
        } else if (image.getSize() < 1024 * 1024) {
            size = String.format("%.1f KB", image.getSize() / 1024.0);
        } else {
            size = String.format("%.2f MB", image.getSize() / (1024.0 * 1024.0));
        }
        holder.sizeTextView.setText(size);

        // 设置索引
        int combinedIndex = image.getIndex() & 0xFF; // 确保为正数
        int fileIndex = combinedIndex & 0x0F; // 提取低4位作为文件索引

        // 根据需求显示文件索引或文件名
        /*if (!image.getName().isEmpty()) {
            // 如果有文件名，显示文件名
            holder.indexTextView.setText(image.getName());
        } else {
            // 如果没有文件名，显示文件索引
            holder.indexTextView.setText(String.valueOf(fileIndex));
        }*/
        holder.indexTextView.setText(String.valueOf(fileIndex));

        // 设置格式信息
        if (holder.formatTextView != null) {
            String format = getFormatName(image.getFormat());
            holder.formatTextView.setText(format);

            // 设置不同格式的背景颜色
            int bgColor;
            switch (image.getFormat()) {
                case 0x10: // JPG
                    bgColor = Color.parseColor("#FF9800"); // 橙色
                    break;
                case 0x20: // PNG
                    bgColor = Color.parseColor("#4CAF50"); // 绿色
                    break;
                case 0x30: // GIF
                    bgColor = Color.parseColor("#9C27B0"); // 紫色
                    break;
                default: // BIN
                    bgColor = Color.parseColor("#607D8B"); // 灰蓝色
                    break;
            }

            // 获取背景并设置颜色
            Drawable background = holder.formatTextView.getBackground();
            if (background != null) {
                background.setColorFilter(bgColor, PorterDuff.Mode.SRC_ATOP);
            }


            // 设置选中状态
            //holder.selectedIndicator.setVisibility(image.isSelected() ? View.VISIBLE : View.INVISIBLE);

            // 设置当前显示状态
            holder.displayingIndicator.setVisibility(
                    image.getIndex() == currentDisplayIndex ? View.VISIBLE : View.INVISIBLE);

            // 保存位置
            holder.itemView.setTag(position);
        }
    }

    // 获取格式名称
    private String getFormatName(byte format) {
        switch (format) {
            case 0x10: return "JPG";
            case 0x20: return "PNG";
            case 0x30: return "GIF";
            case 0x00: return "BIN";
            default: return "未知";
        }
    }

    @Override
    public int getItemCount() {
        return imageList.size();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setImageList(List<DeviceImage> images) {
        this.imageList = new ArrayList<>(images);
        this.selectedPosition = -1; // 重置选中位置
        notifyDataSetChanged();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setCurrentDisplayIndex(byte index) {
        this.currentDisplayIndex = index;
        notifyDataSetChanged();
    }

    // 获取选中的图片列表
    public List<DeviceImage> getSelectedImages() {
        List<DeviceImage> selected = new ArrayList<>();
        for (DeviceImage image : imageList) {
            if (image.isSelected()) {
                selected.add(image);
            }
        }
        return selected;
    }

    // 清除所有选择
    @SuppressLint("NotifyDataSetChanged")
    public void clearSelection() {
        if (selectedPosition != -1 && selectedPosition < imageList.size()) {
            imageList.get(selectedPosition).setSelected(false);
            notifyItemChanged(selectedPosition);
            selectedPosition = -1;
        }
    }

    // 选择或取消选择指定位置
    public void toggleSelection(int position) {
        if (position >= 0 && position < imageList.size()) {
            // 如果点击的是已选中的图片，则取消选择
            if (selectedPosition == position) {
                imageList.get(position).setSelected(false);
                notifyItemChanged(position);
                selectedPosition = -1;
                return;
            }

            // 取消之前的选择
            if (selectedPosition != -1 && selectedPosition < imageList.size()) {
                imageList.get(selectedPosition).setSelected(false);
                notifyItemChanged(selectedPosition);
            }

            // 选中新的图片
            imageList.get(position).setSelected(true);
            notifyItemChanged(position);
            selectedPosition = position;
        }
    }

    // 启用拖拽排序
    public void enableDrag(boolean enable) {
        this.dragEnabled = enable;
    }

    // 是否启用拖拽
    public boolean isDragEnabled() {
        return dragEnabled;
    }

    // 交换两个位置的项
    public void swapItems(int fromPosition, int toPosition) {
        if (fromPosition < toPosition) {
            for (int i = fromPosition; i < toPosition; i++) {
                Collections.swap(imageList, i, i + 1);
            }
        } else {
            for (int i = fromPosition; i > toPosition; i--) {
                Collections.swap(imageList, i, i - 1);
            }
        }
        notifyItemMoved(fromPosition, toPosition);
    }

    // 获取重排序后的索引数组
    public byte[] getReorderedIndices() {
        byte[] indices = new byte[imageList.size()];
        for (int i = 0; i < imageList.size(); i++) {
            indices[i] = imageList.get(i).getIndex();
        }
        return indices;
    }

    // 点击事件处理
    @Override
    public void onClick(View v) {
        if (listener != null) {
            int position = (int) v.getTag();
            listener.onItemClick(position, imageList.get(position));
        }
    }

    // 长按事件处理
    @Override
    public boolean onLongClick(View v) {
        if (listener != null) {
            int position = (int) v.getTag();
            listener.onItemLongClick(position, imageList.get(position));
            return true;
        }
        return false;
    }
}