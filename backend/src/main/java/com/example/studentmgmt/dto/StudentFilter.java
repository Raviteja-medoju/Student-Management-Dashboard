package com.example.studentmgmt.dto;

/**
 * Optional filters for the student list and CSV export. A null field means "don't filter on it".
 *
 * @param search     matches first name, last name, full name or email (case-insensitive)
 * @param courseId   only students enrolled in this course
 * @param enrolled   true = enrolled in at least one course, false = in none
 * @param minCredits only students whose enrolled courses add up to at least this many credits
 */
public record StudentFilter(String search, Long courseId, Boolean enrolled, Integer minCredits) {

    public static StudentFilter none() {
        return new StudentFilter(null, null, null, null);
    }

    public static StudentFilter search(String term) {
        return new StudentFilter(term, null, null, null);
    }
}
