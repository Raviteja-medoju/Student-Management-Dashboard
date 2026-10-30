package com.example.studentmgmt.export;

import com.example.studentmgmt.entity.Student;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static com.example.studentmgmt.support.TestData.course;
import static com.example.studentmgmt.support.TestData.student;
import static org.assertj.core.api.Assertions.assertThat;

class StudentCsvExporterTest {

    private final StudentCsvExporter exporter = new StudentCsvExporter();

    @Test
    void writesHeaderAndOneRowPerStudent_withSortedCourseCodesAndCredits() {
        Student ada = student(1L, "Ada", "Lovelace");
        ada.enrollIn(course(2L, "MA101", 4, 30), LocalDate.of(2026, 9, 1));
        ada.enrollIn(course(1L, "CS101", 3, 30), LocalDate.of(2026, 9, 1));

        String[] lines = exporter.export(List.of(ada)).split("\r\n");

        assertThat(lines[0]).isEqualTo(StudentCsvExporter.HEADER);
        assertThat(lines[1]).isEqualTo(
                "1,Ada,Lovelace,ada.lovelace@university.edu,2002-05-14,CS101;MA101,2,7,2026-09-01T10:00:00Z");
    }

    @Test
    void quotesValuesWithCommasQuotesOrLineBreaks() {
        assertThat(StudentCsvExporter.cell("Smith, Jr.")).isEqualTo("\"Smith, Jr.\"");
        assertThat(StudentCsvExporter.cell("O\"Neil")).isEqualTo("\"O\"\"Neil\"");
        assertThat(StudentCsvExporter.cell("plain")).isEqualTo("plain");
        assertThat(StudentCsvExporter.cell(null)).isEmpty();
    }

    @Test
    void neutralizesSpreadsheetFormulas() {
        assertThat(StudentCsvExporter.cell("=HYPERLINK(\"http://evil\")")).startsWith("\"'=HYPERLINK");
        assertThat(StudentCsvExporter.cell("+1")).isEqualTo("'+1");
        assertThat(StudentCsvExporter.cell("@cmd")).isEqualTo("'@cmd");
    }

    @Test
    void writesOnlyTheHeader_whenThereAreNoStudents() {
        assertThat(exporter.export(List.of())).isEqualTo(StudentCsvExporter.HEADER + "\r\n");
    }
}
