package com.loopers.infrastructure.member;

import com.loopers.domain.member.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByLoginId(String inputId);

    Optional<Member> findByLoginId(String loginId);
}
