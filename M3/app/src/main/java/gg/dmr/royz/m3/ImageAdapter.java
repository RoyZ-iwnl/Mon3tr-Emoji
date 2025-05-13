package gg.dmr.royz.m3;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.List;

import gg.dmr.royz.m3.databinding.ItemImageBinding;

public class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ViewHolder> {
    private final List<ImageInfo> imageList;
    private final OnItemClickListener clickListener;
    private final OnItemLongClickListener longClickListener;

    public interface OnItemClickListener {
        void onItemClick(ImageInfo image);
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(ImageInfo image);
    }

    public ImageAdapter(List<ImageInfo> imageList,
                        OnItemClickListener clickListener,
                        OnItemLongClickListener longClickListener) {
        this.imageList = imageList;
        this.clickListener = clickListener;
        this.longClickListener = longClickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemImageBinding binding = ItemImageBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ImageInfo image = imageList.get(position);
        holder.bind(image);
    }

    @Override
    public int getItemCount() {
        return imageList.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemImageBinding binding;

        ViewHolder(ItemImageBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(ImageInfo image) {
            // 设置图片名称
            String displayName = image.name.replace("/img_", "").replace(".bin", "");
            binding.imageName.setText("图片 " + displayName);

            // 设置索引
            binding.imageIndex.setText("#" + image.index);

            // 设置激活状态
            binding.imageStatus.setText(image.active ? "激活" : "未激活");
            binding.imageStatus.setTextColor(binding.getRoot().getContext().getColor(
                    image.active ? android.R.color.holo_green_dark : android.R.color.holo_red_dark));

            // 设置占位图
            binding.imageView.setImageResource(R.drawable.ic_image_placeholder);

            // 设置点击事件
            binding.getRoot().setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onItemClick(image);
                }
            });

            binding.getRoot().setOnLongClickListener(v -> {
                if (longClickListener != null) {
                    longClickListener.onItemLongClick(image);
                }
                return true;
            });
        }
    }
}