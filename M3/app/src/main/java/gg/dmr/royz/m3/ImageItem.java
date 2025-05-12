package gg.dmr.royz.m3;

public class ImageItem {
    private int index;
    private String filename;
    private boolean active;
    private String imagePath;

    public ImageItem(int index, String filename, boolean active) {
        this.index = index;
        this.filename = filename;
        this.active = active;
    }

    // Getters and setters
    public int getIndex() { return index; }
    public void setIndex(int index) { this.index = index; }
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }
}