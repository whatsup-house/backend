package com.whatsuphouse.backend.domain.auth.service;

import com.whatsuphouse.backend.domain.auth.dto.response.LoginResponse;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.domain.user.enums.Job;
import com.whatsuphouse.backend.domain.user.repository.UserRepository;
import com.whatsuphouse.backend.global.common.enums.Gender;
import com.whatsuphouse.backend.global.common.enums.Mbti;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 심사용 체험 계정. "체험 시작"을 누를 때마다 데모 회원을 새로 만들고 곧장 로그인시킨다.
 * 공유 계정이면 한 심사위원의 노쇼가 다음 심사위원 시연을 깨기 때문이다. 호출 횟수 제한은 RateLimitFilter(IP당 분당 3회). (KAN-407)
 * 이름은 실제 사람처럼 "김태정(심사용)" 형태로, 성별·나이·MBTI·직업은 계정마다 무작위로 정한다. (KAN-422)
 */
@Service
@Transactional
@RequiredArgsConstructor
public class DemoAccountService {

    public static final String NAME_SUFFIX = "(심사용)";
    private static final int MIN_AGE = 24;
    private static final int MAX_AGE = 34;
    private static final int NAME_ATTEMPTS = 20;

    private static final List<String> SURNAMES = List.of(
            "김", "이", "박", "최", "정", "강", "조", "윤", "장", "임",
            "한", "오", "서", "신", "권", "황", "안", "송", "류", "홍");
    private static final List<String> MALE_NAMES = List.of(
            "민준", "서준", "도윤", "예준", "시우", "주원", "하준", "지호", "준서", "건우",
            "현우", "우진", "지훈", "태정", "성민", "동현", "재원", "승현", "정우", "민재");
    private static final List<String> FEMALE_NAMES = List.of(
            "서연", "지우", "서윤", "하은", "민서", "지유", "윤서", "채원", "수아", "지민",
            "예린", "다은", "소연", "유진", "수빈", "은지", "혜원", "가은", "나연", "세아");
    // 2030 직장인·학생으로 자연스러운 직업만 고른다 (고등학생·판사 같은 항목은 뺀다)
    private static final List<Job> JOBS = List.of(
            Job.SOFTWARE_DEVELOPER, Job.DATA_ANALYST, Job.PRODUCT_MANAGER, Job.UX_UI_DESIGNER, Job.ILLUSTRATOR,
            Job.PHOTOGRAPHER, Job.EDITOR, Job.CONTENT_CREATOR, Job.BRAND_MARKETER, Job.MERCHANDISER,
            Job.BANKER, Job.ACCOUNTANT, Job.NURSE, Job.PHYSICAL_THERAPIST, Job.PHARMACIST,
            Job.TEACHER, Job.ACADEMY_INSTRUCTOR, Job.CIVIL_SERVANT, Job.BARISTA, Job.CHEF,
            Job.FLIGHT_ATTENDANT, Job.SEMICONDUCTOR_ENGINEER, Job.ARCHITECT, Job.HR, Job.FREELANCER,
            Job.GRADUATE_STUDENT, Job.UNIVERSITY_STUDENT);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;

    public LoginResponse createDemo() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Gender gender = random.nextBoolean() ? Gender.FEMALE : Gender.MALE;
        String name = uniqueName(gender, suffix, random);
        int age = random.nextInt(MIN_AGE, MAX_AGE + 1);
        User user = User.builder()
                .email("demo+" + suffix + "@whatsup.house")
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .name(name)
                .nickname(name)
                .gender(gender)
                // applications.phone이 NOT NULL이라 전화번호 없는 체험 계정은 신청 시 500. 고유할 필요 없는 가짜 번호(11자리)를 넣는다.
                .phone(String.format("0100000%04d", random.nextInt(10000)))
                .age(age)
                .birthDate(LocalDate.now().minusYears(age))
                .mbti(Mbti.values()[random.nextInt(Mbti.values().length)])
                .job(JOBS.get(random.nextInt(JOBS.size())).name())
                .intro("심사용 체험 계정입니다.")
                .build();
        user.markDemo();
        user.approveRandomTable();
        userRepository.save(user);
        return authService.issueLogin(user);
    }

    // 닉네임은 UNIQUE이고, 이름이 겹치면 입금 시뮬레이터가 후보 여러 건으로 막힌다. 안 쓰인 이름을 고른다.
    // ponytail: 성 20 × 이름 20 = 성별당 400개. 다 쓰이면 계정 접미사로 대신한다.
    private String uniqueName(Gender gender, String suffix, ThreadLocalRandom random) {
        List<String> givenNames = gender == Gender.FEMALE ? FEMALE_NAMES : MALE_NAMES;
        for (int i = 0; i < NAME_ATTEMPTS; i++) {
            String name = SURNAMES.get(random.nextInt(SURNAMES.size()))
                    + givenNames.get(random.nextInt(givenNames.size())) + NAME_SUFFIX;
            if (!userRepository.existsByNickname(name)) {
                return name;
            }
        }
        return "체험" + suffix + NAME_SUFFIX;
    }
}
