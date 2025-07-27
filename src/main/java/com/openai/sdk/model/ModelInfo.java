package com.openai.sdk.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * OpenAI模型信息
 */
public class ModelInfo {
    private String id;
    private String object;
    private long created;
    @JsonProperty("owned_by")
    private String ownedBy;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getObject() {
        return object;
    }

    public void setObject(String object) {
        this.object = object;
    }

    public long getCreated() {
        return created;
    }

    public void setCreated(long created) {
        this.created = created;
    }

    public String getOwnedBy() {
        return ownedBy;
    }

    public void setOwnedBy(String ownedBy) {
        this.ownedBy = ownedBy;
    }

    @Override
    public String toString() {
        return "ModelInfo{" +
                "id='" + id + '\'' +
                ", object='" + object + '\'' +
                ", created=" + created +
                ", ownedBy='" + ownedBy + '\'' +
                '}';
    }
}