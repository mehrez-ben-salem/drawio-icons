package m4z.app.etl.drawio;

import com.google.gson.annotations.SerializedName;

import java.util.Base64;

public class MxIcon {
    String svg;
    String xml;
    String data;
    String aspect;
    String title;
    @SerializedName("w")
    String width;
    @SerializedName("h")
    String height;

    public MxIcon() {

    }

    public String getSvg() {
        return svg;
    }

    public void setSvg(String svg) {
        this.svg = svg;
    }

    public String getXml() {
        return xml;
    }

    public void setXml(String xml) {
        this.xml = xml;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getAspect() {
        return aspect;
    }

    public void setAspect(String aspect) {
        this.aspect = aspect;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getWidth() {
        return width;
    }

    public void setWidth(String width) {
        this.width = String.valueOf(Math.round(Float.parseFloat(width)));
    }

    public String getHeight() {
        return height;
    }

    public void setHeight(String height) {
        this.height = String.valueOf(Math.round(Float.parseFloat(height)));
    }

    public boolean isGraphModel() {
        return xml != null;
    }

    public boolean isImage() {
        return (data != null) || (svg != null);
    }

    public boolean isSvgImage() {
        if (isImage()) {
            return (svg != null) || data.startsWith("data:image/svg");
        }
        return false;
    }

    public String getSvgPayload() {
        if (isSvgImage()) {
            if (svg != null) {
                return svg;
            }

            return new String(Base64.getDecoder().decode(data.split(",")[1]));
        }

        return null;
    }
}
