package gg.dmr.royz.m3;

// 图片信息数据类
public class ImageInfo {
    public int index;
    public String name;
    public boolean active;

    public ImageInfo() {
    }

    public ImageInfo(int index, String name, boolean active) {
        this.index = index;
        this.name = name;
        this.active = active;
    }
}