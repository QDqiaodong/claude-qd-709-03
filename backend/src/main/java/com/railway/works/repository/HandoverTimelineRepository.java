package com.railway.works.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.railway.works.domain.HandoverTimeline;

public interface HandoverTimelineRepository extends JpaRepository<HandoverTimeline, Long> {

    List<HandoverTimeline> findAllByHandoverIdOrderByCreatedAtAsc(Long handoverId);
}
