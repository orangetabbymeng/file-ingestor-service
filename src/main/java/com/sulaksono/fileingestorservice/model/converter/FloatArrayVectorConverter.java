package com.sulaksono.fileingestorservice.model.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Converts float[] <-> pgvector textual representation.
 */
@Converter(autoApply = true)
public class FloatArrayVectorConverter implements AttributeConverter<float[], String> {

    /* ---------- WRITE ---------- */
    @Override
    public String convertToDatabaseColumn(float[] attribute) {
        if (attribute == null) {
            return null;            // Hibernate will insert NULL
        }

        // Build pgvector literal e.g.  [1.0,2.3,4]
        StringBuilder sb = new StringBuilder(attribute.length * 8).append('[');
        for (int i = 0; i < attribute.length; i++) {
            sb.append(attribute[i]);
            if (i < attribute.length - 1) sb.append(',');
        }
        sb.append(']');
        return sb.toString();
    }

    /* ---------- READ ----------- */
    @Override
    public float[] convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new float[0];
        }
        String[] parts = dbData.replace("[", "").replace("]", "").split(",");
        float[] vec = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vec[i] = Float.parseFloat(parts[i].trim());
        }
        return vec;
    }
}