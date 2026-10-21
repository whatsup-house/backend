package com.whatsuphouse.backend.domain.matching.service;

import com.whatsuphouse.backend.domain.application.admin.service.AdminApplicationService;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.entity.ApplicationAnswer;
import com.whatsuphouse.backend.domain.dining.entity.MatchingWeights;
import com.whatsuphouse.backend.domain.form.entity.FormQuestion;
import com.whatsuphouse.backend.domain.form.enums.QuestionType;
import com.whatsuphouse.backend.domain.form.enums.SystemQuestionKey;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.matching.dto.response.DiningTableExplanationResponse;
import com.whatsuphouse.backend.domain.matching.entity.DiningTable;
import com.whatsuphouse.backend.domain.matching.entity.DiningTableMember;
import com.whatsuphouse.backend.domain.matching.enums.AssignReason;
import com.whatsuphouse.backend.domain.matching.repository.DiningTableMemberRepository;
import com.whatsuphouse.backend.domain.matching.repository.DiningTableRepository;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.domain.user.service.UserService;
import com.whatsuphouse.backend.global.exception.CustomException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;

/** 관리자 매칭 근거(설계 6.3): 현재 멤버로 다시 계산하고, 응답 원문에서 이름·연락처·이메일은 뺀다. */
@ExtendWith(MockitoExtension.class)
class DiningTableExplanationServiceTest {

    @Mock
    private DiningTableRepository diningTableRepository;
    @Mock
    private DiningTableMemberRepository diningTableMemberRepository;
    @Mock
    private DiningMatchService diningMatchService;
    @Mock
    private AdminApplicationService adminApplicationService;
    @Mock
    private UserService userService;
    @Spy
    private MatchingEngine matchingEngine = new MatchingEngine();

    @InjectMocks
    private DiningTableExplanationService diningTableExplanationService;

    @Test
    @DisplayName("근거: 엔진 점수·쌍·장점 문구와 멤버 응답 원문(당시 라벨, 목록은 쉼표, 시스템 질문 제외, 질문 순)")
    void getExplanation_returnsScoresAndAnswersWithoutContacts() {
        // given
        Gathering gathering = Gathering.builder().title("우연한 식탁").gatheringType(GatheringType.RANDOM_TABLE).build();
        GatheringSession session = GatheringSession.builder().gathering(gathering).eventDate(LocalDate.now()).maxAttendees(12).build();
        DiningTable table = DiningTable.builder().session(session).eventDate(session.getEventDate()).region("강남/신논현")
                .groupSize(2).algorithmVersion(MatchingEngine.ALGORITHM_VERSION).build();
        UUID tableId = UUID.randomUUID();
        ReflectionTestUtils.setField(table, "id", tableId);

        FormQuestion name = question(SystemQuestionKey.NAME.getKey(), "이름", 0);
        FormQuestion phone = question(SystemQuestionKey.PHONE.getKey(), "연락처", 1);
        FormQuestion mbti = question("mbti", "MBTI", 3);
        FormQuestion interests = question("interests", "관심사", 2);
        Application first = application(0, "SOFTWARE_DEVELOPER",
                Map.of("questions", List.of(Map.of("questionId", interests.getId().toString(), "label", "요즘 관심사"))));
        Application second = application(1, null, null);
        List<Application> applications = List.of(first, second);
        given(diningTableRepository.findById(tableId)).willReturn(Optional.of(table));
        given(diningTableMemberRepository.findByTableIdWithApplication(tableId)).willReturn(applications.stream()
                .map(application -> DiningTableMember.builder().application(application).table(table)
                        .seatOrder(1).assignReason(AssignReason.INITIAL).build())
                .toList());
        given(adminApplicationService.findAnswers(anyCollection())).willReturn(List.of(
                answer(first, mbti, "ENFP"), answer(first, name, "김와썹"), answer(first, phone, "01012345678"),
                answer(first, interests, List.of("전시", "와인")), answer(second, interests, "")));

        Map<UUID, MatchingEngine.Applicant> profiles = Map.of(
                first.getId(), profile(first, "FEMALE", "ENFP", "전시", "와인"),
                second.getId(), profile(second, "MALE", "INFJ", "와인"));
        MatchingEngine.Rules rules = new MatchingEngine.Rules(2, 6, 10, MatchingWeights.defaults(), List.of());
        given(diningMatchService.evaluator(any(), anyCollection(), any())).willReturn(new DiningMatchService.TableEvaluator(
                matchingEngine, profiles, rules, MatchingEngine.Relations.none()));
        given(userService.resolveCharacterUrl(any())).willAnswer(invocation -> "https://cdn/" + invocation.getArgument(0) + ".png");

        // when
        DiningTableExplanationResponse response = diningTableExplanationService.getExplanation(tableId);

        // then
        List<MatchingEngine.Applicant> members = List.of(profiles.get(first.getId()), profiles.get(second.getId()));
        MatchingEngine.Score expected = matchingEngine.score(members, rules, MatchingEngine.Relations.none());
        assertThat(response.getTableId()).isEqualTo(tableId);
        assertThat(response.getRegion()).isEqualTo("강남/신논현");
        assertThat(response.getGroupScore()).isEqualTo(expected.value());
        assertThat(response.getScoreDetail().penalties()).allSatisfy((key, value) -> assertThat(value).isPositive());
        assertThat(response.getPairs()).singleElement().satisfies(pair -> {
            assertThat(pair.getA()).isEqualTo(first.getId());
            assertThat(pair.getB()).isEqualTo(second.getId());
            assertThat(pair.getComponents()).containsOnlyKeys("ageBand", "gender", "mbti", "interests");
        });
        assertThat(response.getStrengths()).isNotEmpty();
        assertThat(response.getMembers()).extracting(DiningTableExplanationResponse.MemberView::getJob,
                        DiningTableExplanationResponse.MemberView::getCharacterUrl, DiningTableExplanationResponse.MemberView::getMbti)
                .containsExactly(tuple("개발자", "https://cdn/SOFTWARE_DEVELOPER.png", "ENFP"),
                        tuple(null, "https://cdn/null.png", "INFJ"));
        assertThat(response.getMembers().get(0).getAnswers())
                .extracting(DiningTableExplanationResponse.AnswerView::getLabel, DiningTableExplanationResponse.AnswerView::getValue)
                .containsExactly(tuple("요즘 관심사", "전시, 와인"), tuple("MBTI", "ENFP"));
        assertThat(response.getMembers().get(1).getAnswers()).isEmpty();
    }

    @Test
    @DisplayName("없는 테이블이면 CustomException")
    void getExplanation_notFound_throwsCustomException() {
        // given
        UUID tableId = UUID.randomUUID();
        given(diningTableRepository.findById(tableId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> diningTableExplanationService.getExplanation(tableId)).isInstanceOf(CustomException.class);
    }

    private static FormQuestion question(String key, String label, int displayOrder) {
        FormQuestion question = FormQuestion.builder().questionKey(key).type(QuestionType.SHORT_TEXT).label(label)
                .displayOrder(displayOrder).build();
        ReflectionTestUtils.setField(question, "id", UUID.randomUUID());
        return question;
    }

    private static Application application(int no, String job, Map<String, Object> formSnapshot) {
        User user = User.builder().email("u" + no + "@example.com").name("회원" + no).nickname("닉" + no).job(job).build();
        ReflectionTestUtils.setField(user, "id", new UUID(1, no));
        Gathering gathering = Gathering.builder().title("우연한 식탁").gatheringType(GatheringType.RANDOM_TABLE).build();
        Application application = Application.builder().bookingNumber("WH-" + no).gathering(gathering).user(user)
                .name(user.getName()).phone("01012345678").formSnapshot(formSnapshot).build();
        ReflectionTestUtils.setField(application, "id", new UUID(0, no));
        return application;
    }

    private static ApplicationAnswer answer(Application application, FormQuestion question, Object value) {
        return ApplicationAnswer.builder().application(application).question(question).value(Map.of("value", value)).build();
    }

    private static MatchingEngine.Applicant profile(Application application, String gender, String mbti, String... interests) {
        return new MatchingEngine.Applicant(application.getId(), application.getUser().getId(), 1995, gender, mbti,
                Set.of(interests), Set.of(), Set.of(), null, Map.of());
    }
}
