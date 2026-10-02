package com.clinic.opendental.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "documents")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document {

    @EmbeddedId
    private DocumentId id;

    @Column(name = "description")
    private String description;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "date_created")
    private LocalDateTime dateCreated;

    @Column(name = "doc_category")
    private Long docCategory;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "img_type")
    private String imgType;

    @Column(name = "tooth_numbers")
    private String toothNumbers;

    @Column(name = "date_t_stamp")
    private LocalDateTime dateTStamp;

    @Column(name = "prov_num")
    private Long provNum;

    @Column(name = "print_heading")
    private String printHeading;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}