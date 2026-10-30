package com.example.studentmgmt.export;

import com.example.studentmgmt.entity.Enrollment;
import com.example.studentmgmt.entity.Student;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Writes students as CSV (RFC 4180 quoting). Values that a spreadsheet would treat as a formula
 * (starting with =, +, -, @) are prefixed with a quote, so an exported file can't run anything.
 */
@Component
public class StudentCsvExporter {

    static final String HEADER = "id,first_name,last_name,email,date_of_birth,courses,course_count,total_credits,created_at";

    /** Expects each student's enrollments and courses to be loadable (call inside a transaction). */
    public String export(List<Student> students) {
        StringBuilder csv = new StringBuilder(HEADER).append("\r\n");
        for (Student student : students) {
            List<Enrollment> enrollments = student.getEnrollments().stream().toList();
            String courses = enrollments.stream()
                    .map(e -> e.getCourse().getCode())
                    .sorted()
                    .collect(Collectors.joining(";"));
            int credits = enrollments.stream().mapToInt(e -> e.getCourse().getCredits()).sum();
            csv.append(String.join(",",
                    String.valueOf(student.getId()),
                    cell(student.getFirstName()),
                    cell(student.getLastName()),
                    cell(student.getEmail()),
                    String.valueOf(student.getDateOfBirth()),
                    cell(courses),
                    String.valueOf(enrollments.size()),
                    String.valueOf(credits),
                    String.valueOf(student.getCreatedAt())))
                    .append("\r\n");
        }
        return csv.toString();
    }

    static String cell(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String safe = "=+-@".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
        boolean needsQuotes = safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r");
        return needsQuotes ? "\"" + safe.replace("\"", "\"\"") + "\"" : safe;
    }
}
