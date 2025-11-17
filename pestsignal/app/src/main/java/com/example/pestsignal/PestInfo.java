package com.example.pestsignal;

/**
 * Data class representing pest information with bilingual support
 */
public class PestInfo {
    private String key;
    private LocalizedText name;
    private LocalizedText type;
    private LocalizedText description;
    private LocalizedText prevention;

    public PestInfo() {
    }

    public PestInfo(String key, LocalizedText name, LocalizedText type, 
                   LocalizedText description, LocalizedText prevention) {
        this.key = key;
        this.name = name;
        this.type = type;
        this.description = description;
        this.prevention = prevention;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public LocalizedText getName() {
        return name;
    }

    public void setName(LocalizedText name) {
        this.name = name;
    }

    public LocalizedText getType() {
        return type;
    }

    public void setType(LocalizedText type) {
        this.type = type;
    }

    public LocalizedText getDescription() {
        return description;
    }

    public void setDescription(LocalizedText description) {
        this.description = description;
    }

    public LocalizedText getPrevention() {
        return prevention;
    }

    public void setPrevention(LocalizedText prevention) {
        this.prevention = prevention;
    }

    /**
     * Get name in specified language
     */
    public String getName(String language) {
        if (name == null) return "";
        return "en".equals(language) ? name.getEn() : name.getBn();
    }

    /**
     * Get type in specified language
     */
    public String getType(String language) {
        if (type == null) return "";
        return "en".equals(language) ? type.getEn() : type.getBn();
    }

    /**
     * Get description in specified language
     */
    public String getDescription(String language) {
        if (description == null) return "";
        return "en".equals(language) ? description.getEn() : description.getBn();
    }

    /**
     * Get prevention methods in specified language
     */
    public String getPrevention(String language) {
        if (prevention == null) return "";
        return "en".equals(language) ? prevention.getEn() : prevention.getBn();
    }

    /**
     * Inner class for localized text (English and Bengali)
     */
    public static class LocalizedText {
        private String en;
        private String bn;

        public LocalizedText() {
        }

        public LocalizedText(String en, String bn) {
            this.en = en;
            this.bn = bn;
        }

        public String getEn() {
            return en != null ? en : "";
        }

        public void setEn(String en) {
            this.en = en;
        }

        public String getBn() {
            return bn != null ? bn : "";
        }

        public void setBn(String bn) {
            this.bn = bn;
        }
    }
}

