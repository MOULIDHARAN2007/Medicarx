package com.medicarex.model;

import java.util.HashMap;
import java.util.Map;

public class TreatmentProgressEntry {
    private int id;
    private int goalId;
    private String value;
    private String recordedDate;
    private String notes;
    private String createdBy;
    private String createdAt;

    public TreatmentProgressEntry() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getGoalId() { return goalId; }
    public void setGoalId(int goalId) { this.goalId = goalId; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public String getRecordedDate() { return recordedDate; }
    public void setRecordedDate(String recordedDate) { this.recordedDate = recordedDate; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("goalId", goalId);
        map.put("value", value != null ? value : "");
        map.put("recordedDate", recordedDate != null ? recordedDate : "");
        map.put("notes", notes != null ? notes : "");
        map.put("createdBy", createdBy != null ? createdBy : "Doctor");
        map.put("createdAt", createdAt != null ? createdAt : "");
        return map;
    }
}
