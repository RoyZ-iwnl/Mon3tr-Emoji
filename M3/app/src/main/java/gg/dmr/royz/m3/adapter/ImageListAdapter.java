package gg.dmr.royz.m3.adapter;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

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
        public View selectedIndicator;
        public View displayingIndicator;
        public View container;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            nameTextView = itemView.findViewById(R.id.image_name);
            sizeTextView = itemView.findViewById(R.id.image_size);
            indexTextView = itemView.findViewById(R.id.image_index);
            selectedIndicator = itemView.findViewById(R.id.selected_indicator);
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
        holder.indexTextView.setText(String.valueOf(image.getIndex()));

        // 设置选中状态
        holder.selectedIndicator.setVisibility(image.isSelected() ? View.VISIBLE : View.INVISIBLE);

        // 设置当前显示状态
        holder.displayingIndicator.setVisibility(
                image.getIndex() == currentDisplayIndex ? View.VISIBLE : View.INVISIBLE);

        // 保存位置
        holder.itemView.setTag(position);
    }

    @Override
    public int getItemCount() {
        return imageList.size();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setImageList(List<DeviceImage> images) {
        this.imageList = new ArrayList<>(images);
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
        boolean hasSelection = false;
        for (DeviceImage image : imageList) {
            if (image.isSelected()) {
                image.setSelected(false);
                hasSelection = true;
            }
        }

        if (hasSelection) {
            notifyDataSetChanged();
        }
    }

    // 选择或取消选择指定位置
    public void toggleSelection(int position) {
        if (position >= 0 && position < imageList.size()) {
            DeviceImage image = imageList.get(position);
            image.setSelected(!image.isSelected());
            notifyItemChanged(position);
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