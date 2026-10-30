package com.example.studentmgmt.service.impl;

import com.example.studentmgmt.dto.CourseResponse;
import com.example.studentmgmt.dto.StatsResponse;
import com.example.studentmgmt.dto.StatsResponse.CreditBand;
import com.example.studentmgmt.dto.StatsResponse.MonthlyCount;
import com.example.studentmgmt.dto.StudentResponse;
import com.example.studentmgmt.entity.Course;
import com.example.studentmgmt.entity.Student;
import com.example.studentmgmt.mapper.CourseMapper;
import com.example.studentmgmt.mapper.StudentMapper;
import com.example.studentmgmt.repository.CourseRepository;
import com.example.studentmgmt.repository.EnrollmentRepository;
import com.example.studentmgmt.repository.StudentRepository;
import com.example.studentmgmt.repository.projection.CourseEnrollmentCount;
import com.example.studentmgmt.repository.projection.MonthlyEnrollmentCount;
import com.example.studentmgmt.repository.projection.StudentEnrollmentSummary;
import com.example.studentmgmt.service.StatsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class StatsServiceImpl implements StatsService {

    private static final int TOP_N = 5;
    private static final int MONTHS = 6;

    /** Credit-load bands for the donut chart. 12+ credits is a typical full-time load. */
    private static final List<CreditBand> BANDS = List.of(
            new CreditBand("Not enrolled", 0, 0, 0),
            new CreditBand("1–6 credits", 1, 6, 0),
            new CreditBand("7–11 credits", 7, 11, 0),
            new CreditBand("12–17 credits", 12, 17, 0),
            new CreditBand("18+ credits", 18, null, 0));

    private final StudentRepository studentRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final StudentMapper studentMapper;
    private final CourseMapper courseMapper;
    private final Clock clock;

    public StatsServiceImpl(StudentRepository studentRepository,
                            CourseRepository courseRepository,
                            EnrollmentRepository enrollmentRepository,
                            StudentMapper studentMapper,
                            CourseMapper courseMapper,
                            Clock clock) {
        this.studentRepository = studentRepository;
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.studentMapper = studentMapper;
        this.courseMapper = courseMapper;
        this.clock = clock;
    }

    @Override
    public StatsResponse getStats() {
        long totalStudents = studentRepository.count();
        long totalEnrollments = enrollmentRepository.count();
        long totalCredits = enrollmentRepository.sumAllCredits();

        List<Course> courses = courseRepository.findAll();
        Map<Long, Long> counts = courses.isEmpty() ? Map.of()
                : enrollmentRepository.countByCourseIds(courses.stream().map(Course::getId).toList()).stream()
                .collect(Collectors.toMap(CourseEnrollmentCount::courseId, CourseEnrollmentCount::enrolledCount));
        long totalCapacity = courses.stream().mapToLong(Course::getCapacity).sum();

        List<CourseResponse> byEnrollment = courses.stream()
                .map(course -> courseMapper.toResponse(course, counts.getOrDefault(course.getId(), 0L)))
                .sorted(Comparator.comparingInt(CourseResponse::enrolledCount).reversed()
                        .thenComparing(CourseResponse::code))
                .toList();

        List<Student> recent = studentRepository.findTop5ByOrderByCreatedAtDescIdDesc();
        Map<Long, StudentEnrollmentSummary> summaries = recent.isEmpty() ? Map.of()
                : enrollmentRepository.summarizeByStudentIds(recent.stream().map(Student::getId).toList()).stream()
                .collect(Collectors.toMap(StudentEnrollmentSummary::studentId, Function.identity()));
        List<StudentResponse> recentStudents = recent.stream()
                .map(student -> studentMapper.toResponse(student, summaries.get(student.getId())))
                .toList();

        double averageCredits = totalStudents == 0 ? 0 : Math.round(totalCredits * 10.0 / totalStudents) / 10.0;
        int utilization = totalCapacity == 0 ? 0 : (int) Math.round(totalEnrollments * 100.0 / totalCapacity);

        return new StatsResponse(totalStudents, courses.size(), totalEnrollments, averageCredits, utilization,
                byEnrollment.stream().limit(TOP_N).toList(), recentStudents,
                byEnrollment, enrollmentsByMonth(), creditLoad(totalStudents));
    }

    /** The last six calendar months (current one included), with zero for months without enrollments. */
    private List<MonthlyCount> enrollmentsByMonth() {
        YearMonth current = YearMonth.now(clock);
        YearMonth first = current.minusMonths(MONTHS - 1L);
        Map<YearMonth, Long> counts = enrollmentRepository.countPerMonthSince(first.atDay(1)).stream()
                .collect(Collectors.toMap(c -> YearMonth.of(c.year(), c.month()), MonthlyEnrollmentCount::count));
        List<MonthlyCount> months = new ArrayList<>();
        for (YearMonth month = first; !month.isAfter(current); month = month.plusMonths(1)) {
            months.add(new MonthlyCount(month.toString(), counts.getOrDefault(month, 0L)));
        }
        return months;
    }

    /** Students per credit-load band. Students without enrollments land in the first band. */
    private List<CreditBand> creditLoad(long totalStudents) {
        List<StudentEnrollmentSummary> enrolled = enrollmentRepository.summarizeAllStudents();
        long[] perBand = new long[BANDS.size()];
        perBand[0] = totalStudents - enrolled.size();
        for (StudentEnrollmentSummary summary : enrolled) {
            long credits = summary.totalCredits();
            for (int i = 1; i < BANDS.size(); i++) {
                CreditBand band = BANDS.get(i);
                if (credits >= band.minCredits() && (band.maxCredits() == null || credits <= band.maxCredits())) {
                    perBand[i]++;
                    break;
                }
            }
        }
        List<CreditBand> result = new ArrayList<>();
        for (int i = 0; i < BANDS.size(); i++) {
            CreditBand band = BANDS.get(i);
            result.add(new CreditBand(band.label(), band.minCredits(), band.maxCredits(), perBand[i]));
        }
        return result;
    }
}
