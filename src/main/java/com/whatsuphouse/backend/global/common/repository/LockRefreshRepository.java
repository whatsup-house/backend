package com.whatsuphouse.backend.global.common.repository;

/**
 * 잠금 후 재조회 조각(Spring Data fragment). 각 도메인 Repository가 상속해 자기 엔티티에만 쓴다.
 * <p>
 * 같은 트랜잭션에서 엔티티가 이미 영속성 컨텍스트에 올라와 있으면 {@code ...ForUpdate} 잠금 쿼리는 행을 잠그지만
 * Hibernate는 기존(오래된) 인스턴스를 그대로 돌려준다. 상태 가드 전에 이걸 불러 DB 최신 상태로 덮어쓴다. (KAN-403)
 */
public interface LockRefreshRepository<T> {

    /** PESSIMISTIC_WRITE로 잠그고 DB 상태로 다시 읽는다. 아직 flush하지 않은 이 엔티티의 변경은 버려진다. */
    void refreshForUpdate(T entity);
}
