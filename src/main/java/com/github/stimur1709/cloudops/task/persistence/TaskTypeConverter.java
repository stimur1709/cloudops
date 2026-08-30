package com.github.stimur1709.cloudops.task.persistence;

import com.github.stimur1709.cloudops.task.TaskType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class TaskTypeConverter implements AttributeConverter<TaskType, String> {

    @Override
    public String convertToDatabaseColumn(TaskType type) {
        return type == null ? null : type.value();
    }

    @Override
    public TaskType convertToEntityAttribute(String value) {
        return value == null ? null : new TaskType(value);
    }
}
