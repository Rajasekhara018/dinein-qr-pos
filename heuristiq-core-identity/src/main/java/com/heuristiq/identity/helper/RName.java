package com.heuristiq.identity.helper;

import java.io.Serializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Embeddable
public class RName implements Serializable {
	private static final Logger logger = LoggerFactory.getLogger(RName.class);
	@JsonIgnore
	private static final long serialVersionUID = -166836399481362596L;

	private String firstName;

	private String middleName;

	private String lastName;

	@JsonIgnore
	public String getFullName() {
		String fN = "";
		String lN = "";
		String mN = "";

		if (this.getFirstName() != null) {
			fN = this.getFirstName();
			if (fN != null && !fN.isBlank()) {
				fN = fN.concat(" ");
			}
		}

		if (this.getLastName() != null) {
			lN = this.getLastName();
		}

		if (this.getMiddleName() != null) {
			mN = this.getMiddleName();
			if (mN != null && !mN.isBlank()) {
				mN = mN.concat(" ");
			}
		}

		return fN.concat(mN).concat(lN);
	}

	public RName(String fN) {
		this.firstName = fN;
		this.lastName = "";
		this.middleName = "";
	}

}
