package com.medicarex.model;

import java.util.HashMap;
import java.util.Map;

public class TimelineEvent implements Comparable<TimelineEvent> {
    private String id;
    private String eventType; // "APPOINTMENT", "PRESCRIPTION", "LAB_REPORT", "HEALTH_RECORD", "UPLOAD", "TREATMENT_UPDATE"
    private String eventDate; // ISO date / timestamp for ordering
    private String displayDate;
    private String title;
    private String subtitle;
    private String badge;
    private String badgeColor; // "blue", "green", "amber", "purple", "red"
    private String description;
    private String doctorName;
    private String referenceId;
    private Map<String, Object> details = new HashMap<>();

    public TimelineEvent() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getEventDate() { return eventDate; }
    public void setEventDate(String eventDate) { this.eventDate = eventDate; }

    public String getDisplayDate() { return displayDate; }
    public void setDisplayDate(String displayDate) { this.displayDate = displayDate; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSubtitle() { return subtitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }

    public String getBadge() { return badge; }
    public void setBadge(String badge) { this.badge = badge; }

    public String getBadgeColor() { return badgeColor; }
    public void setBadgeColor(String badgeColor) { this.badgeColor = badgeColor; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }

    public String getReferenceId() { return referenceId; }
    public void setReferenceId(String referenceId) { this.referenceId = referenceId; }

    public Map<String, Object> getDetails() { return details; }
    public void setDetails(Map<String, Object> details) { this.details = details; }

    @Override
    public int compareTo(TimelineEvent o) {
        if (this.eventDate == null && o.eventDate == null) return 0;
        if (this.eventDate == null) return 1;
        if (o.eventDate == null) return -1;
        return o.eventDate.compareTo(this.eventDate); // Reverse chronological (newest first)
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id != null ? id : "");
        map.put("eventType", eventType != null ? eventType : "");
        map.put("eventDate", eventDate != null ? eventDate : "");
        map.put("displayDate", displayDate != null ? displayDate : eventDate);
        map.put("title", title != null ? title : "");
        map.put("subtitle", subtitle != null ? subtitle : "");
        map.put("badge", badge != null ? badge : "");
        map.put("badgeColor", badgeColor != null ? badgeColor : "blue");
        map.put("description", description != null ? description : "");
        map.put("doctorName", doctorName != null ? doctorName : "");
        map.put("referenceId", referenceId != null ? referenceId : "");
        map.put("details", details != null ? details : new HashMap<>());
        return map;
    }
}
