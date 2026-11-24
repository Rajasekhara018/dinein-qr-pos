package com.railcab.nms.adapters.out.persistence.mongo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

@Document(collection = "nms_event_definitions")
@Data
public class NotificationEventDefinitionDocument {

    @Id
    private String id;

    @Indexed(unique = true)
    private String value;

    private String label;

    private List<String> attributes = new ArrayList<>();

    private boolean active = true;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
