package com.whatsuphouse.backend.domain.ticket.repository;

import com.whatsuphouse.backend.domain.ticket.entity.TicketPass;
import com.whatsuphouse.backend.domain.ticket.entity.TicketProductOption;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.domain.user.repository.UserRepository;
import com.whatsuphouse.backend.global.common.enums.Gender;
import com.whatsuphouse.backend.global.config.TestJpaConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestJpaConfig.class)
@ActiveProfiles("test")
class TicketPassRepositoryTest {

    @Autowired private TicketPassRepository ticketPassRepository;
    @Autowired private TicketProductRepository ticketProductRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TestEntityManager em;

    @Test
    @DisplayName("잠금 조회(ForUpdate)는 삭제되지 않은 이용권만 돌려준다")
    void findByIdAndDeletedAtIsNullForUpdate_skipsDeleted() {
        User user = userRepository.save(User.builder()
                .email("lock@example.com").password("encoded").name("김철수")
                .gender(Gender.MALE).age(28).nickname("locker").phone("01012345678").build());
        TicketProductOption product = ticketProductRepository.save(new TicketProductOption("1회권", 1, 9900));
        TicketPass live = ticketPassRepository.save(new TicketPass(user, null, product));
        TicketPass deleted = ticketPassRepository.save(new TicketPass(user, null, product));
        deleted.delete();
        em.flush();
        em.clear();

        assertThat(ticketPassRepository.findByIdAndDeletedAtIsNullForUpdate(live.getId())).isPresent();
        assertThat(ticketPassRepository.findByIdAndDeletedAtIsNullForUpdate(deleted.getId())).isEmpty();
    }
}
