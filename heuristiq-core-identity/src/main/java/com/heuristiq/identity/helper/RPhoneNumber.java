package com.heuristiq.uam.helper;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Embeddable
public class RPhoneNumber implements Serializable {

	@JsonIgnore
	private static final long serialVersionUID = 4642596677538079507L;

	String countryCode;

	String number;

	@JsonIgnore
	public String getNormalizedCountryCode() {
		return normalize(countryCode);
	}

	@JsonIgnore
	public String getNormalizedNumber() {
		return normalize(number);
	}

	@JsonIgnore
	public boolean hasValue() {
		return !getConcatString().isBlank();
	}

	@JsonIgnore
	public String getConcatString() {
		String s = "";
		if (this.getCountryCode() != null) {
			s = s.concat(this.getCountryCode());
		}
		if (this.getNumber() != null) {
			s = s.concat(this.getNumber());
		}
		s = s.replaceAll("[^0-9]", "");
		return s;
	}

	public RPhoneNumber(String phoneNumber) {
		this.number = phoneNumber;
	}

	private String normalize(String value) {
		return value == null ? "" : value.replaceAll("[^0-9]", "");
	}

	public void splitCountryCode(String countryCode, String fullPhoneNuber) {
		this.number = fullPhoneNuber.substring(0, countryCode.replaceAll("[^0-9]", "").length());
		this.countryCode = countryCode;
	}

}
