package com.heuristiq.identity.helper;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldChange {

    private String field;

    private Object oldValue;

    private Object newValue;
}
