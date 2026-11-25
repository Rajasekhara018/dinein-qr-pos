package com.heuristiq.identity.helper;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;

@Data
public class Bookmarks implements Serializable {

	@JsonIgnore
	private static final long serialVersionUID = -7049457405281296310L;

	private String description;

	private String pathUrl;
}
