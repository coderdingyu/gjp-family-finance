package com.gjp.record;

import com.gjp.common.Role;
import com.gjp.common.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecordAskGuardTest {

    @AfterEach
    void clear() {
        UserContext.clear();
    }

    @Test
    void memberAlwaysSelfEvenIfLlmAsksOthers() {
        Long got = RecordAskGuard.sanitizeMemberId(99L, 1L, false, 7L, Set.of(7L));
        assertEquals(7L, got);
    }

    @Test
    void ownerCanScopeToVisibleMember() {
        Long got = RecordAskGuard.sanitizeMemberId(2L, null, true, null, Set.of(1L, 2L));
        assertEquals(2L, got);
    }

    @Test
    void ownerIgnoresForeignMemberId() {
        Long got = RecordAskGuard.sanitizeMemberId(9999L, null, true, null, Set.of(1L, 2L));
        assertNull(got);
        Long keepRequested = RecordAskGuard.sanitizeMemberId(9999L, 1L, true, null, Set.of(1L, 2L));
        assertEquals(1L, keepRequested);
    }

    @Test
    void concurrentResolveDoesNotLeak() throws Exception {
        UserContext.LoginUser owner = user(1L, 10L, 101L, Role.OWNER);
        UserContext.LoginUser member = user(2L, 10L, 202L, Role.MEMBER);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        try {
            Future<Long> a = pool.submit(() -> {
                UserContext.set(owner);
                barrier.await(5, TimeUnit.SECONDS);
                Long id = UserContext.resolveMemberId(303L);
                UserContext.clear();
                return id;
            });
            Future<Long> b = pool.submit(() -> {
                UserContext.set(member);
                barrier.await(5, TimeUnit.SECONDS);
                Long id = UserContext.resolveMemberId(303L);
                UserContext.clear();
                return id;
            });
            assertEquals(303L, a.get(5, TimeUnit.SECONDS));
            assertEquals(202L, b.get(5, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void adminForbidden() {
        UserContext.set(user(9L, 0L, null, Role.ADMIN));
        assertThrows(RuntimeException.class, () -> UserContext.resolveMemberId(1L));
    }

    private static UserContext.LoginUser user(long userId, long familyId, Long memberId, int role) {
        UserContext.LoginUser u = new UserContext.LoginUser();
        u.setUserId(userId);
        u.setUsername("u" + userId);
        u.setFamilyId(familyId);
        u.setMemberId(memberId);
        u.setRole(role);
        return u;
    }
}
