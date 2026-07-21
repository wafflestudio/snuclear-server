ALTER TABLE courses
    ADD COLUMN course_title_normalized VARCHAR(255)
        GENERATED ALWAYS AS (REPLACE(course_title, ' ', '')) STORED,
    ADD COLUMN instructor_normalized VARCHAR(100)
        GENERATED ALWAYS AS (REPLACE(instructor, ' ', '')) STORED;

CREATE INDEX idx_courses_target_department
    ON courses (year, semester, department);

CREATE INDEX idx_courses_target_college
    ON courses (year, semester, college);

CREATE INDEX idx_courses_target_classification
    ON courses (year, semester, classification);

CREATE INDEX idx_courses_target_academic_year
    ON courses (year, semester, academic_year);

CREATE INDEX idx_courses_target_course_title_normalized
    ON courses (year, semester, course_title_normalized);

CREATE INDEX idx_courses_target_instructor_normalized
    ON courses (year, semester, instructor_normalized);

CREATE INDEX idx_courses_target_sort
    ON courses (year, semester, course_title, id);
