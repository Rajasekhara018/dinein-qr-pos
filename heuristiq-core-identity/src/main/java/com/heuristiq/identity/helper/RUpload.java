package com.heuristiq.uam.helper;
import java.io.Serializable;

import org.springframework.data.annotation.Transient;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.heuristiq.uam.enums.UamEnums.FileRetentionPolicyEnum;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class RUpload implements Serializable {

	@JsonIgnore
	private static final long serialVersionUID = -1776250395676463050L;

	private String name;

	private String fileType;

	private long size;

	private String location;

	private String title;

	private String prefixFolder;

	@Transient
	@jakarta.persistence.Transient
	private String base64EncodedData;

	private String modelKey;

	@Enumerated(EnumType.STRING)
	private FileRetentionPolicyEnum retentionPolicy;

	private String versionId;
}

