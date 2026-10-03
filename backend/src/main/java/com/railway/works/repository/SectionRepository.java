package com.railway.works.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.railway.works.domain.Section;

public interface SectionRepository extends JpaRepository<Section, Long> {

    List<Section> findAllByOrderByStartMileAsc();

    List<Section> findAllByLineIdOrderByStartMileAsc(Long lineId);

    boolean existsBySectionNo(String sectionNo);
}
