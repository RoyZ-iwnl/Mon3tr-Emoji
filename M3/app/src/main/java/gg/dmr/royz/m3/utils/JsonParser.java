package gg.dmr.royz.m3.utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

import gg.dmr.royz.m3.ImageItem;

public class JsonParser {
    public static List<ImageItem> parseImageList(String jsonStr) throws JSONException {
        List<ImageItem> imageList = new ArrayList<>();
        JSONObject jsonObject = new JSONObject(jsonStr);
        JSONArray images = jsonObject.getJSONArray("images");

        for (int i = 0; i < images.length(); i++) {
            JSONObject img = images.getJSONObject(i);
            int index = img.getInt("index");
            String name = img.getString("name");
            boolean active = img.getBoolean("active");
            imageList.add(new ImageItem(index, name, active));
        }

        return imageList;
    }

    public static String createImageList(List<ImageItem> imageList) throws JSONException {
        JSONObject jsonObject = new JSONObject();
        JSONArray images = new JSONArray();

        for (ImageItem item : imageList) {
            JSONObject img = new JSONObject();
            img.put("index", item.getIndex());
            img.put("name", item.getFilename());
            img.put("active", item.isActive());
            images.put(img);
        }

        jsonObject.put("images", images);
        return jsonObject.toString();
    }
}