package com.whatsuphouse.backend.global.common.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

/** {@link LockRefreshRepository} 구현. Spring Data가 이름(…Impl)으로 찾아 각 Repository에 붙인다. */
public class LockRefreshRepositoryImpl<T> implements LockRefreshRepository<T> {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public void refreshForUpdate(T entity) {
        entityManager.refresh(entity, LockModeType.PESSIMISTIC_WRITE);
    }
}
