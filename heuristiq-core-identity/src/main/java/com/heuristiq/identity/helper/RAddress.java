package com.heuristiq.identity.helper;

import java.io.Serializable;


import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RAddress implements Serializable {

	@JsonIgnore
	private static final long serialVersionUID = 7342123820158723365L;

	private String lines;

	private String city;

	private String state;

	private String stateId;

	private String country;

	private String pincode;

	private String landmark;

	private String countryCodeNumeric;

	private String region;
	
	private String isoCountryCode;
	
	private String district;
	
	public RAddress() {
		this.setRegion("");
	}

	public String fullAddressStore() {
		String fullAddress = "";
		if (lines != null && !lines.isBlank()) {
			fullAddress = fullAddress.concat(lines.trim()).concat(" ");
		}
		if (city != null && !city.isBlank()) {
			fullAddress = fullAddress.concat(city.trim()).concat(" ");
		}
		if (state != null && !state.isBlank()) {
			fullAddress = fullAddress.concat(state.trim()).concat(" ");
		}
		if (stateId != null && !stateId.isBlank()) {
			fullAddress = fullAddress.concat(stateId.trim()).concat(" ");
		}
		if (country != null && !country.isBlank()) {
			fullAddress = fullAddress.concat(country.trim()).concat(" ");
		}
		if (pincode != null && !pincode.isBlank()) {
			fullAddress = fullAddress.concat(pincode.trim()).concat(" ");
		}
		if (landmark != null && !landmark.isBlank()) {
			fullAddress = fullAddress.concat(landmark.trim());
		}
		fullAddress = fullAddress.toUpperCase();

		return fullAddress;
	}

	@JsonIgnore
	public String fullAddressStoreHASH() {
		String fullAddress = "";
		if (lines != null) {
			fullAddress = fullAddress.concat("#").concat(lines.trim());
		}
		if (city != null) {
			fullAddress = fullAddress.concat("#").concat(city.trim());
		}
		if (state != null) {
			fullAddress = fullAddress.concat("#").concat(state.trim());
		}
		if (stateId != null) {
			fullAddress = fullAddress.concat("#").concat(stateId.trim());
		}
		if (country != null) {
			fullAddress = fullAddress.concat("#").concat(country.trim());
		}
		if (pincode != null) {
			fullAddress = fullAddress.concat("#").concat(pincode.trim());
		}
		if (landmark != null) {
			fullAddress = fullAddress.concat("#").concat(landmark.trim());
		}
		fullAddress = fullAddress.toUpperCase();

		return fullAddress;
	}

	@JsonIgnore
	public String[] gerAddressLines() {
		String[] s = new String[3];
		if (this.getLines() != null) {
			s[0] = this.getLines().substring(0, this.getLines().length() > 80 ? 80 : this.getLines().length() - 1);
		}
		String line2 = "";
		if (city != null) {
			line2 = line2.concat(city.trim()).concat(" ");
		}
		if (state != null) {
			line2 = line2.concat(state.trim()).concat(" ");
		}
		if (country != null) {
			line2 = line2.concat(country.trim()).concat(" ");
		}
		if (pincode != null) {
			line2 = line2.concat(pincode.trim()).concat(" ");
		}
		s[1] = line2.toUpperCase();

		return s;
	}
}
